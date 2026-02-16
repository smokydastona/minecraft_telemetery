package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
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

        double gain = impulse.gain;
        if (impulse.distanceRefBlocks > 0.0) {
            gain *= distanceScale01(mc.player.position(), sound, impulse.distanceRefBlocks);
        }
        gain = clamp(gain, 0.0, 1.0);

        if (!rateLimit(impulse.bucketKey, cfg.soundHapticsCooldownMs)) {
            return;
        }

        VibrationIngress.playSoundImpulse(
                impulse.bucketKey,
                impulse.directional,
                sound.getX(),
                sound.getY(),
                sound.getZ(),
                impulse.freqHz,
                impulse.durationMs,
                gain,
                impulse.noiseMix,
                impulse.priority
        );
    }

    private static double distanceScale01(Vec3 playerPos, SoundInstance sound, double refBlocks) {
        try {
            double dx = sound.getX() - playerPos.x;
            double dy = sound.getY() - playerPos.y;
            double dz = sound.getZ() - playerPos.z;
            double dist = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
            double r = Math.max(0.01, refBlocks);
            // Smooth falloff: 1.0 near the source, ~0.5 around ref distance, then fades.
            double d = dist / r;
            return 1.0 / (1.0 + (d * d));
        } catch (Exception ignored) {
            return 1.0;
        }
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
            return new HapticImpulse("explosion", 26.0, 240, clamp(base * 1.4, 0.0, 1.0), 0.75, 10.0, true, 10);
        }

        // Thunder / lightning.
        if (p.contains("thunder") || p.contains("lightning")) {
            return new HapticImpulse("thunder", 22.0, 260, clamp(base * 0.9, 0.0, 1.0), 0.80, 18.0, true, 9);
        }

        // Player/Entity damage.
        if (p.contains("hurt") || p.contains("damage")) {
            return new HapticImpulse("hurt", 34.0, 110, clamp(base * 1.1, 0.0, 1.0), 0.35, 0.0, true, 6);
        }

        // Totem of undying.
        if (p.contains("totem") && p.contains("use")) {
            return new HapticImpulse("totem", 30.0, 280, clamp(base * 1.0, 0.0, 1.0), 0.55, 0.0, true, 8);
        }

        // Block break / place.
        if (p.contains(".break") || p.contains("break")) {
            return new HapticImpulse("block_break", 42.0, 70, clamp(base * 0.9, 0.0, 1.0), 0.25, 0.0, true, 4);
        }
        if (p.contains(".place") || p.contains("place")) {
            return new HapticImpulse("block_place", 40.0, 55, clamp(base * 0.7, 0.0, 1.0), 0.15, 0.0, true, 3);
        }

        // World interactions: doors, trapdoors, gates, chests, buttons, levers.
        if ((p.contains("door") || p.contains("trapdoor") || p.contains("fence_gate") || p.contains("gate"))
                && (p.contains("open") || p.contains("close"))) {
            return new HapticImpulse("door", 38.0, 55, clamp(base * 0.45, 0.0, 0.8), 0.18, 0.0, true, 3);
        }
        if ((p.contains("chest") || p.contains("barrel") || p.contains("shulker"))
                && (p.contains("open") || p.contains("close"))) {
            return new HapticImpulse("container", 36.0, 65, clamp(base * 0.50, 0.0, 0.85), 0.22, 0.0, true, 3);
        }
        if (p.contains("button") && (p.contains("click") || p.contains("press"))) {
            return new HapticImpulse("button", 46.0, 32, clamp(base * 0.35, 0.0, 0.6), 0.06, 0.0, true, 2);
        }
        if (p.contains("lever") && (p.contains("click") || p.contains("switch") || p.contains("toggle"))) {
            return new HapticImpulse("lever", 44.0, 38, clamp(base * 0.35, 0.0, 0.65), 0.10, 0.0, true, 2);
        }

        // Utility blocks / crafting stations.
        if (p.contains("anvil") && (p.contains("use") || p.contains("land") || p.contains("place") || p.contains("hit"))) {
            return new HapticImpulse("anvil", 30.0, 110, clamp(base * 0.75, 0.0, 1.0), 0.25, 0.0, true, 5);
        }
        if (p.contains("grindstone") && p.contains("use")) {
            return new HapticImpulse("grindstone", 40.0, 95, clamp(base * 0.55, 0.0, 0.95), 0.28, 0.0, true, 4);
        }
        if ((p.contains("smithing") || p.contains("stonecutter") || p.contains("loom")) && (p.contains("use") || p.contains("take") || p.contains("result"))) {
            return new HapticImpulse("station", 42.0, 70, clamp(base * 0.45, 0.0, 0.8), 0.20, 0.0, true, 3);
        }

        // Footsteps / movement texture.
        if (p.contains(".step") || p.contains("step")) {
            // Prefer explicit on-foot movement haptics (grounded pitter-patter).
            if (BstConfig.get().footstepHapticsEnabled) {
                return null;
            }
            // Let the continuous road texture cover most of this; steps are light taps.
            return new HapticImpulse("step", 55.0, 30, clamp(base * 0.35, 0.0, 0.6), 0.05, 0.0, true, 1);
        }

        // Attacks / swings.
        if (p.contains("attack") || p.contains("sweep") || p.contains("crit")) {
            // Keep this subtle: gameplay melee click provides the main "thump" when enabled.
            return new HapticImpulse("attack", 52.0, 34, clamp(base * 0.32, 0.0, 0.55), 0.06, 0.0, true, 5);
        }

        // Projectiles.
        if (p.contains("arrow") || p.contains("bow") || p.contains("crossbow")) {
            return new HapticImpulse("proj", 46.0, 40, clamp(base * 0.45, 0.0, 0.7), 0.10, 0.0, true, 4);
        }

        // Boss / major events (sound-driven): warden / dragon cues.
        if (p.contains("warden") && (p.contains("sonic") || p.contains("boom") || p.contains("roar"))) {
            return new HapticImpulse("warden", 26.0, 240, clamp(base * 0.95, 0.0, 1.0), 0.65, 14.0, true, 9);
        }
        if ((p.contains("ender_dragon") || p.contains("dragon")) && (p.contains("growl") || p.contains("flap") || p.contains("death") || p.contains("fireball"))) {
            return new HapticImpulse("dragon", 24.0, 320, clamp(base * 0.9, 0.0, 1.0), 0.60, 16.0, true, 9);
        }

        // Default for other player/block/hostile sounds: tiny nudge, but only from "active" sources.
        if (source == SoundSource.PLAYERS || source == SoundSource.BLOCKS || source == SoundSource.HOSTILE) {
            return new HapticImpulse("misc", 44.0, 25, clamp(base * 0.18, 0.0, 0.35), 0.05, 0.0, true, 1);
        }

        return null;
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private record HapticImpulse(String bucketKey, double freqHz, int durationMs, double gain, double noiseMix, double distanceRefBlocks, boolean directional, int priority) {
    }
}
