package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstVibrationProfiles;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;

/**
 * Single ingress point for client-side vibration playback.
 *
 * All sources (network-relayed authoritative events, sound-inferred events, local client events)
 * should go through this class so suppression/dedupe rules stay consistent.
 *
 * IMPORTANT: Client-only. Imports net.minecraft.client.*
 */
public final class VibrationIngress {
    private VibrationIngress() {
    }

    private static final Object suppressLock = new Object();
    private static final ArrayList<Suppression> suppressions = new ArrayList<>();

    private static final Object debugLock = new Object();
    private static DebugEvent lastEvent = null;
    private static SuppressionEvent lastSuppression = null;

    private record Suppression(String bucket, int priority, long untilNanos, double x, double y, double z, double radiusBlocks) {
    }

    public enum SourceType {
        NETWORK,
        SOUND,
        LOCAL
    }

    private record DebugEvent(SourceType sourceType, String keyOrBucket, int priority, double frequencyHz, double gain01) {
    }

    private record SuppressionEvent(String bucket, int incomingPriority, int suppressPriority) {
    }

    /**
     * Called from the network handler via reflection.
     * Records a short suppression window for equivalent sound-inferred buckets (prevents double-triggering).
     */
    public static void playNetworkVibrationWithKey(String key, BstVibrationProfiles.Resolved resolved, double baseGain01, boolean hasSource, double sourceX, double sourceY, double sourceZ) {
        noteAuthoritativeEvent(key, resolved.priority(), hasSource, sourceX, sourceY, sourceZ);

        var store = BstVibrationProfiles.get();
        var player = Minecraft.getInstance().player;
        var encoded = DirectionalEncoding.apply(
                store,
                player,
                resolved.directional(),
                hasSource,
                sourceX,
                sourceY,
                sourceZ,
                resolved.frequencyHz(),
                baseGain01
        );

        AudioOutputEngine.get().triggerImpulse(
                encoded.frequencyHz(),
                resolved.durationMs(),
                encoded.gain01(),
                resolved.noiseMix01(),
                resolved.pattern(),
                resolved.pulsePeriodMs(),
                resolved.pulseWidthMs(),
                resolved.priority(),
            encoded.delayMs(),
            (key == null) ? "" : key
        );

        setLastEvent(SourceType.NETWORK, (key == null || key.isBlank()) ? "<network>" : key, resolved.priority(), encoded.frequencyHz(), encoded.gain01());
    }

    public static boolean shouldSuppressSoundBucket(String bucket, double x, double y, double z, int incomingPriority) {
        if (bucket == null || bucket.isBlank()) {
            return false;
        }

        long now = System.nanoTime();
        synchronized (suppressLock) {
            for (int i = suppressions.size() - 1; i >= 0; i--) {
                Suppression s = suppressions.get(i);
                if (now > s.untilNanos) {
                    suppressions.remove(i);
                    continue;
                }
                if (!bucket.equalsIgnoreCase(s.bucket)) {
                    continue;
                }

                // Priority-aware: only suppress lower or equal priority events.
                if (incomingPriority > s.priority) {
                    continue;
                }

                double dx = x - s.x;
                double dy = y - s.y;
                double dz = z - s.z;
                double r = Math.max(0.5, s.radiusBlocks); // spatial fuzz for offset audio origins
                if ((dx * dx) + (dy * dy) + (dz * dz) <= (r * r)) {
                    setLastSuppression(bucket, incomingPriority, s.priority);
                    return true;
                }
            }
        }
        return false;
    }

    public static void playSoundImpulse(String bucket, boolean directional, double sourceX, double sourceY, double sourceZ, double frequencyHz, int durationMs, double gain01, double noiseMix01, int priority) {
        if (shouldSuppressSoundBucket(bucket, sourceX, sourceY, sourceZ, priority)) {
            return;
        }

        var store = BstVibrationProfiles.get();
        var player = Minecraft.getInstance().player;
        var encoded = DirectionalEncoding.apply(
                store,
                player,
                directional,
                true,
                sourceX,
                sourceY,
                sourceZ,
                frequencyHz,
                gain01
        );

        AudioOutputEngine.get().triggerImpulse(
                encoded.frequencyHz(),
                durationMs,
                encoded.gain01(),
                noiseMix01,
                "single",
                160,
                60,
                priority,
            encoded.delayMs(),
            (bucket == null) ? "" : bucket
        );

        setLastEvent(SourceType.SOUND, (bucket == null || bucket.isBlank()) ? "<sound>" : bucket, priority, encoded.frequencyHz(), encoded.gain01());
    }

