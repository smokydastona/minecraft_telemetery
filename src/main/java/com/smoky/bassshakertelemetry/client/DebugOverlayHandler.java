package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Objects;

/**
 * Minimal dev overlay for seeing what the haptics engine is doing.
 */
public final class DebugOverlayHandler {
    @SubscribeEvent
    @SuppressWarnings("null")
    public void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!BstConfig.get().debugOverlayEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = Objects.requireNonNull(mc.font, "font");

        String l1 = VibrationIngress.getOverlayLine1();
        String l2 = VibrationIngress.getOverlayLine2();
        String l3 = VibrationIngress.getOverlayLine3();
        String l4 = VibrationIngress.getOverlayLine4();

        int x = 6;
        int y = 6;
        event.getGuiGraphics().drawString(font, l1, x, y, 0xFFFFFF);
        if (!l2.isBlank()) {
            event.getGuiGraphics().drawString(font, l2, x, y + 10, 0xCCCCCC);
        }
        if (!l3.isBlank()) {
            event.getGuiGraphics().drawString(font, l3, x, y + 20, 0xFFCC66);
        }
        if (!l4.isBlank()) {
            event.getGuiGraphics().drawString(font, l4, x, y + 30, 0x99FF99);
        }
    }
}
