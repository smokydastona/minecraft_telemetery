package com.smoky.bassshakertelemetry.client.ui.neon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class NeonToggleButton extends Button {
    private final BooleanSupplier state;
    private float hoverT;
    private float pressT;

    public NeonToggleButton(int x, int y, int width, int height, Component message, BooleanSupplier state, Runnable onToggle) {
        super(x, y, width, height,
                Objects.requireNonNull(message),
                b -> Objects.requireNonNull(onToggle).run(),
                Button.DEFAULT_NARRATION);
        this.state = Objects.requireNonNull(state);
    }

    @Override
    @SuppressWarnings("null")
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        NeonStyle style = NeonStyle.get();
        NeonAnimationTokens anim = style.anim;
        boolean on = state.getAsBoolean();

        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;

        boolean hovered = this.isHoveredOrFocused();
        boolean pressed = this.isMouseOver(mouseX, mouseY) && Minecraft.getInstance().mouseHandler.isLeftPressed();

        hoverT = step(hoverT, hovered ? 1.0f : 0.0f, hovered ? anim.hoverSpeed : anim.idleSpeed);
        pressT = step(pressT, pressed ? 1.0f : 0.0f, pressed ? anim.pressSpeed : anim.idleSpeed);

        int baseBg = on ? style.toggleOn : style.toggleOff;
        int bg = NeonColorUtil.lerp(baseBg, style.primaryPressed, pressT);
        bg = NeonColorUtil.mulRgb(bg, lerp(anim.brightnessIdle, anim.brightnessHover, hoverT));
        int border = NeonColorUtil.lerp(style.primary, style.primaryHover, hoverT);

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
            textColor = on ? style.panel : style.text;
        }
        guiGraphics.drawCenteredString(font, this.getMessage(), x + (w / 2), y + (h - 8) / 2, textColor);
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
