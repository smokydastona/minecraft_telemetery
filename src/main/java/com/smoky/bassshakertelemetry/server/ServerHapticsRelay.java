package com.smoky.bassshakertelemetry.server;

import com.smoky.bassshakertelemetry.net.BstNet;
import com.smoky.bassshakertelemetry.net.PlayVibrationMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side event hooks relayed to the client via a small packet.
 *
 * This enables “true” event-timed haptics in multiplayer, instead of relying only on client sound inference.
 */
public final class ServerHapticsRelay {
    private final Map<UUID, Long> lastAttackNanosByPlayer = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        Vec3 pos = event.getExplosion().getPosition();
        if (pos == null) {
            return;
        }

        double refBlocks = 16.0;
        var server = level.getServer();
        if (server == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != level) {
                continue;
            }
            double dist = player.position().distanceTo(pos);
            if (dist > 80.0) {
                continue;
            }
            float distanceScale01 = (float) distanceScale01(dist, refBlocks);
            if (distanceScale01 <= 0.02f) {
                continue;
            }
            BstNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PlayVibrationMessage(
                    "explosion.generic",
                    1.0f,
                    distanceScale01,
                    true,
                    pos.x,
                    pos.y,
                    pos.z
            ));
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() == null || event.getPlayer().level().isClientSide()) {
            return;
        }

        if (!(event.getPlayer() instanceof ServerPlayer sp)) {
            return;
        }

        BlockPos pos = event.getPos();
        if (pos == null) {
            return;
        }

        BstNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new PlayVibrationMessage(
                "world.block_break",
                1.0f,
                1.0f,
                true,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
        ));
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }

        float amount = event.getAmount();
        if (!(amount > 0.0f)) {
            return;
        }

        // De-dupe: some damage sources can fire in quick succession.
        long now = System.nanoTime();
        Long last = lastAttackNanosByPlayer.get(attacker.getUUID());
        if (last != null && (now - last) < 50_000_000L) {
            return;
        }
        lastAttackNanosByPlayer.put(attacker.getUUID(), now);

        float scale01 = (float) clamp(amount / 8.0, 0.15, 1.0);
        Vec3 targetPos = event.getEntity().position();
        BstNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> attacker), new PlayVibrationMessage(
            "combat.hit",
            scale01,
            1.0f,
            true,
            targetPos.x,
            targetPos.y,
            targetPos.z
        ));
    }

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }

        if (!(entity instanceof ServerPlayer sp)) {
            return;
        }

        float dist = event.getDistance();
        if (!(dist > 2.0f)) {
            return;
        }

        float scale01 = (float) clamp(dist / 12.0, 0.10, 1.0);
        BstNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new PlayVibrationMessage(
            "damage.fall",
            scale01,
            1.0f,
            false,
            0.0,
            0.0,
            0.0
        ));
    }

    private static double distanceScale01(double distBlocks, double refBlocks) {
        double r = Math.max(0.01, refBlocks);
        double d = distBlocks / r;
        return 1.0 / (1.0 + (d * d));
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
