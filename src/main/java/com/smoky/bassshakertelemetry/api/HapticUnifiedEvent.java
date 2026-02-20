package com.smoky.bassshakertelemetry.api;

import java.util.Map;

/**
 * Unified middleware-style event model.
 *
 * <p>This is separate from {@link HapticEvent} (which is a low-level synthesis request).
 * Unified events are intended for routing/mixing/DSP graphs and external integrations.
 */
public record HapticUnifiedEvent(
        String id,
        HapticEventType type,
        String source,
        HapticPosition position,
        double intensity01,
        String instrument,
        Map<String, String> metadata
) {
    public HapticUnifiedEvent {
        if (id == null) id = "";
        if (type == null) type = HapticEventType.IMPACT;
        if (source == null) source = "";
        if (instrument == null) instrument = "";
        metadata = (metadata == null) ? Map.of() : Map.copyOf(metadata);
    }

    public static HapticUnifiedEvent simple(String id, HapticEventType type, double intensity01) {
        return new HapticUnifiedEvent(id, type, "", null, intensity01, "", Map.of());
    }
}
