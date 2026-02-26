package com.smoky.bassshakertelemetry.client.ui.neon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public final class NeonRangeSlider extends AbstractSliderButton {
    private final Component label;
    private final double min;
    private final double max;
    private final double step;
    private final String format;

    private final DoubleSupplier getter;
    private final DoubleConsumer setter;

    private float hoverT;
    private float pressT;

    public NeonRangeSlider(
            int x,
            int y,
            int width,
            int height,
            Component label,
            double min,
            double max,
            double step,
            String format,
            DoubleSupplier getter,
            DoubleConsumer setter
    ) {
        super(x, y, width, height, Component.empty(), 0.0);
        this.label = Objects.requireNonNullElse(label, Component.empty());
        this.min = min;
        this.max = Math.max(min + 1e-9, max);
        this.step = step <= 0 ? 0 : step;
        this.format = format;
        this.getter = Objects.requireNonNull(getter);
        this.setter = Objects.requireNonNull(setter);

        double initial = clamp(getter.getAsDouble(), this.min, this.max);
        this.value = toNormalized(initial);
        updateMessage();
    }

    public double getRealValue() {
        return fromNormalized(this.value);
    }

    @Override
    @SuppressWarnings("null")
    protected void updateMessage() {
        double v = fromNormalized(this.value);
        String fmt = (format == null) ? "" : format.trim();
        Component suffix;

        if ("percent".equalsIgnoreCase(fmt)) {
            suffix = Component.literal(((int) Math.round(v * 100.0)) + "%");
        } else if ("percentrange".equalsIgnoreCase(fmt)) {
            suffix = Component.literal(((int) Math.round(clamp01(this.value) * 100.0)) + "%");
        } else if ("pct".equalsIgnoreCase(fmt)) {
            suffix = Component.literal(((int) Math.round(v)) + "%");
        } else if ("ms".equalsIgnoreCase(fmt)) {
            suffix = Component.literal(((int) Math.round(v)) + "ms");
        } else if ("hz".equalsIgnoreCase(fmt)) {
            suffix = Component.literal(((int) Math.round(v)) + "Hz");
        } else if ("db".equalsIgnoreCase(fmt)) {
            suffix = Component.literal(((int) Math.round(v)) + "dB");
        } else if ("int".equalsIgnoreCase(fmt)) {
            suffix = Component.literal(Integer.toString((int) Math.round(v)));
        } else {
            // Default: show a compact number.
            String s = (Math.abs(v) >= 10.0) ? String.format("%.0f", v) : String.format("%.2f", v);
            suffix = Component.literal(s);
        }

        this.setMessage(label.copy().append(": ").append(suffix));
    }

    @Override
    protected void applyValue() {
        double v = fromNormalized(this.value);
        setter.accept(v);
    }

    @Override
    @SuppressWarnings("null")
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Keep slider synced to external state unless the mouse is currently pressed.
        if (!Minecraft.getInstance().mouseHandler.isLeftPressed()) {
            double external = clamp(getter.getAsDouble(), this.min, this.max);
            double normalized = toNormalized(external);
            if (Math.abs(normalized - this.value) > 1e-6) {
                this.value = normalized;
                updateMessage();
            }
        }

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

    private double fromNormalized(double normalized) {
        double v = min + (max - min) * clamp01(normalized);
        if (step > 0) {
            v = min + Math.round((v - min) / step) * step;
        }
        return clamp(v, min, max);
    }

    private double toNormalized(double real) {
        double t = (real - min) / (max - min);
        return clamp01(t);
    }

    private static float step(float current, float target, float speed) {
        return current + (target - current) * clamp01(speed);
    }

    private static float clamp01(float v) {
        if (v < 0.0f) return 0.0f;
        if (v > 1.0f) return 1.0f;
        return v;
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static float lerp(float a, float b, float t) {
        if (t <= 0.0f) return a;
        if (t >= 1.0f) return b;
        return a + (b - a) * t;
    }

    // (double clamp01 moved above for percentRange formatting)

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
