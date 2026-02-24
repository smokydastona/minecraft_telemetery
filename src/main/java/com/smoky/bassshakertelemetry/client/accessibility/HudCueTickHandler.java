package com.smoky.bassshakertelemetry.client.accessibility;

import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class HudCueTickHandler {
    private boolean lastLow;

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled || !cfg.accessibilityHudEnabled || !cfg.accessibilityHudCuesEnabled) {
            lastLow = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.player == null) {
            lastLow = false;
            return;
        }

        var player = mc.player;
        float max = player.getMaxHealth();
        if (!(max > 0.0f)) {
            lastLow = false;
            return;
        }

        float health = player.getHealth();
        double pct = (health / max) * 100.0;
        boolean low = pct <= cfg.accessibilityLowHealthThresholdPct;

        if (low && (!lastLow || HudCueManager.get().canFire(HudCueType.LOW_HEALTH, cfg.accessibilityLowHealthCooldownMs))) {
            HudCueManager.get().push(HudCueType.LOW_HEALTH, Component.translatable("bassshakertelemetry.cue.low_health"));
        }

        lastLow = low;
    }
}
