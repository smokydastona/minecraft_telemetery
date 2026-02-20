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
    private static final ThreadLocal<SpatialHint> SPATIAL_HINT = new ThreadLocal<>();

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

    /**
     * Installs a best-effort spatial hint for the duration of {@code action}.
     *
     * <p>Azimuth is in degrees, relative to the player view: {@code 0=front}, {@code +90=right}, {@code -90=left}, {@code 180/-180=rear}.
     * Distance is in meters/blocks (game units) and is clamped to non-negative values.
     */
    public static void withSpatialHint(double azimuthDeg, double distanceMeters, Runnable action) {
        if (action == null) {
            return;
        }

        SpatialHint prev = SPATIAL_HINT.get();
        SPATIAL_HINT.set(sanitizeSpatial(azimuthDeg, distanceMeters));
        try {
            action.run();
        } finally {
            if (prev == null) {
                SPATIAL_HINT.remove();
            } else {
                SPATIAL_HINT.set(prev);
            }
        }
    }

    public static double currentAzimuthDeg() {
        SpatialHint h = SPATIAL_HINT.get();
        return (h == null) ? 0.0 : h.azimuthDeg;
    }

    public static double currentDistanceMeters() {
        SpatialHint h = SPATIAL_HINT.get();
        return (h == null) ? 0.0 : h.distanceMeters;
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

    private record SpatialHint(double azimuthDeg, double distanceMeters) {
    }

    private static SpatialHint sanitizeSpatial(double azimuthDeg, double distanceMeters) {
        double a = azimuthDeg;
        if (!Double.isFinite(a)) {
            a = 0.0;
        }
        // Normalize to [-180, 180].
        a = a % 360.0;
        if (a > 180.0) a -= 360.0;
        if (a < -180.0) a += 360.0;

        double d = distanceMeters;
        if (!Double.isFinite(d) || d < 0.0) {
            d = 0.0;
        }
        // Prevent extreme/outlier distances from producing weird attenuation math.
        d = Math.min(d, 2048.0);

        return new SpatialHint(a, d);
    }
}
