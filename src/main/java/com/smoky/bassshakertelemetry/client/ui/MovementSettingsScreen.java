package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonCycleButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonRangeSlider;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonStyle;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

public final class MovementSettingsScreen extends Screen {
    private final Screen parent;

    private boolean movementEnabled;
    private double flightGain;
    private double airGain;
    private double swimGain;
    private double waterGain;

    private boolean footstepsEnabled;
    private double footstepsGain;

    private boolean mountedEnabled;
    private double mountedGain;

    private NeonRangeSlider flightSlider;
    private NeonRangeSlider airSlider;
    private NeonRangeSlider swimSlider;
    private NeonRangeSlider waterSlider;

    private NeonRangeSlider mountedGainSlider;

    public MovementSettingsScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.config.movement_title"));
        this.parent = parent;

        BstConfig.Data cfg = BstConfig.get();
        this.movementEnabled = cfg.roadTextureEnabled;
        this.flightGain = clamp01(cfg.movementFlightGain);
        this.airGain = clamp01(cfg.movementAirGain);
        this.swimGain = clamp01(cfg.movementSwimGain);
        this.waterGain = clamp01(cfg.movementWaterGain);

        this.footstepsEnabled = cfg.footstepHapticsEnabled;
        this.footstepsGain = clamp01(cfg.footstepHapticsGain);

