package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonCycleButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonRangeSlider;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonStyle;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

/**
 * Phase 3 entry screen for spatial + multi-transducer settings.
 */
public final class SpatialConfigScreen extends Screen {
    private final Screen parent;

    private NeonCycleButton<Boolean> soundScapeToggle;
    private NeonCycleButton<Boolean> spatialToggle;
    private NeonRangeSlider distanceSlider;

    private double distanceAttenStrength;

    public SpatialConfigScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.spatial.title"));
        this.parent = parent;
    }

    @Override
    @SuppressWarnings("null")
    protected void init() {
        super.init();
        NeonStyle.initClient();

        int centerX = this.width / 2;
        int contentWidth = Math.min(310, this.width - 40);
        int leftX = centerX - (contentWidth / 2);

        int rowH = 20;
        int rowGap = 6;

        var font = Objects.requireNonNull(this.font, "font");

        this.addRenderableWidget(new StringWidget(
            centerX - 140,
            20,
            280,
            20,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial.title")),
            font
        ));

        int y = 50;

        soundScapeToggle = new NeonCycleButton<>(
            leftX,
            y,
            contentWidth,
            rowH,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial.soundscape_enabled")),
            List.of(Boolean.TRUE, Boolean.FALSE),
            BstConfig.get().soundScapeEnabled,
            v -> v ? Component.translatable("options.on") : Component.translatable("options.off"),
            v -> {
            }
        );
        this.addRenderableWidget(soundScapeToggle);

        y += rowH + rowGap;

        spatialToggle = new NeonCycleButton<>(
            leftX,
            y,
            contentWidth,
            rowH,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial.panning_enabled")),
            List.of(Boolean.TRUE, Boolean.FALSE),
            BstConfig.get().soundScapeSpatialEnabled,
            v -> v ? Component.translatable("options.on") : Component.translatable("options.off"),
            v -> {
            }
        );
        this.addRenderableWidget(spatialToggle);

        y += rowH + rowGap;

        distanceAttenStrength = BstConfig.get().soundScapeSpatialDistanceAttenStrength;
        distanceSlider = new NeonRangeSlider(
            leftX,
            y,
            contentWidth,
            rowH,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial.distance_atten")),
            0.0,
            1.0,
            0.01,
            "percentRange",
            () -> distanceAttenStrength,
            v -> distanceAttenStrength = v
        );
        this.addRenderableWidget(distanceSlider);

        y += rowH + rowGap;

        this.addRenderableWidget(new NeonButton(
            leftX,
            y,
            contentWidth,
            rowH,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial.open_routing")),
            () -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new SoundScapeConfigScreen(this));
                }
            }
        ));

        y += rowH + rowGap;

        this.addRenderableWidget(new NeonButton(
            leftX,
            y,
            contentWidth,
            rowH,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial.open_bus_routing")),
            () -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new SpatialBusRoutingScreen(this));
                }
            }
        ));

        y += rowH + rowGap;

        this.addRenderableWidget(new NeonButton(
            leftX,
            y,
            contentWidth,
            rowH,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial.open_calibration")),
            () -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new SpatialCalibrationScreen(this));
                }
            }
        ));

        y += rowH + rowGap;

        this.addRenderableWidget(new NeonButton(
            leftX,
            y,
            contentWidth,
            rowH,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial.open_debugger")),
            () -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new SpatialDebuggerScreen(this));
                }
            }
        ));

        int buttonW = (contentWidth - 10) / 2;
        this.addRenderableWidget(new NeonButton(leftX, this.height - 28, buttonW, 20, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.done")), this::onDone));
        this.addRenderableWidget(new NeonButton(leftX + buttonW + 10, this.height - 28, buttonW, 20, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cancel")), this::onCancel));
    }

    private void onDone() {
        BstConfig.Data data = BstConfig.get();
        data.soundScapeEnabled = soundScapeToggle.getValue();
        data.soundScapeSpatialEnabled = spatialToggle.getValue();
        data.soundScapeSpatialDistanceAttenStrength = distanceAttenStrength;
        BstConfig.set(data);

        AudioOutputEngine.get().startOrRestart();

        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void onCancel() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
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
}
