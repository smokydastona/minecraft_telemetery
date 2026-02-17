package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import com.smoky.bassshakertelemetry.config.BstVibrationProfiles;
import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
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
        double scale01 = clamp(amount / 8.0, 0.15, 1.0);
        var resolved = BstVibrationProfiles.get().resolve("damage.generic", scale01, 1.0);
        if (resolved != null) {
            double gain01 = clamp(resolved.intensity01() * clamp(cfg.damageBurstGain, 0.0, 1.0), 0.0, 1.0);

            // Directional damage: if the damage source has a position, encode the direction so you can
            // feel where the hit came from.
            var player = mc.player;
            DamageSource src = event.getSource();
            SourcePos source = resolveDamageSourcePos(src);

            var store = BstVibrationProfiles.get();
            var encoded = DirectionalEncoding.apply(
                    store,
                    player,
                    resolved.directional(),
                    source.hasSource,
                    source.x,
                    source.y,
                    source.z,
                    resolved.frequencyHz(),
                    gain01
            );

            String pat = (resolved.pattern() == null || resolved.pattern().isBlank()) ? "single" : resolved.pattern();
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
                    "damage.generic"
            );
        } else {
            AudioOutputEngine.get().triggerDamageBurst(scale01);
        }
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
            var resolved = BstVibrationProfiles.get().resolve("damage.death", 1.0, 1.0);
            double baseGain01;
            double baseHz;
            int baseMs;
            double baseNoise;
            String basePattern;
            if (resolved != null) {
                baseGain01 = clamp(resolved.intensity01() * clamp(cfg.damageBurstGain, 0.0, 1.0), 0.0, 1.0);
                baseHz = resolved.frequencyHz();
                baseMs = resolved.durationMs();
                baseNoise = resolved.noiseMix01();
                basePattern = resolved.pattern();
            } else {
                baseGain01 = clamp(cfg.damageBurstGain * 0.95, 0.0, 1.0);
                baseHz = 30.0;
                baseMs = 1050;
                baseNoise = 0.55;
                basePattern = "fade_out";
            }

            // Desired feel: a larger boom, then a slower "womp-wah" tail.
            // We approximate the downward sweep as a 2-stage impulse with a short delay.
            double boomHz = clamp(baseHz + 14.0, 34.0, 78.0);
            int boomMs = Math.max(120, Math.min(220, (int) (baseMs * 0.18)));
            double boomGain01 = clamp(baseGain01 * 1.00, 0.0, 1.0);
            double boomNoise = clamp(0.18 + (baseNoise * 0.30), 0.0, 0.65);

            double tailHz = clamp(baseHz - 10.0, 20.0, 52.0);
            int tailMs = Math.max(650, Math.min(1100, (int) (baseMs * 0.85)));
            double tailGain01 = clamp(baseGain01 * 0.92, 0.0, 1.0);
            double tailNoise = clamp(0.55 + (baseNoise * 0.25), 0.0, 0.85);

            AudioOutputEngine.get().triggerImpulse(
                    boomHz,
                    boomMs,
                    boomGain01,
                    boomNoise,
                    "punch",
                    160,
                    60,
                    95,
                    0,
                    "damage.death_boom"
            );
            AudioOutputEngine.get().triggerImpulse(
                    tailHz,
                    tailMs,
                    tailGain01,
                    tailNoise,
                    (basePattern == null || basePattern.isBlank()) ? "fade_out" : basePattern,
                    160,
                    60,
                    90,
                    115,
                    "damage.death_womp"
            );
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
                double scale01 = clamp(delta / 8.0, 0.15, 1.0);
                var resolved = BstVibrationProfiles.get().resolve("damage.generic", scale01, 1.0);
                if (resolved != null) {
                    double gain01 = clamp(resolved.intensity01() * clamp(cfg.damageBurstGain, 0.0, 1.0), 0.0, 1.0);

                    // Tick-based fallback may not have a DamageSource; use lastHurtByMob when available.
                    Entity attacker = player.getLastHurtByMob();
                    boolean hasSource = attacker != null;
                    double sx = hasSource ? attacker.getX() : player.getX();
                    double sy = hasSource ? attacker.getY() : player.getY();
                    double sz = hasSource ? attacker.getZ() : player.getZ();

                    var store = BstVibrationProfiles.get();
                    var encoded = DirectionalEncoding.apply(
                        store,
                        player,
                        resolved.directional(),
                        hasSource,
                        sx,
                        sy,
                        sz,
                        resolved.frequencyHz(),
                        gain01
                    );

                    String pat = (resolved.pattern() == null || resolved.pattern().isBlank()) ? "single" : resolved.pattern();
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
                        "damage.generic"
                    );
                } else {
                    AudioOutputEngine.get().triggerDamageBurst(scale01);
                }
            }

            lastHealth = health;
            lastHurtTime = hurtTime;
        }

        long now = System.nanoTime();

        // Fire/lava tick: small repeating pulse while burning.
        if (player.isOnFire()) {
            if ((now - lastFireTickNanos) > 260_000_000L) {
                lastFireTickNanos = now;
                var resolved = BstVibrationProfiles.get().resolve("damage.fire", 1.0, 1.0);
                if (resolved != null) {
                    double gain01 = clamp(resolved.intensity01() * clamp(cfg.damageBurstGain, 0.0, 1.0), 0.0, 1.0);
                    AudioOutputEngine.get().triggerImpulse(
                            resolved.frequencyHz(),
                            resolved.durationMs(),
                            gain01,
                            resolved.noiseMix01(),
                            resolved.pattern(),
                            resolved.pulsePeriodMs(),
                            resolved.pulseWidthMs()
                    );
                }
            }
        }

        // Drowning: pulse when air is low and decreasing.
        int air = player.getAirSupply();
        if (lastAir >= 0) {
            boolean airDropping = air < lastAir;
            boolean lowAir = air <= 60; // ~3 seconds
            if (airDropping && lowAir && (now - lastDrownTickNanos) > 520_000_000L) {
                lastDrownTickNanos = now;
                var resolved = BstVibrationProfiles.get().resolve("damage.drowning", 1.0, 1.0);
                if (resolved != null) {
                    double gain01 = clamp(resolved.intensity01() * clamp(cfg.damageBurstGain, 0.0, 1.0), 0.0, 1.0);
                    AudioOutputEngine.get().triggerImpulse(
                            resolved.frequencyHz(),
                            resolved.durationMs(),
                            gain01,
                            resolved.noiseMix01(),
                            resolved.pattern(),
                            resolved.pulsePeriodMs(),
                            resolved.pulseWidthMs()
                    );
                }
            }
        }
        lastAir = air;

        // Poison / wither: light rhythmic pulses while effect is active.
        if (player.hasEffect(MobEffects.POISON)) {
            if ((now - lastPoisonTickNanos) > 650_000_000L) {
                lastPoisonTickNanos = now;
                var resolved = BstVibrationProfiles.get().resolve("damage.poison", 1.0, 1.0);
                if (resolved != null) {
                    double gain01 = clamp(resolved.intensity01() * clamp(cfg.damageBurstGain, 0.0, 1.0), 0.0, 1.0);
                    AudioOutputEngine.get().triggerImpulse(
                            resolved.frequencyHz(),
                            resolved.durationMs(),
                            gain01,
                            resolved.noiseMix01(),
                            resolved.pattern(),
                            resolved.pulsePeriodMs(),
                            resolved.pulseWidthMs()
                    );
                }
            }
        }
        if (player.hasEffect(MobEffects.WITHER)) {
            if ((now - lastWitherTickNanos) > 560_000_000L) {
                lastWitherTickNanos = now;
                var resolved = BstVibrationProfiles.get().resolve("damage.wither", 1.0, 1.0);
                if (resolved != null) {
                    double gain01 = clamp(resolved.intensity01() * clamp(cfg.damageBurstGain, 0.0, 1.0), 0.0, 1.0);
                    AudioOutputEngine.get().triggerImpulse(
                            resolved.frequencyHz(),
                            resolved.durationMs(),
                            gain01,
                            resolved.noiseMix01(),
                            resolved.pattern(),
                            resolved.pulsePeriodMs(),
                            resolved.pulseWidthMs()
                    );
                }
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

    private record SourcePos(boolean hasSource, double x, double y, double z) {
    }

    private static SourcePos resolveDamageSourcePos(DamageSource src) {
        if (src == null) {
            return new SourcePos(false, 0.0, 0.0, 0.0);
        }

        try {
            Entity e = src.getEntity();
            if (e != null) {
                return new SourcePos(true, e.getX(), e.getY(), e.getZ());
            }
        } catch (Exception ignored) {
        }

        try {
            Entity e = src.getDirectEntity();
            if (e != null) {
                return new SourcePos(true, e.getX(), e.getY(), e.getZ());
            }
        } catch (Exception ignored) {
        }

        // Environmental damage sometimes reports a source position without an entity.
        Vec3 pos = tryGetSourcePositionReflective(src);
        if (pos != null) {
            return new SourcePos(true, pos.x, pos.y, pos.z);
        }

        return new SourcePos(false, 0.0, 0.0, 0.0);
    }

    private static Vec3 tryGetSourcePositionReflective(DamageSource src) {
        try {
            var m = src.getClass().getMethod("getSourcePosition");
            Object r = m.invoke(src);
            if (r instanceof Vec3 v) {
                return v;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
