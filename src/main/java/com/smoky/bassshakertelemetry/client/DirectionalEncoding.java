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

    /**
     * Returns the best-effort direction band name for the given source/player context.
     *
     * <p>Possible values: {@code center|front|rear|left|right}.
     */
    public static String chooseBandName(BstVibrationProfiles.Store store, Player player, boolean directional, boolean hasSource, double sourceX, double sourceZ) {
        if (store == null) {
            return "center";
        }
        if (!directional || !hasSource || player == null) {
            return "center";
        }
        return chooseBandName(store.encoding, player, sourceX, sourceZ);
    }

    public static Encoded apply(BstVibrationProfiles.Store store, Player player, boolean directional, boolean hasSource, double sourceX, double sourceY, double sourceZ, double baseFrequencyHz, double baseGain01) {
        if (store == null) {
            return new Encoded(baseFrequencyHz, clamp(baseGain01, 0.0, 1.0), 0);
        }

        double freqBiasHz = store.encoding.center.frequencyBiasHz;
        double intensityMul = store.encoding.center.intensityMul;
        double timeOffsetMs = store.encoding.center.timeOffsetMs;

        if (directional && hasSource && player != null) {
            double az = computeAzimuthDeg(player, sourceX, sourceZ);
            if (Double.isFinite(az)) {
                BlendedBand b = blendBands(store.encoding, az);
                freqBiasHz = b.frequencyBiasHz;
                intensityMul = b.intensityMul;
                timeOffsetMs = b.timeOffsetMs;
            }
        }

        double freqHz = baseFrequencyHz + freqBiasHz;
        freqHz = clamp(freqHz, store.global.minFrequency, store.global.maxFrequency);

        double gain01 = clamp(baseGain01 * intensityMul, 0.0, 1.0);

        int delayMs = clampInt((int) Math.round(timeOffsetMs), -30, 30);
        if (delayMs < 0) {
            delayMs = 0;
        }

        return new Encoded(freqHz, gain01, delayMs);
    }

    private static String chooseBandName(BstVibrationProfiles.Encoding encoding, Player player, double sourceX, double sourceZ) {
        double az = computeAzimuthDeg(player, sourceX, sourceZ);
        if (!Double.isFinite(az)) {
            return "center";
        }
        return dominantBandName(az);
    }

    private record BlendedBand(double frequencyBiasHz, double intensityMul, double timeOffsetMs) {
    }

    private static double computeAzimuthDeg(Player player, double sourceX, double sourceZ) {
        if (player == null) {
            return Double.NaN;
        }

        double dx = sourceX - player.getX();
        double dz = sourceZ - player.getZ();
        double mag2 = (dx * dx) + (dz * dz);
        if (mag2 < 0.0004) {
            return Double.NaN;
        }

        double yawRad = Math.toRadians(player.getYRot());
        // Minecraft axes: +X east, +Z south. yaw=0 faces +Z.
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        double localX = (dx * rightX) + (dz * rightZ);
        double localZ = (dx * fwdX) + (dz * fwdZ);

        double az = Math.toDegrees(Math.atan2(localX, localZ));
        az = az % 360.0;
        if (az > 180.0) az -= 360.0;
        if (az < -180.0) az += 360.0;
        return az;
    }

    private static String dominantBandName(double azimuthDeg) {
        double az = azimuthDeg;
        if (!Double.isFinite(az)) {
            return "center";
        }
        az = az % 360.0;
        if (az > 180.0) az -= 360.0;
        if (az < -180.0) az += 360.0;

        // Nearest-cardinal name (used as a hint, not for blending).
        if (az >= 45.0 && az <= 135.0) {
            return "right";
        }
        if (az <= -45.0 && az >= -135.0) {
            return "left";
        }
        if (az > 135.0 || az < -135.0) {
            return "rear";
        }
        return "front";
    }

    private static BlendedBand blendBands(BstVibrationProfiles.Encoding encoding, double azimuthDeg) {
        if (encoding == null) {
            return new BlendedBand(0.0, 1.0, 0.0);
        }

        double az = azimuthDeg;
        if (!Double.isFinite(az)) {
            return new BlendedBand(encoding.center.frequencyBiasHz, encoding.center.intensityMul, encoding.center.timeOffsetMs);
        }
        az = az % 360.0;
        if (az > 180.0) az -= 360.0;
        if (az < -180.0) az += 360.0;

        // Blend between adjacent cardinals around the listener.
        // Segments: front(0) <-> right(90) <-> rear(180/-180) <-> left(-90) <-> front(0)
        BstVibrationProfiles.Encoding.Band b0;
        BstVibrationProfiles.Encoding.Band b1;
        double t;

        if (az >= 0.0) {
            if (az <= 90.0) {
                b0 = encoding.front;
                b1 = encoding.right;
                t = az / 90.0;
            } else {
                b0 = encoding.right;
                b1 = encoding.rear;
                t = (az - 90.0) / 90.0;
            }
        } else {
            if (az >= -90.0) {
                b0 = encoding.front;
                b1 = encoding.left;
                t = (-az) / 90.0;
            } else {
                b0 = encoding.rear;
                b1 = encoding.left;
                t = (az + 180.0) / 90.0;
            }
        }

        t = clamp(t, 0.0, 1.0);
        // Smooth the transition so diagonals don't feel like hard switches.
        t = t * t * (3.0 - (2.0 * t));

        double w0 = 1.0 - t;
        double w1 = t;

        double freqBiasHz = (w0 * b0.frequencyBiasHz) + (w1 * b1.frequencyBiasHz);
        double intensityMul = (w0 * b0.intensityMul) + (w1 * b1.intensityMul);
        double timeOffsetMs = (w0 * b0.timeOffsetMs) + (w1 * b1.timeOffsetMs);

        return new BlendedBand(freqBiasHz, intensityMul, timeOffsetMs);
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
