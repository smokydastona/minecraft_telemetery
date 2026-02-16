package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import com.smoky.bassshakertelemetry.config.BstVibrationProfiles;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Triggers damage haptics at the moment damage is applied (not when the hurt sound plays).
 */
public final class DamageHapticsHandler {
    private long lastFireNanos;

    private int lastHurtTime;
    private float lastHealth = -1.0f;
    private boolean lastDead;

    private int lastAir = -1;
    private long lastFireTickNanos;
    private long lastDrownTickNanos;
    private long lastPoisonTickNanos;
    private long lastWitherTickNanos;

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onLivingHurt(LivingHurtEvent event) {
        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled || !cfg.damageBurstEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.player == null) {
            return;
        }

        if (event.getEntity() != mc.player) {
            return;
        }

        float amount = event.getAmount();
        if (!(amount > 0.0f)) {
            return;
        }

        // Simple de-dupe in case multiple hooks fire in quick succession.
        long now = System.nanoTime();
        if (lastFireNanos != 0L && (now - lastFireNanos) < 80_000_000L) { // 80ms
            return;
        }
        lastFireNanos = now;

        // Scale intensity a bit with damage amount; keep it stable and bounded.
        double scale01 = clamp(amount / 8.0, 0.15, 1.0);
        var resolved = BstVibrationProfiles.get().resolve("damage.generic", scale01, 1.0);
        if (resolved != null) {
            AudioOutputEngine.get().triggerImpulse(resolved.frequencyHz(), resolved.durationMs(), resolved.intensity01(), resolved.noiseMix01());
        } else {
            AudioOutputEngine.get().triggerDamageBurst(scale01);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled || !cfg.damageBurstEnabled) {
            lastHurtTime = 0;
            lastHealth = -1.0f;
            lastDead = false;
            lastAir = -1;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.player == null) {
            lastHurtTime = 0;
            lastHealth = -1.0f;
            lastDead = false;
            lastAir = -1;
            return;
        }

        var player = mc.player;

        // Death rumble (one-shot).
        boolean deadNow = player.isDeadOrDying() || player.getHealth() <= 0.0f;
        if (deadNow && !lastDead) {
            var resolved = BstVibrationProfiles.get().resolve("damage.death", 1.0, 1.0);
            if (resolved != null) {
                AudioOutputEngine.get().triggerImpulse(resolved.frequencyHz(), resolved.durationMs(), resolved.intensity01(), resolved.noiseMix01());
            } else {
                double gain01 = clamp(cfg.damageBurstGain * 0.95, 0.0, 1.0);
                AudioOutputEngine.get().triggerImpulse(24.0, 520, gain01, 0.65);
            }
        }
        lastDead = deadNow;

        // Core damage fallback: use hurtTime rising edge (client-visible) and health delta.
        int hurtTime = player.hurtTime;
        float health = player.getHealth();

        if (lastHealth < 0.0f) {
            lastHealth = health;
            lastHurtTime = hurtTime;
        } else {
            boolean hurtStarted = (hurtTime > 0) && (hurtTime > lastHurtTime);
            float delta = lastHealth - health;
            boolean healthDropped = delta > 0.001f;

            if ((hurtStarted || healthDropped) && canFireNow(80_000_000L)) {
                // Approx intensity from observed health delta. Keep stable/minimum so small hits still register.
                double scale01 = clamp(delta / 8.0, 0.15, 1.0);
                var resolved = BstVibrationProfiles.get().resolve("damage.generic", scale01, 1.0);
                if (resolved != null) {
                    AudioOutputEngine.get().triggerImpulse(resolved.frequencyHz(), resolved.durationMs(), resolved.intensity01(), resolved.noiseMix01());
                } else {
                    AudioOutputEngine.get().triggerDamageBurst(scale01);
                }
            }

            lastHealth = health;
            lastHurtTime = hurtTime;
        }

        long now = System.nanoTime();

        // Fire/lava tick: small repeating pulse while burning.
        if (player.isOnFire()) {
            if ((now - lastFireTickNanos) > 260_000_000L) {
                lastFireTickNanos = now;
                var resolved = BstVibrationProfiles.get().resolve("damage.fire", 1.0, 1.0);
                if (resolved != null) {
                    AudioOutputEngine.get().triggerImpulse(resolved.frequencyHz(), resolved.durationMs(), resolved.intensity01(), resolved.noiseMix01());
                }
            }
        }

        // Drowning: pulse when air is low and decreasing.
        int air = player.getAirSupply();
        if (lastAir >= 0) {
            boolean airDropping = air < lastAir;
            boolean lowAir = air <= 60; // ~3 seconds
            if (airDropping && lowAir && (now - lastDrownTickNanos) > 520_000_000L) {
                lastDrownTickNanos = now;
                var resolved = BstVibrationProfiles.get().resolve("damage.drowning", 1.0, 1.0);
                if (resolved != null) {
                    AudioOutputEngine.get().triggerImpulse(resolved.frequencyHz(), resolved.durationMs(), resolved.intensity01(), resolved.noiseMix01());
                }
            }
        }
        lastAir = air;

        // Poison / wither: light rhythmic pulses while effect is active.
        if (player.hasEffect(MobEffects.POISON)) {
            if ((now - lastPoisonTickNanos) > 650_000_000L) {
                lastPoisonTickNanos = now;
                var resolved = BstVibrationProfiles.get().resolve("damage.poison", 1.0, 1.0);
                if (resolved != null) {
                    AudioOutputEngine.get().triggerImpulse(resolved.frequencyHz(), resolved.durationMs(), resolved.intensity01(), resolved.noiseMix01());
                }
            }
        }
        if (player.hasEffect(MobEffects.WITHER)) {
            if ((now - lastWitherTickNanos) > 560_000_000L) {
                lastWitherTickNanos = now;
                var resolved = BstVibrationProfiles.get().resolve("damage.wither", 1.0, 1.0);
                if (resolved != null) {
                    AudioOutputEngine.get().triggerImpulse(resolved.frequencyHz(), resolved.durationMs(), resolved.intensity01(), resolved.noiseMix01());
                }
            }
        }
    }

    private boolean canFireNow(long minDeltaNs) {
        long now = System.nanoTime();
        if (lastFireNanos != 0L && (now - lastFireNanos) < minDeltaNs) {
            return false;
        }
        lastFireNanos = now;
        return true;
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
