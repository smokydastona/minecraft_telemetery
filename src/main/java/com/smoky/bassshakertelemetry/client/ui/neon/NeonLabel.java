package com.smoky.bassshakertelemetry.client.ui.neon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.Objects;

public final class NeonLabel extends AbstractWidget {
    private final boolean centered;

    public NeonLabel(int x, int y, int width, int height, Component message, boolean centered) {
        super(x, y, width, height, Objects.requireNonNullElse(message, Component.empty()));
        this.centered = centered;
        this.active = false;
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput narrationElementOutput) {
    }

    @Override
    @SuppressWarnings("null")
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font);
        int color = NeonStyle.get().text;
        int x = this.getX();
        int y = this.getY();
        int h = this.getHeight();
        int textY = y + (h - 8) / 2;

        if (centered) {
            guiGraphics.drawCenteredString(font, this.getMessage(), x + (this.width / 2), textY, color);
        } else {
            guiGraphics.drawString(font, this.getMessage(), x, textY, color);
        }
    }
}