        this.mountedEnabled = cfg.mountedHapticsEnabled;
        this.mountedGain = clamp01(cfg.mountedHapticsGain);
    }

    @Override
    protected void init() {
        Font font = Objects.requireNonNull(this.font, "font");

        int contentWidth = Math.min(320, this.width - 40);
        int leftX = (this.width - contentWidth) / 2;

        int rowH = 20;
        int rowGap = 6;

        this.addRenderableWidget(new StringWidget(
            leftX,
            18,
            contentWidth,
            20,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.movement_title")),
            font
        ));

        int y = 46;

        this.addRenderableWidget(UiTooltip.withLabelKey(new NeonCycleButton<>(
            leftX,
            y,
            contentWidth,
            rowH,
            Component.translatable("bassshakertelemetry.config.movement_enabled"),
            List.of(Boolean.TRUE, Boolean.FALSE),
            movementEnabled,
            v -> v ? Component.translatable("options.on") : Component.translatable("options.off"),
            v -> movementEnabled = v
        ), "bassshakertelemetry.config.movement_enabled"));

        y += rowH + (rowGap * 2);

        int sliderW = contentWidth;
        flightSlider = UiTooltip.withLabelKey(new NeonRangeSlider(
            leftX,
            y,
            sliderW,
            rowH,
            Component.translatable("bassshakertelemetry.config.movement_flight"),
            0.0,
            1.0,
            0.01,
            "percent",
            () -> flightGain,
            v -> flightGain = clamp01(v)
        ), "bassshakertelemetry.config.movement_flight");
        this.addRenderableWidget(flightSlider);

        y += rowH + rowGap;

        airSlider = UiTooltip.withLabelKey(new NeonRangeSlider(
            leftX,
            y,
            sliderW,
            rowH,
            Component.translatable("bassshakertelemetry.config.movement_air"),
            0.0,
            1.0,
            0.01,
            "percent",
            () -> airGain,
            v -> airGain = clamp01(v)
        ), "bassshakertelemetry.config.movement_air");
        this.addRenderableWidget(airSlider);

        y += rowH + rowGap;

        swimSlider = UiTooltip.withLabelKey(new NeonRangeSlider(
            leftX,
            y,
            sliderW,
            rowH,
            Component.translatable("bassshakertelemetry.config.movement_swim"),
            0.0,
            1.0,
            0.01,
            "percent",
            () -> swimGain,
            v -> swimGain = clamp01(v)
        ), "bassshakertelemetry.config.movement_swim");
        this.addRenderableWidget(swimSlider);

        y += rowH + rowGap;

        waterSlider = UiTooltip.withLabelKey(new NeonRangeSlider(
            leftX,
            y,
            sliderW,
            rowH,
            Component.translatable("bassshakertelemetry.config.movement_water"),
            0.0,
            1.0,
            0.01,
            "percent",
            () -> waterGain,
            v -> waterGain = clamp01(v)
        ), "bassshakertelemetry.config.movement_water");
        this.addRenderableWidget(waterSlider);

        y += rowH + (rowGap * 2);

        this.addRenderableWidget(UiTooltip.withLabelKey(new NeonCycleButton<>(
            leftX,
            y,
            contentWidth,
            rowH,
            Component.translatable("bassshakertelemetry.config.footsteps_enabled"),
            List.of(Boolean.TRUE, Boolean.FALSE),
            footstepsEnabled,
            v -> v ? Component.translatable("options.on") : Component.translatable("options.off"),
            v -> footstepsEnabled = v
        ), "bassshakertelemetry.config.footsteps_enabled"));

        y += rowH + rowGap;

        this.addRenderableWidget(UiTooltip.withLabelKey(new NeonRangeSlider(
            leftX,
            y,
            sliderW,
            rowH,
            Component.translatable("bassshakertelemetry.config.footstep_gain"),
            0.0,
            1.0,
            0.01,
            "percent",
            () -> footstepsGain,
            v -> footstepsGain = clamp01(v)
        ), "bassshakertelemetry.config.footstep_gain"));

        y += rowH + (rowGap * 2);

        this.addRenderableWidget(UiTooltip.withLabelKey(new NeonCycleButton<>(
            leftX,
            y,
            contentWidth,
            rowH,
            Component.translatable("bassshakertelemetry.config.mounted_enabled"),
            List.of(Boolean.TRUE, Boolean.FALSE),
            mountedEnabled,
            v -> v ? Component.translatable("options.on") : Component.translatable("options.off"),
            v -> mountedEnabled = v
        ), "bassshakertelemetry.config.mounted_enabled"));

        y += rowH + rowGap;

        mountedGainSlider = UiTooltip.withLabelKey(new NeonRangeSlider(
            leftX,
            y,
            sliderW,
            rowH,
            Component.translatable("bassshakertelemetry.config.mounted_gain"),
            0.0,
            1.0,
            0.01,
            "percent",
            () -> mountedGain,
            v -> mountedGain = clamp01(v)
        ), "bassshakertelemetry.config.mounted_gain");
        this.addRenderableWidget(mountedGainSlider);

        int buttonW = (contentWidth - 10) / 2;
        this.addRenderableWidget(UiTooltip.withLabelKey(new NeonButton(
            leftX,
            this.height - 28,
            buttonW,
            20,
            Component.translatable("bassshakertelemetry.config.done"),
            this::onDone
        ), "bassshakertelemetry.config.done"));

        this.addRenderableWidget(UiTooltip.withLabelKey(new NeonButton(
            leftX + buttonW + 10,
            this.height - 28,
            buttonW,
            20,
            Component.translatable("bassshakertelemetry.config.cancel"),
            this::onCancel
        ), "bassshakertelemetry.config.cancel"));
    }

    private void onDone() {
        BstConfig.Data data = BstConfig.get();
        data.roadTextureEnabled = movementEnabled;
        data.movementFlightGain = clamp01(flightGain);
        data.movementAirGain = clamp01(airGain);
        data.movementSwimGain = clamp01(swimGain);
        data.movementWaterGain = clamp01(waterGain);

        data.footstepHapticsEnabled = footstepsEnabled;
        data.footstepHapticsGain = clamp01(footstepsGain);

        data.mountedHapticsEnabled = mountedEnabled;
        data.mountedHapticsGain = clamp01(mountedGain);

        BstConfig.set(data);
        AudioOutputEngine.get().startOrRestart();

        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(parent);
        }
    }

    private void onCancel() {
        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        onCancel();
    }

    @Override
    @SuppressWarnings("null")
    public void renderBackground(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, NeonStyle.get().background);
    }

    @Override
    @SuppressWarnings("null")
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Disable movement sliders when the master switch is off.
        boolean active = movementEnabled;
        if (flightSlider != null) flightSlider.active = active;
        if (airSlider != null) airSlider.active = active;
        if (swimSlider != null) swimSlider.active = active;
        if (waterSlider != null) waterSlider.active = active;

        if (mountedGainSlider != null) mountedGainSlider.active = mountedEnabled;

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private static double clamp01(double v) {
        if (!Double.isFinite(v)) return 0.0;
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
