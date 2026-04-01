package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Clean-room, explicitly non-sexual gameplay -> haptics translator.
 * <p>
 * Purpose: drive a bass shaker / haptic chair from in-game events that would commonly produce
 * controller vibration (clicks, mining, XP gains), without relying on any adult-only mods or APIs.
 */
public final class GameplayHapticsHandler {
    private final Map<String, Long> lastFireByBucketNanos = new HashMap<>();

    private int lastXpLevel = -1;
    private float lastXpProgress = -1.0f;

    private long lastMinePulseNanos;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled || !cfg.gameplayHapticsEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.player == null || mc.level == null || mc.options == null) {
            return;
        }

        var player = mc.player;
        if (player == null) {
            return;
        }

        // While holding attack on a block, emit a periodic low pulse (legacy mining texture).
        // If swing-synced mining is enabled, prefer that (it matches the on-screen animation).
        if (!cfg.miningSwingHapticsEnabled && cfg.gameplayMiningPulseEnabled && mc.options.keyAttack.isDown() && isCrosshairOnBlock(mc)) {
            onMiningPulse(cfg);
        }

        // XP changes (orb pickup / smelting / etc). Client-safe and loader-safe.
        if (cfg.gameplayXpEnabled) {
            onXpChange(player.experienceLevel, player.experienceProgress, cfg);
        }
    }

    @SubscribeEvent
    public void onMouseButton(InputEvent.MouseButton event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled || !cfg.gameplayHapticsEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.screen != null || mc.player == null || mc.level == null || mc.options == null) {
            return;
        }
        if (cfg.gameplayUseClickEnabled && isBoundToMouse(mc.options.keyUse, event.getButton())) {
            onUseClick(mc, cfg);
        }
    }

    @SubscribeEvent
    public void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled || !cfg.gameplayHapticsEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.screen != null || mc.player == null || mc.level == null || mc.options == null) {
            return;
        }

        // Respect keybinds: only fire if the pressed key matches the binding.
        if (cfg.gameplayUseClickEnabled && isBoundToKey(mc.options.keyUse, event.getKey())) {
            onUseClick(mc, cfg);
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled || !cfg.gameplayHapticsEnabled || !cfg.gameplayAttackClickEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.screen != null || mc.player == null || mc.level == null) {
            return;
        }

        // Client-only handler: ensure it's the local player.
        if (event.getEntity() != mc.player) {
            return;
        }

        // Only fire on actual entity attack attempts (contact), not air swings.
        onMeleeHit(mc, cfg);
    }

    private void onMeleeHit(Minecraft mc, BstConfig.Data cfg) {
        String bucket = "gameplay.attack_entity";

        // Small "thump". Target: around block-break intensity, slightly higher.
        // Also routes via VibrationIngress so sound-inferred attack swings can be suppressed.
        double gain = 0.20 * clamp(cfg.gameplayHapticsGain, 0.0, 2.0);
        if (!rateLimit(bucket, cfg.gameplayHapticsCooldownMs)) {
            return;
        }

        if (mc.player == null) {
            return;
        }

        var player = mc.player;

        VibrationIngress.playLocalImpulse(
                bucket,
                false,
                true,
                player.getX(),
                player.getY(),
                player.getZ(),
            48.0,
            72,
                clamp(gain, 0.0, 1.0),
            0.12,
                5
        );
    }

    private void onUseClick(Minecraft mc, BstConfig.Data cfg) {
        HitResult hr = mc.hitResult;
        String bucket;
        double freq;
        int dur;
        double noise;

        if (hr != null && hr.getType() == HitResult.Type.BLOCK) {
            bucket = "gameplay.use_block";
            // Short, higher-frequency "click" feel.
            freq = 72.0;
            dur = 34;
            noise = 0.03;
        } else {
            bucket = "gameplay.use_misc";
            freq = 76.0;
            dur = 30;
            noise = 0.025;
        }

        double gain = 0.18 * clamp(cfg.gameplayHapticsGain, 0.0, 2.0);
        if (!rateLimit(bucket, cfg.gameplayHapticsCooldownMs)) {
            return;
        }

        if (mc.player == null) {
            return;
        }

        var player = mc.player;

        VibrationIngress.playLocalImpulse(
                bucket,
                false,
                true,
                player.getX(),
                player.getY(),
                player.getZ(),
                freq,
                dur,
                clamp(gain, 0.0, 1.0),
                noise,
                3
        );
    }

    private void onMiningPulse(BstConfig.Data cfg) {
        long now = System.nanoTime();
        long periodNs = Math.max(30L, (long) cfg.gameplayMiningPulsePeriodMs) * 1_000_000L;
        if ((now - lastMinePulseNanos) < periodNs) {
            return;
        }
        lastMinePulseNanos = now;

        if (!rateLimit("mine_pulse", Math.max(10, cfg.gameplayMiningPulsePeriodMs))) {
            return;
        }

        double gain = 0.28 * clamp(cfg.gameplayHapticsGain, 0.0, 2.0);
        AudioOutputEngine.get().triggerImpulse(42.0, 30, clamp(gain, 0.0, 1.0), 0.18, "single", 160, 60, 3, 0, "gameplay.mine_pulse");
    }

    private void onXpChange(int level, float progress, BstConfig.Data cfg) {

        if (lastXpLevel < 0) {
            lastXpLevel = level;
            lastXpProgress = progress;
            return;
        }

        boolean leveledUp = level > lastXpLevel;
        boolean progressIncreased = (level == lastXpLevel) && (progress > lastXpProgress + 0.001f);

        if (!leveledUp && !progressIncreased) {
            lastXpLevel = level;
            lastXpProgress = progress;
            return;
        }

        String bucket = leveledUp ? "xp_level" : "xp_gain";
        if (!rateLimit(bucket, 140)) {
            lastXpLevel = level;
            lastXpProgress = progress;
            return;
        }

        double gain = (leveledUp ? 0.55 : 0.30) * clamp(cfg.gameplayHapticsGain, 0.0, 2.0);
        AudioOutputEngine.get().triggerImpulse(58.0, leveledUp ? 90 : 55, clamp(gain, 0.0, 1.0), 0.08, "single", 160, 60, 3, 0, leveledUp ? "gameplay.xp_level" : "gameplay.xp_gain");

        lastXpLevel = level;
        lastXpProgress = progress;
    }

    private boolean rateLimit(String bucket, int cooldownMs) {
        long now = System.nanoTime();
        long cooldownNs = Math.max(10L, (long) cooldownMs) * 1_000_000L;
        Long last = lastFireByBucketNanos.get(bucket);
        if (last != null && (now - last) < cooldownNs) {
            return false;
        }
        lastFireByBucketNanos.put(bucket, now);
        return true;
    }

    private static boolean isCrosshairOnBlock(Minecraft mc) {
        HitResult hr = mc.hitResult;
        return hr != null && hr.getType() == HitResult.Type.BLOCK;
    }

    private static boolean isBoundToMouse(KeyMapping mapping, int mouseButton) {
        if (mapping == null) {
            return false;
        }
        InputConstants.Key key = mapping.getKey();
        if (key == null || key.getType() != InputConstants.Type.MOUSE) {
            return false;
        }
        return key.getValue() == mouseButton;
    }

    private static boolean isBoundToKey(KeyMapping mapping, int keyCode) {
        if (mapping == null) {
            return false;
        }
        InputConstants.Key key = mapping.getKey();
        if (key == null || key.getType() != InputConstants.Type.KEYSYM) {
            return false;
        }
        return key.getValue() == keyCode;
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
