package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
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
        double intensity01 = clamp(amount / 6.0, 0.25, 1.0);
        AudioOutputEngine.get().triggerDamageBurst(intensity01);
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
            // Longer, lower rumble than a normal hit.
            double gain01 = clamp(cfg.damageBurstGain * 0.95, 0.0, 1.0);
            AudioOutputEngine.get().triggerImpulse(24.0, 520, gain01, 0.65);
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
                double intensity01 = clamp(delta / 6.0, 0.25, 1.0);
                AudioOutputEngine.get().triggerDamageBurst(intensity01);
            }

            lastHealth = health;
            lastHurtTime = hurtTime;
        }

        long now = System.nanoTime();

        // Fire/lava tick: small repeating pulse while burning.
        if (player.isOnFire()) {
            if ((now - lastFireTickNanos) > 260_000_000L) {
                lastFireTickNanos = now;
                double gain01 = clamp(cfg.damageBurstGain * 0.30, 0.0, 0.75);
                AudioOutputEngine.get().triggerImpulse(34.0, 70, gain01, 0.45);
            }
        }

        // Drowning: pulse when air is low and decreasing.
        int air = player.getAirSupply();
        if (lastAir >= 0) {
            boolean airDropping = air < lastAir;
            boolean lowAir = air <= 60; // ~3 seconds
            if (airDropping && lowAir && (now - lastDrownTickNanos) > 520_000_000L) {
                lastDrownTickNanos = now;
                double gain01 = clamp(cfg.damageBurstGain * 0.28, 0.0, 0.70);
                AudioOutputEngine.get().triggerImpulse(30.0, 90, gain01, 0.55);
            }
        }
        lastAir = air;

        // Poison / wither: light rhythmic pulses while effect is active.
        if (player.hasEffect(MobEffects.POISON)) {
            if ((now - lastPoisonTickNanos) > 650_000_000L) {
                lastPoisonTickNanos = now;
                double gain01 = clamp(cfg.damageBurstGain * 0.22, 0.0, 0.60);
                AudioOutputEngine.get().triggerImpulse(40.0, 55, gain01, 0.30);
            }
        }
        if (player.hasEffect(MobEffects.WITHER)) {
            if ((now - lastWitherTickNanos) > 560_000_000L) {
                lastWitherTickNanos = now;
                double gain01 = clamp(cfg.damageBurstGain * 0.26, 0.0, 0.70);
                AudioOutputEngine.get().triggerImpulse(32.0, 80, gain01, 0.40);
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
