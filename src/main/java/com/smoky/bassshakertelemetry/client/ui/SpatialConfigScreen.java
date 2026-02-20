package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Phase 3 entry screen for spatial + multi-transducer settings.
 */
public final class SpatialConfigScreen extends Screen {
    private final Screen parent;

    private CycleButton<Boolean> soundScapeToggle;
    private CycleButton<Boolean> spatialToggle;
    private DistanceAttenSlider distanceSlider;

    public SpatialConfigScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.spatial.title"));
        this.parent = parent;
    }

    @Override
    @SuppressWarnings("null")
    protected void init() {
        super.init();

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

        soundScapeToggle = CycleButton.onOffBuilder(BstConfig.get().soundScapeEnabled)
            .create(leftX, y, contentWidth, rowH, Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial.soundscape_enabled")));
        this.addRenderableWidget(soundScapeToggle);

        y += rowH + rowGap;

        spatialToggle = CycleButton.onOffBuilder(BstConfig.get().soundScapeSpatialEnabled)
            .create(leftX, y, contentWidth, rowH, Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial.panning_enabled")));
        this.addRenderableWidget(spatialToggle);

        y += rowH + rowGap;

        distanceSlider = new DistanceAttenSlider(leftX, y, contentWidth, rowH, BstConfig.get().soundScapeSpatialDistanceAttenStrength);
        this.addRenderableWidget(distanceSlider);

        y += rowH + rowGap;

        this.addRenderableWidget(Button.builder(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial.open_routing")),
                b -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new SoundScapeConfigScreen(this));
                    }
                })
            .bounds(leftX, y, contentWidth, rowH)
            .build());

        y += rowH + rowGap;

        this.addRenderableWidget(Button.builder(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial.open_bus_routing")),
                b -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new SpatialBusRoutingScreen(this));
                    }
                })
            .bounds(leftX, y, contentWidth, rowH)
            .build());

        y += rowH + rowGap;

        this.addRenderableWidget(Button.builder(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial.open_calibration")),
                b -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new SpatialCalibrationScreen(this));
                    }
                })
            .bounds(leftX, y, contentWidth, rowH)
            .build());

        y += rowH + rowGap;

        this.addRenderableWidget(Button.builder(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial.open_debugger")),
                b -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new SpatialDebuggerScreen(this));
                    }
                })
            .bounds(leftX, y, contentWidth, rowH)
            .build());

        int buttonW = (contentWidth - 10) / 2;
        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.done")), b -> onDone())
            .bounds(leftX, this.height - 28, buttonW, 20)
            .build());

        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cancel")), b -> onCancel())
            .bounds(leftX + buttonW + 10, this.height - 28, buttonW, 20)
            .build());
    }

    private void onDone() {
        BstConfig.Data data = BstConfig.get();
        data.soundScapeEnabled = soundScapeToggle.getValue();
        data.soundScapeSpatialEnabled = spatialToggle.getValue();
        data.soundScapeSpatialDistanceAttenStrength = distanceSlider.getValue();
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

    private static final class DistanceAttenSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        DistanceAttenSlider(int x, int y, int width, int height, double initial01) {
            super(x, y, width, height, Component.empty(), clamp01(initial01));
            updateMessage();
        }

        @Override
        @SuppressWarnings("null")
        protected void updateMessage() {
            int pct = (int) Math.round(value * 100.0);
            this.setMessage(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial.distance_atten"))
                    .append(": ")
                    .append(Objects.requireNonNull(Component.literal(pct + "%")))
            );
        }

        @Override
        protected void applyValue() {
        }

        double getValue() {
            return clamp01(this.value);
        }

        private static double clamp01(double v) {
            if (v < 0.0) return 0.0;
            if (v > 1.0) return 1.0;
            return v;
        }
    }
}
