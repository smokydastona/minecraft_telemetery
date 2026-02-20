package com.smoky.bassshakertelemetry.api;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import com.smoky.bassshakertelemetry.telemetryout.HapticEventContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.Map;

/**
 * Public integration API for other mods.
 *
 * <p>Safe to call on dedicated servers: all audio work is gated behind {@link Dist#CLIENT}.
 */
public final class HapticApi {
    private HapticApi() {
    }

    /**
     * Emits a haptic event (impulse/tone/sweep) if Bass Shaker Telemetry is enabled on the client.
     */
    public static void send(HapticEvent event) {
        if (event == null) {
            return;
        }

        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (!BstConfig.get().enabled()) {
                return;
            }

            double intensity01 = clamp01(event.intensity01());
            if (intensity01 <= 0.0) {
                return;
            }

            int durationMs = Math.max(0, event.durationMs());
            int delayMs = Math.max(0, event.delayMs());

            // Keep external events in the existing range used by internal emitters.
            int priority = clampInt(event.priority(), 0, 100);
            int pulsePeriodMs = clampInt(event.pulsePeriodMs(), 1, 10_000);
            int pulseWidthMs = clampInt(event.pulseWidthMs(), 0, pulsePeriodMs);

            String debugKey = safeKey(event.key());
            String pattern = safePattern(event.pattern());

            // Let external callers blend in a bit of noise if desired.
            double noiseMix01 = clamp01(event.noiseMix01());

            HapticUnifiedEvent unified = new HapticUnifiedEvent(
                    toUnifiedId(debugKey),
                    HapticEventType.MODDED,
                    "api",
                    null,
                    intensity01,
                    "",
                    Map.of(
                            "key", debugKey,
                            "pattern", pattern
                    )
            );

            HapticEventContext.withEventContext(unified, () -> {
                if (Double.compare(event.startFreqHz(), event.endFreqHz()) == 0) {
                    AudioOutputEngine.get().triggerImpulse(
                            event.startFreqHz(),
                            durationMs,
                            intensity01,
                            noiseMix01,
                            pattern,
                            pulsePeriodMs,
                            pulseWidthMs,
                            priority,
                            delayMs,
                            debugKey
                    );
                } else {
                    AudioOutputEngine.get().triggerSweepImpulse(
                            event.startFreqHz(),
                            event.endFreqHz(),
                            durationMs,
                            intensity01,
                            noiseMix01,
                            pattern,
                            pulsePeriodMs,
                            pulseWidthMs,
                            priority,
                            delayMs,
                            debugKey
                    );
                }
            });
        });
    }

    private static String toUnifiedId(String key) {
        if (key == null || key.isBlank()) {
            return "modded:api";
        }
        String k = key.trim();
        if (k.indexOf(':') >= 0) {
            return k;
        }
        return "modded:" + k;
    }

    public static void sendImpulse(String key, double freqHz, int durationMs, double intensity01) {
        send(HapticEvent.impulse(key, freqHz, durationMs, intensity01));
    }

    public static void sendTone(String key, double freqHz, int durationMs, double intensity01) {
        send(HapticEvent.tone(key, freqHz, durationMs, intensity01));
    }

    public static void sendSweep(String key, double startFreqHz, double endFreqHz, int durationMs, double intensity01) {
        send(HapticEvent.sweep(key, startFreqHz, endFreqHz, durationMs, intensity01));
    }

    private static String safeKey(String key) {
        if (key == null || key.isBlank()) {
            return "api";
        }
        // Avoid giant keys / spam from other mods.
        if (key.length() > 64) {
            return key.substring(0, 64);
        }
        return key;
    }

    private static String safePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return "single";
        }
        if (pattern.length() > 32) {
            return pattern.substring(0, 32);
        }
        return pattern;
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
