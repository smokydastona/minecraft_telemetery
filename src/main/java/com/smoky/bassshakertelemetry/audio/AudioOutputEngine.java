package com.smoky.bassshakertelemetry.audio;

import com.smoky.bassshakertelemetry.config.BstConfig;

import javax.sound.sampled.*;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class AudioOutputEngine {
    private static final AudioOutputEngine INSTANCE = new AudioOutputEngine();

    // 48kHz, mono rendered then duplicated to stereo for better device compatibility.
    private static final float SAMPLE_RATE = 48_000f;
    private static final int CHANNELS = 2;
    private static final int BYTES_PER_SAMPLE = 2; // 16-bit
    private static final AudioFormat FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            SAMPLE_RATE,
            16,
            CHANNELS,
            CHANNELS * BYTES_PER_SAMPLE,
            SAMPLE_RATE,
            false
    );

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Thread> audioThread = new AtomicReference<>();

    // Live telemetry inputs (client thread updates)
    private volatile double speed;
    private volatile double accel;
    private volatile boolean elytra;

    // Event triggers
    private final AtomicInteger damageBurstSamplesLeft = new AtomicInteger(0);
    private final AtomicInteger damageBurstTotalSamples = new AtomicInteger(1);
    private final AtomicInteger biomeChimeSamplesLeft = new AtomicInteger(0);
    private final AtomicInteger biomeChimeTotalSamples = new AtomicInteger(1);

    private AudioOutputEngine() {
    }

    public static AudioOutputEngine get() {
        return INSTANCE;
    }

    public AudioFormat format() {
        return FORMAT;
    }

    public void updateTelemetry(double speed, double accel, boolean elytra) {
        this.speed = speed;
        this.accel = accel;
        this.elytra = elytra;
    }

    public void triggerDamageBurst() {
        int burstMs = Math.max(10, BstConfig.get().damageBurstMs);
        int samples = (int) ((burstMs / 1000.0) * SAMPLE_RATE);
        damageBurstTotalSamples.set(Math.max(1, samples));
        damageBurstSamplesLeft.set(samples);
    }

    public void triggerBiomeChime() {
        // short low pulse (tactile-friendly)
        int samples = (int) ((50 / 1000.0) * SAMPLE_RATE);
        biomeChimeTotalSamples.set(Math.max(1, samples));
        biomeChimeSamplesLeft.set(Math.max(1, samples));
    }

    public synchronized void startOrRestart() {
        stop();
        if (!BstConfig.get().enabled()) {
            return;
        }
        running.set(true);
        Thread t = new Thread(this::runLoop, "BST-Audio");
        t.setDaemon(true);
        audioThread.set(t);
        t.start();
    }

    public synchronized void stop() {
        running.set(false);
        Thread t = audioThread.getAndSet(null);
        if (t != null) {
            try {
                t.join(1000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void runLoop() {
        SourceDataLine line = null;
        try {
            line = openLine();
            if (line == null) {
                running.set(false);
                return;
            }
            line.start();

            int framesPerChunk = 1024;
            byte[] buffer = new byte[framesPerChunk * CHANNELS * BYTES_PER_SAMPLE];

            double phase = 0.0;
            double chimePhase = 0.0;
            double ampSmoothed = 0.0;
            Random random = new Random();

            while (running.get()) {
                BstConfig.Data cfg = BstConfig.get();

                double localSpeed = this.speed;
                double localAccel = this.accel;
                boolean localElytra = this.elytra;

                double freq = cfg.speedToneBaseHz + (cfg.speedToneHzPerSpeed * localSpeed);
                freq = clamp(freq, 10.0, 200.0);

                double ampTarget = 0.0;
                if (cfg.speedToneEnabled) {
                    ampTarget += clamp(localSpeed * 0.8, 0.0, 1.0);
                }
                // Accel makes it punchier; keep sign but mostly magnitude.
                ampTarget += clamp(Math.abs(localAccel) * cfg.accelToAmp, 0.0, 0.65);
                if (localElytra) {
                    ampTarget += 0.12;
                }
                ampTarget = clamp(ampTarget, 0.0, 1.0);

                // 1-pole smoothing to avoid clicks
                ampSmoothed += (ampTarget - ampSmoothed) * 0.05;

                double master = clamp(cfg.masterVolume, 0.0, 1.0);

                int damageLeft = damageBurstSamplesLeft.get();
                int biomeLeft = biomeChimeSamplesLeft.get();

                int idx = 0;
                for (int i = 0; i < framesPerChunk; i++) {
                    double sample = 0.0;

                    if (cfg.speedToneEnabled) {
                        sample += Math.sin(phase) * ampSmoothed;
                    }

                    if (cfg.damageBurstEnabled && damageLeft > 0) {
                        // White noise with quick decay.
                        int total = Math.max(1, damageBurstTotalSamples.get());
                        double progress = 1.0 - (damageLeft / (double) total);
                        double env = Math.exp(-progress * 6.0);
                        sample += ((random.nextDouble() * 2.0) - 1.0) * cfg.damageBurstGain * env;
                        damageLeft--;
                    }

                    if (cfg.biomeChimeEnabled && biomeLeft > 0) {
                        // A short low sine "bump".
                        int total = Math.max(1, biomeChimeTotalSamples.get());
                        double progress = 1.0 - (biomeLeft / (double) total);
                        double env = Math.sin(progress * Math.PI); // bell-ish half-sine envelope
                        sample += Math.sin(chimePhase) * 0.35 * env;
                        biomeLeft--;
                    }

                    // very soft limiter
                    sample = clamp(sample, -1.0, 1.0);

                    short s16 = (short) (sample * master * 32767);

                    // stereo duplicate
                    buffer[idx++] = (byte) (s16 & 0xFF);
                    buffer[idx++] = (byte) ((s16 >>> 8) & 0xFF);
                    buffer[idx++] = (byte) (s16 & 0xFF);
                    buffer[idx++] = (byte) ((s16 >>> 8) & 0xFF);

                    phase += (2.0 * Math.PI * freq) / SAMPLE_RATE;
                    if (phase > (2.0 * Math.PI)) {
                        phase -= (2.0 * Math.PI);
                    }

                    // Fixed low chime oscillator (~80Hz)
                    chimePhase += (2.0 * Math.PI * 80.0) / SAMPLE_RATE;
                    if (chimePhase > (2.0 * Math.PI)) {
                        chimePhase -= (2.0 * Math.PI);
                    }
                }

                if (cfg.damageBurstEnabled) {
                    damageBurstSamplesLeft.set(Math.max(0, damageLeft));
                } else {
                    damageBurstSamplesLeft.set(0);
                }

                if (cfg.biomeChimeEnabled) {
                    biomeChimeSamplesLeft.set(Math.max(0, biomeLeft));
                } else {
                    biomeChimeSamplesLeft.set(0);
                }

                line.write(buffer, 0, buffer.length);
            }
        } catch (Exception ignored) {
        } finally {
            if (line != null) {
                try {
                    line.stop();
                } catch (Exception ignored) {
                }
                try {
                    line.flush();
                } catch (Exception ignored) {
                }
                try {
                    line.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private SourceDataLine openLine() {
        try {
            DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, FORMAT);
            Mixer.Info mixerInfo = AudioDeviceUtil.findMixerByName(BstConfig.get().outputDeviceName, FORMAT);
            if (mixerInfo == null) {
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
                line.open(FORMAT);
                return line;
            }

            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            SourceDataLine line = (SourceDataLine) mixer.getLine(lineInfo);
            line.open(FORMAT);
            return line;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
