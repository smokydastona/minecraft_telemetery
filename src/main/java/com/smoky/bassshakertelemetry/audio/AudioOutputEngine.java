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

    private volatile boolean telemetryLive;
    private volatile long lastTelemetryNanos;

    // Event triggers
    private final AtomicInteger damageBurstSamplesLeft = new AtomicInteger(0);
    private final AtomicInteger damageBurstTotalSamples = new AtomicInteger(1);
    private volatile double damageBurstIntensity = 1.0;
    private final AtomicInteger biomeChimeSamplesLeft = new AtomicInteger(0);
    private final AtomicInteger biomeChimeTotalSamples = new AtomicInteger(1);

    private final AtomicInteger accelBumpSamplesLeft = new AtomicInteger(0);
    private final AtomicInteger accelBumpTotalSamples = new AtomicInteger(1);
    private volatile long lastAccelBumpNanos;

    // Generic impulse (used by sound-to-haptics)
    private final AtomicInteger impulseSamplesLeft = new AtomicInteger(0);
    private final AtomicInteger impulseTotalSamples = new AtomicInteger(1);
    private volatile double impulseFreqHz = 36.0;
    private volatile double impulseGain = 0.0;
    private volatile double impulseNoiseMix = 0.25;
    private volatile double impulsePhase;
    private volatile double impulseNoiseState;

    // Damage burst noise filter state
    private volatile double damageNoiseState;

    // Road texture state
    private volatile double roadNoiseState;

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
        this.telemetryLive = true;
        this.lastTelemetryNanos = System.nanoTime();

        // Optional accel-driven thump, with a small cooldown to avoid machine-gun pulses.
        BstConfig.Data cfg = BstConfig.get();
        if (cfg.accelBumpEnabled) {
            double a = Math.abs(accel);
            if (a >= cfg.accelBumpThreshold) {
                long now = this.lastTelemetryNanos;
                if ((now - lastAccelBumpNanos) > 120_000_000L) { // 120ms
                    triggerAccelBump(clamp(a / (cfg.accelBumpThreshold * 2.0), 0.0, 1.0));
                    lastAccelBumpNanos = now;
                }
            }
        }
    }

    public void setTelemetryLive(boolean live) {
        this.telemetryLive = live;
        if (live) {
            this.lastTelemetryNanos = System.nanoTime();
        }
    }

    public void triggerDamageBurst() {
        triggerDamageBurst(1.0);
    }

    public void triggerDamageBurst(double intensity01) {
        int burstMs = Math.max(10, BstConfig.get().damageBurstMs);
        int samples = (int) ((burstMs / 1000.0) * SAMPLE_RATE);
        damageBurstTotalSamples.set(Math.max(1, samples));
        damageBurstSamplesLeft.set(samples);
        // Let the most intense recent hit win.
        damageBurstIntensity = Math.max(damageBurstIntensity, clamp(intensity01, 0.0, 1.0));
    }

    /**
     * Generic one-shot impulse. Intended for translating arbitrary events (e.g. game sounds) into tactile audio.
     */
    public void triggerImpulse(double freqHz, int durationMs, double gain01, double noiseMix01) {
        int ms = Math.max(10, durationMs);
        int samples = (int) ((ms / 1000.0) * SAMPLE_RATE);
        impulseTotalSamples.set(Math.max(1, samples));
        // Extend if already active.
        int current = impulseSamplesLeft.get();
        impulseSamplesLeft.set(Math.max(current, samples));

        impulseFreqHz = clamp(freqHz, 10.0, 120.0);
        impulseGain = Math.max(impulseGain, clamp(gain01, 0.0, 1.0));
        impulseNoiseMix = clamp(noiseMix01, 0.0, 1.0);
    }

    public void triggerBiomeChime() {
        // short low pulse (tactile-friendly)
        int samples = (int) ((50 / 1000.0) * SAMPLE_RATE);
        biomeChimeTotalSamples.set(Math.max(1, samples));
        biomeChimeSamplesLeft.set(Math.max(1, samples));
    }

    // --- UI test triggers (always audible even if the effect toggle is currently off) ---

    public void testRoadTexture() {
        // Short filtered-noise-ish burst via the impulse path.
        BstConfig.Data cfg = BstConfig.get();
        double gain01 = clamp(cfg.roadTextureGain / 0.30, 0.0, 1.0);
        triggerImpulse(34.0, 260, gain01, 1.0);
    }

    public void testDamageBurst() {
        BstConfig.Data cfg = BstConfig.get();
        double gain01 = clamp(cfg.damageBurstGain, 0.0, 1.0);
        triggerImpulse(42.0, Math.max(40, cfg.damageBurstMs), gain01, 0.90);
    }

    public void testBiomeChime() {
        BstConfig.Data cfg = BstConfig.get();
        double gain01 = clamp(cfg.biomeChimeGain, 0.0, 1.0);
        triggerImpulse(80.0, 90, gain01, 0.0);
    }

    public void testAccelBump() {
        BstConfig.Data cfg = BstConfig.get();
        double gain01 = clamp(cfg.accelBumpGain, 0.0, 1.0);
        triggerImpulse(32.0, Math.max(40, cfg.accelBumpMs), gain01, 0.0);
    }

    public void testSoundHaptics() {
        BstConfig.Data cfg = BstConfig.get();
        double gain01 = clamp(cfg.soundHapticsGain * 0.60, 0.0, 1.0);
        triggerImpulse(36.0, 120, gain01, 0.35);
    }

    public void testGameplayHaptics() {
        BstConfig.Data cfg = BstConfig.get();
        double gain01 = clamp(cfg.gameplayHapticsGain * 0.60, 0.0, 1.0);
        triggerImpulse(44.0, 120, gain01, 0.25);
    }

    public void testFootsteps() {
        BstConfig.Data cfg = BstConfig.get();
        double gain01 = clamp(cfg.footstepHapticsGain, 0.0, 1.0);
        triggerImpulse(44.0, 55, gain01, 0.42);
    }

    public void testMiningSwing() {
        BstConfig.Data cfg = BstConfig.get();
        double gain01 = clamp(cfg.miningSwingHapticsGain, 0.0, 1.0);
        triggerImpulse(46.0, 26, gain01, 0.08);
    }

    private void triggerAccelBump(double intensity01) {
        int bumpMs = Math.max(10, BstConfig.get().accelBumpMs);
        int samples = (int) ((bumpMs / 1000.0) * SAMPLE_RATE);
        accelBumpTotalSamples.set(Math.max(1, samples));
        accelBumpSamplesLeft.set(Math.max(1, samples));
        // Encode intensity by scaling total samples left a bit.
        // Actual gain is applied in the render loop.
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
            int framesPerChunk = 1024;
            byte[] buffer = new byte[framesPerChunk * CHANNELS * BYTES_PER_SAMPLE];

            double bumpPhase = 0.0;
            double chimePhase = 0.0;
            double streamGain = 0.0;
            Random random = new Random();

            final long staleNs = 1_000_000_000L; // 1s without telemetry => fade out
            final long sleepNs = 10_000_000_000L; // 10s without telemetry => close the audio line

            while (running.get()) {
                BstConfig.Data cfg = BstConfig.get();

                long nowNs = System.nanoTime();
                boolean hasFreshTelemetry = telemetryLive && ((nowNs - lastTelemetryNanos) <= staleNs);

                // If we've been stale for a long time, close the device to avoid rumble in menus.
                if (!hasFreshTelemetry && line != null && (nowNs - lastTelemetryNanos) > sleepNs) {
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
                    line = null;
                }

                if (line == null) {
                    if (!hasFreshTelemetry) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }

                    line = openLine();
                    if (line == null) {
                        running.set(false);
                        return;
                    }
                    try {
                        line.start();
                    } catch (Exception ignored) {
                        running.set(false);
                        return;
                    }
                }

                double localSpeed = this.speed;

                // Stream gating (fade in/out when telemetry appears/disappears)
                double targetStreamGain = hasFreshTelemetry ? 1.0 : 0.0;
                double startGain = streamGain;
                streamGain += (targetStreamGain - streamGain) * 0.08;
                double endGain = streamGain;

                // Output headroom + master
                double headroom = clamp(cfg.outputHeadroom, 0.10, 1.0);
                double master = clamp(cfg.masterVolume, 0.0, 1.0) * headroom;
                double limiterDrive = clamp(cfg.limiterDrive, 1.0, 8.0);

                int damageLeft = damageBurstSamplesLeft.get();
                int biomeLeft = biomeChimeSamplesLeft.get();
                int bumpLeft = accelBumpSamplesLeft.get();
                int impulseLeft = impulseSamplesLeft.get();

                // Effect priority / ducking: let short impacts read clearly.
                double duckContinuous = 1.0;
                if (cfg.damageBurstEnabled && damageLeft > 0) {
                    duckContinuous = 0.35;
                } else if (cfg.accelBumpEnabled && bumpLeft > 0) {
                    duckContinuous = 0.55;
                }

                int idx = 0;
                for (int i = 0; i < framesPerChunk; i++) {
                    double sample = 0.0;

                    double g = startGain + ((endGain - startGain) * (i / (double) framesPerChunk));

                    if (cfg.roadTextureEnabled) {
                        // A filtered noise rumble, speed-scaled.
                        double absSpeed = Math.abs(localSpeed);
                        // Keep this from feeling like an "engine/road" at normal walking speeds:
                        // ramp in later and with a gentler curve.
                        double speedRamp = clamp((absSpeed - 0.09) / 0.18, 0.0, 1.0);
                        speedRamp *= speedRamp;

                        double fc = clamp(cfg.roadTextureCutoffHz, 10.0, 80.0);
                        double a = 1.0 - Math.exp(-(2.0 * Math.PI * fc) / SAMPLE_RATE);
                        double white = (random.nextDouble() * 2.0) - 1.0;
                        roadNoiseState += (white - roadNoiseState) * a;

                        sample += roadNoiseState * cfg.roadTextureGain * speedRamp * duckContinuous;
                    }

                    if (cfg.damageBurstEnabled && damageLeft > 0) {
                        // White noise with quick decay.
                        int total = Math.max(1, damageBurstTotalSamples.get());
                        double progress = 1.0 - (damageLeft / (double) total);

                        // Softer, less "clicky" transient: apply a smooth attack envelope and low-pass the noise.
                        // This keeps the effect tactile (low-frequency) without harsh high-frequency punch.
                        double fc = 55.0;
                        double a = 1.0 - Math.exp(-(2.0 * Math.PI * fc) / SAMPLE_RATE);
                        double white = (random.nextDouble() * 2.0) - 1.0;
                        damageNoiseState += (white - damageNoiseState) * a;

                        double env = Math.sin(progress * Math.PI) * Math.exp(-progress * 5.0);
                        sample += damageNoiseState * cfg.damageBurstGain * clamp(damageBurstIntensity, 0.0, 1.0) * env;
                        damageLeft--;
                    }

                    if (impulseLeft > 0) {
                        int total = Math.max(1, impulseTotalSamples.get());
                        double progress = 1.0 - (impulseLeft / (double) total);
                        // Raised-cosine envelope (sin^2) reduces attack punch vs a simple half-sine.
                        double env = Math.sin(progress * Math.PI);
                        env *= env;

                        double step = (2.0 * Math.PI * impulseFreqHz) / SAMPLE_RATE;
                        double sine = Math.sin(impulsePhase);

                        // Low-pass the noise component to keep impulses tactile and less "snappy".
                        double fc = 65.0;
                        double a = 1.0 - Math.exp(-(2.0 * Math.PI * fc) / SAMPLE_RATE);
                        double white = (random.nextDouble() * 2.0) - 1.0;
                        impulseNoiseState += (white - impulseNoiseState) * a;

                        double w = (sine * (1.0 - impulseNoiseMix)) + (impulseNoiseState * impulseNoiseMix);
                        sample += w * impulseGain * env;

                        impulsePhase += step;
                        if (impulsePhase > (2.0 * Math.PI)) {
                            impulsePhase -= (2.0 * Math.PI);
                        }

                        impulseLeft--;
                    }

                    if (cfg.accelBumpEnabled && bumpLeft > 0) {
                        int total = Math.max(1, accelBumpTotalSamples.get());
                        double progress = 1.0 - (bumpLeft / (double) total);
                        double env = Math.sin(progress * Math.PI);
                        // Low thump around ~32Hz
                        double bump = Math.sin(bumpPhase) * env;
                        sample += bump * cfg.accelBumpGain;
                        bumpLeft--;
                    }

                    if (cfg.biomeChimeEnabled && biomeLeft > 0) {
                        // A short low sine "bump".
                        int total = Math.max(1, biomeChimeTotalSamples.get());
                        double progress = 1.0 - (biomeLeft / (double) total);
                        double env = Math.sin(progress * Math.PI); // bell-ish half-sine envelope
                        sample += Math.sin(chimePhase) * clamp(cfg.biomeChimeGain, 0.0, 1.0) * env;
                        biomeLeft--;
                    }

                    // Soft limiter (tanh saturation) + hard clamp as final safety.
                    sample = softClipTanh(sample, limiterDrive);
                    sample = clamp(sample, -1.0, 1.0);

                    short s16 = (short) (sample * master * g * 32767);

                    // stereo duplicate
                    buffer[idx++] = (byte) (s16 & 0xFF);
                    buffer[idx++] = (byte) ((s16 >>> 8) & 0xFF);
                    buffer[idx++] = (byte) (s16 & 0xFF);
                    buffer[idx++] = (byte) ((s16 >>> 8) & 0xFF);

                    // Fixed low thump oscillator (~32Hz) used for accel bump.
                    bumpPhase += (2.0 * Math.PI * 32.0) / SAMPLE_RATE;
                    if (bumpPhase > (2.0 * Math.PI)) {
                        bumpPhase -= (2.0 * Math.PI);
                    }

                    // Fixed low chime oscillator (~80Hz)
                    chimePhase += (2.0 * Math.PI * 80.0) / SAMPLE_RATE;
                    if (chimePhase > (2.0 * Math.PI)) {
                        chimePhase -= (2.0 * Math.PI);
                    }
                }

                if (cfg.damageBurstEnabled) {
                    damageBurstSamplesLeft.set(Math.max(0, damageLeft));
                    if (damageLeft <= 0) {
                        damageBurstIntensity = 1.0;
                    }
                } else {
                    damageBurstSamplesLeft.set(0);
                    damageBurstIntensity = 1.0;
                }

                if (cfg.accelBumpEnabled) {
                    accelBumpSamplesLeft.set(Math.max(0, bumpLeft));
                } else {
                    accelBumpSamplesLeft.set(0);
                }

                if (cfg.biomeChimeEnabled) {
                    biomeChimeSamplesLeft.set(Math.max(0, biomeLeft));
                } else {
                    biomeChimeSamplesLeft.set(0);
                }

                impulseSamplesLeft.set(Math.max(0, impulseLeft));
                if (impulseLeft <= 0) {
                    impulseGain *= 0.65;
                    if (impulseGain < 0.001) {
                        impulseGain = 0.0;
                    }
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
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, FORMAT);
        String preferred = BstConfig.get().outputDeviceName;

        // Try the preferred device first.
        if (preferred != null && !preferred.isBlank()) {
            try {
                Mixer.Info mixerInfo = AudioDeviceUtil.findMixerByName(preferred, FORMAT);
                if (mixerInfo != null) {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    SourceDataLine line = (SourceDataLine) mixer.getLine(lineInfo);
                    line.open(FORMAT);
                    return line;
                }
            } catch (Exception e) {
                System.err.println("[BST] Failed to open preferred audio device: " + preferred + " (" + e.getClass().getSimpleName() + ")");
            }
        }

        // Fallback: default output device.
        try {
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
            line.open(FORMAT);
            return line;
        } catch (Exception e) {
            System.err.println("[BST] Failed to open default audio device (" + e.getClass().getSimpleName() + ")");
            return null;
        }
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static double softClipTanh(double x, double drive) {
        double d = clamp(drive, 1.0, 12.0);
        double denom = Math.tanh(d);
        if (denom == 0.0) {
            return clamp(x, -1.0, 1.0);
        }
        return Math.tanh(x * d) / denom;
    }
}
