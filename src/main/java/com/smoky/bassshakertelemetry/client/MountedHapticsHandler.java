package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import com.smoky.bassshakertelemetry.config.BstVibrationProfiles;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Mounted haptics:
 * - Ground mounts: hoof "clump" pulses while moving on the ground.
 * - Flying mounts: use the same directional wind rumble as Elytra while airborne.
 */
public final class MountedHapticsHandler {
    private boolean hasLast;
    private double lastX;
    private double lastZ;
    private double hoofAccum;

    private float lastYawDeg = Float.NaN;
    private long lastFlightWindNanos;

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled || !cfg.mountedHapticsEnabled || cfg.mountedHapticsGain <= 0.0001) {
            reset();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.player == null || mc.level == null) {
            reset();
            return;
        }

        var player = mc.player;
        Entity vehicle = player.getVehicle();
        if (vehicle == null) {
            reset();
            return;
        }

        // Flying mounts: use Elytra-style directional wind while airborne.
        if (canFly(vehicle) && !vehicle.onGround()) {
            float yawNow = player.getYRot();
            if (Float.isNaN(lastYawDeg)) {
                lastYawDeg = yawNow;
            }
            float yawDelta = wrapDegrees(yawNow - lastYawDeg);
            lastYawDeg = yawNow;

            double speed = vehicle.getDeltaMovement().length();
            maybeTriggerFlightWind(player, speed, yawDelta);

            // Avoid a "hooves immediately after landing" burst.
            hasLast = false;
            hoofAccum = 0.0;
            return;
        }

        // Ground hooves.
        lastYawDeg = Float.NaN;
        lastFlightWindNanos = 0L;

        if (!vehicle.onGround()) {
            hasLast = false;
            hoofAccum = 0.0;
            return;
        }

        double x = vehicle.getX();
        double z = vehicle.getZ();

        if (!hasLast) {
            hasLast = true;
            lastX = x;
            lastZ = z;
            return;
        }

        double dx = x - lastX;
        double dz = z - lastZ;
        double dist = Math.sqrt((dx * dx) + (dz * dz));

        // Ignore tiny jitter.
        if (dist > 0.0005) {
            hoofAccum += dist;

            var dv = vehicle.getDeltaMovement();
            double horizSpeed = Math.sqrt((dv.x * dv.x) + (dv.z * dv.z));

            // Stride spacing in blocks; tighten with speed.
            double stride = clamp(0.92 - (horizSpeed * 0.55), 0.42, 0.98);

            // At most one hoof trigger per tick.
            if (hoofAccum >= stride) {
                hoofAccum -= stride;
                triggerHoofClump(horizSpeed);
            }
        }

        lastX = x;
        lastZ = z;
    }

    private void triggerHoofClump(double horizSpeed) {
        var cfg = BstConfig.get();
        var store = BstVibrationProfiles.get();
        var resolved = store.resolve("mount.hoof", 1.0, 1.0);
        if (resolved == null) {
            return;
        }

        double speedScale = clamp((horizSpeed - 0.05) / 0.35, 0.0, 1.0);
        double baseGain01 = clamp(resolved.intensity01() * clamp(cfg.mountedHapticsGain, 0.0, 1.0) * (0.28 + (0.72 * speedScale)), 0.0, 1.0);
        if (baseGain01 <= 0.0008) {
            return;
        }

        String pat = (resolved.pattern() == null || resolved.pattern().isBlank()) ? "punch" : resolved.pattern();

        // Two-stage "clump" (heel/toe-ish) to read more like hooves than footsteps.
        AudioOutputEngine.get().triggerImpulse(
                resolved.frequencyHz(),
                resolved.durationMs(),
                baseGain01,
                resolved.noiseMix01(),
                pat,
                resolved.pulsePeriodMs(),
                resolved.pulseWidthMs(),
                resolved.priority(),
                0,
                "mount.hoof"
        );

        AudioOutputEngine.get().triggerImpulse(
                clamp(resolved.frequencyHz() - 4.0, store.global.minFrequency, store.global.maxFrequency),
                Math.max(25, (int) Math.round(resolved.durationMs() * 0.70)),
                clamp(baseGain01 * 0.65, 0.0, 1.0),
                clamp(resolved.noiseMix01() + 0.10, 0.0, 0.90),
                pat,
                resolved.pulsePeriodMs(),
                resolved.pulseWidthMs(),
                Math.max(0, resolved.priority() - 1),
                32,
                "mount.hoof_tail"
        );
    }

    private void maybeTriggerFlightWind(net.minecraft.world.entity.player.Player player, double speed, float yawDeltaDeg) {
        long now = System.nanoTime();
        // Keep this fairly smooth but bounded.
        if (lastFlightWindNanos != 0L && (now - lastFlightWindNanos) < 90_000_000L) {
            return;
        }
        lastFlightWindNanos = now;

        var store = BstVibrationProfiles.get();
        var resolved = store.resolve("flight.wind", 1.0, 1.0);
        if (resolved == null) {
            return;
        }

        // Speed scaling: only engage once we're really moving.
        double speedScale = clamp((speed - 0.70) / 1.90, 0.0, 1.0);
        speedScale *= speedScale;

        double gain01 = clamp(resolved.intensity01() * speedScale * clamp(BstConfig.get().mountedHapticsGain, 0.0, 1.0), 0.0, 1.0);
        if (gain01 <= 0.001) {
            return;
        }

        // Synthetic source point that shifts with turning.
        double yawRad = Math.toRadians(player.getYRot());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double rx = Math.cos(yawRad);
        double rz = Math.sin(yawRad);

        double dist = 4.5;
        double sx;
        double sz;

        if (Math.abs(yawDeltaDeg) > 1.6f) {
            // Positive yaw delta corresponds to turning left.
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

    private static boolean canFly(Entity vehicle) {
        if (vehicle instanceof FlyingMob) {
            return true;
        }
        if (vehicle instanceof Mob mob) {
            return mob.getNavigation() instanceof FlyingPathNavigation;
        }
        return false;
    }

    private void reset() {
        hasLast = false;
        hoofAccum = 0.0;
        lastYawDeg = Float.NaN;
        lastFlightWindNanos = 0L;
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
