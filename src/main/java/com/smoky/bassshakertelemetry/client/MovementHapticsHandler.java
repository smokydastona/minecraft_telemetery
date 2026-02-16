package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * On-foot movement haptics.
 *
 * Goal: avoid any "engine"-like continuous rumble while walking/jumping and instead
 * emit short footstep pulses only when grounded (plus a landing thump).
 */
public final class MovementHapticsHandler {
    private boolean hasLast;
    private double lastX;
    private double lastZ;
    private boolean lastOnGround;

    private double stepAccum;

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled || !cfg.footstepHapticsEnabled) {
            hasLast = false;
            stepAccum = 0.0;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.player == null || mc.level == null) {
            hasLast = false;
            stepAccum = 0.0;
            return;
        }

        var player = mc.player;

        // Only do footsteps when standing on something.
        boolean onGround = player.onGround();

        double x = player.getX();
        double z = player.getZ();

        if (!hasLast) {
            hasLast = true;
            lastX = x;
            lastZ = z;
            lastOnGround = onGround;
            return;
        }

        // Landing thump.
        if (!lastOnGround && onGround) {
            float fall = player.fallDistance;
            if (fall > 0.4f) {
                double gain01 = clamp(cfg.footstepHapticsGain * 0.85, 0.0, 1.0);
                AudioOutputEngine.get().triggerImpulse(30.0, 70, gain01, 0.10);
            }
            stepAccum = 0.0;
        }

        // Grounded step pulses.
        if (onGround) {
            double dx = x - lastX;
            double dz = z - lastZ;
            double dist = Math.sqrt((dx * dx) + (dz * dz));

            // Ignore tiny jitter.
            if (dist > 0.0005) {
                stepAccum += dist;

                Vec3 dv = player.getDeltaMovement();
                double horizSpeed = Math.sqrt((dv.x * dv.x) + (dv.z * dv.z));

                // Step spacing in blocks.
                // The previous spacing was too small and produced a "double step" feel.
                // Aim for ~1 pulse per visible step while still tightening slightly at higher speed.
                double stepDistance = clamp(1.05 - (horizSpeed * 0.35), 0.70, 1.15);

                // Intensity ramps with movement speed.
                double intensity = clamp((horizSpeed - 0.02) / 0.18, 0.0, 1.0);
                double gain01 = clamp(cfg.footstepHapticsGain * (0.35 + (0.65 * intensity)), 0.0, 1.0);

                while (stepAccum >= stepDistance) {
                    stepAccum -= stepDistance;
                    AudioOutputEngine.get().triggerImpulse(62.0, 22, gain01, 0.02);
                }
            }
        }

        lastX = x;
        lastZ = z;
        lastOnGround = onGround;
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
