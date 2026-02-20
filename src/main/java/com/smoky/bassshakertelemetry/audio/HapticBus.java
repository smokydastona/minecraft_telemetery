package com.smoky.bassshakertelemetry.audio;

/**
 * Multi-bus mixer categories.
 *
 * <p>These are the internal buses used by the audio engine. They intentionally match
 * the roadmap naming so future routing/UI can stay consistent.
 */
public enum HapticBus {
    IMPACT,
    CONTINUOUS,
    ENVIRONMENTAL,
    UI,
    DANGER,
    MODDED;
}
