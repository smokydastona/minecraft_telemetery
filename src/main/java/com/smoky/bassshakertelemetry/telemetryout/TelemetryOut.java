package com.smoky.bassshakertelemetry.telemetryout;

import com.smoky.bassshakertelemetry.api.HapticEventType;
import com.smoky.bassshakertelemetry.api.HapticPosition;
import com.smoky.bassshakertelemetry.api.HapticUnifiedEvent;

import java.util.Locale;
import java.util.Map;
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

    /**
     * Emits a high-level middleware-style event.
     *
     * <p>These are intended to remain relatively stable across versions compared to low-level synthesis fields.
     */
    public static void emitEvent(HapticUnifiedEvent event) {
        if (event == null) {
            return;
        }

        long t = System.currentTimeMillis();
        String id = escapeJson(event.id());
        HapticEventType type = (event.type() == null) ? HapticEventType.IMPACT : event.type();
        String kind = escapeJson(type.wireName());
        String source = escapeJson(event.source());
        String instrument = escapeJson(event.instrument());
        double intensity01 = clamp01(event.intensity01());

        StringBuilder sb = new StringBuilder(160);
        sb.append("{\"type\":\"event\",\"t\":").append(t)
                .append(",\"id\":\"").append(id).append("\"")
                .append(",\"kind\":\"").append(kind).append("\"")
                .append(",\"intensity\":").append(String.format(Locale.ROOT, "%.4f", intensity01));

        if (!source.isEmpty()) {
            sb.append(",\"source\":\"").append(source).append("\"");
        }

        HapticPosition pos = event.position();
        if (pos != null) {
            sb.append(",\"pos\":[")
                    .append(String.format(Locale.ROOT, "%.4f", pos.x())).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", pos.y())).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", pos.z()))
                    .append(']');
        }

        if (!instrument.isEmpty()) {
            sb.append(",\"instrument\":\"").append(instrument).append("\"");
        }

        Map<String, String> meta = event.metadata();
        if (meta != null && !meta.isEmpty()) {
            sb.append(",\"meta\":{");
            boolean first = true;
            for (var e : meta.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(escapeJson(e.getKey())).append('"')
                        .append(':')
                        .append('"').append(escapeJson(e.getValue())).append('"');
            }
            sb.append('}');
        }

        sb.append('}');
        emitRawJson(sb.toString());
    }

    /**
     * Convenience helper to emit a high-level event from an internal haptic key.
     *
     * <p>This does not change audio behavior; it only adds an additional JSON packet for integrations.
     */
    public static void emitEventFromHapticKey(String debugKey, double intensity01) {
        String k = (debugKey == null) ? "" : debugKey;

        HapticUnifiedEvent ctx = HapticEventContext.current();
        if (ctx != null) {
            emitEvent(new HapticUnifiedEvent(
                    ctx.id(),
                    ctx.type(),
                    ctx.source(),
                    ctx.position(),
                    intensity01,
                    ctx.instrument(),
                    ctx.metadata()
            ));
            return;
        }

        if (k.isBlank()) {
            // No meaningful ID available.
            return;
        }

        String id = "bst:" + k;
        emitEvent(new HapticUnifiedEvent(id, classifyTypeFromKey(k), "bst", null, intensity01, "", Map.of()));
    }

    public static HapticEventType classifyTypeFromKey(String key) {
        if (key == null || key.isBlank()) {
            return HapticEventType.IMPACT;
        }

        String k = key.toLowerCase(Locale.ROOT);

        if (k.startsWith("cal.") || k.startsWith("ui.") || k.startsWith("config.")) {
            return HapticEventType.UI;
        }
        if (k.startsWith("warden.") || k.startsWith("danger.")) {
            return HapticEventType.DANGER;
        }
        if (k.startsWith("movement.") || k.startsWith("flight.") || k.startsWith("wind.")) {
            return HapticEventType.CONTINUOUS;
        }
        if (k.startsWith("biome.") || k.startsWith("env.") || k.startsWith("sound.")) {
            return HapticEventType.ENVIRONMENTAL;
        }
        if (k.startsWith("api") || k.startsWith("modded.")) {
            return HapticEventType.MODDED;
        }

        // Most remaining built-ins are short "hits".
        return HapticEventType.IMPACT;
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
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
