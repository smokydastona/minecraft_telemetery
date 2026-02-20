package com.smoky.bassshakertelemetry.api;

import java.util.Objects;

/**
 * Public API event object for other mods to emit haptic impulses through Bass Shaker Telemetry.
 *
 * <p>This is intentionally small and stable. Do not reference Minecraft client classes here.
 */
public record HapticEvent(
        String key,
        double startFreqHz,
        double endFreqHz,
        int durationMs,
        double intensity01,
        double noiseMix01,
        String pattern,
        int pulsePeriodMs,
        int pulseWidthMs,
        int priority,
        int delayMs
) {
    public HapticEvent {
    key = Objects.requireNonNullElse(key, "");
    pattern = Objects.requireNonNullElse(pattern, "single");
    }

    public static HapticEvent impulse(String key, double freqHz, int durationMs, double intensity01) {
        return new HapticEvent(key, freqHz, freqHz, durationMs, intensity01, 0.0, "single", 160, 60, 5, 0);
    }

    public static HapticEvent tone(String key, double freqHz, int durationMs, double intensity01) {
        return new HapticEvent(key, freqHz, freqHz, durationMs, intensity01, 0.0, "flat", 160, 60, 5, 0);
    }

    public static HapticEvent sweep(String key, double startFreqHz, double endFreqHz, int durationMs, double intensity01) {
        return new HapticEvent(key, startFreqHz, endFreqHz, durationMs, intensity01, 0.0, "flat", 160, 60, 5, 0);
    }
}
