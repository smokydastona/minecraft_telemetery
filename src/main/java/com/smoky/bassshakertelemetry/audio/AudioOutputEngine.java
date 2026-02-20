package com.smoky.bassshakertelemetry.audio;

import com.smoky.bassshakertelemetry.audio.backend.AudioOutputDevice;
import com.smoky.bassshakertelemetry.audio.backend.BackendSelector;
import com.smoky.bassshakertelemetry.audio.backend.HapticAudioBackend;
import com.smoky.bassshakertelemetry.audio.dsp.DspContext;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraphInstance;
import com.smoky.bassshakertelemetry.audio.dsp.DspNodeFactory;
import com.smoky.bassshakertelemetry.config.BstConfig;
import com.smoky.bassshakertelemetry.config.BstHapticInstruments;
import com.smoky.bassshakertelemetry.telemetryout.HapticEventContext;
import com.smoky.bassshakertelemetry.telemetryout.TelemetryOut;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
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

    private static final DspNodeFactory DSP_FACTORY = new DspNodeFactory();

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

    // --- Phase 3: Real-time debugger taps (capture is opt-in for performance) ---

    private static final int DEBUG_WAVE_SAMPLES = 256;
    private static final int DEBUG_SPECT_FFT_SIZE = 4096;
    private static final int DEBUG_SPECT_BINS = 16;
    private static final int DEBUG_SPECT_COLS = 64;

    private static final AtomicBoolean DEBUG_CAPTURE_ENABLED = new AtomicBoolean(false);
    private static final AtomicReference<DebugSnapshot> DEBUG_SNAPSHOT = new AtomicReference<>(null);

    private static final Object DEBUG_EVENT_LOCK = new Object();
    private static final int DEBUG_EVENT_CAPACITY = 64;
    private static final DebugEvent[] DEBUG_EVENTS = new DebugEvent[DEBUG_EVENT_CAPACITY];
    private static int debugEventWriteIndex;

    public static void setDebugCaptureEnabled(boolean enabled) {
        DEBUG_CAPTURE_ENABLED.set(enabled);
    }

    public static DebugSnapshot getDebugSnapshot() {
        return DEBUG_SNAPSHOT.get();
    }

    public static DebugEvent[] getRecentDebugEvents(int maxCount) {
        int n = Math.max(0, Math.min(DEBUG_EVENT_CAPACITY, Math.max(0, maxCount)));
        if (n == 0) {
            return new DebugEvent[0];
        }
        DebugEvent[] out = new DebugEvent[n];
        synchronized (DEBUG_EVENT_LOCK) {
            int w = debugEventWriteIndex;
            int copied = 0;
            for (int i = 0; i < DEBUG_EVENT_CAPACITY && copied < n; i++) {
                int idx = (w - 1 - i);
                while (idx < 0) {
                    idx += DEBUG_EVENT_CAPACITY;
                }
                DebugEvent ev = DEBUG_EVENTS[idx];
                if (ev == null) {
                    continue;
                }
                out[copied++] = ev;
            }
            if (copied < n) {
                return Arrays.copyOf(out, copied);
            }
        }
        return out;
    }

    private static void recordDebugEvent(String debugKey,
                                         HapticBus bus,
                                         double startFreqHz,
                                         double endFreqHz,
                                         int durationMs,
                                         double gain01,
                                         int priority,
                                         int delayMs,
                                         int forcedMask,
                                         double azimuthDeg,
                                         double distanceM) {
        DebugEvent ev = new DebugEvent(
                System.nanoTime(),
                (debugKey == null) ? "" : debugKey,
                (bus == null) ? HapticBus.MODDED : bus,
                startFreqHz,
                endFreqHz,
                durationMs,
                gain01,
                priority,
                delayMs,
                forcedMask,
                azimuthDeg,
                distanceM
        );
        synchronized (DEBUG_EVENT_LOCK) {
            DEBUG_EVENTS[debugEventWriteIndex] = ev;
            debugEventWriteIndex = (debugEventWriteIndex + 1) % DEBUG_EVENT_CAPACITY;
        }
    }

    public static final class DebugSnapshot {
        public final long updatedNanos;
        public final int channels;
        public final float[] rms01;
        public final float[] peak01;

        /** Latest mono waveform window, normalized to roughly [-1..1]. */
        public final float[] waveform;

        /** Flattened spectrogram buffer: cols x bins, column-major history ring. */
        public final float[] spectrogram;
        public final int spectrogramCols;
        public final int spectrogramBins;
        /** Next write column index in the spectrogram ring. */
        public final int spectrogramWriteCol;

        public final int deviceBufferBytes;
        public final int deviceAvailableBytes;
        public final double deviceBufferMs;
        public final double queuedMs;

        private DebugSnapshot(long updatedNanos,
                              int channels,
                              float[] rms01,
                              float[] peak01,
                              float[] waveform,
                              float[] spectrogram,
                              int spectrogramCols,
                              int spectrogramBins,
                              int spectrogramWriteCol,
                              int deviceBufferBytes,
                              int deviceAvailableBytes,
                              double deviceBufferMs,
                              double queuedMs) {
            this.updatedNanos = updatedNanos;
            this.channels = channels;
            this.rms01 = rms01;
            this.peak01 = peak01;
            this.waveform = waveform;
            this.spectrogram = spectrogram;
            this.spectrogramCols = spectrogramCols;
            this.spectrogramBins = spectrogramBins;
            this.spectrogramWriteCol = spectrogramWriteCol;
            this.deviceBufferBytes = deviceBufferBytes;
            this.deviceAvailableBytes = deviceAvailableBytes;
            this.deviceBufferMs = deviceBufferMs;
            this.queuedMs = queuedMs;
        }
    }

    public static final class DebugEvent {
        public final long createdNanos;
        public final String debugKey;
        public final HapticBus bus;
        public final double startFreqHz;
        public final double endFreqHz;
        public final int durationMs;
        public final double gain01;
        public final int priority;
        public final int delayMs;
        public final int forcedMask;
        public final double azimuthDeg;
        public final double distanceM;

        private DebugEvent(long createdNanos,
                           String debugKey,
                           HapticBus bus,
                           double startFreqHz,
                           double endFreqHz,
                           int durationMs,
                           double gain01,
                           int priority,
                           int delayMs,
                           int forcedMask,
                           double azimuthDeg,
                           double distanceM) {
            this.createdNanos = createdNanos;
            this.debugKey = debugKey;
            this.bus = bus;
            this.startFreqHz = startFreqHz;
            this.endFreqHz = endFreqHz;
            this.durationMs = durationMs;
            this.gain01 = gain01;
            this.priority = priority;
            this.delayMs = delayMs;
            this.forcedMask = forcedMask;
            this.azimuthDeg = azimuthDeg;
            this.distanceM = distanceM;
        }
    }

    private static void fftRadix2InPlace(double[] real, double[] imag) {
        int n = real.length;
        if (n <= 1) {
            return;
        }
        // Bit-reversal permutation
        int j = 0;
        for (int i = 1; i < n; i++) {
            int bit = n >>> 1;
            while ((j & bit) != 0) {
                j ^= bit;
                bit >>>= 1;
            }
            j ^= bit;
            if (i < j) {
                double tr = real[i];
                real[i] = real[j];
                real[j] = tr;
                double ti = imag[i];
                imag[i] = imag[j];
                imag[j] = ti;
            }
        }

        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2.0 * Math.PI / len;
            double wlenR = Math.cos(ang);
            double wlenI = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double wR = 1.0;
                double wI = 0.0;
                int half = len >>> 1;
                for (int k = 0; k < half; k++) {
                    int u = i + k;
                    int v = u + half;
                    double vR = (real[v] * wR) - (imag[v] * wI);
                    double vI = (real[v] * wI) + (imag[v] * wR);
                    double uR = real[u];
                    double uI = imag[u];
                    real[u] = uR + vR;
                    imag[u] = uI + vI;
                    real[v] = uR - vR;
                    imag[v] = uI - vI;
                    double nextWR = (wR * wlenR) - (wI * wlenI);
                    wI = (wR * wlenI) + (wI * wlenR);
                    wR = nextWR;
                }
            }
        }
    }

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
     * Phase 2: Trigger a DSP-backed "instrument" impulse.
     *
     * <p>If the instrument id isn't found, this falls back to the legacy impulse path.
     */
    public void triggerInstrumentImpulse(String instrumentId,
                                         double freqHz,
                                         int durationMs,
                                         double gain01,
                                         String pattern,
                                         int pulsePeriodMs,
                                         int pulseWidthMs,
                                         int priority,
                                         int delayMs,
                                         String debugKey) {
        triggerInstrumentInternal(instrumentId, freqHz, freqHz, durationMs, gain01, pattern, pulsePeriodMs, pulseWidthMs, priority, delayMs, debugKey);
    }

    /**
     * Phase 2: Trigger a DSP-backed instrument impulse with a frequency sweep.
     */
    public void triggerInstrumentSweepImpulse(String instrumentId,
                                              double startFreqHz,
                                              double endFreqHz,
                                              int durationMs,
                                              double gain01,
                                              String pattern,
                                              int pulsePeriodMs,
                                              int pulseWidthMs,
                                              int priority,
                                              int delayMs,
                                              String debugKey) {
        triggerInstrumentInternal(instrumentId, startFreqHz, endFreqHz, durationMs, gain01, pattern, pulsePeriodMs, pulseWidthMs, priority, delayMs, debugKey);
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
        HapticBus bus = busForDebugKey(dk);

        double azimuthDeg = HapticEventContext.currentAzimuthDeg();
        double distanceM = HapticEventContext.currentDistanceMeters();

        int forcedMask = forcedMaskFromDebugKey(dk);
        recordDebugEvent(dk, bus, f0, f1, ms, g, pri, delayMs, forcedMask, azimuthDeg, distanceM);

        enqueueImpulseVoice(f0, f1, samples, g, n, pat, pulsePeriodSamples, pulseWidthSamples, pri, delaySamples, dk, bus, "", null, null, azimuthDeg, distanceM);

        if (BstConfig.get().webSocketEnabled && BstConfig.get().webSocketSendHapticEvents) {
            TelemetryOut.emitHaptic(dk, f0, f1, ms, g, n, pat, pulsePeriodMs, pulseWidthMs, pri, delayMs);
            if (BstConfig.get().webSocketSendUnifiedEvents) {
                TelemetryOut.emitEventFromHapticKey(dk, g);
            }
        }
    }

    private void triggerInstrumentInternal(String instrumentId,
                                           double startFreqHz,
                                           double endFreqHz,
                                           int durationMs,
                                           double gain01,
                                           String pattern,
                                           int pulsePeriodMs,
                                           int pulseWidthMs,
                                           int priority,
                                           int delayMs,
                                           String debugKey) {
        String instId = (instrumentId == null) ? "" : instrumentId.trim();
        if (instId.isEmpty()) {
            triggerImpulseInternal(startFreqHz, endFreqHz, durationMs, gain01, 0.0, pattern, pulsePeriodMs, pulseWidthMs, priority, delayMs, debugKey);
            return;
        }

        BstHapticInstruments.Instrument inst = BstHapticInstruments.get().get(instId);
        if (inst == null || inst.graph == null) {
            triggerImpulseInternal(startFreqHz, endFreqHz, durationMs, gain01, 0.0, pattern, pulsePeriodMs, pulseWidthMs, priority, delayMs, debugKey);
            return;
        }

        int ms = Math.max(10, durationMs);
        int samples = (int) ((ms / 1000.0) * SAMPLE_RATE);
        samples = Math.max(1, samples);

        double f0 = clamp(startFreqHz, 10.0, 120.0);
        double f1 = clamp(endFreqHz, 10.0, 120.0);
        double g = clamp(gain01, 0.0, 1.0);
        String pat = (pattern == null || pattern.isBlank()) ? "single" : pattern;

        int periodS = (int) ((Math.max(20, pulsePeriodMs) / 1000.0) * SAMPLE_RATE);
        int widthS = (int) ((Math.max(10, pulseWidthMs) / 1000.0) * SAMPLE_RATE);
        int pulsePeriodSamples = Math.max(1, periodS);
        int pulseWidthSamples = Math.max(1, Math.min(Math.max(1, widthS), pulsePeriodSamples));

        int pri = clampInt(priority, 0, 100);
        int delaySamples = (int) ((Math.max(0, delayMs) / 1000.0) * SAMPLE_RATE);
        delaySamples = Math.max(0, delaySamples);

        String dk = (debugKey == null) ? "" : debugKey.trim();
        HapticBus bus = busForDebugKey(dk);

        long seed = System.nanoTime()
                ^ (((long) dk.toLowerCase(java.util.Locale.ROOT).hashCode()) << 1)
                ^ (((long) instId.toLowerCase(java.util.Locale.ROOT).hashCode()) << 17);
        DspContext ctx = new DspContext(seed, f0, f1, samples);
        ctx.directionBand = HapticEventContext.currentDirectionBand();
        DspGraphInstance graph = inst.graph.instantiate(DSP_FACTORY);

        double azimuthDeg = HapticEventContext.currentAzimuthDeg();
        double distanceM = HapticEventContext.currentDistanceMeters();

        int forcedMask = forcedMaskFromDebugKey(dk);
        recordDebugEvent(dk, bus, f0, f1, ms, g, pri, delayMs, forcedMask, azimuthDeg, distanceM);

        enqueueImpulseVoice(f0, f1, samples, g, 0.0, pat, pulsePeriodSamples, pulseWidthSamples, pri, delaySamples, dk, bus, instId, graph, ctx, azimuthDeg, distanceM);

        if (BstConfig.get().webSocketEnabled && BstConfig.get().webSocketSendHapticEvents) {
            // Legacy haptic packet format doesn't include instrument id yet; emit with noiseMix=0.
            TelemetryOut.emitHaptic(dk, f0, f1, ms, g, 0.0, pat, pulsePeriodMs, pulseWidthMs, pri, delayMs);
            if (BstConfig.get().webSocketSendUnifiedEvents) {
                TelemetryOut.emitEventFromHapticKey(dk, g);
            }
        }
    }

    private void enqueueImpulseVoice(double f0,
                                     double f1,
                                     int samples,
                                     double gain01,
                                     double noiseMix01,
                                     String pattern,
                                     int pulsePeriodSamples,
                                     int pulseWidthSamples,
                                     int priority,
                                     int delaySamples,
                                     String debugKey,
                                     HapticBus bus,
                                     String instrumentId,
                                     DspGraphInstance dspGraph,
                                     DspContext dspContext,
                                     double azimuthDeg,
                                     double distanceM) {
        synchronized (impulseLock) {
            String dk = (debugKey == null) ? "" : debugKey;
            String inst = (instrumentId == null) ? "" : instrumentId;
            int forcedMask = forcedMaskFromDebugKey(dk);

            // Coalesce/extend a very similar active voice to avoid stacking identical pulses.
            for (ImpulseVoice v : impulses) {
                String vdk = (v.debugKey == null) ? "" : v.debugKey;
                if (!dk.equalsIgnoreCase(vdk)) {
                    continue;
                }

                if (v.forcedMask != forcedMask) {
                    continue;
                }
                String vInst = (v.instrumentId == null) ? "" : v.instrumentId;
                if (!inst.equalsIgnoreCase(vInst)) {
                    continue;
                }
                if (v.priority != priority) {
                    continue;
                }
                if (v.delaySamplesLeft != delaySamples) {
                    continue;
                }
                if (!pattern.equalsIgnoreCase(v.pattern)) {
                    continue;
                }
                if (Math.abs(v.startFreqHz - f0) > 0.75) {
                    continue;
                }
                if (Math.abs(v.endFreqHz - f1) > 0.75) {
                    continue;
                }

                // Spatial: avoid coalescing events from clearly different directions/distances.
                if (Math.abs(v.spatialAzimuthDeg - azimuthDeg) > 12.0) {
                    continue;
                }
                if (Math.abs(v.spatialDistanceM - distanceM) > 2.5) {
                    continue;
                }

                v.totalSamples = Math.max(v.totalSamples, samples);
                v.samplesLeft = Math.max(v.samplesLeft, samples);
                v.gain = Math.max(v.gain, gain01);
                v.noiseMix = noiseMix01;
                v.pulsePeriodSamples = pulsePeriodSamples;
                v.pulseWidthSamples = pulseWidthSamples;
                v.createdNanos = System.nanoTime();
                if (v.dspContext != null) {
                    v.dspContext.retune(v.startFreqHz, v.endFreqHz);
                    v.dspContext.resize(v.totalSamples);
                }
                // Refresh spatial gains (config may have changed).
                initVoiceSpatial(v, BstConfig.get(), azimuthDeg, distanceM);
                return;
            }

            ImpulseVoice voice = new ImpulseVoice();
            voice.totalSamples = samples;
            voice.samplesLeft = samples;
            voice.delaySamplesLeft = delaySamples;
            voice.freqHz = f0;
            voice.startFreqHz = f0;
            voice.endFreqHz = f1;
            voice.gain = gain01;
            voice.noiseMix = noiseMix01;
            voice.pattern = pattern;
            voice.debugKey = dk;
            voice.instrumentId = inst;
            voice.pulsePeriodSamples = pulsePeriodSamples;
            voice.pulseWidthSamples = pulseWidthSamples;
            voice.phase = 0.0;
            voice.noiseState = 0.0;
            voice.priority = priority;
            voice.bus = bus;
            voice.createdNanos = System.nanoTime();
            voice.dspGraph = dspGraph;
            voice.dspContext = dspContext;
            voice.forcedMask = forcedMask;
            initVoiceSpatial(voice, BstConfig.get(), azimuthDeg, distanceM);
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

    private static HapticBus busForDebugKey(String debugKey) {
        if (debugKey == null) {
            return HapticBus.MODDED;
        }
        String dk = debugKey.trim().toLowerCase(java.util.Locale.ROOT);
        if (dk.isEmpty()) {
            return HapticBus.MODDED;
        }
        if (dk.contains("ui") || dk.startsWith("ui/") || dk.startsWith("menu")) {
            return HapticBus.UI;
        }
        if (dk.contains("damage") || dk.contains("warden") || dk.contains("danger") || dk.contains("hurt")) {
            return HapticBus.DANGER;
        }
        if (dk.contains("biome") || dk.contains("ambient") || dk.contains("env") || dk.contains("weather")) {
            return HapticBus.ENVIRONMENTAL;
        }
        if (dk.contains("road") || dk.contains("move") || dk.contains("walk") || dk.contains("run")) {
            return HapticBus.CONTINUOUS;
        }
        if (dk.contains("impact") || dk.contains("hit") || dk.contains("mine") || dk.contains("swing")) {
            return HapticBus.IMPACT;
        }
        return HapticBus.MODDED;
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
        String inst = (resolved.instrumentId() == null) ? "" : resolved.instrumentId().trim();
        if (!inst.isBlank()) {
            triggerInstrumentImpulse(inst, resolved.frequencyHz(), resolved.durationMs(), gain01, pat, resolved.pulsePeriodMs(), resolved.pulseWidthMs(), resolved.priority(), 0, "mount.hoof");
            triggerInstrumentImpulse(inst,
                    clamp(resolved.frequencyHz() - 4.0, store.global.minFrequency, store.global.maxFrequency),
                    Math.max(25, (int) Math.round(resolved.durationMs() * 0.70)),
                    clamp(gain01 * 0.65, 0.0, 1.0),
                    pat,
                    resolved.pulsePeriodMs(),
                    resolved.pulseWidthMs(),
                    Math.max(0, resolved.priority() - 1),
                    32,
                    "mount.hoof_tail");
        } else {
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

    // --- Phase 3: Per-transducer calibration helpers (forced channel routing) ---

    public void testCalibrationToneOnChannel(String channelId, double freqHz, int durationMs) {
        wakeForTests();
        String ch = (channelId == null) ? "" : channelId.trim().toUpperCase(java.util.Locale.ROOT);
        double f = clamp(freqHz, 10.0, 120.0);
        int ms = Math.max(50, durationMs);
        double gain01 = clamp(BstConfig.get().masterVolume * 0.85, 0.0, 1.0);
        triggerImpulse(f, ms, gain01, 0.0, "flat", 160, 60, 98, 0, "cal.ch." + ch + ".tone");
    }

    public void testCalibrationSweepOnChannel(String channelId) {
        wakeForTests();
        String ch = (channelId == null) ? "" : channelId.trim().toUpperCase(java.util.Locale.ROOT);
        double gain01 = clamp(BstConfig.get().masterVolume * 0.85, 0.0, 1.0);
        triggerSweepImpulse(20.0, 120.0, 6500, gain01, 0.0, "flat", 160, 60, 98, 0, "cal.ch." + ch + ".sweep");
    }

    /**
     * Short, punchy burst for per-transducer calibration.
     */
    public void testCalibrationBurstOnChannel(String channelId) {
        wakeForTests();
        String ch = (channelId == null) ? "" : channelId.trim().toUpperCase(java.util.Locale.ROOT);
        // Slightly lower than tone/sweep to keep it comfortable.
        double gain01 = clamp(BstConfig.get().masterVolume * 0.75, 0.0, 1.0);
        triggerImpulse(45.0, 45, gain01, 0.10, "punch", 160, 60, 98, 0, "cal.ch." + ch + ".burst");
    }

    public void testLatencyPulseOnChannel(String channelId) {
        wakeForTests();
        String ch = (channelId == null) ? "" : channelId.trim().toUpperCase(java.util.Locale.ROOT);
        double gain01 = clamp(BstConfig.get().masterVolume * 0.90, 0.0, 1.0);
        triggerImpulse(42.0, 55, gain01, 0.12, "single", 160, 60, 99, 0, "cal.ch." + ch + ".latency");
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
        AudioOutputDevice device = null;
        long startedNs = System.nanoTime();
        boolean loggedNoTelemetryHint = false;
        try {
            int framesPerChunk = 1024;
            byte[] buffer = new byte[framesPerChunk * 2 * BYTES_PER_SAMPLE];
            int bufferChannels = 2;

            OutputEq outputEq = new OutputEq();
            TransducerEq transducerEq = new TransducerEq();
            SmartVolume smartVolume = new SmartVolume();

            float[] transducerGain = new float[8];

            // Per-frame accumulation (reused).
            double[] ch = new double[8];

            // Debug capture state (only used when DEBUG_CAPTURE_ENABLED is true).
            float[] debugMonoRing = new float[DEBUG_SPECT_FFT_SIZE];
            int debugMonoWrite = 0;
            float[] debugWave = new float[DEBUG_WAVE_SAMPLES];
            float[] debugSpect = new float[DEBUG_SPECT_COLS * DEBUG_SPECT_BINS];
            int debugSpectWriteCol = 0;
            double[] dbgSumSq = new double[8];
            double[] dbgPeak = new double[8];
            double[] fftReal = new double[DEBUG_SPECT_FFT_SIZE];
            double[] fftImag = new double[DEBUG_SPECT_FFT_SIZE];

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
                if (!hasFreshTelemetry && device != null && (nowNs - lastTelemetryNanos) > sleepNs) {
                    try {
                        device.stopFlushClose();
                    } catch (Exception ignored) {
                    }
                    device = null;
                }

                if (device == null) {
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

                    HapticAudioBackend backend = BackendSelector.select(cfg);
                    device = backend.open(preferredFormat(), cfg);
                    if (device == null) {
                        LOGGER.error("[BST] Failed to open any audio line; will retry in 1s");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }

                    bufferChannels = Math.max(1, device.channels());
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
                        device.start();
                    } catch (Exception e) {
                        LOGGER.error("[BST] Failed to start audio device ({}); will retry", e.toString());
                        try {
                            device.stopFlushClose();
                        } catch (Exception ignored) {
                        }
                        device = null;
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

                // Phase 3: per-transducer calibration is only meaningful in Sound Scape mode.
                if (cfg.soundScapeEnabled) {
                    fillTransducerGains(cfg, bufferChannels, transducerGain);
                    transducerEq.updateIfNeeded(cfg, bufferChannels);
                } else {
                    for (int c = 0; c < 8; c++) {
                        transducerGain[c] = 1.0f;
                    }
                    transducerEq.resetIfDisabled();
                }

                int damageLeft = damageBurstSamplesLeft.get();
                int biomeLeft = biomeChimeSamplesLeft.get();
                int bumpLeft = accelBumpSamplesLeft.get();

                // Determine the single dominant source for this chunk.
                int dominantKind = 0; // 0 none, 1 road, 2 damage, 3 impulse, 4 bump, 5 chime
                int dominantPriority = -1;
                double dominantStrength = -1.0;
                ImpulseVoice dominantImpulse = null;
                EnumMap<HapticBus, ImpulseVoice> dominantImpulseByBus = new EnumMap<>(HapticBus.class);

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

                // Impulses: choose a dominant voice per bus (multi-bus foundation).
                synchronized (impulseLock) {
                    for (int vi = impulses.size() - 1; vi >= 0; vi--) {
                        ImpulseVoice v = impulses.get(vi);
                        if (v.delaySamplesLeft > 0) {
                            continue;
                        }
                        if (v.samplesLeft <= 0 || v.gain <= 0.00001) {
                            continue;
                        }

                        HapticBus bus = (v.bus == null) ? HapticBus.MODDED : v.bus;
                        ImpulseVoice dom = dominantImpulseByBus.get(bus);
                        if (dom == null
                                || v.priority > dom.priority
                                || (v.priority == dom.priority && v.gain > dom.gain)
                                || (v.priority == dom.priority && v.gain == dom.gain && v.createdNanos > dom.createdNanos)) {
                            dominantImpulseByBus.put(bus, v);
                        }
                    }
                }
                // For debug display: pick the overall best impulse across buses.
                for (ImpulseVoice v : dominantImpulseByBus.values()) {
                    if (v == null) {
                        continue;
                    }
                    if (dominantImpulse == null
                            || v.priority > dominantImpulse.priority
                            || (v.priority == dominantImpulse.priority && v.gain > dominantImpulse.gain)
                            || (v.priority == dominantImpulse.priority && v.gain == dominantImpulse.gain && v.createdNanos > dominantImpulse.createdNanos)) {
                        dominantImpulse = v;
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

                boolean anyImpulseActive = !dominantImpulseByBus.isEmpty();

                // Debug-only dominant: allow impulses to win, without affecting mixing.
                int debugKind = dominantKind;
                int debugPriority = dominantPriority;
                double debugStrength = dominantStrength;
                ImpulseVoice debugImpulse = null;
                if (dominantImpulse != null) {
                    int pri = dominantImpulse.priority;
                    double strength = dominantImpulse.gain;
                    if (pri > debugPriority || (pri == debugPriority && strength > debugStrength)) {
                        debugKind = 3;
                        debugPriority = pri;
                        debugStrength = strength;
                        debugImpulse = dominantImpulse;
                    }
                }

                // Update dominant debug snapshot (only when it changes).
                String domLabel;
                double domFreq = 0.0;
                double domGain = 0.0;
                if (debugKind == 1) {
                    domLabel = "road";
                    domGain = dominantStrength;
                } else if (debugKind == 2) {
                    domLabel = "damage";
                    domGain = dominantStrength;
                } else if (debugKind == 4) {
                    domLabel = "accel_bump";
                    domGain = dominantStrength;
                } else if (debugKind == 5) {
                    domLabel = "biome_chime";
                    domGain = dominantStrength;
                } else if (debugKind == 3 && debugImpulse != null) {
                    String dk = (debugImpulse.debugKey == null) ? "" : debugImpulse.debugKey;
                    domLabel = dk.isBlank() ? "impulse" : dk;
                    if (Math.abs(debugImpulse.endFreqHz - debugImpulse.startFreqHz) > 0.01) {
                        domFreq = (debugImpulse.startFreqHz + debugImpulse.endFreqHz) * 0.5;
                    } else {
                        domFreq = debugImpulse.freqHz;
                    }
                    domGain = debugImpulse.gain;
                } else {
                    domLabel = "none";
                }

                boolean differsFromPublished = !domLabel.equals(debugDominantLabel)
                    || debugPriority != debugDominantPriority
                        || Math.abs(domFreq - debugDominantFreqHz) > 0.05
                        || Math.abs(domGain - debugDominantGain01) > 0.01;

                if (!differsFromPublished) {
                    // Candidate matches what's already published; clear any pending transition.
                    pendingDominantSinceNs = 0L;
                } else {
                    boolean matchesPending = domLabel.equals(pendingDominantLabel)
                            && debugPriority == pendingDominantPriority
                            && Math.abs(domFreq - pendingDominantFreqHz) <= 0.05
                            && Math.abs(domGain - pendingDominantGain01) <= 0.01;

                    if (!matchesPending) {
                        pendingDominantLabel = domLabel;
                        pendingDominantPriority = debugPriority;
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

                double roadMul = (dominantKind == 0 || dominantKind == 1) ? (anyImpulseActive ? DUCK_FACTOR : 1.0) : DUCK_FACTOR;
                double damageMul = (dominantKind == 2) ? 1.0 : DUCK_FACTOR;
                double bumpMul = (dominantKind == 4) ? 1.0 : DUCK_FACTOR;
                double chimeMul = (dominantKind == 5) ? 1.0 : DUCK_FACTOR;

                boolean debugCapture = DEBUG_CAPTURE_ENABLED.get();
                if (debugCapture) {
                    Arrays.fill(dbgSumSq, 0, Math.max(1, bufferChannels), 0.0);
                    Arrays.fill(dbgPeak, 0, Math.max(1, bufferChannels), 0.0);
                }

                int idx = 0;
                synchronized (impulseLock) {
                    int waveStep = debugCapture ? Math.max(1, framesPerChunk / DEBUG_WAVE_SAMPLES) : 1;
                    int waveIndex = 0;

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
                            HapticBus bus = (v.bus == null) ? HapticBus.MODDED : v.bus;
                            ImpulseVoice dom = dominantImpulseByBus.get(bus);
                            voiceMul = (dom != null && v == dom) ? 1.0 : DUCK_FACTOR;

                            double overallProgress = (total <= 1) ? 1.0 : clamp(sampleIndex / (double) (total - 1), 0.0, 1.0);
                            double freqHz = v.startFreqHz + ((v.endFreqHz - v.startFreqHz) * overallProgress);
                            double w;
                            if (v.dspGraph != null && v.dspContext != null) {
                                v.dspContext.retune(v.startFreqHz, v.endFreqHz);
                                v.dspContext.resize(total);
                                v.dspContext.sampleIndex = sampleIndex;
                                w = v.dspGraph.out(v.dspContext);
                            } else {
                                double step = (2.0 * Math.PI * freqHz) / SAMPLE_RATE;
                                double sine = Math.sin(v.phase);

                                // Low-pass the noise component to keep impulses tactile and less "snappy".
                                double fc = 65.0;
                                double a = 1.0 - Math.exp(-(2.0 * Math.PI * fc) / SAMPLE_RATE);
                                double white = (random.nextDouble() * 2.0) - 1.0;
                                v.noiseState += (white - v.noiseState) * a;

                                w = (sine * (1.0 - v.noiseMix)) + (v.noiseState * v.noiseMix);

                                v.phase += step;
                                if (v.phase > (2.0 * Math.PI)) {
                                    v.phase -= (2.0 * Math.PI);
                                }
                            }
                            double voice = w * v.gain * env * voiceMul;
                                int mask = (v.forcedMask != 0)
                                    ? v.forcedMask
                                    : (router.maskForEffectKey(v.debugKey) & router.maskForBus(bus));
                            addToChannelsSpatial(ch, bufferChannels, mask, v, voice);

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
                    double monoSum = 0.0;
                    for (int c = 0; c < bufferChannels; c++) {
                        double s = ch[c] * sv;
                        // Per-transducer calibration (gain + EQ)
                        if (cfg.soundScapeEnabled) {
                            s *= transducerGain[c];
                            s = transducerEq.process(c, s);
                        }
                            if (cfg.outputEqEnabled && cfg.outputEqGainDb != 0) {
                                outputEq.updateIfNeeded(cfg.outputEqFreqHz, cfg.outputEqGainDb, bufferChannels);
                                s = outputEq.process(c, s);
                            } else {
                                outputEq.resetIfDisabled();
                            }

                            double sample = softClipTanh(s, limiterDrive);
                        sample = clamp(sample, -1.0, 1.0);

                        double out = sample * mg;
                        if (debugCapture) {
                            dbgSumSq[c] += (out * out);
                            double abs = Math.abs(out);
                            if (abs > dbgPeak[c]) {
                                dbgPeak[c] = abs;
                            }
                            monoSum += out;
                        }

                        short s16 = (short) (out * 32767);

                        buffer[idx++] = (byte) (s16 & 0xFF);
                        buffer[idx++] = (byte) ((s16 >>> 8) & 0xFF);
                    }

                    if (debugCapture) {
                        double mono = monoSum / Math.max(1, bufferChannels);
                        debugMonoRing[debugMonoWrite] = (float) clamp(mono, -1.0, 1.0);
                        debugMonoWrite = (debugMonoWrite + 1) & (DEBUG_SPECT_FFT_SIZE - 1);
                        if ((i % waveStep) == 0 && waveIndex < DEBUG_WAVE_SAMPLES) {
                            debugWave[waveIndex++] = (float) clamp(mono, -1.0, 1.0);
                        }
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

                if (debugCapture) {
                    int fmtFrameSize = 0;
                    try {
                        AudioFormat fmt = device.format();
                        fmtFrameSize = (fmt == null) ? 0 : fmt.getFrameSize();
                    } catch (Exception ignored) {
                    }

                    int bufferBytes = (device == null) ? -1 : device.bufferSizeBytes();
                    int availableBytes = (device == null) ? -1 : device.availableBytes();
                    double bufferMs = 0.0;
                    double queuedMs = 0.0;
                    if (bufferBytes > 0 && fmtFrameSize > 0) {
                        int frames = bufferBytes / fmtFrameSize;
                        bufferMs = (frames * 1000.0) / SAMPLE_RATE;
                    }
                    if (bufferBytes > 0 && availableBytes >= 0 && fmtFrameSize > 0) {
                        int queuedBytes = Math.max(0, bufferBytes - availableBytes);
                        int queuedFrames = queuedBytes / fmtFrameSize;
                        queuedMs = (queuedFrames * 1000.0) / SAMPLE_RATE;
                    }

                    // FFT-based low-frequency "spectrogram" (bins 1..DEBUG_SPECT_BINS).
                    for (int n = 0; n < DEBUG_SPECT_FFT_SIZE; n++) {
                        int src = (debugMonoWrite + n) & (DEBUG_SPECT_FFT_SIZE - 1);
                        double x = debugMonoRing[src];
                        double w = 0.5 - (0.5 * Math.cos((2.0 * Math.PI * n) / (DEBUG_SPECT_FFT_SIZE - 1)));
                        fftReal[n] = x * w;
                        fftImag[n] = 0.0;
                    }
                    fftRadix2InPlace(fftReal, fftImag);

                    int colBase = debugSpectWriteCol * DEBUG_SPECT_BINS;
                    double norm = Math.max(1.0, DEBUG_SPECT_FFT_SIZE / 2.0);
                    for (int b = 0; b < DEBUG_SPECT_BINS; b++) {
                        int k = b + 1;
                        double re = fftReal[k];
                        double im = fftImag[k];
                        double mag = Math.sqrt((re * re) + (im * im)) / norm;
                        double db = 20.0 * Math.log10(mag + 1.0e-9);
                        double v = (db + 60.0) / 60.0; // -60dB..0dB -> 0..1
                        debugSpect[colBase + b] = (float) clamp(v, 0.0, 1.0);
                    }
                    debugSpectWriteCol = (debugSpectWriteCol + 1) % DEBUG_SPECT_COLS;

                    float[] rms01 = new float[bufferChannels];
                    float[] peak01 = new float[bufferChannels];
                    for (int c = 0; c < bufferChannels; c++) {
                        double rms = Math.sqrt(dbgSumSq[c] / Math.max(1, framesPerChunk));
                        rms01[c] = (float) clamp(rms, 0.0, 1.0);
                        peak01[c] = (float) clamp(dbgPeak[c], 0.0, 1.0);
                    }

                    DEBUG_SNAPSHOT.set(new DebugSnapshot(
                            nowNs,
                            bufferChannels,
                            rms01,
                            peak01,
                            Arrays.copyOf(debugWave, debugWave.length),
                            Arrays.copyOf(debugSpect, debugSpect.length),
                            DEBUG_SPECT_COLS,
                            DEBUG_SPECT_BINS,
                            debugSpectWriteCol,
                            bufferBytes,
                            availableBytes,
                            bufferMs,
                            queuedMs
                    ));
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
                    device.write(buffer, 0, buffer.length);
                } catch (Exception e) {
                    LOGGER.warn("[BST] Audio write failed ({}); reopening device", e.toString());
                    try {
                        device.stopFlushClose();
                    } catch (Exception ignored) {
                    }
                    device = null;
                }
            }
        } catch (Exception e) {
            LOGGER.error("[BST] Audio thread crashed", e);
        } finally {
            if (device != null) {
                try {
                    device.stopFlushClose();
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

    private static final String[] CHANNEL_IDS_7_1 = new String[]{"FL", "FR", "C", "LFE", "SL", "SR", "BL", "BR"};

    private static void fillTransducerGains(BstConfig.Data cfg, int channelCount, float[] out) {
        if (out == null) {
            return;
        }
        int n = (channelCount == 8) ? 8 : 2;
        for (int i = 0; i < n; i++) {
            out[i] = 1.0f;
        }
        if (cfg == null || cfg.soundScapeCalibration == null) {
            return;
        }
        for (int c = 0; c < n; c++) {
            String id = (n == 2) ? ((c == 0) ? "FL" : "FR") : CHANNEL_IDS_7_1[c];
            BstConfig.Data.TransducerCalibration cal = cfg.soundScapeCalibration.get(id);
            double db = (cal == null) ? 0.0 : cal.gainDb;
            double comfort = (cal == null) ? 1.0 : cal.comfortLimit01;
            comfort = clamp(comfort, 0.0, 1.0);
            out[c] = (float) (dbToLinear(db) * comfort);
        }
    }

    private static float dbToLinear(double db) {
        double d = db;
        if (!Double.isFinite(d)) {
            d = 0.0;
        }
        d = clamp(d, -24.0, 24.0);
        return (float) Math.pow(10.0, d / 20.0);
    }

    private static int forcedMaskFromDebugKey(String debugKey) {
        if (debugKey == null || debugKey.isBlank()) {
            return 0;
        }
        String raw = debugKey.trim();
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith("cal.ch.")) {
            return 0;
        }
        int start = "cal.ch.".length();
        int end = raw.indexOf('.', start);
        String id = ((end >= 0) ? raw.substring(start, end) : raw.substring(start)).trim().toUpperCase(java.util.Locale.ROOT);
        if (id.isEmpty()) {
            return 0;
        }
        return switch (id) {
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

    private static void addToChannelsSpatial(double[] ch, int channelCount, int mask, ImpulseVoice voice, double v) {
        if (voice == null) {
            addToChannels(ch, channelCount, mask, v);
            return;
        }
        if (v == 0.0) {
            return;
        }
        if (!voice.spatialEnabled) {
            addToChannels(ch, channelCount, mask, v);
            return;
        }
        int m = (mask == 0) ? ((channelCount == 8) ? 0xFF : 0x03) : mask;
        voice.ensureMaskedPan(channelCount, m);

        if (channelCount == 2) {
            if ((m & 0x01) != 0) ch[0] += v * voice.maskedPan2L;
            if ((m & 0x02) != 0) ch[1] += v * voice.maskedPan2R;
            return;
        }

        // 8ch (7.1) expected; channelCount is clamped elsewhere.
        if ((m & 0x01) != 0) ch[0] += v * voice.maskedPan8FL;
        if ((m & 0x02) != 0) ch[1] += v * voice.maskedPan8FR;
        if ((m & 0x04) != 0) ch[2] += v * voice.maskedPan8C;
        if ((m & 0x08) != 0) ch[3] += v * voice.maskedPan8LFE;
        if ((m & 0x10) != 0) ch[4] += v * voice.maskedPan8SL;
        if ((m & 0x20) != 0) ch[5] += v * voice.maskedPan8SR;
        if ((m & 0x40) != 0) ch[6] += v * voice.maskedPan8BL;
        if ((m & 0x80) != 0) ch[7] += v * voice.maskedPan8BR;
    }

    private static void initVoiceSpatial(ImpulseVoice v, BstConfig.Data cfg, double azimuthDeg, double distanceM) {
        if (v == null) {
            return;
        }

        double az = azimuthDeg;
        if (!Double.isFinite(az)) {
            az = 0.0;
        }
        az = az % 360.0;
        if (az > 180.0) az -= 360.0;
        if (az < -180.0) az += 360.0;

        double dist = distanceM;
        if (!Double.isFinite(dist) || dist < 0.0) {
            dist = 0.0;
        }
        dist = Math.min(dist, 2048.0);

        v.spatialAzimuthDeg = az;
        v.spatialDistanceM = dist;

        boolean spatialEnabled = cfg != null && cfg.soundScapeEnabled && cfg.soundScapeSpatialEnabled;
        v.spatialEnabled = spatialEnabled;

        // Reset cached masked gains whenever base changes.
        v.cachedPanChannels = -1;
        v.cachedPanMask = Integer.MIN_VALUE;

        if (!spatialEnabled) {
            v.basePan2L = 1.0f;
            v.basePan2R = 1.0f;
            v.basePan8FL = v.basePan8FR = v.basePan8C = v.basePan8LFE = v.basePan8SL = v.basePan8SR = v.basePan8BL = v.basePan8BR = 1.0f;
            return;
        }

        double strength = (cfg == null) ? 0.5 : clamp(cfg.soundScapeSpatialDistanceAttenStrength, 0.0, 1.0);
        double curve = 1.0 / (1.0 + (dist / 6.0));
        curve = clamp(curve, 0.25, 1.0);
        float distMul = (float) (1.0 + ((curve - 1.0) * strength));

        // Stereo (fallback): equal-power left/right pan using azimuth.
        double pan = clamp(az / 90.0, -1.0, 1.0); // -1 left .. +1 right
        double theta = (pan + 1.0) * (Math.PI / 4.0);
        v.basePan2L = (float) (Math.cos(theta) * distMul);
        v.basePan2R = (float) (Math.sin(theta) * distMul);

        // 7.1 ring panning (LFE excluded from ring).
        float[] g8 = computeRing71PanGains(az);
        v.basePan8FL = g8[0] * distMul;
        v.basePan8FR = g8[1] * distMul;
        v.basePan8C = g8[2] * distMul;
        v.basePan8LFE = g8[3] * distMul;
        v.basePan8SL = g8[4] * distMul;
        v.basePan8SR = g8[5] * distMul;
        v.basePan8BL = g8[6] * distMul;
        v.basePan8BR = g8[7] * distMul;
    }

    private static float[] computeRing71PanGains(double azimuthDeg) {
        // Returns base gains for channel indices:
        // 0 FL, 1 FR, 2 C, 3 LFE, 4 SL, 5 SR, 6 BL, 7 BR
        float[] out = new float[8];

        // Convert [-180,180] to [0,360): 0 front, 90 right, 180 rear, 270 left.
        double a = azimuthDeg;
        a = a % 360.0;
        if (a < 0.0) a += 360.0;

        // Ring points in ascending order (degrees).
        // C(0), FR(45), SR(110), BR(150), BL(210), SL(250), FL(315)
        final double[] ang = new double[]{0.0, 45.0, 110.0, 150.0, 210.0, 250.0, 315.0};
        final int[] ch = new int[]{2, 1, 5, 7, 6, 4, 0};

        int i0 = 0;
        for (int i = ang.length - 1; i >= 0; i--) {
            if (a >= ang[i]) {
                i0 = i;
                break;
            }
        }
        int i1 = (i0 + 1) % ang.length;
        double a0 = ang[i0];
        double a1 = ang[i1];
        if (i1 == 0) {
            a1 += 360.0;
        }
        double t = (a1 == a0) ? 0.0 : clamp((a - a0) / (a1 - a0), 0.0, 1.0);

        // Equal-power crossfade between the two nearest ring speakers.
        float w0 = (float) Math.cos(t * (Math.PI / 2.0));
        float w1 = (float) Math.sin(t * (Math.PI / 2.0));

        out[ch[i0]] = w0;
        out[ch[i1]] = w1;
        // LFE stays 0 by default for directional panning.
        out[3] = 0.0f;
        return out;
    }

    @SuppressWarnings("unused")
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
        private final Map<String, Integer> busMasks = new java.util.HashMap<>();

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

            Map<String, String> buses = (cfg == null) ? null : cfg.soundScapeBusRouting;
            if (buses != null) {
                for (Map.Entry<String, String> e : buses.entrySet()) {
                    if (e == null) continue;
                    String k = (e.getKey() == null) ? "" : e.getKey().trim().toLowerCase(java.util.Locale.ROOT);
                    if (k.isEmpty()) continue;
                    busMasks.put(k, targetToMask(e.getValue()));
                }
            }

            // Defaults: if a bus isn't explicitly set, treat it as All.
            busMasks.putIfAbsent("ui", allMask);
            busMasks.putIfAbsent("danger", allMask);
            busMasks.putIfAbsent("environmental", allMask);
            busMasks.putIfAbsent("continuous", allMask);
            busMasks.putIfAbsent("impact", allMask);
            busMasks.putIfAbsent("modded", allMask);
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

        int maskForBus(HapticBus bus) {
            if (bus == null) {
                return allMask;
            }
            String key = switch (bus) {
                case UI -> "ui";
                case DANGER -> "danger";
                case ENVIRONMENTAL -> "environmental";
                case CONTINUOUS -> "continuous";
                case IMPACT -> "impact";
                case MODDED -> "modded";
            };
            Integer m = busMasks.get(key);
            return (m == null || m == 0) ? allMask : m;
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
        String instrumentId;
        int forcedMask;
        int pulsePeriodSamples;
        int pulseWidthSamples;

        double phase;
        double noiseState;

        DspGraphInstance dspGraph;
        DspContext dspContext;

        int priority;
        HapticBus bus;
        long createdNanos;

        // --- Phase 3: Spatial panning ---
        boolean spatialEnabled;
        double spatialAzimuthDeg;
        double spatialDistanceM;

        // Base pan gains (pre-mask, pre-normalization)
        float basePan2L;
        float basePan2R;

        float basePan8FL;
        float basePan8FR;
        float basePan8C;
        float basePan8LFE;
        float basePan8SL;
        float basePan8SR;
        float basePan8BL;
        float basePan8BR;

        // Cached masked+normalized gains for the last (channels,mask) combination.
        int cachedPanChannels = -1;
        int cachedPanMask = Integer.MIN_VALUE;

        float maskedPan2L;
        float maskedPan2R;

        float maskedPan8FL;
        float maskedPan8FR;
        float maskedPan8C;
        float maskedPan8LFE;
        float maskedPan8SL;
        float maskedPan8SR;
        float maskedPan8BL;
        float maskedPan8BR;

        void ensureMaskedPan(int channelCount, int mask) {
            int ch = (channelCount == 8) ? 8 : 2;
            if (cachedPanChannels == ch && cachedPanMask == mask) {
                return;
            }
            cachedPanChannels = ch;
            cachedPanMask = mask;

            if (ch == 2) {
                maskedPan2L = ((mask & 0x01) != 0) ? basePan2L : 0.0f;
                maskedPan2R = ((mask & 0x02) != 0) ? basePan2R : 0.0f;

                double n = Math.sqrt((maskedPan2L * maskedPan2L) + (maskedPan2R * maskedPan2R));
                if (n < 1.0e-6) {
                    int count = 0;
                    if ((mask & 0x01) != 0) count++;
                    if ((mask & 0x02) != 0) count++;
                    if (count <= 0) {
                        maskedPan2L = maskedPan2R = 0.0f;
                        return;
                    }
                    float g = (float) (1.0 / Math.sqrt(count));
                    maskedPan2L = ((mask & 0x01) != 0) ? g : 0.0f;
                    maskedPan2R = ((mask & 0x02) != 0) ? g : 0.0f;
                    return;
                }

                float inv = (float) (1.0 / n);
                maskedPan2L *= inv;
                maskedPan2R *= inv;
                return;
            }

            maskedPan8FL = ((mask & 0x01) != 0) ? basePan8FL : 0.0f;
            maskedPan8FR = ((mask & 0x02) != 0) ? basePan8FR : 0.0f;
            maskedPan8C = ((mask & 0x04) != 0) ? basePan8C : 0.0f;
            maskedPan8LFE = ((mask & 0x08) != 0) ? basePan8LFE : 0.0f;
            maskedPan8SL = ((mask & 0x10) != 0) ? basePan8SL : 0.0f;
            maskedPan8SR = ((mask & 0x20) != 0) ? basePan8SR : 0.0f;
            maskedPan8BL = ((mask & 0x40) != 0) ? basePan8BL : 0.0f;
            maskedPan8BR = ((mask & 0x80) != 0) ? basePan8BR : 0.0f;

            double n = 0.0;
            n += maskedPan8FL * maskedPan8FL;
            n += maskedPan8FR * maskedPan8FR;
            n += maskedPan8C * maskedPan8C;
            n += maskedPan8LFE * maskedPan8LFE;
            n += maskedPan8SL * maskedPan8SL;
            n += maskedPan8SR * maskedPan8SR;
            n += maskedPan8BL * maskedPan8BL;
            n += maskedPan8BR * maskedPan8BR;
            n = Math.sqrt(n);

            if (n < 1.0e-6) {
                int count = Integer.bitCount(mask & 0xFF);
                if (count <= 0) {
                    maskedPan8FL = maskedPan8FR = maskedPan8C = maskedPan8LFE = maskedPan8SL = maskedPan8SR = maskedPan8BL = maskedPan8BR = 0.0f;
                    return;
                }
                float g = (float) (1.0 / Math.sqrt(count));
                maskedPan8FL = ((mask & 0x01) != 0) ? g : 0.0f;
                maskedPan8FR = ((mask & 0x02) != 0) ? g : 0.0f;
                maskedPan8C = ((mask & 0x04) != 0) ? g : 0.0f;
                maskedPan8LFE = ((mask & 0x08) != 0) ? g : 0.0f;
                maskedPan8SL = ((mask & 0x10) != 0) ? g : 0.0f;
                maskedPan8SR = ((mask & 0x20) != 0) ? g : 0.0f;
                maskedPan8BL = ((mask & 0x40) != 0) ? g : 0.0f;
                maskedPan8BR = ((mask & 0x80) != 0) ? g : 0.0f;
                return;
            }

            float inv = (float) (1.0 / n);
            maskedPan8FL *= inv;
            maskedPan8FR *= inv;
            maskedPan8C *= inv;
            maskedPan8LFE *= inv;
            maskedPan8SL *= inv;
            maskedPan8SR *= inv;
            maskedPan8BL *= inv;
            maskedPan8BR *= inv;
        }
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

    private static final class TransducerEq {
        // RBJ peaking EQ with fixed Q.
        private static final double Q = 1.0;

        private final boolean[] active = new boolean[8];
        private final int[] lastFreqHz = new int[8];
        private final int[] lastGainDb = new int[8];

        // Coeffs (per channel)
        private final double[] b0 = new double[8];
        private final double[] b1 = new double[8];
        private final double[] b2 = new double[8];
        private final double[] a1 = new double[8];
        private final double[] a2 = new double[8];

        // State
        private final double[] x1 = new double[8];
        private final double[] x2 = new double[8];
        private final double[] y1 = new double[8];
        private final double[] y2 = new double[8];

        TransducerEq() {
            for (int i = 0; i < 8; i++) {
                lastFreqHz[i] = -1;
                lastGainDb[i] = Integer.MIN_VALUE;
            }
        }

        void updateIfNeeded(BstConfig.Data cfg, int channels) {
            int n = (channels == 8) ? 8 : 2;

            for (int c = 0; c < 8; c++) {
                if (c >= n) {
                    active[c] = false;
                    continue;
                }

                if (cfg == null || cfg.soundScapeCalibration == null) {
                    active[c] = false;
                    continue;
                }

                String id = (n == 2) ? ((c == 0) ? "FL" : "FR") : CHANNEL_IDS_7_1[c];
                BstConfig.Data.TransducerCalibration cal = cfg.soundScapeCalibration.get(id);
                int f = (cal == null) ? 45 : clampInt(cal.eqFreqHz, 10, 120);
                int g = (cal == null) ? 0 : clampInt(cal.eqGainDb, -12, 12);

                if (g == 0) {
                    // Disable EQ on this channel.
                    active[c] = false;
                    lastFreqHz[c] = f;
                    lastGainDb[c] = 0;
                    continue;
                }

                if (active[c] && f == lastFreqHz[c] && g == lastGainDb[c]) {
                    continue;
                }

                lastFreqHz[c] = f;
                lastGainDb[c] = g;
                active[c] = true;

                // Reset state for this channel when settings change.
                x1[c] = x2[c] = y1[c] = y2[c] = 0.0;

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

                b0[c] = bb0 / aa0;
                b1[c] = bb1 / aa0;
                b2[c] = bb2 / aa0;
                a1[c] = aa1 / aa0;
                a2[c] = aa2 / aa0;
            }
        }

        double process(int channel, double x) {
            int c = (channel < 0) ? 0 : Math.min(channel, 7);
            if (!active[c]) {
                return x;
            }
            double y = (b0[c] * x) + (b1[c] * x1[c]) + (b2[c] * x2[c]) - (a1[c] * y1[c]) - (a2[c] * y2[c]);
            x2[c] = x1[c];
            x1[c] = x;
            y2[c] = y1[c];
            y1[c] = y;
            return y;
        }

        void resetIfDisabled() {
            for (int c = 0; c < 8; c++) {
                active[c] = false;
                lastFreqHz[c] = -1;
                lastGainDb[c] = Integer.MIN_VALUE;
                x1[c] = x2[c] = y1[c] = y2[c] = 0.0;
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
