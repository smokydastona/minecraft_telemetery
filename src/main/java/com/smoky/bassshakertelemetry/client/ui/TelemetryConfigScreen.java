package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioDeviceUtil;
import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.narration.NarratableEntry;
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

    private EffectVolumeList effectVolumeList;
    private RangedSlider roadGainSlider;
    private RangedSlider damageGainSlider;
    private RangedSlider biomeGainSlider;
    private RangedSlider accelGainSlider;
    private RangedSlider soundGainSlider;
    private RangedSlider gameplayGainSlider;

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

        y += (rowH + rowGap) * 2;

        this.addRenderableWidget(new StringWidget(
            leftX,
            y,
            contentWidth,
            rowH,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.effect_volumes")),
            font
        ));

        y += rowH + rowGap;

        Minecraft mc = Objects.requireNonNull(this.minecraft, "minecraft");
        int listTop = y;
        int listBottom = this.height - 34;
        int listHeight = Math.max(70, listBottom - listTop);

        effectVolumeList = new EffectVolumeList(mc, contentWidth, listHeight, listTop, listTop + listHeight, 44, leftX);

        roadGainSlider = new RangedSlider(0, 0, contentWidth - 12, rowH, "bassshakertelemetry.config.road_gain", BstConfig.get().roadTextureGain, 0.0, 0.50);
        damageGainSlider = new RangedSlider(0, 0, contentWidth - 12, rowH, "bassshakertelemetry.config.damage_gain", BstConfig.get().damageBurstGain, 0.0, 1.00);
        biomeGainSlider = new RangedSlider(0, 0, contentWidth - 12, rowH, "bassshakertelemetry.config.biome_gain", BstConfig.get().biomeChimeGain, 0.0, 1.00);
        accelGainSlider = new RangedSlider(0, 0, contentWidth - 12, rowH, "bassshakertelemetry.config.accel_gain", BstConfig.get().accelBumpGain, 0.0, 1.00);
        soundGainSlider = new RangedSlider(0, 0, contentWidth - 12, rowH, "bassshakertelemetry.config.sound_gain", BstConfig.get().soundHapticsGain, 0.0, 2.00);
        gameplayGainSlider = new RangedSlider(0, 0, contentWidth - 12, rowH, "bassshakertelemetry.config.gameplay_gain", BstConfig.get().gameplayHapticsGain, 0.0, 2.00);

        effectVolumeList.addEffectEntry(new EffectVolumeEntry(roadGainSlider, AudioOutputEngine.get()::testRoadTexture));
        effectVolumeList.addEffectEntry(new EffectVolumeEntry(damageGainSlider, AudioOutputEngine.get()::testDamageBurst));
        effectVolumeList.addEffectEntry(new EffectVolumeEntry(biomeGainSlider, AudioOutputEngine.get()::testBiomeChime));
        effectVolumeList.addEffectEntry(new EffectVolumeEntry(accelGainSlider, AudioOutputEngine.get()::testAccelBump));
        effectVolumeList.addEffectEntry(new EffectVolumeEntry(soundGainSlider, AudioOutputEngine.get()::testSoundHaptics));
        effectVolumeList.addEffectEntry(new EffectVolumeEntry(gameplayGainSlider, AudioOutputEngine.get()::testGameplayHaptics));

        this.addRenderableWidget(effectVolumeList);

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

        if (roadGainSlider != null) data.roadTextureGain = roadGainSlider.getRealValue();
        if (damageGainSlider != null) data.damageBurstGain = damageGainSlider.getRealValue();
        if (biomeGainSlider != null) data.biomeChimeGain = biomeGainSlider.getRealValue();
        if (accelGainSlider != null) data.accelBumpGain = accelGainSlider.getRealValue();
        if (soundGainSlider != null) data.soundHapticsGain = soundGainSlider.getRealValue();
        if (gameplayGainSlider != null) data.gameplayHapticsGain = gameplayGainSlider.getRealValue();

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

    private static final class RangedSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        private final String labelKey;
        private final double min;
        private final double max;

        RangedSlider(int x, int y, int width, int height, String labelKey, double initial, double min, double max) {
            super(x, y, width, height, Component.empty(), to01(initial, min, max));
            this.labelKey = Objects.requireNonNull(labelKey);
            this.min = min;
            this.max = Math.max(min + 1e-9, max);
            updateMessage();
        }

        void setPos(int x, int y) {
            this.setX(x);
            this.setY(y);
        }

        @Override
        @SuppressWarnings("null")
        protected void updateMessage() {
            int pct = (int) Math.round(clamp01(this.value) * 100.0);
            this.setMessage(
                    Objects.requireNonNull(Component.translatable(labelKey))
                            .append(": ")
                            .append(Objects.requireNonNull(Component.literal(pct + "%")))
            );
        }

        @Override
        protected void applyValue() {
        }

        double getRealValue() {
            return clamp(min + (clamp01(this.value) * (max - min)), min, max);
        }

        private static double to01(double actual, double min, double max) {
            double denom = Math.max(1e-9, max - min);
            return clamp01((actual - min) / denom);
        }

        private static double clamp01(double v) {
            if (v < 0.0) return 0.0;
            if (v > 1.0) return 1.0;
            return v;
        }

        private static double clamp(double v, double lo, double hi) {
            if (v < lo) return lo;
            if (v > hi) return hi;
            return v;
        }
    }

    private static final class EffectVolumeList extends ContainerObjectSelectionList<EffectVolumeEntry> {
        private final int left;

        EffectVolumeList(Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight, int left) {
            super(minecraft, width, height, y0, y1, itemHeight);
            this.left = left;
            this.setLeftPos(left);
            this.setRenderHeader(false, 0);
        }

        @Override
        public int getRowWidth() {
            return this.width;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.left + this.width - 6;
        }

        void addEffectEntry(EffectVolumeEntry entry) {
            this.addEntry(Objects.requireNonNull(entry));
        }
    }

    private static final class EffectVolumeEntry extends ContainerObjectSelectionList.Entry<EffectVolumeEntry> {
        private final RangedSlider slider;
        private final Button testButton;

        EffectVolumeEntry(RangedSlider slider, Runnable testAction) {
            this.slider = Objects.requireNonNull(slider);
            Runnable action = Objects.requireNonNull(testAction);
            this.testButton = Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.test")), b -> action.run())
                    .bounds(0, 0, 90, 20)
                    .build();
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(slider, testButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(slider, testButton);
        }

        @Override
        @SuppressWarnings("null")
        public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            int innerX = x + 2;
            slider.setPos(innerX, y);

            testButton.setX(innerX);
            testButton.setY(y + 22);

            slider.render(guiGraphics, mouseX, mouseY, partialTick);
            testButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }

    }
}
