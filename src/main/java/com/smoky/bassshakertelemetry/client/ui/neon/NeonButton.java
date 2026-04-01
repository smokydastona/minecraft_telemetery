package com.smoky.bassshakertelemetry.client.ui.neon;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Objects;

public final class NeonButton extends Button {
    private float hoverT;
    private float pressT;

    public NeonButton(int x, int y, int width, int height, Component message, Runnable onClick) {
        super(
                x,
                y,
                width,
                height,
                Objects.requireNonNull(message),
                b -> Objects.requireNonNull(onClick).run(),
                Button.DEFAULT_NARRATION
        );
    }

    @Override
    @SuppressWarnings("null")
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        NeonStyle style = NeonStyle.get();
        NeonAnimationTokens anim = style.anim;

        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;

        boolean hovered = this.isHoveredOrFocused();
        boolean pressed = this.isMouseOver(mouseX, mouseY) && Minecraft.getInstance().mouseHandler.isLeftPressed();

        hoverT = step(hoverT, hovered ? 1.0f : 0.0f, hovered ? anim.hoverSpeed : anim.idleSpeed);
        pressT = step(pressT, pressed ? 1.0f : 0.0f, pressed ? anim.pressSpeed : anim.idleSpeed);

        int border = NeonColorUtil.lerp(style.primary, style.primaryHover, hoverT);
        int bg = NeonColorUtil.lerp(style.panel, style.primaryPressed, pressT);
        bg = NeonColorUtil.mulRgb(bg, lerp(anim.brightnessIdle, hovered ? anim.brightnessHover : anim.brightnessIdle, hoverT));

        if (!this.active) {
            bg = NeonColorUtil.withAlpha(bg, 180);
            border = NeonColorUtil.withAlpha(border, 180);
        }

        guiGraphics.fill(x, y, x + w, y + h, bg);
        guiGraphics.fill(x, y, x + w, y + 1, border);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, border);
        guiGraphics.fill(x, y, x + 1, y + h, border);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, border);

        var font = Objects.requireNonNull(Minecraft.getInstance().font);
        int textColor;
        if (!this.active) {
            textColor = style.textDim;
        } else {
            textColor = pressed ? style.panel : style.text;
        }

        Component message = this.getMessage();
        int textWidth = font.width(message);
        int padding = 6;
        int innerLeft = x + padding;
        int innerRight = x + w - padding;
        int availableWidth = Math.max(0, innerRight - innerLeft);
        int textY = y + (h - 8) / 2;

        if (textWidth <= availableWidth) {
            guiGraphics.drawCenteredString(font, message, x + (w / 2), textY, textColor);
            return;
        }

        // Long labels (e.g., audio device names) should never spill outside the button.
        // Keep UI clean: clip always; scroll only on hover/focus so it isn't distracting.
        guiGraphics.enableScissor(innerLeft, y + 2, innerRight, y + h - 2);

        if (hovered) {
            int gapPx = 24;
            float speedPxPerSec = 18.0f;

            float t = (float) (Util.getMillis() / 1000.0);
            float cycle = textWidth + gapPx;
            float offset = (t * speedPxPerSec) % cycle;

            guiGraphics.drawString(font, message, innerLeft - (int) offset, textY, textColor);
            guiGraphics.drawString(font, message, innerLeft - (int) offset + (int) cycle, textY, textColor);
        } else {
            guiGraphics.drawString(font, message, innerLeft, textY, textColor);
        }

        guiGraphics.disableScissor();
    }

    private static float step(float current, float target, float speed) {
        return current + (target - current) * clamp01(speed);
    }

    private static float clamp01(float v) {
        if (v < 0.0f) return 0.0f;
        if (v > 1.0f) return 1.0f;
        return v;
    }

    private static float lerp(float a, float b, float t) {
        if (t <= 0.0f) return a;
        if (t >= 1.0f) return b;
        return a + (b - a) * t;
    }
}
