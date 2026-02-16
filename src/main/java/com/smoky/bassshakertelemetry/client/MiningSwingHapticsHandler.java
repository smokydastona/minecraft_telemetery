package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Mining haptics synchronized to the on-screen arm swing.
 *
 * Implementation note: this uses the player's attack animation progress (getAttackAnim)
 * to detect the start of each swing, avoiding timer-based pulses that drift from visuals.
 */
public final class MiningSwingHapticsHandler {
    private float lastAttackAnim;

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled || !cfg.miningSwingHapticsEnabled) {
            lastAttackAnim = 0.0f;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.player == null || mc.level == null || mc.options == null) {
            lastAttackAnim = 0.0f;
            return;
        }

        // Only when the player is actively mining a block (attack held + crosshair on block).
        if (!mc.options.keyAttack.isDown()) {
            lastAttackAnim = 0.0f;
            return;
        }

        HitResult hr = mc.hitResult;
        if (hr == null || hr.getType() != HitResult.Type.BLOCK) {
            lastAttackAnim = 0.0f;
            return;
        }

        var player = mc.player;

        float anim = player.getAttackAnim(0.0f);
        boolean swingStarted = (anim > 0.001f) && (lastAttackAnim <= 0.001f);
        lastAttackAnim = anim;

        if (!swingStarted) {
            return;
        }

        double base = clamp(cfg.miningSwingHapticsGain, 0.0, 1.0);
        double master = clamp(cfg.gameplayHapticsGain, 0.0, 2.0);
        double gain01 = clamp(base * (0.55 + (0.45 * (master / 2.0))), 0.0, 1.0);

        // Short tactile "tap" that matches the swing.
        AudioOutputEngine.get().triggerImpulse(46.0, 26, gain01, 0.08);
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
