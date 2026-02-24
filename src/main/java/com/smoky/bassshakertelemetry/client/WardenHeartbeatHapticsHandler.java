package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.client.accessibility.HudCueManager;
import com.smoky.bassshakertelemetry.client.accessibility.HudCueType;
import com.smoky.bassshakertelemetry.config.BstConfig;
import com.smoky.bassshakertelemetry.config.BstVibrationProfiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Directional "heartbeat" haptic triggered from the actual Warden heartbeat sound.
 *
 * Cadence should match the game audio; loudness scales with distance and is capped to stay
 * quieter than damage haptics.
 */
public final class WardenHeartbeatHapticsHandler {
    private static final String PROFILE_KEY = "boss.warden_heartbeat";

    private static final double MAX_RANGE_BLOCKS = 36.0;

    private long lastFireNanos;

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onPlaySound(PlaySoundEvent event) {
        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled) {
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

        var player = mc.player;
        if (player.isDeadOrDying() || player.getHealth() <= 0.0f) {
            return;
        }

        // Ignore music/records/ambient.
        SoundSource source;
        try {
            source = sound.getSource();
        } catch (NullPointerException ignored) {
            return;
        }
        if (source == SoundSource.MUSIC || source == SoundSource.RECORDS || source == SoundSource.AMBIENT) {
            return;
        }

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

        String p = path.toLowerCase();
        if (!(p.contains("warden") && p.contains("heartbeat"))) {
            return;
        }

        if (cfg.accessibilityHudEnabled && cfg.accessibilityHudCuesEnabled
                && HudCueManager.get().canFire(HudCueType.WARDEN_HEARTBEAT, 650)) {
            HudCueManager.get().push(HudCueType.WARDEN_HEARTBEAT, Component.translatable("bassshakertelemetry.cue.warden_heartbeat"));
        }

        // De-dupe in case the sound system dispatches multiple instances very close together.
        long now = System.nanoTime();
        if (lastFireNanos != 0L && (now - lastFireNanos) < 80_000_000L) {
            return;
        }
        lastFireNanos = now;

        // If the heartbeat sound is muted to ~0, avoid firing.
        double volume;
        try {
            volume = sound.getVolume();
        } catch (NullPointerException ignored) {
            return;
        }
        if (volume <= 0.001) {
            return;
        }

        // Compute distance scale (louder when closer), and ignore beyond a reasonable radius.
        Vec3 playerPos = player.position();
        double dx = sound.getX() - playerPos.x;
        double dy = sound.getY() - playerPos.y;
        double dz = sound.getZ() - playerPos.z;
        double dist = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        if (dist > MAX_RANGE_BLOCKS) {
            return;
        }

        double distance01 = 1.0 - clamp(dist / MAX_RANGE_BLOCKS, 0.0, 1.0);
        if (distance01 <= 0.001) {
            return;
        }

        var store = BstVibrationProfiles.get();
        var resolved = store.resolve(PROFILE_KEY, 1.0, 1.0);
        if (resolved == null) {
            return;
        }

        // Ensure "always quieter than damage" by hard-capping relative to damage gain knob.
        double cap01 = clamp(cfg.damageBurstGain, 0.0, 1.0) * 0.35;
        if (cap01 <= 0.001) {
            return;
        }

        // Smooth closeness ramp: subtle at distance, noticeable when near.
        double closeness = Math.pow(distance01, 1.35);
        double baseGain01 = clamp(resolved.intensity01() * closeness, 0.0, 1.0);
        double gain01 = Math.min(baseGain01, cap01);
        if (gain01 <= 0.001) {
            return;
        }

        var encoded = DirectionalEncoding.apply(
                store,
                player,
                resolved.directional(),
                true,
                sound.getX(),
                sound.getY(),
                sound.getZ(),
                resolved.frequencyHz(),
                gain01
        );

        String pat = (resolved.pattern() == null || resolved.pattern().isBlank()) ? "soft_single" : resolved.pattern();
        AudioOutputEngine.get().triggerImpulse(
                encoded.frequencyHz(),
                resolved.durationMs(),
                encoded.gain01(),
                resolved.noiseMix01(),
                pat,
                resolved.pulsePeriodMs(),
                resolved.pulseWidthMs(),
                resolved.priority(),
                encoded.delayMs(),
                PROFILE_KEY
        );
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
