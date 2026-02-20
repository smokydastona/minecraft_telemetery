package com.smoky.bassshakertelemetry.telemetryout;

import com.smoky.bassshakertelemetry.api.HapticUnifiedEvent;

/**
 * Thread-local context for enriching outgoing unified events.
 *
 * <p>Call sites that know semantic IDs and positions (e.g., client ingress) can install a context
 * before triggering synthesis, allowing the engine-side emitter to broadcast a rich "event" JSON
 * packet without changing audio method signatures.
 */
public final class HapticEventContext {
    private static final ThreadLocal<HapticUnifiedEvent> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<String> DIRECTION_BAND = new ThreadLocal<>();

    private HapticEventContext() {
    }

    public static void withEventContext(HapticUnifiedEvent event, Runnable action) {
        if (action == null) {
            return;
        }

        HapticUnifiedEvent prev = CURRENT.get();
        CURRENT.set(event);
        try {
            action.run();
        } finally {
            if (prev == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(prev);
            }
        }
    }

    /**
     * Installs a best-effort direction band hint for the duration of {@code action}.
     *
     * <p>Expected values: {@code center|front|rear|left|right}. Unknown/blank values fall back to {@code center}.
     */
    public static void withDirectionBand(String band, Runnable action) {
        if (action == null) {
            return;
        }

        String prev = DIRECTION_BAND.get();
        DIRECTION_BAND.set(sanitizeBand(band));
        try {
            action.run();
        } finally {
            if (prev == null) {
                DIRECTION_BAND.remove();
            } else {
                DIRECTION_BAND.set(prev);
            }
        }
    }

    public static String currentDirectionBand() {
        return sanitizeBand(DIRECTION_BAND.get());
    }

    static HapticUnifiedEvent current() {
        return CURRENT.get();
    }

    private static String sanitizeBand(String band) {
        if (band == null) {
            return "center";
        }
        String b = band.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (b) {
            case "front", "rear", "left", "right" -> b;
            default -> "center";
        };
    }
}
