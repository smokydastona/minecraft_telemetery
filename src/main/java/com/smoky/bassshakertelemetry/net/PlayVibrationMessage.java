package com.smoky.bassshakertelemetry.net;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import com.smoky.bassshakertelemetry.config.BstVibrationProfiles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PlayVibrationMessage(String key, float scale01, float distanceScale01, boolean hasSource, double sourceX, double sourceY, double sourceZ) {
    public static void encode(PlayVibrationMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf((msg.key == null) ? "" : msg.key);
        buf.writeFloat(msg.scale01);
        buf.writeFloat(msg.distanceScale01);
        buf.writeBoolean(msg.hasSource);
        if (msg.hasSource) {
            buf.writeDouble(msg.sourceX);
            buf.writeDouble(msg.sourceY);
            buf.writeDouble(msg.sourceZ);
        }
    }

    public static PlayVibrationMessage decode(FriendlyByteBuf buf) {
        String key = buf.readUtf(256);
        float scale01 = buf.readFloat();
        float distanceScale01 = buf.readFloat();
        boolean hasSource = buf.readBoolean();
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        if (hasSource) {
            x = buf.readDouble();
            y = buf.readDouble();
            z = buf.readDouble();
        }
        return new PlayVibrationMessage(key, scale01, distanceScale01, hasSource, x, y, z);
    }

    public static void handle(PlayVibrationMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (ctx.getDirection().getReceptionSide() != LogicalSide.CLIENT) {
                return;
            }

            BstConfig.Data cfg = BstConfig.get();
            if (!cfg.enabled || !cfg.gameplayHapticsEnabled) {
                return;
            }

            var resolved = BstVibrationProfiles.get().resolve(msg.key, msg.scale01, msg.distanceScale01);
            if (resolved == null) {
                return;
            }

            double gain = clamp(resolved.intensity01() * clamp(cfg.gameplayHapticsGain, 0.0, 2.0), 0.0, 1.0);

            // Apply directional encoding (client-only) without introducing client-only imports on the server.
            if (!tryClientPlayback(msg.key, resolved, gain, msg.hasSource, msg.sourceX, msg.sourceY, msg.sourceZ)) {
                AudioOutputEngine.get().triggerImpulse(
                        resolved.frequencyHz(),
                        resolved.durationMs(),
                        gain,
                        resolved.noiseMix01(),
                        resolved.pattern(),
                        resolved.pulsePeriodMs(),
                        resolved.pulseWidthMs(),
                        resolved.priority(),
                        0
                );
            }
        });
        ctx.setPacketHandled(true);
    }

    private static boolean tryClientPlayback(String key, BstVibrationProfiles.Resolved resolved, double gain01, boolean hasSource, double x, double y, double z) {
        try {
            Class<?> clazz = Class.forName("com.smoky.bassshakertelemetry.client.VibrationIngress");
            var method = clazz.getMethod("playNetworkVibrationWithKey", String.class, BstVibrationProfiles.Resolved.class, double.class, boolean.class, double.class, double.class, double.class);
            method.invoke(null, key, resolved, gain01, hasSource, x, y, z);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
