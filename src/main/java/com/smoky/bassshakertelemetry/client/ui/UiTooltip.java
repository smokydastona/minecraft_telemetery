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

    private static boolean shouldSkipLabelKey(String labelKey) {
        // These buttons are self-explanatory; tooltips add noise.
        return switch (labelKey) {
            case "bassshakertelemetry.config.page_prev",
                 "bassshakertelemetry.config.page_next",
                 "bassshakertelemetry.config.done",
                 "bassshakertelemetry.config.cancel",
                 "gui.done",
                 "gui.cancel" -> true;
            default -> false;
        };
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
        if (shouldSkipLabelKey(labelKey)) return widget;
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