    /**
     * Local (client-side) event playback that still participates in suppression/dedupe rules.
     *
     * Typical use: gameplay click/tap events that would otherwise double-trigger with sound inference.
     */
    public static void playLocalImpulse(String keyOrBucket,
                                        boolean directional,
                                        boolean hasSource,
                                        double sourceX,
                                        double sourceY,
                                        double sourceZ,
                                        double frequencyHz,
                                        int durationMs,
                                        double gain01,
                                        double noiseMix01,
                                        int priority) {
        noteLocalEvent(keyOrBucket, priority, hasSource, sourceX, sourceY, sourceZ);

        var store = BstVibrationProfiles.get();
        var player = Minecraft.getInstance().player;
        var encoded = DirectionalEncoding.apply(
                store,
                player,
                directional,
                hasSource,
                sourceX,
                sourceY,
                sourceZ,
                frequencyHz,
                gain01
        );

        AudioOutputEngine.get().triggerImpulse(
                encoded.frequencyHz(),
                durationMs,
                encoded.gain01(),
                noiseMix01,
                "single",
                160,
                60,
                priority,
                encoded.delayMs(),
                (keyOrBucket == null) ? "" : keyOrBucket
        );

        setLastEvent(SourceType.LOCAL, (keyOrBucket == null || keyOrBucket.isBlank()) ? "<local>" : keyOrBucket, priority, encoded.frequencyHz(), encoded.gain01());
    }

    private static void noteAuthoritativeEvent(String key, int priority, boolean hasSource, double x, double y, double z) {
        if (key == null || key.isBlank() || !hasSource) {
            return;
        }

        String k = key.trim().toLowerCase();
        long now = System.nanoTime();

        // Keep this small and explicit: one authoritative source per event type.
        // Windows are short to avoid suppressing chained/nearby-but-distinct events.
        if (k.startsWith("explosion.")) {
            // Explosion sounds can be slightly offset from the logical center.
            addSuppression("explosion", priority, now, 120, x, y, z, 18.0);
        } else if (k.equals("world.block_break")) {
            addSuppression("block_break", priority, now, 80, x, y, z, 6.0);
        } else if (k.equals("combat.hit")) {
            // Suppress both swing/hit and hurt sounds near the target.
            addSuppression("attack", priority, now, 90, x, y, z, 6.0);
            addSuppression("hurt", priority, now, 90, x, y, z, 6.0);
        }
    }

    private static void noteLocalEvent(String keyOrBucket, int priority, boolean hasSource, double x, double y, double z) {
        if (keyOrBucket == null || keyOrBucket.isBlank() || !hasSource) {
            return;
        }

        String k = keyOrBucket.trim().toLowerCase();
        long now = System.nanoTime();

        // Gameplay clicks are intentionally small and should not stack with sound inference.
        if (k.startsWith("gameplay.attack_")) {
            // Sound-inferred attack swings use bucket "attack".
            addSuppression("attack", Math.max(priority, 5), now, 120, x, y, z, 5.5);
        } else if (k.startsWith("gameplay.use_")) {
            int supPri = Math.max(priority, 3);
            addSuppression("button", supPri, now, 120, x, y, z, 5.0);
            addSuppression("lever", supPri, now, 120, x, y, z, 5.0);
            addSuppression("door", supPri, now, 140, x, y, z, 6.5);
            addSuppression("container", supPri, now, 140, x, y, z, 6.5);
            addSuppression("block_place", supPri, now, 120, x, y, z, 5.5);
        }
    }

    private static void addSuppression(String bucket, int priority, long nowNanos, int windowMs, double x, double y, double z, double radiusBlocks) {
        long until = nowNanos + (Math.max(10, windowMs) * 1_000_000L);
        synchronized (suppressLock) {
            // Bound memory and keep most-recent items.
            if (suppressions.size() > 24) {
                suppressions.subList(0, suppressions.size() - 16).clear();
            }
            suppressions.add(new Suppression(bucket, priority, until, x, y, z, radiusBlocks));
        }
    }

    private static void setLastEvent(SourceType sourceType, String keyOrBucket, int priority, double frequencyHz, double gain01) {
        synchronized (debugLock) {
            lastEvent = new DebugEvent(sourceType, keyOrBucket, priority, frequencyHz, gain01);
        }
    }

    private static void setLastSuppression(String bucket, int incomingPriority, int suppressPriority) {
        synchronized (debugLock) {
            lastSuppression = new SuppressionEvent(bucket, incomingPriority, suppressPriority);
        }
    }

    public static String getOverlayLine1() {
        synchronized (debugLock) {
            if (lastEvent == null) {
                return "BST: (no recent vibration)";
            }
            return "BST: src=" + lastEvent.sourceType + " key=" + lastEvent.keyOrBucket + " pri=" + lastEvent.priority;
        }
    }

    public static String getOverlayLine2() {
        synchronized (debugLock) {
            if (lastEvent == null) {
                return "";
            }
            return String.format(java.util.Locale.ROOT, "freq=%.1fHz gain=%.2f", lastEvent.frequencyHz, lastEvent.gain01);
        }
    }

    public static String getOverlayLine3() {
        synchronized (debugLock) {
            if (lastSuppression == null) {
                return "";
            }
            return "suppressed sound bucket=" + lastSuppression.bucket + " (inPri=" + lastSuppression.incomingPriority + " <= supPri=" + lastSuppression.suppressPriority + ")";
        }
    }

    public static String getOverlayLine4() {
        return AudioOutputEngine.get().getDominantDebugString();
    }
}
