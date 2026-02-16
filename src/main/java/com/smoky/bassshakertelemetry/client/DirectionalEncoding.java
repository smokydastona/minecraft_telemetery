package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.config.BstVibrationProfiles;
import net.minecraft.world.entity.player.Player;

/**
 * Client-side helper for "encoded mono" directional feel.
 *
 * IMPORTANT: Client-only usage. Callers provide the client player.
 */
public final class DirectionalEncoding {
    private DirectionalEncoding() {
    }

    public record Encoded(double frequencyHz, double gain01, int delayMs) {
    }

    public static Encoded apply(BstVibrationProfiles.Store store, Player player, boolean directional, boolean hasSource, double sourceX, double sourceY, double sourceZ, double baseFrequencyHz, double baseGain01) {
        if (store == null) {
            return new Encoded(baseFrequencyHz, clamp(baseGain01, 0.0, 1.0), 0);
        }

        BstVibrationProfiles.Encoding.Band band = store.encoding.center;
        if (directional && hasSource && player != null) {
            band = chooseBand(store.encoding, player, sourceX, sourceZ);
        }

        double freqHz = baseFrequencyHz + band.frequencyBiasHz;
        freqHz = clamp(freqHz, store.global.minFrequency, store.global.maxFrequency);

        double gain01 = clamp(baseGain01 * band.intensityMul, 0.0, 1.0);

        int delayMs = clampInt(band.timeOffsetMs, -30, 30);
        if (delayMs < 0) {
            delayMs = 0;
        }

        return new Encoded(freqHz, gain01, delayMs);
    }

    private static BstVibrationProfiles.Encoding.Band chooseBand(BstVibrationProfiles.Encoding encoding, Player player, double sourceX, double sourceZ) {
        double dx = sourceX - player.getX();
        double dz = sourceZ - player.getZ();
        double mag2 = (dx * dx) + (dz * dz);
        if (mag2 < 0.0004) {
            return encoding.center;
        }

        double yawRad = Math.toRadians(player.getYRot());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double rx = Math.cos(yawRad);
        double rz = Math.sin(yawRad);

        double dotF = (dx * fx) + (dz * fz);
        double dotR = (dx * rx) + (dz * rz);

        if (Math.abs(dotF) >= Math.abs(dotR)) {
            return (dotF >= 0.0) ? encoding.front : encoding.rear;
        }
        return (dotR >= 0.0) ? encoding.right : encoding.left;
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static int clampInt(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
