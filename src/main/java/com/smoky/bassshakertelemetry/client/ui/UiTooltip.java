package com.smoky.bassshakertelemetry.client.ui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

import java.util.Objects;

/**
 * Tiny helper to keep tooltip wiring consistent across config screens.
 */
public final class UiTooltip {
    private UiTooltip() {
    }

    @Nonnull
    public static <T extends AbstractWidget> T withKey(T widget, String tooltipKey) {
        Objects.requireNonNull(widget, "widget");
        if (tooltipKey == null || tooltipKey.isBlank()) return widget;
        widget.setTooltip(Tooltip.create(Objects.requireNonNull(Component.translatable(tooltipKey))));
        return widget;
    }

    @Nonnull
    public static <T extends AbstractWidget> T withLabelKey(T widget, String labelKey) {
        Objects.requireNonNull(widget, "widget");
        if (labelKey == null || labelKey.isBlank()) return widget;
        return withKey(widget, labelKey + ".tooltip");
    }

    @Nonnull
    public static <T extends AbstractWidget> T withText(T widget, Component tooltip) {
        Objects.requireNonNull(widget, "widget");
        if (tooltip == null) return widget;
        widget.setTooltip(Tooltip.create(Objects.requireNonNull(tooltip)));
        return widget;
    }
}
