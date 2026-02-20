package com.smoky.bassshakertelemetry.telemetryout;

/**
 * Pluggable sink for external telemetry output (e.g. WebSocket broadcast).
 *
 * <p>Must be thread-safe: it can be called from gameplay threads and the audio engine thread.
 */
public interface TelemetryOutSink {
    TelemetryOutSink NOOP = message -> {
    };

    /**
     * Emits a complete JSON message as a single line (no trailing newline required).
     */
    void emitJson(String message);
}
