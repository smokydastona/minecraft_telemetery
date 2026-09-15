package com.smoky.bassshakertelemetry.fabric;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import com.smoky.bassshakertelemetry.config.BstVibrationProfiles;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.atomic.AtomicLong;

final class FabricClientLifecycle {
    private static final AtomicLong TICKS = new AtomicLong();
    private static double lastSpeed;
    private static float lastHealth = -1.0f;
    private static boolean lastDead;
    private static boolean lastOnGround;
    private static boolean movementInitialized;
    private static double lastX;
    private static double lastZ;
    private static double stepDistance;
    private static long lastMinePulseNanos;

    private FabricClientLifecycle() {
    }

    static void onEndTick(Minecraft client) {
        BstConfig.Data config = BstConfig.get();
        if (!config.enabled || client.isPaused() || client.player == null || client.level == null) {
            AudioOutputEngine.get().setTelemetryLive(false);
            reset(client.player);
            return;
        }

        TICKS.incrementAndGet();
        Player player = client.player;
        double speed = player.getDeltaMovement().length();
        double accel = speed - lastSpeed;
        lastSpeed = speed;
        AudioOutputEngine.get().setTelemetryLive(true);
        AudioOutputEngine.get().updateTelemetry(speed, accel, player.isFallFlying(), player.onGround(),
                player.isInWater(), player.isSwimming());

        if (config.damageBurstEnabled) {
            detectDamage(player, config);
        }
        if (config.footstepHapticsEnabled) {
            detectMovement(player, config);
        }
        if (config.gameplayHapticsEnabled && client.options.keyAttack.isDown() && client.hitResult != null
                && client.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            long now = System.nanoTime();
            long period = Math.max(30L, config.gameplayMiningPulsePeriodMs) * 1_000_000L;
            if (config.gameplayMiningPulseEnabled && now - lastMinePulseNanos >= period) {
                lastMinePulseNanos = now;
                AudioOutputEngine.get().triggerImpulse(42.0, 30,
                        clamp(0.20 * config.gameplayHapticsGain, 0.0, 1.0), 0.18,
                        "single", 160, 60, 3, 0, "gameplay.mine_pulse");
            }
        }

        if (config.webSocketEnabled && config.webSocketSendTelemetry) {
            com.smoky.bassshakertelemetry.telemetryout.TelemetryOut.emitTelemetry(
                    speed, accel, player.isFallFlying());
        }
    }

    private static void detectDamage(Player player, BstConfig.Data config) {
        float health = player.getHealth();
        boolean dead = player.isDeadOrDying() || health <= 0.0f;
        if (dead && !lastDead) {
            triggerProfile("damage.death", config.damageBurstGain);
        } else if (lastHealth >= 0.0f && health < lastHealth - 0.001f) {
            double scale = clamp((lastHealth - health) / 8.0, 0.55, 1.0);
            var resolved = BstVibrationProfiles.get().resolve("damage.generic", scale, 1.0);
            if (resolved != null) {
                AudioOutputEngine.get().triggerImpulse(resolved.frequencyHz(), resolved.durationMs(),
                        clamp(resolved.intensity01() * config.damageBurstGain, 0.0, 1.0),
                        resolved.noiseMix01(), resolved.pattern(), resolved.pulsePeriodMs(),
                        resolved.pulseWidthMs(), resolved.priority(), 0, "damage.generic");
            }
        }
        lastHealth = health;
        lastDead = dead;
    }

    private static void detectMovement(Player player, BstConfig.Data config) {
        double x = player.getX();
        double z = player.getZ();
        boolean grounded = player.onGround();
        if (!movementInitialized) {
            movementInitialized = true;
            lastX = x;
            lastZ = z;
            lastOnGround = grounded;
            return;
        }
        if (!lastOnGround && grounded && player.fallDistance > 0.4f) {
            triggerProfile("movement.land", config.footstepHapticsGain);
        }
        if (grounded) {
            double dx = x - lastX;
            double dz = z - lastZ;
            stepDistance += Math.sqrt((dx * dx) + (dz * dz));
            double spacing = clamp(1.05 - (player.getDeltaMovement().horizontalDistance() * 0.35), 0.70, 1.15);
            if (stepDistance >= spacing) {
                stepDistance -= spacing;
                triggerProfile("movement.footstep", config.footstepHapticsGain);
            }
        }
        lastX = x;
        lastZ = z;
        lastOnGround = grounded;
    }

    private static void triggerProfile(String key, double gainScale) {
        var resolved = BstVibrationProfiles.get().resolve(key, 1.0, 1.0);
        if (resolved == null) {
            return;
        }
        AudioOutputEngine.get().triggerImpulse(resolved.frequencyHz(), resolved.durationMs(),
                clamp(resolved.intensity01() * gainScale, 0.0, 1.0), resolved.noiseMix01(),
                resolved.pattern(), resolved.pulsePeriodMs(), resolved.pulseWidthMs(),
                resolved.priority(), 0, key);
    }

    private static void reset(Player player) {
        lastSpeed = 0.0;
        lastHealth = player == null ? -1.0f : player.getHealth();
        lastDead = false;
        movementInitialized = false;
        stepDistance = 0.0;
        lastMinePulseNanos = 0L;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    static long ticks() {
        return TICKS.get();
    }
}