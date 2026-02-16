package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioDeviceUtil;
import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TelemetryConfigScreen extends Screen {
    private final Screen parent;

    private CycleButton<String> deviceCycle;
    private CycleButton<Boolean> speedToggle;
    private CycleButton<Boolean> damageToggle;
    private CycleButton<Boolean> biomeToggle;
    private CycleButton<Boolean> roadToggle;
    private CycleButton<Boolean> soundToggle;
    private CycleButton<Boolean> gameplayToggle;
    private VolumeSlider volumeSlider;

    public TelemetryConfigScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.config.title"));
        this.parent = parent;
    }

    @Override
    @SuppressWarnings("null")
    protected void init() {
        int centerX = this.width / 2;
        var font = Objects.requireNonNull(this.font, "font");

        List<String> devices = new ArrayList<>();
        devices.add("<Default>");
        devices.addAll(AudioDeviceUtil.listOutputDeviceNames(AudioOutputEngine.get().format()));

        String current = BstConfig.get().outputDeviceName;
        String currentDisplay = (current == null || current.isBlank()) ? "<Default>" : current;
        if (!devices.contains(currentDisplay)) {
            currentDisplay = "<Default>";
        }

        this.addRenderableWidget(new StringWidget(
            centerX,
            20,
            200,
            20,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.title")),
            font
        ));

        deviceCycle = CycleButton.<String>builder(s -> Component.literal(Objects.requireNonNullElse(s, "")))
                .withValues(devices)
                .withInitialValue(currentDisplay)
            .create(centerX - 155, 50, 310, 20, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.output_device")));
        this.addRenderableWidget(deviceCycle);

        volumeSlider = new VolumeSlider(centerX - 155, 80, 310, 20, BstConfig.get().masterVolume);
        this.addRenderableWidget(volumeSlider);

        speedToggle = CycleButton.onOffBuilder(BstConfig.get().speedToneEnabled)
            .create(centerX - 155, 110, 310, 20, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.speed_enabled")));
        this.addRenderableWidget(speedToggle);

        damageToggle = CycleButton.onOffBuilder(BstConfig.get().damageBurstEnabled)
            .create(centerX - 155, 140, 310, 20, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.damage_enabled")));
        this.addRenderableWidget(damageToggle);

        biomeToggle = CycleButton.onOffBuilder(BstConfig.get().biomeChimeEnabled)
            .create(centerX - 155, 170, 310, 20, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.biome_enabled")));
        this.addRenderableWidget(biomeToggle);

        roadToggle = CycleButton.onOffBuilder(BstConfig.get().roadTextureEnabled)
            .create(centerX - 155, 200, 310, 20, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.road_enabled")));
        this.addRenderableWidget(roadToggle);

        soundToggle = CycleButton.onOffBuilder(BstConfig.get().soundHapticsEnabled)
            .create(centerX - 155, 230, 310, 20, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.sound_enabled")));
        this.addRenderableWidget(soundToggle);

        gameplayToggle = CycleButton.onOffBuilder(BstConfig.get().gameplayHapticsEnabled)
            .create(centerX - 155, 260, 310, 20, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.gameplay_enabled")));
        this.addRenderableWidget(gameplayToggle);

        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.done")), b -> onDone())
                .bounds(centerX - 155, this.height - 28, 150, 20)
                .build());

        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cancel")), b -> onCancel())
                .bounds(centerX + 5, this.height - 28, 150, 20)
                .build());
    }

    private void onDone() {
        BstConfig.Data data = BstConfig.get();
        String selected = deviceCycle.getValue();
        data.outputDeviceName = "<Default>".equals(selected) ? "" : selected;
        data.masterVolume = volumeSlider.getValue();
        data.speedToneEnabled = speedToggle.getValue();
        data.damageBurstEnabled = damageToggle.getValue();
        data.biomeChimeEnabled = biomeToggle.getValue();
        data.roadTextureEnabled = roadToggle.getValue();
        data.soundHapticsEnabled = soundToggle.getValue();
        data.gameplayHapticsEnabled = gameplayToggle.getValue();
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

    private static final class VolumeSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        VolumeSlider(int x, int y, int width, int height, double initial) {
            super(x, y, width, height, Component.empty(), clamp01(initial));
            updateMessage();
        }

        @Override
        @SuppressWarnings("null")
        protected void updateMessage() {
            int pct = (int) Math.round(value * 100.0);
            this.setMessage(
                    Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.master_volume"))
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
