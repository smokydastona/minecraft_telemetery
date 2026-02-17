package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import com.smoky.bassshakertelemetry.config.BstVibrationProfiles;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Proximity-driven directional "heartbeat" coming from nearby Wardens.
 *
 * This is intentionally quieter than damage haptics and uses a lower priority so it never masks hits.
 */
public final class WardenHeartbeatHapticsHandler {
    private static final String PROFILE_KEY = "boss.warden_heartbeat";

    // Detection radius in blocks. Kept moderate to avoid constant scanning.
    private static final double MAX_RANGE_BLOCKS = 36.0;

    // Heartbeat cadence bounds.
    private static final int FAR_PERIOD_MS = 1200;
    private static final int NEAR_PERIOD_MS = 520;

    private static final long SCAN_COOLDOWN_NANOS = 160_000_000L; // 160ms

    private long lastScanNanos;
    private long lastBeatNanos;

    private Warden cachedNearest;
    private double cachedDist;

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled) {
            clear();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.player == null || mc.level == null) {
            clear();
            return;
        }

        var player = mc.player;
        if (player.isDeadOrDying() || player.getHealth() <= 0.0f) {
            clear();
            return;
        }

        long now = System.nanoTime();

        if (now - lastScanNanos >= SCAN_COOLDOWN_NANOS) {
            lastScanNanos = now;
            scanNearestWarden(mc);
        }

        if (cachedNearest == null) {
            return;
        }

        // If the warden is no longer valid or out of range, stop.
        if (!cachedNearest.isAlive() || cachedNearest.isRemoved() || cachedDist > MAX_RANGE_BLOCKS) {
            cachedNearest = null;
            return;
        }

        // Louder when closer.
        double distance01 = 1.0 - clamp(cachedDist / MAX_RANGE_BLOCKS, 0.0, 1.0);
        if (distance01 <= 0.001) {
            return;
        }

        int periodMs = (int) Math.round(lerp(FAR_PERIOD_MS, NEAR_PERIOD_MS, distance01));
        long periodNs = Math.max(80L, (long) periodMs) * 1_000_000L;

        if (lastBeatNanos != 0L && (now - lastBeatNanos) < periodNs) {
            return;
        }
        lastBeatNanos = now;

        // Resolve profile. Use a low priority and cap the gain so it stays quieter than damage.
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
                cachedNearest.getX(),
                cachedNearest.getY(),
                cachedNearest.getZ(),
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

    @SuppressWarnings("null")
    private void scanNearestWarden(Minecraft mc) {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) {
            cachedNearest = null;
            return;
        }

        var scanBox = player.getBoundingBox().inflate(MAX_RANGE_BLOCKS);
        List<Warden> wardens = level.getEntitiesOfClass(
                Warden.class,
                scanBox,
                w -> w != null && w.isAlive()
        );

        if (wardens.isEmpty()) {
            cachedNearest = null;
            cachedDist = 0.0;
            return;
        }

        Warden best = null;
        double bestDist = Double.MAX_VALUE;
        for (Warden w : wardens) {
            if (w == null) {
                continue;
            }
            double d = w.distanceTo(player);
            if (d < bestDist) {
                bestDist = d;
                best = w;
            }
        }

        cachedNearest = best;
        cachedDist = bestDist;
    }

    private void clear() {
        cachedNearest = null;
        cachedDist = 0.0;
        lastBeatNanos = 0L;
        lastScanNanos = 0L;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * clamp(t, 0.0, 1.0);
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
