package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class TelemetryEventHandler {
    private double lastSpeed = 0.0;
    private float lastHealth = -1.0f;
    private ResourceKey<Biome> lastBiome = null;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!BstConfig.get().enabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        double speed = mc.player.getDeltaMovement().length();
        double accel = speed - lastSpeed;
        lastSpeed = speed;

        boolean elytra = mc.player.isFallFlying();

        // Damage detection (client-safe): detect health dropping.
        float health = mc.player.getHealth();
        if (lastHealth >= 0.0f && health < lastHealth) {
            AudioOutputEngine.get().triggerDamageBurst();
        }
        lastHealth = health;

        // Biome transitions (kept for later use; currently UI exposes toggle)
        if (BstConfig.get().biomeChimeEnabled) {
            Holder<Biome> biomeHolder = mc.level.getBiome(mc.player.blockPosition());
            ResourceKey<Biome> biomeKey = biomeHolder.unwrapKey().orElse(null);
            if (biomeKey != null && lastBiome != null && biomeKey != lastBiome) {
                AudioOutputEngine.get().triggerBiomeChime();
            }
            lastBiome = biomeKey;
        }

        AudioOutputEngine.get().updateTelemetry(speed, accel, elytra);
    }
}
