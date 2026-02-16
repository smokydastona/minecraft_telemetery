package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

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

    private boolean lastAttackDown;
    private boolean lastUseDown;

    private int lastXpLevel = -1;
    private float lastXpProgress = -1.0f;

    private long lastMinePulseNanos;

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled || !cfg.gameplayHapticsEnabled) {
            lastAttackDown = false;
            lastUseDown = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.player == null || mc.level == null || mc.options == null) {
            lastAttackDown = false;
            lastUseDown = false;
            return;
        }

        var player = mc.player;

        boolean attackDown = mc.options.keyAttack.isDown();
        boolean useDown = mc.options.keyUse.isDown();

        // Rising edges: treat as discrete "click" events.
        if (cfg.gameplayAttackClickEnabled && attackDown && !lastAttackDown && !isCrosshairOnBlock(mc)) {
            onAttackClick(mc, cfg);
        }
        if (cfg.gameplayUseClickEnabled && useDown && !lastUseDown) {
            onUseClick(mc, cfg);
        }

        // While holding attack on a block, emit a periodic low pulse (legacy mining texture).
        // If swing-synced mining is enabled, prefer that (it matches the on-screen animation).
        if (!cfg.miningSwingHapticsEnabled && cfg.gameplayMiningPulseEnabled && attackDown && isCrosshairOnBlock(mc)) {
            onMiningPulse(cfg);
        }

        // XP changes (orb pickup / smelting / etc). Client-safe and loader-safe.
        if (cfg.gameplayXpEnabled) {
            onXpChange(player.experienceLevel, player.experienceProgress, cfg);
        }

        lastAttackDown = attackDown;
        lastUseDown = useDown;
    }

    private void onAttackClick(Minecraft mc, BstConfig.Data cfg) {
        HitResult hr = mc.hitResult;
        String bucket = (hr != null && hr.getType() == HitResult.Type.ENTITY) ? "attack_entity" : "attack_click";

        // If player is mining, the mining pulse handles it; keep this tap smaller.
        double gain = 0.40 * clamp(cfg.gameplayHapticsGain, 0.0, 2.0);
        if (!rateLimit(bucket, cfg.gameplayHapticsCooldownMs)) {
            return;
        }

        AudioOutputEngine.get().triggerImpulse(48.0, 45, clamp(gain, 0.0, 1.0), 0.10);
    }

    private void onUseClick(Minecraft mc, BstConfig.Data cfg) {
        HitResult hr = mc.hitResult;
        String bucket;
        double freq;
        int dur;
        double noise;

        if (hr != null && hr.getType() == HitResult.Type.BLOCK) {
            bucket = "use_block";
            freq = 40.0;
            dur = 55;
            noise = 0.12;
        } else {
            bucket = "use_misc";
            freq = 44.0;
            dur = 35;
            noise = 0.05;
        }

        double gain = 0.32 * clamp(cfg.gameplayHapticsGain, 0.0, 2.0);
        if (!rateLimit(bucket, cfg.gameplayHapticsCooldownMs)) {
            return;
        }

        AudioOutputEngine.get().triggerImpulse(freq, dur, clamp(gain, 0.0, 1.0), noise);
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
        AudioOutputEngine.get().triggerImpulse(42.0, 30, clamp(gain, 0.0, 1.0), 0.18);
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
        AudioOutputEngine.get().triggerImpulse(58.0, leveledUp ? 90 : 55, clamp(gain, 0.0, 1.0), 0.08);

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

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
