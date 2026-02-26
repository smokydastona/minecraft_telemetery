package com.smoky.bassshakertelemetry.client.ui.neon;

import java.util.Locale;

final class NeonColorUtil {
    private NeonColorUtil() {
    }

    static int argb(int a, int r, int g, int b) {
        a = clampByte(a);
        r = clampByte(r);
        g = clampByte(g);
        b = clampByte(b);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static int withAlpha(int argb, int a) {
        a = clampByte(a);
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    static int parseColorOrDefault(String s, int fallbackArgb) {
        if (s == null) return fallbackArgb;
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return fallbackArgb;
        if (t.startsWith("#")) t = t.substring(1);

        try {
            if (t.length() == 6) {
                int rgb = Integer.parseInt(t, 16);
                return 0xFF000000 | rgb;
            }
            if (t.length() == 8) {
                // Treat as AARRGGBB
                long v = Long.parseLong(t, 16);
                return (int) v;
            }
        } catch (NumberFormatException ignored) {
        }

        return fallbackArgb;
    }

    static int lerp(int aArgb, int bArgb, float t) {
        if (t <= 0.0f) return aArgb;
        if (t >= 1.0f) return bArgb;

        int aA = (aArgb >>> 24) & 0xFF;
        int aR = (aArgb >>> 16) & 0xFF;
        int aG = (aArgb >>> 8) & 0xFF;
        int aB = aArgb & 0xFF;

        int bA = (bArgb >>> 24) & 0xFF;
        int bR = (bArgb >>> 16) & 0xFF;
        int bG = (bArgb >>> 8) & 0xFF;
        int bB = bArgb & 0xFF;

        int oA = (int) Math.round(aA + (bA - aA) * t);
        int oR = (int) Math.round(aR + (bR - aR) * t);
        int oG = (int) Math.round(aG + (bG - aG) * t);
        int oB = (int) Math.round(aB + (bB - aB) * t);
        return argb(oA, oR, oG, oB);
    }

    static int mulRgb(int argb, float multiplier) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        r = clampByte((int) Math.round(r * multiplier));
        g = clampByte((int) Math.round(g * multiplier));
        b = clampByte((int) Math.round(b * multiplier));
        return argb(a, r, g, b);
    }

    private static int clampByte(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }
}
