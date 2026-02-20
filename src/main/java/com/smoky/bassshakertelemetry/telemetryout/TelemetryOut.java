package com.smoky.bassshakertelemetry.telemetryout;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Centralized external telemetry emitter.
 *
 * <p>Common code (audio engine, handlers) can call into this without depending on any client-only code.
 * A client-side controller may install a real sink (e.g. WebSocket broadcast); otherwise it's a no-op.
 */
public final class TelemetryOut {
    private static final AtomicReference<TelemetryOutSink> SINK = new AtomicReference<>(TelemetryOutSink.NOOP);

    private TelemetryOut() {
    }

    public static void setSink(TelemetryOutSink sink) {
        SINK.set(sink == null ? TelemetryOutSink.NOOP : sink);
    }

    public static void emitRawJson(String jsonLine) {
        if (jsonLine == null || jsonLine.isBlank()) {
            return;
        }
        SINK.get().emitJson(jsonLine);
    }

    public static void emitHaptic(String key, double startFreqHz, double endFreqHz, int durationMs,
                                 double intensity01, double noiseMix01, String pattern,
                                 int pulsePeriodMs, int pulseWidthMs, int priority, int delayMs) {
        String k = escapeJson((key == null) ? "" : key);
        String pat = escapeJson((pattern == null) ? "" : pattern);
        long t = System.currentTimeMillis();

        String msg = String.format(Locale.ROOT,
                "{\"type\":\"haptic\",\"t\":%d,\"key\":\"%s\",\"f0\":%.3f,\"f1\":%.3f,\"ms\":%d,\"gain\":%.4f,\"noise\":%.4f,\"pattern\":\"%s\",\"pulsePeriodMs\":%d,\"pulseWidthMs\":%d,\"priority\":%d,\"delayMs\":%d}",
                t,
                k,
                startFreqHz,
                endFreqHz,
                Math.max(0, durationMs),
                intensity01,
                noiseMix01,
                pat,
                Math.max(0, pulsePeriodMs),
                Math.max(0, pulseWidthMs),
                Math.max(0, priority),
                Math.max(0, delayMs)
        );
        emitRawJson(msg);
    }

    public static void emitTelemetry(double speed, double accel, boolean elytra) {
        long t = System.currentTimeMillis();
        String msg = String.format(Locale.ROOT,
                "{\"type\":\"telemetry\",\"t\":%d,\"speed\":%.6f,\"accel\":%.6f,\"elytra\":%s}",
                t,
                speed,
                accel,
                elytra ? "true" : "false"
        );
        emitRawJson(msg);
    }

    private static String escapeJson(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
