package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

public final class AdvancedSettingsScreen extends Screen {
    private final Screen parent;

    private SettingsList settingsList;

    private GainSlider roadGainSlider;
    private GainSlider damageGainSlider;
    private GainSlider biomeGainSlider;
    private GainSlider accelGainSlider;
    private GainSlider soundGainSlider;
    private GainSlider gameplayGainSlider;

    private IntSlider damageMsSlider;
    private IntSlider accelMsSlider;
    private IntSlider soundCooldownMsSlider;
    private IntSlider gameplayCooldownMsSlider;
    private IntSlider miningPeriodMsSlider;

    public AdvancedSettingsScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.config.advanced_title"));
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

        var font = Objects.requireNonNull(this.font, "font");

        this.addRenderableWidget(new StringWidget(
                centerX - 140,
                20,
                280,
                20,
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.advanced_title")),
                font
        ));

        Minecraft mc = Objects.requireNonNull(this.minecraft, "minecraft");

        int listTop = 50;
        int listBottom = this.height - 34;
        int listHeight = Math.max(80, listBottom - listTop);

        settingsList = new SettingsList(mc, contentWidth, listHeight, listTop, listTop + listHeight, 44, leftX);

        // --- Effect volumes ---
        settingsList.addSettingEntry(new LabelEntry("bassshakertelemetry.config.effect_volumes"));

        roadGainSlider = new GainSlider(contentWidth - 12, rowH, "bassshakertelemetry.config.road_gain", BstConfig.get().roadTextureGain, 0.0, 0.50);
        damageGainSlider = new GainSlider(contentWidth - 12, rowH, "bassshakertelemetry.config.damage_gain", BstConfig.get().damageBurstGain, 0.0, 1.00);
        biomeGainSlider = new GainSlider(contentWidth - 12, rowH, "bassshakertelemetry.config.biome_gain", BstConfig.get().biomeChimeGain, 0.0, 1.00);
        accelGainSlider = new GainSlider(contentWidth - 12, rowH, "bassshakertelemetry.config.accel_gain", BstConfig.get().accelBumpGain, 0.0, 1.00);
        soundGainSlider = new GainSlider(contentWidth - 12, rowH, "bassshakertelemetry.config.sound_gain", BstConfig.get().soundHapticsGain, 0.0, 2.00);
        gameplayGainSlider = new GainSlider(contentWidth - 12, rowH, "bassshakertelemetry.config.gameplay_gain", BstConfig.get().gameplayHapticsGain, 0.0, 2.00);

        settingsList.addSettingEntry(new SliderWithTestEntry(roadGainSlider, AudioOutputEngine.get()::testRoadTexture));
        settingsList.addSettingEntry(new SliderWithTestEntry(damageGainSlider, AudioOutputEngine.get()::testDamageBurst));
        settingsList.addSettingEntry(new SliderWithTestEntry(biomeGainSlider, AudioOutputEngine.get()::testBiomeChime));
        settingsList.addSettingEntry(new SliderWithTestEntry(accelGainSlider, AudioOutputEngine.get()::testAccelBump));
        settingsList.addSettingEntry(new SliderWithTestEntry(soundGainSlider, AudioOutputEngine.get()::testSoundHaptics));
        settingsList.addSettingEntry(new SliderWithTestEntry(gameplayGainSlider, AudioOutputEngine.get()::testGameplayHaptics));

        // --- Timing ---
        settingsList.addSettingEntry(new LabelEntry("bassshakertelemetry.config.timing"));

        damageMsSlider = new IntSlider(contentWidth - 12, rowH, "bassshakertelemetry.config.damage_ms", BstConfig.get().damageBurstMs, 20, 250, "ms");
        accelMsSlider = new IntSlider(contentWidth - 12, rowH, "bassshakertelemetry.config.accel_ms", BstConfig.get().accelBumpMs, 20, 200, "ms");
        soundCooldownMsSlider = new IntSlider(contentWidth - 12, rowH, "bassshakertelemetry.config.sound_cooldown_ms", BstConfig.get().soundHapticsCooldownMs, 0, 250, "ms");
        gameplayCooldownMsSlider = new IntSlider(contentWidth - 12, rowH, "bassshakertelemetry.config.gameplay_cooldown_ms", BstConfig.get().gameplayHapticsCooldownMs, 0, 400, "ms");
        miningPeriodMsSlider = new IntSlider(contentWidth - 12, rowH, "bassshakertelemetry.config.mining_period_ms", BstConfig.get().gameplayMiningPulsePeriodMs, 60, 350, "ms");

        settingsList.addSettingEntry(new SliderOnlyEntry(damageMsSlider));
        settingsList.addSettingEntry(new SliderOnlyEntry(accelMsSlider));
        settingsList.addSettingEntry(new SliderOnlyEntry(soundCooldownMsSlider));
        settingsList.addSettingEntry(new SliderOnlyEntry(gameplayCooldownMsSlider));
        settingsList.addSettingEntry(new SliderOnlyEntry(miningPeriodMsSlider));

