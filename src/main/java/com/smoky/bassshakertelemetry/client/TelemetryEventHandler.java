package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import com.smoky.bassshakertelemetry.config.BstVibrationProfiles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class TelemetryEventHandler {
    private double lastSpeed = 0.0;
    private ResourceKey<Biome> lastBiome = null;

    private float lastYawDeg = Float.NaN;
    private long lastFlightWindNanos = 0L;

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!BstConfig.get().enabled()) {
            AudioOutputEngine.get().setTelemetryLive(false);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            AudioOutputEngine.get().setTelemetryLive(false);
            return;
        }

        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) {
            AudioOutputEngine.get().setTelemetryLive(false);
            return;
        }

        AudioOutputEngine.get().setTelemetryLive(true);

        double speed = player.getDeltaMovement().length();
        double accel = speed - lastSpeed;
        lastSpeed = speed;

        boolean elytra = player.isFallFlying();

        // Directional Elytra wind rumble: as you turn/bank, the wind shifts left/right for immersion.
        // Implemented as short, overlapping impulses so it still participates in priority/ducking.
        float yawNow = player.getYRot();
        if (Float.isNaN(lastYawDeg)) {
            lastYawDeg = yawNow;
        }
        float yawDelta = wrapDegrees(yawNow - lastYawDeg);
        lastYawDeg = yawNow;

        if (elytra) {
            maybeTriggerFlightWind(player, speed, yawDelta);
        } else {
            lastFlightWindNanos = 0L;
        }

        // Biome transitions (kept for later use; currently UI exposes toggle)
        if (BstConfig.get().biomeChimeEnabled) {
            Holder<Biome> biomeHolder = level.getBiome(player.blockPosition());
            ResourceKey<Biome> biomeKey = biomeHolder.unwrapKey().orElse(null);
            if (biomeKey != null && lastBiome != null && biomeKey != lastBiome) {
                AudioOutputEngine.get().triggerBiomeChime();
            }
            lastBiome = biomeKey;
        }

        AudioOutputEngine.get().updateTelemetry(speed, accel, elytra);
    }

    private void maybeTriggerFlightWind(Player player, double speed, float yawDeltaDeg) {
        // Simple rate limit so we don't spawn an unbounded number of voices.
        long now = System.nanoTime();
        // ~10-12 Hz is enough for a continuous feel with overlap.
        if (lastFlightWindNanos != 0L && (now - lastFlightWindNanos) < 90_000_000L) {
            return;
        }
        lastFlightWindNanos = now;

        var store = BstVibrationProfiles.get();
        var resolved = store.resolve("flight.wind", 1.0, 1.0);
        if (resolved == null) {
            return;
        }

        // Speed scaling: only engage once we're really gliding.
        double speedScale = clamp((speed - 0.70) / 1.90, 0.0, 1.0);
        speedScale *= speedScale;

        double gain01 = clamp(resolved.intensity01() * speedScale, 0.0, 1.0);
        if (gain01 <= 0.001) {
            return;
        }

        // Choose a synthetic source point relative to the player's facing.
        // If you're turning, bias to the side you're turning toward.
        double yawRad = Math.toRadians(player.getYRot());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double rx = Math.cos(yawRad);
        double rz = Math.sin(yawRad);

        double dist = 4.5;
        double sx;
        double sz;

        if (Math.abs(yawDeltaDeg) > 1.6f) {
            // Convention: positive yaw delta feels like turning left; negative feels like turning right.
            // If this is inverted for your setup, flip the sign here.
            boolean turnLeft = yawDeltaDeg > 0.0f;
            if (turnLeft) {
                sx = player.getX() - (rx * dist);
                sz = player.getZ() - (rz * dist);
            } else {
                sx = player.getX() + (rx * dist);
                sz = player.getZ() + (rz * dist);
            }
        } else {
            sx = player.getX() + (fx * dist);
            sz = player.getZ() + (fz * dist);
        }

        double sy = player.getY();

        var encoded = DirectionalEncoding.apply(
                store,
                player,
                resolved.directional(),
                true,
                sx,
                sy,
                sz,
                resolved.frequencyHz(),
                gain01
        );

        String pat = (resolved.pattern() == null || resolved.pattern().isBlank()) ? "soft_single" : resolved.pattern();

        AudioOutputEngine.get().triggerImpulse(
                encoded.frequencyHz(),
                resolved.durationMs(),
                encoded.gain01(),
                resolved.noiseMix01(),
                pat,
                resolved.pulsePeriodMs(),
                resolved.pulseWidthMs(),
                resolved.priority(),
                encoded.delayMs(),
                "flight.wind"
        );
    }

    private static float wrapDegrees(float deg) {
        float d = deg % 360.0f;
        if (d >= 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
