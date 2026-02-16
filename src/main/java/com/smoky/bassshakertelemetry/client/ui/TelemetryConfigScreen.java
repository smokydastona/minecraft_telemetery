package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioDeviceUtil;
import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TelemetryConfigScreen extends Screen {
    private final Screen parent;

    private Button deviceDropdownButton;
    private DeviceDropdownList deviceDropdownList;
    private List<String> devices = List.of("<Default>");
    private String selectedDevice = "<Default>";

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

        List<String> deviceList = new ArrayList<>();
        deviceList.add("<Default>");
        deviceList.addAll(AudioDeviceUtil.listOutputDeviceNames(AudioOutputEngine.get().format()));
        this.devices = deviceList;

        String current = BstConfig.get().outputDeviceName;
        String currentDisplay = AudioDeviceUtil.resolveDisplayName(current, AudioOutputEngine.get().format());
        if (!this.devices.contains(currentDisplay)) currentDisplay = "<Default>";
        this.selectedDevice = currentDisplay;

        this.addRenderableWidget(new StringWidget(
            centerX - 100,
            20,
            200,
            20,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.title")),
            font
        ));

        deviceDropdownButton = Button.builder(deviceDropdownLabel(), b -> toggleDeviceDropdown(leftX, 50 + rowH + 2, contentWidth))
            .bounds(leftX, 50, contentWidth, rowH)
            .build();
        this.addRenderableWidget(deviceDropdownButton);

        int y = 50 + rowH + rowGap;

        volumeSlider = new VolumeSlider(leftX, y, contentWidth, rowH, BstConfig.get().masterVolume);
        this.addRenderableWidget(volumeSlider);

        y += rowH + rowGap;

        this.addRenderableWidget(Button.builder(
                        Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.advanced")),
                        b -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(new AdvancedSettingsScreen(this));
                            }
                        })
                .bounds(leftX, y, contentWidth, rowH)
                .build());

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
        data.outputDeviceName = "<Default>".equals(selectedDevice) ? "" : selectedDevice;
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // If the dropdown is open and the click is outside it (and outside the button), close it.
        if (deviceDropdownList != null) {
            boolean insideList = deviceDropdownList.isMouseOver(mouseX, mouseY);
            boolean insideButton = deviceDropdownButton != null && deviceDropdownButton.isMouseOver(mouseX, mouseY);
            if (!insideList && !insideButton) {
                closeDeviceDropdown();
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @SuppressWarnings("null")
    private Component deviceDropdownLabel() {
        return Component.translatable("bassshakertelemetry.config.output_device")
                .append(": ")
                .append(Component.literal(Objects.requireNonNull(selectedDevice)));
    }

    private void toggleDeviceDropdown(int x, int y, int width) {
        if (deviceDropdownList != null) {
            closeDeviceDropdown();
            return;
        }

        Minecraft mc = this.minecraft;
        if (mc == null) {
            return;
        }

        int maxHeight = Math.min(140, this.height - y - 40);
        int height = Math.max(48, maxHeight);

        DeviceDropdownList list = new DeviceDropdownList(mc, width, height, y, 18, x, devices, selectedDevice);
        this.deviceDropdownList = list;
        this.addRenderableWidget(list);
    }

    private void closeDeviceDropdown() {
        if (deviceDropdownList != null) {
            this.removeWidget(deviceDropdownList);
            this.deviceDropdownList = null;
        }
    }

    private final class DeviceDropdownList extends ObjectSelectionList<DeviceDropdownEntry> {
        private final int left;

        DeviceDropdownList(Minecraft minecraft, int width, int height, int y, int itemHeight, int left, List<String> devices, String selected) {
            super(minecraft, width, height, y, y + height, itemHeight);
            this.left = left;
            this.setLeftPos(left);
            this.setRenderHeader(false, 0);

            for (String d : devices) {
                DeviceDropdownEntry entry = new DeviceDropdownEntry(d);
                this.addEntry(entry);
                if (Objects.equals(entry.deviceId, selected)) {
                    this.setSelected(entry);
                }
            }
        }

        @Override
        public int getRowWidth() {
            return this.width;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.left + this.width - 6;
        }
    }

    private final class DeviceDropdownEntry extends ObjectSelectionList.Entry<DeviceDropdownEntry> {
        private final String deviceId;

        DeviceDropdownEntry(String deviceId) {
            this.deviceId = Objects.requireNonNull(Objects.requireNonNullElse(deviceId, "<Default>"));
        }

        @Override
        public Component getNarration() {
            return Component.literal(Objects.requireNonNull(deviceId));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            selectedDevice = deviceId;
            if (deviceDropdownButton != null) {
                deviceDropdownButton.setMessage(Objects.requireNonNull(deviceDropdownLabel()));
            }
            closeDeviceDropdown();
            return true;
        }

        @Override
        @SuppressWarnings("null")
        public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            int color = (Objects.equals(deviceId, selectedDevice)) ? 0xFFFFFF : 0xE0E0E0;
            guiGraphics.drawString(Objects.requireNonNull(TelemetryConfigScreen.this.font), Objects.requireNonNull(deviceId), x + 4, y + 5, color);
        }
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