        this.addRenderableWidget(settingsList);

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

        if (roadGainSlider != null) data.roadTextureGain = roadGainSlider.getRealValue();
        if (damageGainSlider != null) data.damageBurstGain = damageGainSlider.getRealValue();
        if (biomeGainSlider != null) data.biomeChimeGain = biomeGainSlider.getRealValue();
        if (accelGainSlider != null) data.accelBumpGain = accelGainSlider.getRealValue();
        if (soundGainSlider != null) data.soundHapticsGain = soundGainSlider.getRealValue();
        if (gameplayGainSlider != null) data.gameplayHapticsGain = gameplayGainSlider.getRealValue();

        if (damageMsSlider != null) data.damageBurstMs = damageMsSlider.getIntValue();
        if (accelMsSlider != null) data.accelBumpMs = accelMsSlider.getIntValue();
        if (soundCooldownMsSlider != null) data.soundHapticsCooldownMs = soundCooldownMsSlider.getIntValue();
        if (gameplayCooldownMsSlider != null) data.gameplayHapticsCooldownMs = gameplayCooldownMsSlider.getIntValue();
        if (miningPeriodMsSlider != null) data.gameplayMiningPulsePeriodMs = miningPeriodMsSlider.getIntValue();

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

    private static final class SettingsList extends ContainerObjectSelectionList<SettingsEntry> {
        private final int left;

        SettingsList(Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight, int left) {
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

        void addSettingEntry(SettingsEntry entry) {
            this.addEntry(Objects.requireNonNull(entry));
        }
    }

    private abstract static class SettingsEntry extends ContainerObjectSelectionList.Entry<SettingsEntry> {
    }

    private static final class LabelEntry extends SettingsEntry {
        private final String key;

        LabelEntry(String key) {
            this.key = Objects.requireNonNull(key);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }

        @Override
        @SuppressWarnings("null")
        public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            guiGraphics.drawString(
                    Objects.requireNonNull(Minecraft.getInstance().font),
                    Objects.requireNonNull(Component.translatable(key)),
                    x + 2,
                    y + 6,
                    0xFFFFFF
            );
        }
    }

    private static final class SliderWithTestEntry extends SettingsEntry {
        private final net.minecraft.client.gui.components.AbstractSliderButton slider;
        private final Button testButton;

        SliderWithTestEntry(net.minecraft.client.gui.components.AbstractSliderButton slider, Runnable testAction) {
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
            slider.setX(innerX);
            slider.setY(y);

            testButton.setX(innerX);
            testButton.setY(y + 22);

            slider.render(guiGraphics, mouseX, mouseY, partialTick);
            testButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private static final class SliderOnlyEntry extends SettingsEntry {
        private final net.minecraft.client.gui.components.AbstractSliderButton slider;

        SliderOnlyEntry(net.minecraft.client.gui.components.AbstractSliderButton slider) {
            this.slider = Objects.requireNonNull(slider);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(slider);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(slider);
        }

        @Override
        @SuppressWarnings("null")
        public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            int innerX = x + 2;
            slider.setX(innerX);
            slider.setY(y + 12);
            slider.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private static final class GainSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        private final String labelKey;
        private final double min;
        private final double max;

        GainSlider(int width, int height, String labelKey, double initial, double min, double max) {
            super(0, 0, width, height, Component.empty(), to01(initial, min, max));
            this.labelKey = Objects.requireNonNull(labelKey);
            this.min = min;
            this.max = Math.max(min + 1e-9, max);
            updateMessage();
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

    private static final class IntSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        private final String labelKey;
        private final int min;
        private final int max;
        private final String suffix;

        IntSlider(int width, int height, String labelKey, int initial, int min, int max, String suffix) {
            super(0, 0, width, height, Component.empty(), to01(initial, min, max));
            this.labelKey = Objects.requireNonNull(labelKey);
            this.min = min;
            this.max = Math.max(min + 1, max);
            this.suffix = Objects.requireNonNullElse(suffix, "");
            updateMessage();
        }

        @Override
        @SuppressWarnings("null")
        protected void updateMessage() {
            int v = getIntValue();
            String text = suffix.isEmpty() ? Integer.toString(v) : (v + suffix);
            this.setMessage(
                    Objects.requireNonNull(Component.translatable(labelKey))
                            .append(": ")
                            .append(Objects.requireNonNull(Component.literal(text)))
            );
        }

        @Override
        protected void applyValue() {
        }

        int getIntValue() {
            double t = clamp01(this.value);
            int v = (int) Math.round(min + (t * (max - min)));
            if (v < min) return min;
            if (v > max) return max;
            return v;
        }

        private static double to01(int actual, int min, int max) {
            int denom = Math.max(1, max - min);
            return clamp01((actual - min) / (double) denom);
        }

        private static double clamp01(double v) {
            if (v < 0.0) return 0.0;
            if (v > 1.0) return 1.0;
            return v;
        }
    }
}
