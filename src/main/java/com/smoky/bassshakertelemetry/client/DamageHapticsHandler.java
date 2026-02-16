package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Triggers damage haptics at the moment damage is applied (not when the hurt sound plays).
 */
public final class DamageHapticsHandler {
    private long lastFireNanos;

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

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
