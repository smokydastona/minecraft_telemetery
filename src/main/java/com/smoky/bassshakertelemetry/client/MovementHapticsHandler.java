package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import com.smoky.bassshakertelemetry.config.BstVibrationProfiles;
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
                double fallScale01 = clamp((fall - 0.4f) / 6.0, 0.15, 1.0);
                var resolved = BstVibrationProfiles.get().resolve("movement.land", fallScale01, 1.0);
                if (resolved != null) {
                    double gain01 = clamp(resolved.intensity01() * clamp(cfg.footstepHapticsGain, 0.0, 1.0), 0.0, 1.0);
                    AudioOutputEngine.get().triggerImpulse(resolved.frequencyHz(), resolved.durationMs(), gain01, resolved.noiseMix01());
                }
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
                // Keep steps softer than other impacts; ramp gently with speed.
                double gain01 = clamp(cfg.footstepHapticsGain * (0.22 + (0.48 * intensity)), 0.0, 1.0);

                // Avoid "bursting" multiple steps in a single tick (can happen with lag / high speed),
                // which feels punchy and unnatural.
                if (stepAccum >= stepDistance) {
                    stepAccum -= stepDistance;
                    var resolved = BstVibrationProfiles.get().resolve("movement.footstep", 1.0, 1.0);
                    if (resolved != null) {
                        double outGain01 = clamp(resolved.intensity01() * gain01, 0.0, 1.0);
                        AudioOutputEngine.get().triggerImpulse(resolved.frequencyHz(), resolved.durationMs(), outGain01, resolved.noiseMix01());
                    } else {
                        // Crunchier, less clicky step: more filtered noise, lower fundamental, longer envelope.
                        AudioOutputEngine.get().triggerImpulse(44.0, 55, gain01, 0.42);
                    }
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
