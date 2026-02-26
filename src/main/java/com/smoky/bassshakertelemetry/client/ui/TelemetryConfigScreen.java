package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioDeviceUtil;
import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
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

    private Button outputDeviceButton;
    private List<String> devices = List.of("<Default>");
    private String selectedDevice = "<Default>";

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

        List<String> deviceList = new ArrayList<>();
        deviceList.add("<Default>");
        deviceList.addAll(AudioDeviceUtil.listOutputDeviceNames(AudioOutputEngine.get().formatStereo()));
        this.devices = deviceList;

        String current = BstConfig.get().outputDeviceName;
        String currentDisplay = AudioDeviceUtil.resolveDisplayName(current, AudioOutputEngine.get().formatStereo());
        if (!this.devices.contains(currentDisplay)) currentDisplay = "<Default>";
        this.selectedDevice = currentDisplay;

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

        outputDeviceButton = new NeonButton(
                leftX,
                50,
                contentWidth,
                rowH,
                deviceButtonLabel(),
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new OutputDeviceScreen(this));
                    }
                }
        );

        this.addRenderableWidget(outputDeviceButton);

        int y = 50 + rowH + rowGap;

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
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.advanced")),
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new AdvancedSettingsScreen(this));
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
            this.height - 52,
            contentWidth,
            20,
            Component.literal("Reload UI bundle"),
            () -> {
                boolean ok = NeonStyle.reloadFromDiskBundleIfPresent();
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.displayClientMessage(
                        Component.literal(ok ? "UI bundle reloaded" : "UI bundle not found"),
                        true
                    );
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
        data.damageBurstEnabled = damageEnabled;
        data.biomeChimeEnabled = biomeEnabled;
        data.roadTextureEnabled = roadEnabled;
        data.soundHapticsEnabled = soundEnabled;
        data.gameplayHapticsEnabled = gameplayEnabled;
        data.accessibilityHudEnabled = accessibilityHudEnabled;
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

    void setSelectedDevice(String displayDeviceId) {
        String v = Objects.requireNonNullElse(displayDeviceId, "<Default>");
        if (!devices.contains(v)) {
            v = "<Default>";
        }
        this.selectedDevice = v;

        if (outputDeviceButton != null) {
            outputDeviceButton.setMessage(Objects.requireNonNull(deviceButtonLabel()));
        }
    }

}
