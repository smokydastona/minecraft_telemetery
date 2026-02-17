package com.smoky.bassshakertelemetry.audio;

import com.smoky.bassshakertelemetry.config.BstConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;
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
        int ms = Math.max(10, durationMs);
        int samples = (int) ((ms / 1000.0) * SAMPLE_RATE);
        samples = Math.max(1, samples);

        double f = clamp(freqHz, 10.0, 120.0);
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
                if (Math.abs(v.freqHz - f) > 0.75) {
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
            voice.freqHz = f;
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
            List<String> devices = AudioDeviceUtil.listOutputDeviceNames(FORMAT);
            if (devices.isEmpty()) {
                LOGGER.warn("[BST] No JavaSound output devices found that support {}", FORMAT);
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

                    line = openLine();
                    if (line == null) {
                        LOGGER.error("[BST] Failed to open any audio line; will retry in 1s");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {
                        }
                        continue;
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
                    domFreq = dominantImpulse.freqHz;
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

                        sample += roadNoiseState * cfg.roadTextureGain * speedRamp * roadMul;
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
                        sample += damageNoiseState * cfg.damageBurstGain * clamp(damageBurstIntensity, 0.0, 1.0) * env * damageMul;
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

                            double step = (2.0 * Math.PI * v.freqHz) / SAMPLE_RATE;
                            double sine = Math.sin(v.phase);

                            // Low-pass the noise component to keep impulses tactile and less "snappy".
                            double fc = 65.0;
                            double a = 1.0 - Math.exp(-(2.0 * Math.PI * fc) / SAMPLE_RATE);
                            double white = (random.nextDouble() * 2.0) - 1.0;
                            v.noiseState += (white - v.noiseState) * a;

                            double w = (sine * (1.0 - v.noiseMix)) + (v.noiseState * v.noiseMix);
                            sample += w * v.gain * env * voiceMul;

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
                        sample += bump * cfg.accelBumpGain * bumpMul;
                        bumpLeft--;
                    }

                    if (cfg.biomeChimeEnabled && biomeLeft > 0) {
                        // A short low sine "bump".
                        int total = Math.max(1, biomeChimeTotalSamples.get());
                        double progress = 1.0 - (biomeLeft / (double) total);
                        double env = Math.sin(progress * Math.PI); // bell-ish half-sine envelope
                        sample += Math.sin(chimePhase) * clamp(cfg.biomeChimeGain, 0.0, 1.0) * env * chimeMul;
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

    private SourceDataLine openLine() {
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, FORMAT);
        BstConfig.Data cfg = BstConfig.get();
        String preferred = cfg.outputDeviceName;
        int bufferMs = clampInt(cfg.javaSoundBufferMs, 0, 500);
        int frameSize = FORMAT.getFrameSize();
        int requestedFrames = (bufferMs <= 0) ? 0 : (int) Math.round((bufferMs / 1000.0) * SAMPLE_RATE);
        // Keep the request within a reasonable range (device may clamp anyway).
        requestedFrames = clampInt(requestedFrames, 0, ((int) SAMPLE_RATE) * 2);
        int requestedBytes = (requestedFrames <= 0) ? 0 : Math.max(frameSize, requestedFrames * frameSize);

        // Try the preferred device first.
        if (preferred != null && !preferred.isBlank()) {
            try {
                Mixer.Info mixerInfo = AudioDeviceUtil.findMixerByName(preferred, FORMAT);
                if (mixerInfo != null) {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    SourceDataLine line = (SourceDataLine) mixer.getLine(lineInfo);
                    if (requestedBytes > 0) {
                        try {
                            line.open(FORMAT, requestedBytes);
                        } catch (Exception ignored) {
                            line.open(FORMAT);
                        }
                    } else {
                        line.open(FORMAT);
                    }

                    logOpenedLine(line, preferred, bufferMs);
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
                    line.open(FORMAT, requestedBytes);
                } catch (Exception ignored) {
                    line.open(FORMAT);
                }
            } else {
                line.open(FORMAT);
            }

            logOpenedLine(line, "<Default>", bufferMs);
            return line;
        } catch (Exception e) {
            LOGGER.error("[BST] Failed to open default audio device", e);
            return null;
        }
    }

    private static void logOpenedLine(SourceDataLine line, String deviceLabel, int requestedBufferMs) {
        try {
            int bytes = line.getBufferSize();
            int frameSize = FORMAT.getFrameSize();
            int frames = (frameSize <= 0) ? 0 : (bytes / frameSize);
            double ms = (frames * 1000.0) / SAMPLE_RATE;
            LOGGER.info("[BST] Opened audio line: device='{}' requestedBufferMs={} actualBufferMs~={} ({} bytes)", deviceLabel, requestedBufferMs, String.format(java.util.Locale.ROOT, "%.1f", ms), bytes);
        } catch (Exception ignored) {
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
        // Small attack/release to avoid clicks when starting/stopping.
        int attackSamples = (int) (SAMPLE_RATE * 0.010); // 10ms
        int releaseSamples = (int) (SAMPLE_RATE * 0.015); // 15ms
        double attack = (attackSamples <= 0) ? 1.0 : clamp(sampleIndex / (double) attackSamples, 0.0, 1.0);
        double release = (releaseSamples <= 0) ? 1.0 : clamp(samplesLeft / (double) releaseSamples, 0.0, 1.0);

        double overallProgress = (totalSamples <= 1) ? 1.0 : clamp(sampleIndex / (double) (totalSamples - 1), 0.0, 1.0);

        String p = (pattern == null) ? "single" : pattern.trim().toLowerCase();
        double env;

        switch (p) {
            case "fade_out" -> {
                // Strong at the start, fades to 0 over the duration.
                env = Math.pow(1.0 - overallProgress, 1.15);
            }
            case "shockwave" -> {
                // Punchy onset then rapid decay.
                env = Math.exp(-overallProgress * 6.0);
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
}
