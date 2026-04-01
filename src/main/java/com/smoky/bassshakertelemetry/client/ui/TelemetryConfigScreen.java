package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioDeviceUtil;
import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.client.audio.MinecraftSoundDeviceUtil;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonCycleButton;
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

    private boolean damageEnabled;
    private boolean biomeEnabled;
    private boolean roadEnabled;
    private boolean soundEnabled;
    private boolean gameplayEnabled;
    private boolean accessibilityHudEnabled;

    private NeonCycleButton<Boolean> damageToggle;
    private NeonCycleButton<Boolean> biomeToggle;
    private NeonCycleButton<Boolean> roadToggle;
    private NeonCycleButton<Boolean> soundToggle;
    private NeonCycleButton<Boolean> gameplayToggle;
    private NeonCycleButton<Boolean> accessibilityHudToggle;
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

        damageEnabled = BstConfig.get().damageBurstEnabled;
        biomeEnabled = BstConfig.get().biomeChimeEnabled;
        roadEnabled = BstConfig.get().roadTextureEnabled;
        soundEnabled = BstConfig.get().soundHapticsEnabled;
        gameplayEnabled = BstConfig.get().gameplayHapticsEnabled;
        accessibilityHudEnabled = BstConfig.get().accessibilityHudEnabled;

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

        this.addRenderableWidget(new NeonButton(
                leftX,
                y,
                contentWidth,
                rowH,
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.movement_settings")),
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new MovementSettingsScreen(this));
                    }
                }
        ));

        y += rowH + rowGap;

        this.addRenderableWidget(new NeonButton(
                leftX,
                y,
                contentWidth,
                rowH,
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.advanced")),
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

        y += rowH + rowGap;

        this.addRenderableWidget(new NeonButton(
                leftX,
                y,
                contentWidth,
                rowH,
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.open")),
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new SoundScapeConfigScreen(this));
                    }
                }
        ));

        y += rowH + rowGap;

        int colGap = 10;
        int colWidth = Math.max(60, (contentWidth - colGap) / 2);
        int colLeftX = leftX;
        int colRightX = leftX + colWidth + colGap;

        damageToggle = new NeonCycleButton<>(
            colLeftX,
            y,
            colWidth,
            rowH,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.damage_enabled")),
            List.of(Boolean.TRUE, Boolean.FALSE),
            damageEnabled,
            v -> v ? Component.translatable("options.on") : Component.translatable("options.off"),
            v -> damageEnabled = v
        );
        this.addRenderableWidget(damageToggle);

        biomeToggle = new NeonCycleButton<>(
            colRightX,
            y,
            colWidth,
            rowH,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.biome_enabled")),
            List.of(Boolean.TRUE, Boolean.FALSE),
            biomeEnabled,
            v -> v ? Component.translatable("options.on") : Component.translatable("options.off"),
            v -> biomeEnabled = v
        );
        this.addRenderableWidget(biomeToggle);

        roadToggle = new NeonCycleButton<>(
            colLeftX,
            y + rowH + rowGap,
            colWidth,
            rowH,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.road_enabled")),
            List.of(Boolean.TRUE, Boolean.FALSE),
            roadEnabled,
            v -> v ? Component.translatable("options.on") : Component.translatable("options.off"),
            v -> roadEnabled = v
        );
        this.addRenderableWidget(roadToggle);

        y += rowH + rowGap;
        soundToggle = new NeonCycleButton<>(
            colRightX,
            y,
            colWidth,
            rowH,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.sound_enabled")),
            List.of(Boolean.TRUE, Boolean.FALSE),
            soundEnabled,
            v -> v ? Component.translatable("options.on") : Component.translatable("options.off"),
            v -> soundEnabled = v
        );
        this.addRenderableWidget(soundToggle);

        gameplayToggle = new NeonCycleButton<>(
            leftX,
            y + rowH + rowGap,
            contentWidth,
            rowH,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.gameplay_enabled")),
            List.of(Boolean.TRUE, Boolean.FALSE),
            gameplayEnabled,
            v -> v ? Component.translatable("options.on") : Component.translatable("options.off"),
            v -> gameplayEnabled = v
        );
        this.addRenderableWidget(gameplayToggle);

        accessibilityHudToggle = new NeonCycleButton<>(
            leftX,
            y + ((rowH + rowGap) * 2),
            contentWidth,
            rowH,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.accessibility_hud")),
            List.of(Boolean.TRUE, Boolean.FALSE),
            accessibilityHudEnabled,
            v -> v ? Component.translatable("options.on") : Component.translatable("options.off"),
            v -> accessibilityHudEnabled = v
        );
        this.addRenderableWidget(accessibilityHudToggle);

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
        data.damageBurstEnabled = damageEnabled;
        data.biomeChimeEnabled = biomeEnabled;
        data.roadTextureEnabled = roadEnabled;
        data.soundHapticsEnabled = soundEnabled;
        data.gameplayHapticsEnabled = gameplayEnabled;
        data.accessibilityHudEnabled = accessibilityHudEnabled;
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

    @Override
    @SuppressWarnings("null")
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        var font = this.font;
        if (font == null) return;

        int contentWidth = Math.min(310, this.width - 40);
        int leftX = (this.width / 2) - (contentWidth / 2);
        int y = this.height - 74;

        guiGraphics.drawString(
            font,
            uiBundleStatusLabel(),
            leftX,
            y,
            NeonStyle.get().textDim,
            false
        );
    }

    private static Component uiBundleStatusLabel() {
        return switch (NeonStyle.getActiveBundleSource()) {
            case DISK_OVERRIDE -> Component.literal("UI Bundle: Disk (override)");
            case DISK_REMOTE -> Component.literal("UI Bundle: Disk (remote)");
            case BUILT_IN -> Component.literal("UI Bundle: Built-in");
        };
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
