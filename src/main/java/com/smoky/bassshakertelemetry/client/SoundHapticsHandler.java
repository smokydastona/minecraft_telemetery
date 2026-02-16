package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Clean-room "sound -> haptics" translator.
 * <p>
 * Minecraft Java doesn't have a canonical controller rumble API; instead we use the game sound stream
 * as a proxy for the kinds of events that would normally vibrate a controller.
 */
public final class SoundHapticsHandler {
    private final Map<String, Long> lastFireByBucketNanos = new HashMap<>();

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onPlaySound(PlaySoundEvent event) {
        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled || !cfg.soundHapticsEnabled) {
            return;
        }

        SoundInstance sound = event.getSound();
        if (sound == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.player == null || mc.level == null) {
            return;
        }

        // Ignore music/records/ambient by default.
        SoundSource source;
        try {
            source = sound.getSource();
        } catch (NullPointerException ignored) {
            return;
        }
        if (source == SoundSource.MUSIC || source == SoundSource.RECORDS || source == SoundSource.AMBIENT) {
            return;
        }

        // Some SoundInstance implementations (including AbstractSoundInstance) can temporarily have an
        // unresolved backing Sound during early dispatch / reload. In that state, getLocation() may throw.
        ResourceLocation loc;
        try {
            loc = sound.getLocation();
        } catch (NullPointerException ignored) {
            return;
        }
        if (loc == null) {
            return;
        }

        String path = loc.getPath();
        if (path == null || path.isBlank()) {
            return;
        }

        // Basic intensity from volume; clamp to be safe.
        double volume;
        try {
            volume = sound.getVolume();
        } catch (NullPointerException ignored) {
            return;
        }

        double base = clamp(volume, 0.0, 1.0) * clamp(cfg.soundHapticsGain, 0.0, 2.0);
        if (base <= 0.001) {
            return;
        }

        // Bucketed mapping. Keep it simple and stable: low frequencies, short durations.
        HapticImpulse impulse = map(path, source, base);
        if (impulse == null) {
            return;
        }

        if (!rateLimit(impulse.bucketKey, cfg.soundHapticsCooldownMs)) {
            return;
        }

        AudioOutputEngine.get().triggerImpulse(impulse.freqHz, impulse.durationMs, impulse.gain, impulse.noiseMix);
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

    private static HapticImpulse map(String soundPath, SoundSource source, double base) {
        String p = soundPath.toLowerCase();

        // Explosions and big impacts.
        if (p.contains("explode") || p.contains("explosion")) {
            return new HapticImpulse("explosion", 28.0, 180, clamp(base * 1.4, 0.0, 1.0), 0.65);
        }

        // Player/Entity damage.
        if (p.contains("hurt") || p.contains("damage")) {
            return new HapticImpulse("hurt", 34.0, 110, clamp(base * 1.1, 0.0, 1.0), 0.35);
        }

        // Block break / place.
        if (p.contains(".break") || p.contains("break")) {
            return new HapticImpulse("block_break", 42.0, 70, clamp(base * 0.9, 0.0, 1.0), 0.25);
        }
        if (p.contains(".place") || p.contains("place")) {
            return new HapticImpulse("block_place", 40.0, 55, clamp(base * 0.7, 0.0, 1.0), 0.15);
        }

        // Footsteps / movement texture.
        if (p.contains(".step") || p.contains("step")) {
            // Prefer explicit on-foot movement haptics (grounded pitter-patter).
            if (BstConfig.get().footstepHapticsEnabled) {
                return null;
            }
            // Let the continuous road texture cover most of this; steps are light taps.
            return new HapticImpulse("step", 55.0, 30, clamp(base * 0.35, 0.0, 0.6), 0.05);
        }

        // Attacks / swings.
        if (p.contains("attack") || p.contains("sweep") || p.contains("crit")) {
            return new HapticImpulse("attack", 48.0, 45, clamp(base * 0.55, 0.0, 0.8), 0.10);
        }

        // Projectiles.
        if (p.contains("arrow") || p.contains("bow") || p.contains("crossbow")) {
            return new HapticImpulse("proj", 46.0, 40, clamp(base * 0.45, 0.0, 0.7), 0.10);
        }

        // Lightning / thunder.
        if (p.contains("thunder") || p.contains("lightning")) {
            return new HapticImpulse("thunder", 20.0, 220, clamp(base * 0.8, 0.0, 1.0), 0.70);
        }

        // Default for other player/block/hostile sounds: tiny nudge, but only from "active" sources.
        if (source == SoundSource.PLAYERS || source == SoundSource.BLOCKS || source == SoundSource.HOSTILE) {
            return new HapticImpulse("misc", 44.0, 25, clamp(base * 0.18, 0.0, 0.35), 0.05);
        }

        return null;
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private record HapticImpulse(String bucketKey, double freqHz, int durationMs, double gain, double noiseMix) {
    }
}
