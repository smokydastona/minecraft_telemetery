package com.smoky.bassshakertelemetry.api;

/**
 * High-level haptic categories for middleware-style events.
 *
 * <p>These are intentionally broad so they can map to a multi-bus mixer later.
 */
public enum HapticEventType {
    IMPACT("impact"),
    CONTINUOUS("continuous"),
    ENVIRONMENTAL("environmental"),
    UI("ui"),
    DANGER("danger"),
    MODDED("modded");

    private final String wireName;

    HapticEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
