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
        super.init();
        int centerX = this.width / 2;
        var font = Objects.requireNonNull(this.font, "font");

        int contentWidth = Math.min(310, this.width - 40);
        int leftX = centerX - (contentWidth / 2);
        int rowH = 20;
        int rowGap = 6;

        List<String> devices = new ArrayList<>();
        devices.add("<Default>");
        devices.addAll(AudioDeviceUtil.listOutputDeviceNames(AudioOutputEngine.get().format()));

        String current = BstConfig.get().outputDeviceName;
        String currentDisplay = AudioDeviceUtil.resolveDisplayName(current, AudioOutputEngine.get().format());
        if (!devices.contains(currentDisplay)) {
            currentDisplay = "<Default>";
        }

        this.addRenderableWidget(new StringWidget(
            centerX - 100,
            20,
            200,
            20,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.title")),
            font
        ));

        deviceCycle = CycleButton.<String>builder(s -> Component.literal(Objects.requireNonNullElse(s, "")))
                .withValues(devices)
                .withInitialValue(currentDisplay)
            .create(leftX, 50, contentWidth, rowH, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.output_device")));
        this.addRenderableWidget(deviceCycle);

        int y = 50 + rowH + rowGap;

        volumeSlider = new VolumeSlider(leftX, y, contentWidth, rowH, BstConfig.get().masterVolume);
        this.addRenderableWidget(volumeSlider);

        y += rowH + rowGap;
        int colGap = 10;
        int colWidth = Math.max(60, (contentWidth - colGap) / 2);
        int colLeftX = leftX;
        int colRightX = leftX + colWidth + colGap;

        damageToggle = CycleButton.onOffBuilder(BstConfig.get().damageBurstEnabled)
            .create(colLeftX, y, colWidth, rowH, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.damage_enabled")));
        this.addRenderableWidget(damageToggle);

        biomeToggle = CycleButton.onOffBuilder(BstConfig.get().biomeChimeEnabled)
            .create(colRightX, y, colWidth, rowH, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.biome_enabled")));
        this.addRenderableWidget(biomeToggle);

        roadToggle = CycleButton.onOffBuilder(BstConfig.get().roadTextureEnabled)
            .create(colLeftX, y + rowH + rowGap, colWidth, rowH, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.road_enabled")));
        this.addRenderableWidget(roadToggle);

        y += rowH + rowGap;
        soundToggle = CycleButton.onOffBuilder(BstConfig.get().soundHapticsEnabled)
            .create(colRightX, y, colWidth, rowH, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.sound_enabled")));
        this.addRenderableWidget(soundToggle);

        gameplayToggle = CycleButton.onOffBuilder(BstConfig.get().gameplayHapticsEnabled)
            .create(leftX, y + rowH + rowGap, contentWidth, rowH, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.gameplay_enabled")));
        this.addRenderableWidget(gameplayToggle);

        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.done")), b -> onDone())
                .bounds(leftX, this.height - 28, (contentWidth - 10) / 2, 20)
                .build());

        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cancel")), b -> onCancel())
                .bounds(leftX + ((contentWidth - 10) / 2) + 10, this.height - 28, (contentWidth - 10) / 2, 20)
                .build());
    }

    private void onDone() {
        BstConfig.Data data = BstConfig.get();
        String selected = deviceCycle.getValue();
        data.outputDeviceName = "<Default>".equals(selected) ? "" : selected;
        data.masterVolume = volumeSlider.getValue();
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
