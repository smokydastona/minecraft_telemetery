package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioDeviceUtil;
import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.client.audio.MinecraftSoundDeviceUtil;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonStyle;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonVolumeSlider;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TelemetryConfigScreen extends Screen {
    private final Screen parent;

    private List<String> gameSoundDevices = List.of("<Default>");
    private String selectedGameSoundDevice = "<Default>";
    private boolean gameSoundDeviceDirty = false;
    private Button gameSoundDeviceButton;

    private List<String> devices = List.of("<Default>");
    private String selectedDevice = "<Default>";
    private boolean outputDeviceDirty = false;
    private Button outputDeviceButton;

    private NeonVolumeSlider volumeSlider;

    public TelemetryConfigScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        NeonStyle.initClient();

        int centerX = this.width / 2;
        var font = Objects.requireNonNull(this.font, "font");

        int contentWidth = Math.min(310, this.width - 40);
        int leftX = centerX - (contentWidth / 2);
        int rowH = 20;
        int rowGap = 6;

        List<String> gameDeviceList = new ArrayList<>();
        gameDeviceList.add("<Default>");
        gameDeviceList.addAll(MinecraftSoundDeviceUtil.listAvailableOutputDevices());
        this.gameSoundDevices = gameDeviceList;

        if (!gameSoundDeviceDirty) {
            String current = MinecraftSoundDeviceUtil.getSelectedSoundDeviceId();
            String currentDisplay = (current == null || current.isBlank()) ? "<Default>" : current;
            if (!this.gameSoundDevices.contains(currentDisplay)) currentDisplay = "<Default>";
            this.selectedGameSoundDevice = currentDisplay;
        } else {
            // Returning from GameSoundDeviceScreen triggers a re-init; keep the in-progress selection.
            if (!this.gameSoundDevices.contains(this.selectedGameSoundDevice)) {
                this.selectedGameSoundDevice = "<Default>";
            }
        }

        List<String> deviceList = new ArrayList<>();
        deviceList.add("<Default>");
        deviceList.addAll(AudioDeviceUtil.listOutputDeviceNames(AudioOutputEngine.get().formatStereo()));
        this.devices = deviceList;

        if (!outputDeviceDirty) {
            String current = BstConfig.get().outputDeviceName;
            String currentDisplay = AudioDeviceUtil.resolveDisplayName(current, AudioOutputEngine.get().formatStereo());
            if (!this.devices.contains(currentDisplay)) currentDisplay = "<Default>";
            this.selectedDevice = currentDisplay;
        } else {
            // Returning from OutputDeviceScreen triggers a re-init; keep the in-progress selection.
            if (!this.devices.contains(this.selectedDevice)) {
                this.selectedDevice = "<Default>";
            }
        }

        this.addRenderableWidget(new StringWidget(
                centerX - 100,
                20,
                200,
                20,
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.title")),
                font
        ));

        gameSoundDeviceButton = UiTooltip.withLabelKey(new NeonButton(
                leftX,
                50,
                contentWidth,
                rowH,
                gameSoundDeviceButtonLabel(),
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new GameSoundDeviceScreen(this, this::setSelectedGameSoundDevice, this.selectedGameSoundDevice));
                    }
                }
        ), "bassshakertelemetry.config.game_sound_device");
        this.addRenderableWidget(gameSoundDeviceButton);

        outputDeviceButton = UiTooltip.withLabelKey(new NeonButton(
                leftX,
                50 + rowH + rowGap,
                contentWidth,
                rowH,
                deviceButtonLabel(),
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new OutputDeviceScreen(this, this::setSelectedDevice, this.selectedDevice));
                    }
                }
        ), "bassshakertelemetry.config.output_device");
        this.addRenderableWidget(outputDeviceButton);

        int y = 50 + ((rowH + rowGap) * 2);

        volumeSlider = UiTooltip.withLabelKey(new NeonVolumeSlider(
                leftX,
                y,
                contentWidth,
                rowH,
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.master_volume")),
                BstConfig.get().masterVolume
        ), "bassshakertelemetry.config.master_volume");
        this.addRenderableWidget(volumeSlider);

        y += rowH + rowGap;

        int colGap = 10;
        int colWidth = Math.max(60, (contentWidth - colGap) / 2);
        int colLeftX = leftX;
        int colRightX = leftX + colWidth + colGap;

        this.addRenderableWidget(UiTooltip.withLabelKey(new NeonButton(
                colLeftX,
                y,
                colWidth,
                rowH,
                Component.translatable("bassshakertelemetry.config.page_damage"),
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new DamageSettingsScreen(this));
                    }
                }
        ), "bassshakertelemetry.config.page_damage"));

        this.addRenderableWidget(UiTooltip.withLabelKey(new NeonButton(
                colRightX,
                y,
                colWidth,
                rowH,
                Component.translatable("bassshakertelemetry.config.page_movement"),
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new MovementSettingsScreen(this));
                    }
                }
        ), "bassshakertelemetry.config.page_movement"));

        y += rowH + rowGap;

        this.addRenderableWidget(UiTooltip.withLabelKey(new NeonButton(
                colLeftX,
                y,
                colWidth,
                rowH,
                Component.translatable("bassshakertelemetry.config.page_misc"),
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new MiscSettingsScreen(this));
                    }
                }
        ), "bassshakertelemetry.config.page_misc"));

        this.addRenderableWidget(UiTooltip.withLabelKey(new NeonButton(
                colRightX,
                y,
                colWidth,
                rowH,
                Component.translatable("bassshakertelemetry.config.page_advanced"),
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new AdvancedSettingsScreen(this));
                    }
                }
        ), "bassshakertelemetry.config.page_advanced"));

        this.addRenderableWidget(UiTooltip.withLabelKey(new NeonButton(
                leftX,
                this.height - 28,
                (contentWidth - 10) / 2,
                20,
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.done")),
                this::onDone
        ), "bassshakertelemetry.config.done"));

        this.addRenderableWidget(UiTooltip.withLabelKey(new NeonButton(
                leftX + ((contentWidth - 10) / 2) + 10,
                this.height - 28,
                (contentWidth - 10) / 2,
                20,
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cancel")),
                this::onCancel
        ), "bassshakertelemetry.config.cancel"));
    }

    private void onDone() {
        BstConfig.Data data = BstConfig.get();
        data.outputDeviceName = "<Default>".equals(selectedDevice) ? "" : selectedDevice;
        data.masterVolume = volumeSlider.getValue();
        BstConfig.set(data);

        // Apply Minecraft game sound device (if changed) on Done.
        if (gameSoundDeviceDirty) {
            String deviceId = "<Default>".equals(selectedGameSoundDevice) ? "" : selectedGameSoundDevice;
            MinecraftSoundDeviceUtil.applySelectedSoundDeviceId(deviceId);
        }

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
    public void renderBackground(@Nonnull GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, NeonStyle.get().background);
    }

    private Component deviceButtonLabel() {
        return Component.translatable("bassshakertelemetry.config.output_device")
                .append(": ")
                .append(Objects.requireNonNull(selectedDevice));
    }

    private Component gameSoundDeviceButtonLabel() {
        return Component.translatable("bassshakertelemetry.config.game_sound_device")
                .append(": ")
                .append(Objects.requireNonNull(selectedGameSoundDevice));
    }

    void setSelectedGameSoundDevice(String displayDeviceId) {
        String v = Objects.requireNonNullElse(displayDeviceId, "<Default>");
        if (!gameSoundDevices.contains(v)) {
            v = "<Default>";
        }
        this.selectedGameSoundDevice = v;
        this.gameSoundDeviceDirty = true;

        if (gameSoundDeviceButton != null) {
            gameSoundDeviceButton.setMessage(Objects.requireNonNull(gameSoundDeviceButtonLabel()));
        }
    }

    void setSelectedDevice(String displayDeviceId) {
        String v = Objects.requireNonNullElse(displayDeviceId, "<Default>");
        if (!devices.contains(v)) {
            v = "<Default>";
        }
        this.selectedDevice = v;
        this.outputDeviceDirty = true;

        if (outputDeviceButton != null) {
            outputDeviceButton.setMessage(Objects.requireNonNull(deviceButtonLabel()));
        }
    }
}
