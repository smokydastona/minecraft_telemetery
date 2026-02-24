package com.smoky.bassshakertelemetry.client.accessibility;

import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Objects;

public final class HudCueOverlayHandler {
    @SubscribeEvent
    @SuppressWarnings("null")
    public void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled || !cfg.accessibilityHudEnabled || !cfg.accessibilityHudCuesEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = Objects.requireNonNull(mc.font, "font");

        int x = 6;
        int y = 6;
        if (cfg.debugOverlayEnabled) {
            y += 52;
        }

        var lines = HudCueManager.get().getLines();
        for (int i = 0; i < lines.size(); i++) {
            var l = lines.get(i);
            int color = colorFor(l.type());
            event.getGuiGraphics().drawString(font, l.text(), x, y + (i * 10), color);
        }
    }

    private static int colorFor(HudCueType type) {
        if (type == null) {
            return 0xFFFFFF;
        }
        return switch (type) {
            case DAMAGE, EXPLOSION, LOW_HEALTH -> 0xFFCC66;
            case THUNDER -> 0x66CCFF;
            case BOSS -> 0xCCCCCC;
            case WARDEN_HEARTBEAT -> 0xCCCCCC;
        };
    }
}
