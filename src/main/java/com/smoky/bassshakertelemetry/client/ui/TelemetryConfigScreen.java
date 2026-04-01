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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TelemetryConfigScreen extends Screen {
    private final Screen parent;

    private Button gameSoundDeviceButton;
    private List<String> gameSoundDevices = List.of("<Default>");
    private String selectedGameSoundDevice = "<Default>";
    private boolean gameSoundDeviceDirty = false;

    private Button outputDeviceButton;
    private List<String> devices = List.of("<Default>");
    private String selectedDevice = "<Default>";
    private boolean outputDeviceDirty = false;

    private NeonVolumeSlider volumeSlider;

    public TelemetryConfigScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.config.title"));
        this.parent = parent;
    }

    @Override
    @SuppressWarnings("null")
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

        gameSoundDeviceButton = new NeonButton(
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
        );

        this.addRenderableWidget(gameSoundDeviceButton);

        outputDeviceButton = new NeonButton(
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
        );

        this.addRenderableWidget(outputDeviceButton);

        int y = 50 + ((rowH + rowGap) * 2);

        volumeSlider = new NeonVolumeSlider(
            leftX,
            y,
            contentWidth,
            rowH,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.master_volume")),
            BstConfig.get().masterVolume
        );
        this.addRenderableWidget(volumeSlider);

        y += rowH + rowGap;

        int colGap = 10;
        int colWidth = Math.max(60, (contentWidth - colGap) / 2);
        int colLeftX = leftX;
        int colRightX = leftX + colWidth + colGap;

        this.addRenderableWidget(new NeonButton(
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
        ));

        this.addRenderableWidget(new NeonButton(
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
        ));

        y += rowH + rowGap;

        this.addRenderableWidget(new NeonButton(
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
        ));

        this.addRenderableWidget(new NeonButton(
                colRightX,
                y,
                colWidth,
                rowH,
                Component.translatable("bassshakertelemetry.config.page_advanced"),
                () -> {
                    if (this.minecraft != null) {
                        if (com.smoky.bassshakertelemetry.client.ui.neon.schema.NeonUiSchemaLoader.hasActiveScreen("advanced_settings")) {
                            this.minecraft.setScreen(new SchemaAdvancedSettingsScreen(this));
                        } else {
                            this.minecraft.setScreen(new AdvancedSettingsScreen(this));
                        }
                    }
                }
        ));

        this.addRenderableWidget(new NeonButton(
            leftX,
            this.height - 28,
            (contentWidth - 10) / 2,
            20,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.done")),
            this::onDone
        ));

        this.addRenderableWidget(new NeonButton(
            leftX + ((contentWidth - 10) / 2) + 10,
            this.height - 28,
            (contentWidth - 10) / 2,
            20,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cancel")),
            this::onCancel
        ));
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
    @SuppressWarnings("null")
    public void renderBackground(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, NeonStyle.get().background);
    }

    @SuppressWarnings("null")
    private Component deviceButtonLabel() {
        return Component.translatable("bassshakertelemetry.config.output_device")
                .append(": ")
                .append(Component.literal(Objects.requireNonNull(selectedDevice)));
    }

    @SuppressWarnings("null")
    private Component gameSoundDeviceButtonLabel() {
        return Component.translatable("bassshakertelemetry.config.game_sound_device")
                .append(": ")
                .append(Component.literal(Objects.requireNonNull(selectedGameSoundDevice)));
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
