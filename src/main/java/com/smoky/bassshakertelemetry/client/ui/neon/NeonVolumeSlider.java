package com.smoky.bassshakertelemetry.client.ui.neon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.Objects;

public final class NeonVolumeSlider extends AbstractSliderButton {
    private final Component label;

    private float hoverT;
    private float pressT;

    public NeonVolumeSlider(int x, int y, int width, int height, Component label, double initial) {
        super(x, y, width, height, Component.empty(), clamp01(initial));
        this.label = Objects.requireNonNull(label);
        updateMessage();
    }

    @Override
    @SuppressWarnings("null")
    protected void updateMessage() {
        int pct = (int) Math.round(clamp01(value) * 100.0);
        this.setMessage(
                label.copy()
                        .append(": ")
                        .append(Component.literal(pct + "%"))
        );
    }

    @Override
    protected void applyValue() {
    }

    public double getValue() {
        return clamp01(this.value);
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
        boolean pressed = hovered && Minecraft.getInstance().mouseHandler.isLeftPressed();

        hoverT = step(hoverT, hovered ? 1.0f : 0.0f, hovered ? anim.hoverSpeed : anim.idleSpeed);
        pressT = step(pressT, pressed ? 1.0f : 0.0f, pressed ? anim.pressSpeed : anim.idleSpeed);

        int bg = NeonColorUtil.lerp(style.panel, style.primaryPressed, pressT);
        bg = NeonColorUtil.mulRgb(bg, lerp(anim.brightnessIdle, anim.brightnessHover, hoverT));
        int border = NeonColorUtil.lerp(style.primary, style.primaryHover, hoverT);

        // Background + 1px border.
        guiGraphics.fill(x, y, x + w, y + h, bg);
        guiGraphics.fill(x, y, x + w, y + 1, border);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, border);
        guiGraphics.fill(x, y, x + 1, y + h, border);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, border);

        // Track.
        int pad = Math.max(2, style.paddingPx);
        int trackH = Math.max(4, h / 4);
        int trackY = y + (h / 2) - (trackH / 2);
        int trackX1 = x + pad;
        int trackX2 = x + w - pad;

        guiGraphics.fill(trackX1, trackY, trackX2, trackY + trackH, style.sliderTrack);

        double t = clamp01(this.value);
        int fillX2 = trackX1 + (int) Math.round((trackX2 - trackX1) * t);
        int fillColor = NeonColorUtil.lerp(style.sliderFill, style.primaryHover, hoverT);
        guiGraphics.fill(trackX1, trackY, fillX2, trackY + trackH, fillColor);

        // Knob.
        int knobW = Math.max(6, trackH + 2);
        int knobH = Math.max(10, trackH + 6);
        int knobX = fillX2 - (knobW / 2);
        int knobY = y + (h / 2) - (knobH / 2);
        guiGraphics.fill(knobX, knobY, knobX + knobW, knobY + knobH, border);

        // Text.
        var font = Objects.requireNonNull(Minecraft.getInstance().font);
        int textColor = this.active ? style.text : style.textDim;
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

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
