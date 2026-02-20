package com.smoky.bassshakertelemetry.audio;

import com.smoky.bassshakertelemetry.config.BstConfig;
import com.smoky.bassshakertelemetry.telemetryout.TelemetryOut;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class AudioOutputEngine {
    private static final AudioOutputEngine INSTANCE = new AudioOutputEngine();

    private static final Logger LOGGER = LogManager.getLogger("bassshakertelemetry");

    /**
     * Non-optional: mono means only one dominant vibration at a time.
     * Lower-priority sources get ducked.
     */
    private static final double DUCK_FACTOR = 0.30;

        // 48kHz PCM output.
    private static final float SAMPLE_RATE = 48_000f;
    private static final int BYTES_PER_SAMPLE = 2; // 16-bit

        private static final AudioFormat FORMAT_STEREO = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            SAMPLE_RATE,
            16,
            2,
            2 * BYTES_PER_SAMPLE,
            SAMPLE_RATE,
            false
    );

        private static final AudioFormat FORMAT_7_1 = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            SAMPLE_RATE,
            16,
            8,
            8 * BYTES_PER_SAMPLE,
            SAMPLE_RATE,
            false
        );

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Thread> audioThread = new AtomicReference<>();

    // Last successfully opened output channel count (2 or 8). Used for UI/status.
    private volatile int activeOutputChannels = 2;

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

    // Generic impulses (used by sound-to-haptics / gameplay one-shots)
    private final Object impulseLock = new Object();
    private final ArrayList<ImpulseVoice> impulses = new ArrayList<>();

    // Debug-only: last computed dominant source (updated on dominance changes).
    private volatile String debugDominantLabel = "none";
    private volatile int debugDominantPriority = -1;
    private volatile double debugDominantFreqHz;
    private volatile double debugDominantGain01;

    // Hysteresis for debug snapshot updates (overlay-only; does not affect audio).
    private static final long DEBUG_DOMINANT_HYSTERESIS_NS = 15_000_000L; // 15ms
    private String pendingDominantLabel = "none";
    private int pendingDominantPriority = -1;
    private double pendingDominantFreqHz;
    private double pendingDominantGain01;
    private long pendingDominantSinceNs;

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
        return preferredFormat();
    }

    /**
     * Explicit 7.1 format for probing device support.
     */
    public AudioFormat format7_1() {
        return FORMAT_7_1;
    }

    public int getActiveOutputChannels() {
        return activeOutputChannels;
    }

    private static AudioFormat preferredFormat() {
        BstConfig.Data cfg = BstConfig.get();
        if (cfg != null && cfg.soundScapeEnabled && cfg.soundScapeChannels == 8) {
            return FORMAT_7_1;
        }
        return FORMAT_STEREO;
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
        triggerImpulse(freqHz, durationMs, gain01, noiseMix01, "single", 160, 60);
    }

    /**
     * Pattern-capable impulse.
     * <p>
     * Supported patterns: single, pulse_loop, shockwave, fade_out.
     */
    public void triggerImpulse(double freqHz, int durationMs, double gain01, double noiseMix01, String pattern, int pulsePeriodMs, int pulseWidthMs) {
        triggerImpulse(freqHz, durationMs, gain01, noiseMix01, pattern, pulsePeriodMs, pulseWidthMs, 5, 0);
    }

    /**
     * Pattern-capable impulse with explicit priority and start delay.
     *
     * @param priority 0..100 (higher wins). Only one dominant voice plays full-strength at a time.
     * @param delayMs  optional micro-delay (can be 0). Negative treated as 0.
     */
    public void triggerImpulse(double freqHz, int durationMs, double gain01, double noiseMix01, String pattern, int pulsePeriodMs, int pulseWidthMs, int priority, int delayMs) {
        triggerImpulse(freqHz, durationMs, gain01, noiseMix01, pattern, pulsePeriodMs, pulseWidthMs, priority, delayMs, null);
    }

    /**
     * Same as {@link #triggerImpulse(double, int, double, double, String, int, int, int, int)} but carries a debug label.
     * This does not affect audio; it's used only for the debug overlay.
     */
    public void triggerImpulse(double freqHz, int durationMs, double gain01, double noiseMix01, String pattern, int pulsePeriodMs, int pulseWidthMs, int priority, int delayMs, String debugKey) {
        triggerImpulseInternal(freqHz, freqHz, durationMs, gain01, noiseMix01, pattern, pulsePeriodMs, pulseWidthMs, priority, delayMs, debugKey);
    }

    /**
     * Frequency sweep impulse: linearly moves from startFreqHz to endFreqHz over the impulse duration.
     * Uses the same envelope/pattern system as {@link #triggerImpulse(double, int, double, double, String, int, int, int, int, String)}.
     */
    public void triggerSweepImpulse(double startFreqHz, double endFreqHz, int durationMs, double gain01, double noiseMix01, String pattern,
                                   int pulsePeriodMs, int pulseWidthMs, int priority, int delayMs, String debugKey) {
        triggerImpulseInternal(startFreqHz, endFreqHz, durationMs, gain01, noiseMix01, pattern, pulsePeriodMs, pulseWidthMs, priority, delayMs, debugKey);
    }

    private void triggerImpulseInternal(double startFreqHz, double endFreqHz, int durationMs, double gain01, double noiseMix01, String pattern,
                                        int pulsePeriodMs, int pulseWidthMs, int priority, int delayMs, String debugKey) {
        int ms = Math.max(10, durationMs);
        int samples = (int) ((ms / 1000.0) * SAMPLE_RATE);
        samples = Math.max(1, samples);

        double f0 = clamp(startFreqHz, 10.0, 120.0);
        double f1 = clamp(endFreqHz, 10.0, 120.0);
        double g = clamp(gain01, 0.0, 1.0);
        double n = clamp(noiseMix01, 0.0, 1.0);
        String pat = (pattern == null || pattern.isBlank()) ? "single" : pattern;

        int periodS = (int) ((Math.max(20, pulsePeriodMs) / 1000.0) * SAMPLE_RATE);
        int widthS = (int) ((Math.max(10, pulseWidthMs) / 1000.0) * SAMPLE_RATE);
        int pulsePeriodSamples = Math.max(1, periodS);
        int pulseWidthSamples = Math.max(1, Math.min(Math.max(1, widthS), pulsePeriodSamples));

        int pri = clampInt(priority, 0, 100);
        int delaySamples = (int) ((Math.max(0, delayMs) / 1000.0) * SAMPLE_RATE);
        delaySamples = Math.max(0, delaySamples);

        String dk = (debugKey == null) ? "" : debugKey.trim();

        synchronized (impulseLock) {
            // Coalesce/extend a very similar active voice to avoid stacking identical pulses.
            for (ImpulseVoice v : impulses) {
                String vdk = (v.debugKey == null) ? "" : v.debugKey;
                if (!dk.equalsIgnoreCase(vdk)) {
                    continue;
                }
                if (v.priority != pri) {
                    continue;
                }
                if (v.delaySamplesLeft != delaySamples) {
                    continue;
                }
                if (!pat.equalsIgnoreCase(v.pattern)) {
                    continue;
                }
                if (Math.abs(v.startFreqHz - f0) > 0.75) {
                    continue;
                }
                if (Math.abs(v.endFreqHz - f1) > 0.75) {
                    continue;
                }

                v.totalSamples = Math.max(v.totalSamples, samples);
                v.samplesLeft = Math.max(v.samplesLeft, samples);
                v.gain = Math.max(v.gain, g);
                v.noiseMix = n;
                v.pulsePeriodSamples = pulsePeriodSamples;
                v.pulseWidthSamples = pulseWidthSamples;
                v.createdNanos = System.nanoTime();
                return;
            }

            ImpulseVoice voice = new ImpulseVoice();
            voice.totalSamples = samples;
            voice.samplesLeft = samples;
            voice.delaySamplesLeft = delaySamples;
            voice.freqHz = f0;
            voice.startFreqHz = f0;
            voice.endFreqHz = f1;
            voice.gain = g;
            voice.noiseMix = n;
            voice.pattern = pat;
            voice.debugKey = dk;
            voice.pulsePeriodSamples = pulsePeriodSamples;
            voice.pulseWidthSamples = pulseWidthSamples;
            voice.phase = 0.0;
            voice.noiseState = 0.0;
            voice.priority = pri;
            voice.createdNanos = System.nanoTime();
            impulses.add(voice);

            // Hard cap to avoid unbounded growth in pathological cases.
            while (impulses.size() > 24) {
                impulses.remove(0);
            }
        }

        if (BstConfig.get().webSocketEnabled && BstConfig.get().webSocketSendHapticEvents) {
            TelemetryOut.emitHaptic(dk, f0, f1, ms, g, n, pat, pulsePeriodMs, pulseWidthMs, pri, delayMs);
            if (BstConfig.get().webSocketSendUnifiedEvents) {
                TelemetryOut.emitEventFromHapticKey(dk, g);
            }
        }
    }

    public String getDominantDebugString() {
        String label = debugDominantLabel;
        int pri = debugDominantPriority;
        if (label == null || label.isBlank() || "none".equalsIgnoreCase(label) || pri < 0) {
            return "";
        }
        return String.format(java.util.Locale.ROOT, "dominant=%s pri=%d freq=%.1fHz gain=%.2f", label, pri, debugDominantFreqHz, debugDominantGain01);
    }

    public void triggerBiomeChime() {
        // short low pulse (tactile-friendly)
        int samples = (int) ((50 / 1000.0) * SAMPLE_RATE);
        biomeChimeTotalSamples.set(Math.max(1, samples));
        biomeChimeSamplesLeft.set(Math.max(1, samples));
    }

    // --- UI test triggers (always audible even if the effect toggle is currently off) ---

    public void testRoadTexture() {
        wakeForTests();
        // Short filtered-noise-ish burst via the impulse path.
        BstConfig.Data cfg = BstConfig.get();
        double gain01 = clamp(cfg.roadTextureGain / 0.30, 0.0, 1.0);
        triggerImpulse(34.0, 260, gain01, 1.0);
    }

    public void testDamageBurst() {
        wakeForTests();
        BstConfig.Data cfg = BstConfig.get();
        double gain01 = clamp(cfg.damageBurstGain, 0.0, 1.0);
        triggerImpulse(42.0, Math.max(40, cfg.damageBurstMs), gain01, 0.90);
    }

    public void testBiomeChime() {
        wakeForTests();
        BstConfig.Data cfg = BstConfig.get();
        double gain01 = clamp(cfg.biomeChimeGain, 0.0, 1.0);
        triggerImpulse(80.0, 90, gain01, 0.0);
    }

    public void testAccelBump() {
        wakeForTests();
        BstConfig.Data cfg = BstConfig.get();
        double gain01 = clamp(cfg.accelBumpGain, 0.0, 1.0);
        triggerImpulse(32.0, Math.max(40, cfg.accelBumpMs), gain01, 0.0);
    }

    public void testSoundHaptics() {
        wakeForTests();
        BstConfig.Data cfg = BstConfig.get();
        double gain01 = clamp(cfg.soundHapticsGain * 0.60, 0.0, 1.0);
        triggerImpulse(36.0, 120, gain01, 0.35);
    }

    public void testGameplayHaptics() {
        wakeForTests();
        BstConfig.Data cfg = BstConfig.get();
        double gain01 = clamp(cfg.gameplayHapticsGain * 0.60, 0.0, 1.0);
        triggerImpulse(44.0, 120, gain01, 0.25);
    }

    public void testFootsteps() {
        wakeForTests();
        BstConfig.Data cfg = BstConfig.get();
        double gain01 = clamp(cfg.footstepHapticsGain, 0.0, 1.0);
        triggerImpulse(44.0, 55, gain01, 0.42);
    }

    public void testMountedHooves() {
        wakeForTests();
        BstConfig.Data cfg = BstConfig.get();
        double knob = clamp(cfg.mountedHapticsGain, 0.0, 1.0);

        var store = com.smoky.bassshakertelemetry.config.BstVibrationProfiles.get();
        var resolved = store.resolve("mount.hoof", 1.0, 1.0);
        if (resolved == null) {
            double gain01 = clamp(knob * 0.55, 0.0, 1.0);
            triggerImpulse(52.0, 70, gain01, 0.28, "punch", 160, 60, 2, 0, "mount.hoof");
            triggerImpulse(48.0, 50, clamp(gain01 * 0.65, 0.0, 1.0), 0.40, "punch", 160, 60, 1, 32, "mount.hoof_tail");
            return;
        }

        double gain01 = clamp(resolved.intensity01() * knob, 0.0, 1.0);
        if (gain01 <= 0.0008) {
            return;
        }

        String pat = (resolved.pattern() == null || resolved.pattern().isBlank()) ? "punch" : resolved.pattern();
        triggerImpulse(resolved.frequencyHz(), resolved.durationMs(), gain01, resolved.noiseMix01(), pat, resolved.pulsePeriodMs(), resolved.pulseWidthMs(), resolved.priority(), 0, "mount.hoof");
        triggerImpulse(clamp(resolved.frequencyHz() - 4.0, store.global.minFrequency, store.global.maxFrequency),
                Math.max(25, (int) Math.round(resolved.durationMs() * 0.70)),
                clamp(gain01 * 0.65, 0.0, 1.0),
                clamp(resolved.noiseMix01() + 0.10, 0.0, 0.90),
                pat,
                resolved.pulsePeriodMs(),
                resolved.pulseWidthMs(),
                Math.max(0, resolved.priority() - 1),
                32,
                "mount.hoof_tail");
    }

    public void testMiningSwing() {
        wakeForTests();
        BstConfig.Data cfg = BstConfig.get();
        double gain01 = clamp(cfg.miningSwingHapticsGain, 0.0, 1.0);
        triggerImpulse(46.0, 26, gain01, 0.08);
    }

    /**
     * Latency test pulse: steady, obvious impulse intended for judging end-to-end feel lag.
     * Uses a high priority so it stays dominant while testing.
     */
    public void testLatencyPulse() {
        wakeForTests();
        double gain01 = clamp(BstConfig.get().masterVolume * 0.90, 0.0, 1.0);
        triggerImpulse(42.0, 55, gain01, 0.12, "single", 160, 60, 95, 0);
    }

    public void testCalibrationTone30Hz() {
        wakeForTests();
        double gain01 = clamp(BstConfig.get().masterVolume * 0.85, 0.0, 1.0);
        triggerImpulse(30.0, 2000, gain01, 0.0, "flat", 160, 60, 97, 0, "cal.tone_30hz");
    }

    public void testCalibrationTone60Hz() {
        wakeForTests();
        double gain01 = clamp(BstConfig.get().masterVolume * 0.85, 0.0, 1.0);
        triggerImpulse(60.0, 2000, gain01, 0.0, "flat", 160, 60, 97, 0, "cal.tone_60hz");
    }

    public void testCalibrationSweep() {
        wakeForTests();
        double gain01 = clamp(BstConfig.get().masterVolume * 0.85, 0.0, 1.0);
        triggerSweepImpulse(20.0, 120.0, 6500, gain01, 0.0, "flat", 160, 60, 97, 0, "cal.sweep_20_120hz");
    }

    /**
     * Stops any currently playing calibration tones/sweeps.
     * This targets only impulses with debugKey prefix "cal." and leaves normal gameplay haptics untouched.
     */
    public void stopCalibration() {
        int fadeSamples = (int) (SAMPLE_RATE * 0.030); // 30ms quick fade to avoid clicks
        fadeSamples = Math.max(1, fadeSamples);

        synchronized (impulseLock) {
            for (ImpulseVoice v : impulses) {
                if (v == null) continue;
                String dk = (v.debugKey == null) ? "" : v.debugKey.trim().toLowerCase(java.util.Locale.ROOT);
                if (!dk.startsWith("cal.")) {
                    continue;
                }
                // If the voice is still delayed, start it immediately and fade it out.
                v.delaySamplesLeft = 0;
                if (v.samplesLeft > fadeSamples) {
                    v.samplesLeft = fadeSamples;
                }
            }
        }
    }

    private void wakeForTests() {
        // Tests should be able to open/play even when the player isn't in-world.
        telemetryLive = true;
        lastTelemetryNanos = System.nanoTime();
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

        BstConfig.Data cfg = BstConfig.get();
        String device = (cfg.outputDeviceName == null || cfg.outputDeviceName.isBlank()) ? "<Default>" : cfg.outputDeviceName;
        LOGGER.info("[BST] Starting audio engine: device='{}' bufferMs={} masterVol={} enabled=true", device, cfg.javaSoundBufferMs, cfg.masterVolume);

        try {
            AudioFormat fmt = preferredFormat();
            List<String> devices = AudioDeviceUtil.listOutputDeviceNames(fmt);
            if (devices.isEmpty()) {
                LOGGER.warn("[BST] No JavaSound output devices found that support {}", fmt);
            } else if (devices.size() <= 24) {
                LOGGER.info("[BST] JavaSound output devices ({}): {}", devices.size(), devices);
            } else {
                LOGGER.info("[BST] JavaSound output devices: {} (showing first 24): {}", devices.size(), devices.subList(0, 24));
            }
        } catch (Exception e) {
            LOGGER.warn("[BST] Failed to enumerate JavaSound devices ({})", e.toString());
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
        long startedNs = System.nanoTime();
        boolean loggedNoTelemetryHint = false;
        try {
            int framesPerChunk = 1024;
            byte[] buffer = new byte[framesPerChunk * 2 * BYTES_PER_SAMPLE];
            int bufferChannels = 2;

            OutputEq outputEq = new OutputEq();
            SmartVolume smartVolume = new SmartVolume();

            // Per-frame accumulation (reused).
            double[] ch = new double[8];

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
                        if (!loggedNoTelemetryHint && (System.nanoTime() - startedNs) > 2_000_000_000L) {
                            loggedNoTelemetryHint = true;
                            LOGGER.info("[BST] Waiting for in-world telemetry before opening audio (join a world). Use Advanced Settings 'Test' buttons to force playback.");
                        }
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }

                    line = openLinePreferred();
                    if (line == null) {
                        LOGGER.error("[BST] Failed to open any audio line; will retry in 1s");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }

                    try {
                        bufferChannels = Math.max(1, line.getFormat().getChannels());
                    } catch (Exception ignored) {
                        bufferChannels = 2;
                    }
                    if (bufferChannels != 2 && bufferChannels != 8) {
                        bufferChannels = 2;
                    }
                    activeOutputChannels = bufferChannels;
                    buffer = new byte[framesPerChunk * bufferChannels * BYTES_PER_SAMPLE];
                    // Ensure scratch buffer can hold up to 8 channels.
                    if (ch.length < bufferChannels) {
                        ch = new double[bufferChannels];
                    }

                    try {
                        line.start();
                    } catch (Exception e) {
                        LOGGER.error("[BST] Failed to start audio line ({}); will retry", e.toString());
                        try {
                            line.close();
                        } catch (Exception ignored) {
                        }
                        line = null;
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ignored) {
                        }
                        continue;
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

                SoundScapeRouter router = new SoundScapeRouter(cfg, bufferChannels);
                int roadMask = router.maskForCategory(BstConfig.SoundScapeCategories.ROAD);
                int damageMask = router.maskForCategory(BstConfig.SoundScapeCategories.DAMAGE);
                int bumpMask = router.maskForCategory(BstConfig.SoundScapeCategories.ACCEL_BUMP);
                int chimeMask = router.maskForCategory(BstConfig.SoundScapeCategories.BIOME_CHIME);

                int damageLeft = damageBurstSamplesLeft.get();
                int biomeLeft = biomeChimeSamplesLeft.get();
                int bumpLeft = accelBumpSamplesLeft.get();

                // Determine the single dominant source for this chunk.
                int dominantKind = 0; // 0 none, 1 road, 2 damage, 3 impulse, 4 bump, 5 chime
                int dominantPriority = -1;
                double dominantStrength = -1.0;
                ImpulseVoice dominantImpulse = null;

                // Road: low priority continuous.
                boolean roadActiveForDominance = cfg.roadTextureEnabled && cfg.roadTextureGain > 0.0001 && Math.abs(localSpeed) > 0.09;
                if (roadActiveForDominance) {
                    dominantKind = 1;
                    dominantPriority = 1;
                    dominantStrength = cfg.roadTextureGain;
                }

                // Damage burst: very high priority.
                if (cfg.damageBurstEnabled && damageLeft > 0) {
                    int pri = 10;
                    double strength = cfg.damageBurstGain * clamp(damageBurstIntensity, 0.0, 1.0);
                    if (pri > dominantPriority || (pri == dominantPriority && strength > dominantStrength)) {
                        dominantKind = 2;
                        dominantPriority = pri;
                        dominantStrength = strength;
                    }
                }

                // Impulses: choose a single dominant voice based on profile priority.
                synchronized (impulseLock) {
                    for (int vi = impulses.size() - 1; vi >= 0; vi--) {
                        ImpulseVoice v = impulses.get(vi);
                        if (v.delaySamplesLeft > 0) {
                            continue;
                        }
                        if (v.samplesLeft <= 0 || v.gain <= 0.00001) {
                            continue;
                        }
                        if (dominantImpulse == null
                                || v.priority > dominantImpulse.priority
                                || (v.priority == dominantImpulse.priority && v.gain > dominantImpulse.gain)
                                || (v.priority == dominantImpulse.priority && v.gain == dominantImpulse.gain && v.createdNanos > dominantImpulse.createdNanos)) {
                            dominantImpulse = v;
                        }
                    }
                }
                if (dominantImpulse != null) {
                    int pri = dominantImpulse.priority;
                    double strength = dominantImpulse.gain;
                    if (pri > dominantPriority || (pri == dominantPriority && strength > dominantStrength)) {
                        dominantKind = 3;
                        dominantPriority = pri;
                        dominantStrength = strength;
                    }
                }

                // Accel bump: medium-high priority.
                if (cfg.accelBumpEnabled && bumpLeft > 0) {
                    int pri = 7;
                    double strength = cfg.accelBumpGain;
                    if (pri > dominantPriority || (pri == dominantPriority && strength > dominantStrength)) {
                        dominantKind = 4;
                        dominantPriority = pri;
                        dominantStrength = strength;
                    }
                }

                // Biome chime: medium priority.
                if (cfg.biomeChimeEnabled && biomeLeft > 0) {
                    int pri = 4;
                    double strength = cfg.biomeChimeGain;
                    if (pri > dominantPriority || (pri == dominantPriority && strength > dominantStrength)) {
                        dominantKind = 5;
                        dominantPriority = pri;
                        dominantStrength = strength;
                    }
                }

                // Update dominant debug snapshot (only when it changes).
                String domLabel;
                double domFreq = 0.0;
                double domGain = 0.0;
                if (dominantKind == 1) {
                    domLabel = "road";
                    domGain = dominantStrength;
                } else if (dominantKind == 2) {
                    domLabel = "damage";
                    domGain = dominantStrength;
                } else if (dominantKind == 4) {
                    domLabel = "accel_bump";
                    domGain = dominantStrength;
                } else if (dominantKind == 5) {
                    domLabel = "biome_chime";
                    domGain = dominantStrength;
                } else if (dominantKind == 3 && dominantImpulse != null) {
                    String dk = (dominantImpulse.debugKey == null) ? "" : dominantImpulse.debugKey;
                    domLabel = dk.isBlank() ? "impulse" : dk;
                    if (Math.abs(dominantImpulse.endFreqHz - dominantImpulse.startFreqHz) > 0.01) {
                        domFreq = (dominantImpulse.startFreqHz + dominantImpulse.endFreqHz) * 0.5;
                    } else {
                        domFreq = dominantImpulse.freqHz;
                    }
                    domGain = dominantImpulse.gain;
                } else {
                    domLabel = "none";
                }

                boolean differsFromPublished = !domLabel.equals(debugDominantLabel)
                        || dominantPriority != debugDominantPriority
                        || Math.abs(domFreq - debugDominantFreqHz) > 0.05
                        || Math.abs(domGain - debugDominantGain01) > 0.01;

                if (!differsFromPublished) {
                    // Candidate matches what's already published; clear any pending transition.
                    pendingDominantSinceNs = 0L;
                } else {
                    boolean matchesPending = domLabel.equals(pendingDominantLabel)
                            && dominantPriority == pendingDominantPriority
                            && Math.abs(domFreq - pendingDominantFreqHz) <= 0.05
                            && Math.abs(domGain - pendingDominantGain01) <= 0.01;

                    if (!matchesPending) {
                        pendingDominantLabel = domLabel;
                        pendingDominantPriority = dominantPriority;
                        pendingDominantFreqHz = domFreq;
                        pendingDominantGain01 = domGain;
                        pendingDominantSinceNs = nowNs;
                    } else if (pendingDominantSinceNs > 0L && (nowNs - pendingDominantSinceNs) >= DEBUG_DOMINANT_HYSTERESIS_NS) {
                        // Only publish once the candidate has remained stable long enough.
                        debugDominantLabel = pendingDominantLabel;
                        debugDominantPriority = pendingDominantPriority;
                        debugDominantFreqHz = pendingDominantFreqHz;
                        debugDominantGain01 = pendingDominantGain01;
                        pendingDominantSinceNs = 0L;
                    }
                }

                double roadMul = (dominantKind == 0 || dominantKind == 1) ? 1.0 : DUCK_FACTOR;
                double damageMul = (dominantKind == 2) ? 1.0 : DUCK_FACTOR;
                double bumpMul = (dominantKind == 4) ? 1.0 : DUCK_FACTOR;
                double chimeMul = (dominantKind == 5) ? 1.0 : DUCK_FACTOR;

                int idx = 0;
                synchronized (impulseLock) {
                    for (int i = 0; i < framesPerChunk; i++) {
                    // Clear per-channel accumulation.
                    for (int c = 0; c < bufferChannels; c++) {
                        ch[c] = 0.0;
                    }

                    double g = startGain + ((endGain - startGain) * (i / (double) framesPerChunk));
                    double mg = master * g;

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

                        double v = roadNoiseState * cfg.roadTextureGain * speedRamp * roadMul;
                        addToChannels(ch, bufferChannels, roadMask, v);
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
                        double v = damageNoiseState * cfg.damageBurstGain * clamp(damageBurstIntensity, 0.0, 1.0) * env * damageMul;
                        addToChannels(ch, bufferChannels, damageMask, v);
                        damageLeft--;
                    }

                    if (!impulses.isEmpty()) {
                        for (int vi = impulses.size() - 1; vi >= 0; vi--) {
                            ImpulseVoice v = impulses.get(vi);
                            if (v.delaySamplesLeft > 0) {
                                v.delaySamplesLeft--;
                                continue;
                            }
                            if (v.samplesLeft <= 0 || v.gain <= 0.00001) {
                                continue;
                            }

                            int total = Math.max(1, v.totalSamples);
                            int samplesLeft = Math.max(0, v.samplesLeft);
                            int sampleIndex = Math.max(0, total - samplesLeft);
                            double env = impulseEnvelope(
                                    v.pattern,
                                    sampleIndex,
                                    total,
                                    samplesLeft,
                                    v.pulsePeriodSamples,
                                    v.pulseWidthSamples
                            );

                            double voiceMul;
                            if (dominantKind == 3 && v == dominantImpulse) {
                                voiceMul = 1.0;
                            } else {
                                voiceMul = DUCK_FACTOR;
                            }

                            double overallProgress = (total <= 1) ? 1.0 : clamp(sampleIndex / (double) (total - 1), 0.0, 1.0);
                            double freqHz = v.startFreqHz + ((v.endFreqHz - v.startFreqHz) * overallProgress);
                            double step = (2.0 * Math.PI * freqHz) / SAMPLE_RATE;
                            double sine = Math.sin(v.phase);

                            // Low-pass the noise component to keep impulses tactile and less "snappy".
                            double fc = 65.0;
                            double a = 1.0 - Math.exp(-(2.0 * Math.PI * fc) / SAMPLE_RATE);
                            double white = (random.nextDouble() * 2.0) - 1.0;
                            v.noiseState += (white - v.noiseState) * a;

                            double w = (sine * (1.0 - v.noiseMix)) + (v.noiseState * v.noiseMix);
                            double voice = w * v.gain * env * voiceMul;
                            int mask = router.maskForEffectKey(v.debugKey);
                            addToChannels(ch, bufferChannels, mask, voice);

                            v.phase += step;
                            if (v.phase > (2.0 * Math.PI)) {
                                v.phase -= (2.0 * Math.PI);
                            }

                            v.samplesLeft--;
                        }
                    }

                    if (cfg.accelBumpEnabled && bumpLeft > 0) {
                        int total = Math.max(1, accelBumpTotalSamples.get());
                        double progress = 1.0 - (bumpLeft / (double) total);
                        double env = Math.sin(progress * Math.PI);
                        // Low thump around ~32Hz
                        double bump = Math.sin(bumpPhase) * env;
                        double v = bump * cfg.accelBumpGain * bumpMul;
                        addToChannels(ch, bufferChannels, bumpMask, v);
                        bumpLeft--;
                    }

                    if (cfg.biomeChimeEnabled && biomeLeft > 0) {
                        // A short low sine "bump".
                        int total = Math.max(1, biomeChimeTotalSamples.get());
                        double progress = 1.0 - (biomeLeft / (double) total);
                        double env = Math.sin(progress * Math.PI); // bell-ish half-sine envelope
                        double v = Math.sin(chimePhase) * clamp(cfg.biomeChimeGain, 0.0, 1.0) * env * chimeMul;
                        addToChannels(ch, bufferChannels, chimeMask, v);
                        biomeLeft--;
                    }

                    // Smart Volume (AGC): update once per-frame and apply uniformly.
                    if (cfg.smartVolumeEnabled) {
                        smartVolume.updateTarget(cfg.smartVolumeTargetPct);
                        smartVolume.observeFramePeak(ch, bufferChannels);
                    } else {
                        smartVolume.resetIfDisabled();
                    }
                    double sv = smartVolume.currentGain();

                    // Per-channel limiter + convert to int16.
                    for (int c = 0; c < bufferChannels; c++) {
                        double s = ch[c] * sv;
                            if (cfg.outputEqEnabled && cfg.outputEqGainDb != 0) {
                                outputEq.updateIfNeeded(cfg.outputEqFreqHz, cfg.outputEqGainDb, bufferChannels);
                                s = outputEq.process(c, s);
                            } else {
                                outputEq.resetIfDisabled();
                            }

                            double sample = softClipTanh(s, limiterDrive);
                        sample = clamp(sample, -1.0, 1.0);
                        short s16 = (short) (sample * mg * 32767);

                        buffer[idx++] = (byte) (s16 & 0xFF);
                        buffer[idx++] = (byte) ((s16 >>> 8) & 0xFF);
                    }

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
                }

                synchronized (impulseLock) {
                    for (int vi = impulses.size() - 1; vi >= 0; vi--) {
                        ImpulseVoice v = impulses.get(vi);
                        if (v.delaySamplesLeft <= 0 && v.samplesLeft <= 0) {
                            impulses.remove(vi);
                        }
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

                try {
                    line.write(buffer, 0, buffer.length);
                } catch (Exception e) {
                    LOGGER.warn("[BST] Audio write failed ({}); reopening device", e.toString());
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
            }
        } catch (Exception e) {
            LOGGER.error("[BST] Audio thread crashed", e);
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

    private static void addToChannels(double[] ch, int channelCount, int mask, double v) {
        if (v == 0.0) {
            return;
        }
        int m = mask;
        if (m == 0) {
            // Default: all channels.
            if (channelCount == 2) {
                ch[0] += v;
                ch[1] += v;
                return;
            }
            for (int c = 0; c < channelCount; c++) {
                ch[c] += v;
            }
            return;
        }

        for (int c = 0; c < channelCount; c++) {
            if (((m >>> c) & 1) != 0) {
                ch[c] += v;
            }
        }
    }

    private SourceDataLine openLinePreferred() {
        AudioFormat preferred = preferredFormat();
        SourceDataLine line = openLineWithFormat(preferred);
        if (line != null) {
            return line;
        }

        // Fallback: if we asked for 8ch but couldn't open, try stereo.
        if (preferred.getChannels() != 2) {
            LOGGER.warn("[BST] Falling back to stereo output (device does not support 8-channel output)");
            return openLineWithFormat(FORMAT_STEREO);
        }

        return null;
    }

    private SourceDataLine openLineWithFormat(AudioFormat format) {
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);
        BstConfig.Data cfg = BstConfig.get();
        String preferred = cfg.outputDeviceName;
        int bufferMs = clampInt(cfg.javaSoundBufferMs, 0, 500);
        int frameSize = format.getFrameSize();
        int requestedFrames = (bufferMs <= 0) ? 0 : (int) Math.round((bufferMs / 1000.0) * SAMPLE_RATE);
        // Keep the request within a reasonable range (device may clamp anyway).
        requestedFrames = clampInt(requestedFrames, 0, ((int) SAMPLE_RATE) * 2);
        int requestedBytes = (requestedFrames <= 0) ? 0 : Math.max(frameSize, requestedFrames * frameSize);

        // Try the preferred device first.
        if (preferred != null && !preferred.isBlank()) {
            try {
                Mixer.Info mixerInfo = AudioDeviceUtil.findMixerByName(preferred, format);
                if (mixerInfo != null) {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    SourceDataLine line = (SourceDataLine) mixer.getLine(lineInfo);
                    if (requestedBytes > 0) {
                        try {
                            line.open(format, requestedBytes);
                        } catch (Exception ignored) {
                            line.open(format);
                        }
                    } else {
                        line.open(format);
                    }

                    logOpenedLine(line, preferred, bufferMs, format);
                    return line;
                } else {
                    LOGGER.warn("[BST] Preferred device not found or unsupported: '{}'", preferred);
                }
            } catch (Exception e) {
                LOGGER.warn("[BST] Failed to open preferred audio device: '{}'", preferred, e);
            }
        }

        // Fallback: default output device.
        try {
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
            if (requestedBytes > 0) {
                try {
                    line.open(format, requestedBytes);
                } catch (Exception ignored) {
                    line.open(format);
                }
            } else {
                line.open(format);
            }

            logOpenedLine(line, "<Default>", bufferMs, format);
            return line;
        } catch (Exception e) {
            LOGGER.error("[BST] Failed to open default audio device", e);
            return null;
        }
    }

    private static void logOpenedLine(SourceDataLine line, String deviceLabel, int requestedBufferMs, AudioFormat format) {
        try {
            int bytes = line.getBufferSize();
            int frameSize = format.getFrameSize();
            int frames = (frameSize <= 0) ? 0 : (bytes / frameSize);
            double ms = (frames * 1000.0) / SAMPLE_RATE;
            LOGGER.info("[BST] Opened audio line: device='{}' fmt={}ch requestedBufferMs={} actualBufferMs~={} ({} bytes)", deviceLabel, format.getChannels(), requestedBufferMs, String.format(java.util.Locale.ROOT, "%.1f", ms), bytes);
        } catch (Exception ignored) {
        }
    }

    private static final class SoundScapeRouter {
        private final int channelCount;
        private final int allMask;
        private final Map<String, Integer> groupMasks = new java.util.HashMap<>();
        private final Map<String, Integer> categoryMasks = new java.util.HashMap<>();
        private final Map<String, Integer> overrideMasks = new java.util.HashMap<>();

        SoundScapeRouter(BstConfig.Data cfg, int channelCount) {
            this.channelCount = (channelCount == 8) ? 8 : 2;
            this.allMask = (this.channelCount == 8) ? 0xFF : 0x03;

            Map<String, java.util.List<String>> groups = (cfg == null) ? null : cfg.soundScapeGroups;
            if (groups != null) {
                for (Map.Entry<String, java.util.List<String>> e : groups.entrySet()) {
                    if (e == null) continue;
                    String name = (e.getKey() == null) ? "" : e.getKey().trim();
                    if (name.isEmpty()) continue;
                    int mask = 0;
                    java.util.List<String> members = e.getValue();
                    if (members != null) {
                        for (String m : members) {
                            mask |= channelIdToMask(m);
                        }
                    }
                    if (mask != 0) {
                        groupMasks.put(name.toLowerCase(java.util.Locale.ROOT), mask);
                    }
                }
            }

            // Ensure All exists.
            groupMasks.putIfAbsent("all", allMask);

            Map<String, String> cats = (cfg == null) ? null : cfg.soundScapeCategoryRouting;
            if (cats != null) {
                for (Map.Entry<String, String> e : cats.entrySet()) {
                    if (e == null) continue;
                    String k = (e.getKey() == null) ? "" : e.getKey().trim().toLowerCase(java.util.Locale.ROOT);
                    if (k.isEmpty()) continue;
                    categoryMasks.put(k, targetToMask(e.getValue()));
                }
            }

            Map<String, String> overrides = (cfg == null) ? null : cfg.soundScapeOverrides;
            if (overrides != null) {
                for (Map.Entry<String, String> e : overrides.entrySet()) {
                    if (e == null) continue;
                    String k = (e.getKey() == null) ? "" : e.getKey().trim().toLowerCase(java.util.Locale.ROOT);
                    if (k.isEmpty()) continue;
                    overrideMasks.put(k, targetToMask(e.getValue()));
                }
            }
        }

        int maskForCategory(String categoryKey) {
            if (categoryKey == null) {
                return allMask;
            }
            String k = categoryKey.trim().toLowerCase(java.util.Locale.ROOT);
            if (k.isEmpty()) {
                return allMask;
            }
            Integer m = categoryMasks.get(k);
            return (m == null || m == 0) ? allMask : m;
        }

        int maskForEffectKey(String debugKey) {
            if (debugKey == null || debugKey.isBlank()) {
                return maskForCategory(BstConfig.SoundScapeCategories.CUSTOM);
            }
            String k = debugKey.trim().toLowerCase(java.util.Locale.ROOT);
            Integer o = overrideMasks.get(k);
            if (o != null && o != 0) {
                return o;
            }
            String cat = classifyCategory(k);
            return maskForCategory(cat);
        }

        private String classifyCategory(String key) {
            if (key.startsWith("damage.")) return BstConfig.SoundScapeCategories.DAMAGE;
            if (key.startsWith("movement.")) return BstConfig.SoundScapeCategories.FOOTSTEPS;
            if (key.startsWith("mining.")) return BstConfig.SoundScapeCategories.MINING_SWING;
            if (key.startsWith("mount.") || key.startsWith("flight.")) return BstConfig.SoundScapeCategories.MOUNTED;
            if (key.startsWith("gameplay.")) return BstConfig.SoundScapeCategories.GAMEPLAY;

            // Sound buckets (from SoundHapticsHandler) and everything else default here.
            return BstConfig.SoundScapeCategories.SOUND;
        }

        private int targetToMask(String raw) {
            if (raw == null || raw.isBlank()) {
                return allMask;
            }
            String v = raw.trim();
            String lower = v.toLowerCase(java.util.Locale.ROOT);
            if (lower.startsWith("ch:")) {
                return channelIdToMask(v.substring(3));
            }
            if (lower.startsWith("grp:")) {
                String name = v.substring(4).trim().toLowerCase(java.util.Locale.ROOT);
                if (name.isEmpty()) {
                    return allMask;
                }
                Integer m = groupMasks.get(name);
                return (m == null || m == 0) ? allMask : m;
            }

            // Allow bare channel ids.
            int asCh = channelIdToMask(v);
            if (asCh != 0) {
                return asCh;
            }

            // Treat as group name.
            Integer m = groupMasks.get(lower);
            return (m == null || m == 0) ? allMask : m;
        }

        private int channelIdToMask(String raw) {
            if (raw == null) return 0;
            String v = raw.trim().toUpperCase(java.util.Locale.ROOT);
            if (v.isEmpty()) return 0;

            // Stereo fallback: any non-FL/FR channel collapses to both.
            if (channelCount == 2) {
                if ("FL".equals(v) || "L".equals(v)) return 0x01;
                if ("FR".equals(v) || "R".equals(v)) return 0x02;
                return 0x03;
            }

            return switch (v) {
                case "FL", "L" -> 0x01;
                case "FR", "R" -> 0x02;
                case "C" -> 0x04;
                case "LFE" -> 0x08;
                case "SL" -> 0x10;
                case "SR" -> 0x20;
                case "BL" -> 0x40;
                case "BR" -> 0x80;
                default -> 0;
            };
        }
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static int clampInt(int v, int min, int max) {
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

    private static double impulseEnvelope(String pattern, int sampleIndex, int totalSamples, int samplesLeft, int pulsePeriodSamples, int pulseWidthSamples) {
        double overallProgress = (totalSamples <= 1) ? 1.0 : clamp(sampleIndex / (double) (totalSamples - 1), 0.0, 1.0);

        String p = (pattern == null) ? "single" : pattern.trim().toLowerCase();

        // Small attack/release to avoid clicks when starting/stopping.
        // Some patterns intentionally override this to feel softer (gameplay clicks) or punchier (damage).
        double attackMs;
        double releaseMs;
        switch (p) {
            case "soft_single" -> {
                attackMs = 0.020; // 20ms
                releaseMs = 0.028; // 28ms
            }
            case "punch" -> {
                attackMs = 0.003; // 3ms
                releaseMs = 0.012; // 12ms
            }
            default -> {
                attackMs = 0.010; // 10ms
                releaseMs = 0.015; // 15ms
            }
        }

        int attackSamples = (int) (SAMPLE_RATE * attackMs);
        int releaseSamples = (int) (SAMPLE_RATE * releaseMs);
        double attack = (attackSamples <= 0) ? 1.0 : clamp(sampleIndex / (double) attackSamples, 0.0, 1.0);
        double release = (releaseSamples <= 0) ? 1.0 : clamp(samplesLeft / (double) releaseSamples, 0.0, 1.0);

        double env;

        switch (p) {
            case "flat" -> {
                // Constant sustain (tone-friendly). Attack/release above prevents clicks.
                env = 1.0;
            }
            case "fade_out" -> {
                // Strong at the start, fades to 0 over the duration.
                env = Math.pow(1.0 - overallProgress, 1.15);
            }
            case "shockwave" -> {
                // Punchy onset then rapid decay.
                env = Math.exp(-overallProgress * 6.0);
            }
            case "punch" -> {
                // Like shockwave but with a shorter attack to feel more abrupt.
                env = Math.exp(-overallProgress * 6.6);
            }
            case "pulse_loop" -> {
                int period = Math.max(1, pulsePeriodSamples);
                int width = Math.max(1, Math.min(pulseWidthSamples, period));
                int inPeriod = sampleIndex % period;
                if (inPeriod >= width) {
                    env = 0.0;
                } else {
                    double pulseProgress = inPeriod / (double) width;
                    double e = Math.sin(pulseProgress * Math.PI);
                    env = e * e;
                }
                // Add a gentle overall decay so long loops don't feel too “stuck on”.
                env *= (0.65 + (0.35 * (1.0 - overallProgress)));
            }
            case "single" -> {
                // Raised-cosine envelope (sin^2) reduces attack punch vs a simple half-sine.
                double e = Math.sin(overallProgress * Math.PI);
                env = e * e;
            }
            case "soft_single" -> {
                // Same general shape as "single" but paired with a longer attack/release above.
                double e = Math.sin(overallProgress * Math.PI);
                env = e * e;
            }
            default -> {
                double e = Math.sin(overallProgress * Math.PI);
                env = e * e;
            }
        }

        return env * attack * release;
    }

    private static final class ImpulseVoice {
        int totalSamples;
        int samplesLeft;
        int delaySamplesLeft;

        double freqHz;
        double startFreqHz;
        double endFreqHz;
        double gain;
        double noiseMix;
        String pattern;
        String debugKey;
        int pulsePeriodSamples;
        int pulseWidthSamples;

        double phase;
        double noiseState;

        int priority;
        long createdNanos;
    }

    private static final class OutputEq {
        // RBJ peaking EQ with fixed Q (keeps UI minimal).
        private static final double Q = 1.0;

        private int lastFreqHz = -1;
        private int lastGainDb = Integer.MIN_VALUE;
        private int lastChannels = -1;
        private boolean active;

        // Coeffs
        private double b0, b1, b2, a1, a2;

        // Per-channel state
        private final double[] x1 = new double[8];
        private final double[] x2 = new double[8];
        private final double[] y1 = new double[8];
        private final double[] y2 = new double[8];

        void updateIfNeeded(int freqHz, int gainDb, int channels) {
            int f = clampInt(freqHz, 10, 120);
            int g = clampInt(gainDb, -12, 12);
            int ch = (channels == 8) ? 8 : 2;

            if (active && f == lastFreqHz && g == lastGainDb && ch == lastChannels) {
                return;
            }

            lastFreqHz = f;
            lastGainDb = g;
            lastChannels = ch;
            active = true;

            // Reset state when settings change to avoid dragging old resonances across.
            for (int i = 0; i < 8; i++) {
                x1[i] = x2[i] = y1[i] = y2[i] = 0.0;
            }

            // Compute RBJ peaking EQ coefficients.
            double A = Math.pow(10.0, g / 40.0);
            double w0 = (2.0 * Math.PI * f) / SAMPLE_RATE;
            double cos = Math.cos(w0);
            double sin = Math.sin(w0);
            double alpha = sin / (2.0 * Q);

            double bb0 = 1.0 + alpha * A;
            double bb1 = -2.0 * cos;
            double bb2 = 1.0 - alpha * A;
            double aa0 = 1.0 + (alpha / A);
            double aa1 = -2.0 * cos;
            double aa2 = 1.0 - (alpha / A);

            // Normalize by a0.
            b0 = bb0 / aa0;
            b1 = bb1 / aa0;
            b2 = bb2 / aa0;
            a1 = aa1 / aa0;
            a2 = aa2 / aa0;
        }

        double process(int channel, double x) {
            int c = (channel < 0) ? 0 : Math.min(channel, 7);
            double y = (b0 * x) + (b1 * x1[c]) + (b2 * x2[c]) - (a1 * y1[c]) - (a2 * y2[c]);
            x2[c] = x1[c];
            x1[c] = x;
            y2[c] = y1[c];
            y1[c] = y;
            return y;
        }

        void resetIfDisabled() {
            if (!active) {
                return;
            }
            active = false;
            lastFreqHz = -1;
            lastGainDb = Integer.MIN_VALUE;
            lastChannels = -1;
            for (int i = 0; i < 8; i++) {
                x1[i] = x2[i] = y1[i] = y2[i] = 0.0;
            }
        }
    }

    private static final class SmartVolume {
        // Keep this intentionally slow and bounded so it doesn't fight the priority/ducking mixer.
        private static final double MAX_BOOST_DB = 12.0;
        private static final double MAX_CUT_DB = 12.0;

        // Detector time constants.
        private static final double DETECT_ATTACK_S = 0.025;
        private static final double DETECT_RELEASE_S = 0.250;

        // Gain smoothing time constants.
        private static final double GAIN_DOWN_S = 0.180;
        private static final double GAIN_UP_S = 0.900;

        private boolean active;
        private double target = 0.65;
        private double env = 0.0;
        private double gain = 1.0;

        void updateTarget(int targetPct) {
            int pct = clampInt(targetPct, 10, 90);
            this.target = pct / 100.0;
            this.active = true;
        }

        void observeFramePeak(double[] frameChannels, int channelCount) {
            if (frameChannels == null) {
                return;
            }
            int n = (channelCount == 8) ? 8 : 2;
            double peak = 0.0;
            for (int i = 0; i < n && i < frameChannels.length; i++) {
                double a = Math.abs(frameChannels[i]);
                if (a > peak) {
                    peak = a;
                }
            }

            double aAtk = coeffFromTime(DETECT_ATTACK_S);
            double aRel = coeffFromTime(DETECT_RELEASE_S);
            if (peak > env) {
                env = (aAtk * env) + ((1.0 - aAtk) * peak);
            } else {
                env = (aRel * env) + ((1.0 - aRel) * peak);
            }

            double e = Math.max(1e-6, env);
            double desired = target / e;

            double minGain = Math.pow(10.0, (-MAX_CUT_DB) / 20.0);
            double maxGain = Math.pow(10.0, (MAX_BOOST_DB) / 20.0);
            desired = clamp(desired, minGain, maxGain);

            double a = coeffFromTime(desired < gain ? GAIN_DOWN_S : GAIN_UP_S);
            gain = (a * gain) + ((1.0 - a) * desired);
        }

        double currentGain() {
            return active ? gain : 1.0;
        }

        void resetIfDisabled() {
            if (!active) {
                return;
            }
            active = false;
            env = 0.0;
            gain = 1.0;
        }

        private static double coeffFromTime(double seconds) {
            double s = Math.max(0.001, seconds);
            return Math.exp(-1.0 / (SAMPLE_RATE * s));
        }
    }
}
