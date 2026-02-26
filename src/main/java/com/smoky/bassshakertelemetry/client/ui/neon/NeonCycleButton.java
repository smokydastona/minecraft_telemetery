package com.smoky.bassshakertelemetry.client.ui.neon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class NeonCycleButton<T> extends AbstractWidget {
    private final List<T> values;
    private int index;

    private float hoverT;
    private float pressT;

    private final Component label;
    private final Function<T, Component> formatter;
    private final Consumer<T> onValueChanged;

    public NeonCycleButton(
            int x,
            int y,
            int width,
            int height,
            Component label,
            List<T> values,
            T initial,
            Function<T, Component> formatter,
            Consumer<T> onValueChanged
    ) {
        super(x, y, width, height, Objects.requireNonNull(label));
        this.label = Objects.requireNonNull(label);
        this.values = List.copyOf(Objects.requireNonNull(values));
        this.formatter = Objects.requireNonNull(formatter);
        this.onValueChanged = Objects.requireNonNull(onValueChanged);

        this.index = Math.max(0, this.values.indexOf(initial));
        if (this.index >= this.values.size()) this.index = 0;

        syncMessage();
    }

    public T getValue() {
        return values.get(clampIndex(index));
    }

    public void setValue(T v) {
        int i = values.indexOf(v);
        if (i < 0) return;
        index = i;
        syncMessage();
    }

    private void cycleForward() {
        if (values.isEmpty()) return;
        index = (clampIndex(index) + 1) % values.size();
        onValueChanged.accept(getValue());
        syncMessage();
    }

    private void cycleBackward() {
        if (values.isEmpty()) return;
        index = (clampIndex(index) - 1 + values.size()) % values.size();
        onValueChanged.accept(getValue());
        syncMessage();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible) return false;
        if (!this.isMouseOver(mouseX, mouseY)) return false;

        // Focus for keyboard cycling.
        this.setFocused(true);

        // Arrow hitboxes for directional intent.
        NeonStyle style = NeonStyle.get();
        int pad = Math.max(2, style.paddingPx);
        int arrowSize = Math.max(8, Math.min(this.height - (pad * 2), style.cycleArrowSizePx));
        int leftX = this.getX() + pad;
        int rightX = this.getX() + this.width - arrowSize - pad;

        boolean inLeftArrow = mouseX >= leftX && mouseX <= leftX + arrowSize;
        boolean inRightArrow = mouseX >= rightX && mouseX <= rightX + arrowSize;

        if (button == 1) {
            // Right click cycles backward everywhere.
            cycleBackward();
            return true;
        }

        if (inLeftArrow) {
            cycleBackward();
            return true;
        }
        if (inRightArrow) {
            cycleForward();
            return true;
        }

        // Left click cycles forward elsewhere.
        cycleForward();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.active || !this.visible) return false;
        if (!this.isFocused()) return false;

        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            cycleBackward();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            cycleForward();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            cycleForward();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    @SuppressWarnings("null")
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        NeonStyle style = NeonStyle.get();
        NeonAnimationTokens anim = style.anim;

        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;

        boolean hovered = this.isHoveredOrFocused();
        boolean focused = this.isFocused();

        boolean pressed = hovered && (
            Minecraft.getInstance().mouseHandler.isLeftPressed() || Minecraft.getInstance().mouseHandler.isRightPressed()
        );

        hoverT = step(hoverT, hovered ? 1.0f : 0.0f, hovered ? anim.hoverSpeed : anim.idleSpeed);
        pressT = step(pressT, pressed ? 1.0f : 0.0f, pressed ? anim.pressSpeed : anim.idleSpeed);

        int border = NeonColorUtil.lerp(style.primary, style.primaryHover, hoverT);
        int bg = NeonColorUtil.lerp(style.panel, style.primaryPressed, pressT);
        bg = NeonColorUtil.mulRgb(bg, lerp(anim.brightnessIdle, anim.brightnessHover, hoverT));

        if (!this.active) {
            bg = NeonColorUtil.withAlpha(bg, 180);
            border = NeonColorUtil.withAlpha(border, 180);
        }

        // Frame.
        guiGraphics.fill(x, y, x + w, y + h, bg);
        guiGraphics.fill(x, y, x + w, y + 1, border);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, border);
        guiGraphics.fill(x, y, x + 1, y + h, border);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, border);

        if (focused) {
            int focus = NeonColorUtil.withAlpha(style.accent, 180);
            guiGraphics.fill(x + 1, y + 1, x + w - 1, y + 2, focus);
            guiGraphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, focus);
            guiGraphics.fill(x + 1, y + 1, x + 2, y + h - 1, focus);
            guiGraphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, focus);
        }

        int pad = Math.max(2, style.paddingPx);
        int arrowSize = Math.max(8, Math.min(h - (pad * 2), style.cycleArrowSizePx));
        int leftX = x + pad;
        int rightX = x + w - arrowSize - pad;
        int arrowY = y + (h - arrowSize) / 2;

        boolean inLeftArrow = mouseX >= leftX && mouseX <= leftX + arrowSize && mouseY >= arrowY && mouseY <= arrowY + arrowSize;
        boolean inRightArrow = mouseX >= rightX && mouseX <= rightX + arrowSize && mouseY >= arrowY && mouseY <= arrowY + arrowSize;

        float focusedArrowT = focused ? (hoverT * 0.35f) : 0.0f;
        float leftArrowT = this.active ? (inLeftArrow ? hoverT : focusedArrowT) : 0.0f;
        float rightArrowT = this.active ? (inRightArrow ? hoverT : focusedArrowT) : 0.0f;

        // Slight extra punch if the control is currently pressed.
        if (this.active && pressed) {
            if (inLeftArrow || focused) leftArrowT = Math.min(1.0f, leftArrowT + (pressT * 0.35f));
            if (inRightArrow || focused) rightArrowT = Math.min(1.0f, rightArrowT + (pressT * 0.35f));
        }

        int baseGlow = NeonColorUtil.mulRgb(style.accent, lerp(anim.brightnessIdle, anim.brightnessHover, hoverT));
        int leftGlow = NeonColorUtil.withAlpha(baseGlow, (int) (30 + (150 * leftArrowT)));
        int rightGlow = NeonColorUtil.withAlpha(baseGlow, (int) (30 + (150 * rightArrowT)));

        if (style.cycleArrowLeft != null && style.cycleArrowRight != null) {
            // Use textures if provided by the style pack.
            // Add a subtle “neon” glow behind the icons.
            if (leftArrowT > 0.01f) {
                drawArrowLeft(guiGraphics, leftX, arrowY, arrowSize, leftGlow);
                int faint = NeonColorUtil.withAlpha(leftGlow, (int) (0.45f * ((leftGlow >>> 24) & 0xFF)));
                drawArrowLeft(guiGraphics, leftX - 1, arrowY, arrowSize, faint);
                drawArrowLeft(guiGraphics, leftX + 1, arrowY, arrowSize, faint);
                drawArrowLeft(guiGraphics, leftX, arrowY - 1, arrowSize, faint);
                drawArrowLeft(guiGraphics, leftX, arrowY + 1, arrowSize, faint);
            }
            if (rightArrowT > 0.01f) {
                drawArrowRight(guiGraphics, rightX, arrowY, arrowSize, rightGlow);
                int faint = NeonColorUtil.withAlpha(rightGlow, (int) (0.45f * ((rightGlow >>> 24) & 0xFF)));
                drawArrowRight(guiGraphics, rightX - 1, arrowY, arrowSize, faint);
                drawArrowRight(guiGraphics, rightX + 1, arrowY, arrowSize, faint);
                drawArrowRight(guiGraphics, rightX, arrowY - 1, arrowSize, faint);
                drawArrowRight(guiGraphics, rightX, arrowY + 1, arrowSize, faint);
            }

            guiGraphics.blit(style.cycleArrowLeft, leftX, arrowY, 0, 0, arrowSize, arrowSize, arrowSize, arrowSize);
            guiGraphics.blit(style.cycleArrowRight, rightX, arrowY, 0, 0, arrowSize, arrowSize, arrowSize, arrowSize);
        } else {
            int arrowColor = this.active ? border : style.textDim;
            drawArrowLeft(guiGraphics, leftX, arrowY, arrowSize, arrowColor);
            drawArrowRight(guiGraphics, rightX, arrowY, arrowSize, arrowColor);
        }

        Component valueText = formatter.apply(getValue());

        var font = Objects.requireNonNull(Minecraft.getInstance().font);
        int labelColor = this.active ? style.text : style.textDim;
        int valueColor = this.active ? style.accent : style.textDim;

        int valueWidth = font.width(valueText);
        int valueX = rightX - pad - valueWidth;
        int textY = y + (h - 8) / 2;

        // Label region starts after left arrow.
        int labelX = leftX + arrowSize + pad;
        int maxLabelW = Math.max(10, valueX - pad - labelX);
        String clipped = font.plainSubstrByWidth(label.getString(), maxLabelW);
        Component clippedLabel = Component.literal(clipped);

        guiGraphics.drawString(font, clippedLabel, labelX, textY, labelColor);
        guiGraphics.drawString(font, valueText, valueX, textY, valueColor);
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

    @Override
    @SuppressWarnings("null")
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, buildMessage());
        narrationElementOutput.add(NarratedElementType.USAGE, Component.literal("Left click: next, Right click: previous"));
    }

    @SuppressWarnings("null")
    private void syncMessage() {
        setMessage(buildMessage());
    }

    @SuppressWarnings("null")
    private Component buildMessage() {
        // Keep message in sync for accessibility and debugging.
        return label.copy().append(": ").append(formatter.apply(getValue()));
    }

    private int clampIndex(int i) {
        if (values.isEmpty()) return 0;
        if (i < 0) return 0;
        if (i >= values.size()) return values.size() - 1;
        return i;
    }

    private static void drawArrowLeft(GuiGraphics g, int x, int y, int size, int argb) {
        // Simple chevron: > mirrored. Use 1px lines via fill.
        int midY = y + (size / 2);
        int left = x + (size / 3);
        int right = x + (2 * size / 3);

        for (int i = 0; i < size / 3; i++) {
            // upper stroke
            g.fill(right - i, midY - i, right - i + 1, midY - i + 1, argb);
            // lower stroke
            g.fill(right - i, midY + i, right - i + 1, midY + i + 1, argb);
        }
        // tip
        g.fill(left, midY, left + 1, midY + 1, argb);
    }

    private static void drawArrowRight(GuiGraphics g, int x, int y, int size, int argb) {
        int midY = y + (size / 2);
        int left = x + (size / 3);
        int right = x + (2 * size / 3);

        for (int i = 0; i < size / 3; i++) {
            g.fill(left + i, midY - i, left + i + 1, midY - i + 1, argb);
            g.fill(left + i, midY + i, left + i + 1, midY + i + 1, argb);
        }
        g.fill(right, midY, right + 1, midY + 1, argb);
    }
}
