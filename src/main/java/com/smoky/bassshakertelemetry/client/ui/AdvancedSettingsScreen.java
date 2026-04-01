package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonStyle;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonToggleButton;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

import java.util.Objects;

public final class AdvancedSettingsScreen extends Screen {
    private final Screen parent;

    private int pageIndex = 0;
    private int pageCount = 1;

    private int lastCenterX;
    private int lastContentWidth;
    private int lastLeftX;

    private GainSlider roadGainSlider;
    private GainSlider damageGainSlider;
    private GainSlider biomeGainSlider;
    private GainSlider accelGainSlider;
    private GainSlider soundGainSlider;
    private GainSlider gameplayGainSlider;
    private GainSlider footstepGainSlider;
    private GainSlider mountedGainSlider;
    private GainSlider miningSwingGainSlider;

    private IntSlider damageMsSlider;
    private IntSlider accelMsSlider;
    private IntSlider soundCooldownMsSlider;
    private IntSlider gameplayCooldownMsSlider;
    private IntSlider miningPeriodMsSlider;

    private Button bufferButton;
    private int bufferChoiceIndex;

    private Button latencyTestButton;
    private boolean latencyTestActive;
    private int latencyTestTicks;

    private Button debugOverlayButton;
    private boolean debugOverlayEnabled;

    private Button demoButton;
    private boolean demoActive;
    private int demoStep;
    private long demoNextNanos;

    private Button outputEqButton;
    private boolean outputEqEnabled;
    private IntSlider outputEqFreqSlider;
    private IntSlider outputEqGainSlider;

    private Button smartVolumeButton;
    private boolean smartVolumeEnabled;
    private IntSlider smartVolumeTargetSlider;

    private Button calTone30Button;
    private Button calTone60Button;
    private Button calSweepButton;
    private Button calStopButton;

    private Button spatialOpenButton;
    private Button instrumentsOpenButton;

    private static final int[] BUFFER_CHOICES_MS = new int[]{0, 10, 20, 30, 40, 60, 80, 100, 150, 200};

    public AdvancedSettingsScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.config.advanced_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        NeonStyle.initClient();

        int centerX = this.width / 2;
        int contentWidth = Math.min(310, this.width - 40);
        int leftX = centerX - (contentWidth / 2);
        int rowH = 20;
        int rowGap = 6;

        lastCenterX = centerX;
        lastContentWidth = contentWidth;
        lastLeftX = leftX;

        Font font = Objects.requireNonNull(this.font, "font");

        ensureWidgets(contentWidth - 12, rowH);

        pageCount = 7; // Volumes 1/3, Volumes 2/3, Volumes 3/3, Timing, Audio, Tone shaping, Tools
        if (pageIndex < 0) pageIndex = 0;
        if (pageIndex >= pageCount) pageIndex = pageCount - 1;

        buildUi(font, leftX, centerX, contentWidth, rowGap);
    }

    private void ensureWidgets(int w, int rowH) {
        // Sliders
        roadGainSlider = new GainSlider(w, rowH, "bassshakertelemetry.config.road_gain", BstConfig.get().roadTextureGain, 0.0, 0.50);
        damageGainSlider = new GainSlider(w, rowH, "bassshakertelemetry.config.damage_gain", BstConfig.get().damageBurstGain, 0.0, 1.00);
        biomeGainSlider = new GainSlider(w, rowH, "bassshakertelemetry.config.biome_gain", BstConfig.get().biomeChimeGain, 0.0, 1.00);
        accelGainSlider = new GainSlider(w, rowH, "bassshakertelemetry.config.accel_gain", BstConfig.get().accelBumpGain, 0.0, 1.00);
        soundGainSlider = new GainSlider(w, rowH, "bassshakertelemetry.config.sound_gain", BstConfig.get().soundHapticsGain, 0.0, 2.00);
        gameplayGainSlider = new GainSlider(w, rowH, "bassshakertelemetry.config.gameplay_gain", BstConfig.get().gameplayHapticsGain, 0.0, 2.00);
        footstepGainSlider = new GainSlider(w, rowH, "bassshakertelemetry.config.footstep_gain", BstConfig.get().footstepHapticsGain, 0.0, 1.00);
        mountedGainSlider = new GainSlider(w, rowH, "bassshakertelemetry.config.mounted_gain", BstConfig.get().mountedHapticsGain, 0.0, 1.00);
        miningSwingGainSlider = new GainSlider(w, rowH, "bassshakertelemetry.config.mining_swing_gain", BstConfig.get().miningSwingHapticsGain, 0.0, 1.00);

        damageMsSlider = new IntSlider(w, rowH, "bassshakertelemetry.config.damage_ms", BstConfig.get().damageBurstMs, 20, 250, "ms");
        accelMsSlider = new IntSlider(w, rowH, "bassshakertelemetry.config.accel_ms", BstConfig.get().accelBumpMs, 20, 200, "ms");
        soundCooldownMsSlider = new IntSlider(w, rowH, "bassshakertelemetry.config.sound_cooldown_ms", BstConfig.get().soundHapticsCooldownMs, 0, 250, "ms");
        gameplayCooldownMsSlider = new IntSlider(w, rowH, "bassshakertelemetry.config.gameplay_cooldown_ms", BstConfig.get().gameplayHapticsCooldownMs, 0, 400, "ms");
        miningPeriodMsSlider = new IntSlider(w, rowH, "bassshakertelemetry.config.mining_period_ms", BstConfig.get().gameplayMiningPulsePeriodMs, 60, 350, "ms");

        // Audio / debug state
        if (bufferButton == null) {
            bufferChoiceIndex = findBufferChoiceIndex(BstConfig.get().javaSoundBufferMs);
        }
        bufferButton = new NeonButton(0, 0, w, rowH, Objects.requireNonNull(bufferButtonLabel()), this::cycleBufferChoice);

        if (latencyTestButton == null) {
            latencyTestActive = false;
            latencyTestTicks = 0;
        }
        latencyTestButton = new NeonButton(0, 0, w, rowH, Objects.requireNonNull(latencyButtonLabel(false)), this::toggleLatencyTest);

        if (debugOverlayButton == null) {
            debugOverlayEnabled = BstConfig.get().debugOverlayEnabled;
        }
        debugOverlayButton = new NeonToggleButton(0, 0, w, rowH, Objects.requireNonNull(debugOverlayLabel()), () -> debugOverlayEnabled, this::toggleDebugOverlay);

        if (demoButton == null) {
            demoActive = false;
            demoStep = 0;
            demoNextNanos = 0L;
        }
        demoButton = new NeonButton(0, 0, w, rowH, Objects.requireNonNull(demoLabel()), this::toggleDemo);

        if (outputEqButton == null) {
            outputEqEnabled = BstConfig.get().outputEqEnabled;
        }
        outputEqButton = new NeonButton(0, 0, w, rowH, Objects.requireNonNull(outputEqLabel()), this::toggleOutputEq);
        outputEqFreqSlider = new IntSlider(w, rowH, "bassshakertelemetry.config.output_eq_freq", BstConfig.get().outputEqFreqHz, 10, 120, "Hz");
        outputEqGainSlider = new IntSlider(w, rowH, "bassshakertelemetry.config.output_eq_gain", BstConfig.get().outputEqGainDb, -12, 12, "dB");

        if (smartVolumeButton == null) {
            smartVolumeEnabled = BstConfig.get().smartVolumeEnabled;
        }
        smartVolumeButton = new NeonButton(0, 0, w, rowH, Objects.requireNonNull(smartVolumeLabel()), this::toggleSmartVolume);
        smartVolumeTargetSlider = new IntSlider(w, rowH, "bassshakertelemetry.config.smart_volume_target", BstConfig.get().smartVolumeTargetPct, 10, 90, "%");

        // Tools
        calTone30Button = new NeonButton(0, 0, w, rowH, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cal_tone_30hz")), () -> AudioOutputEngine.get().testCalibrationTone30Hz());
        calTone60Button = new NeonButton(0, 0, w, rowH, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cal_tone_60hz")), () -> AudioOutputEngine.get().testCalibrationTone60Hz());
        calSweepButton = new NeonButton(0, 0, w, rowH, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cal_sweep_20_120hz")), () -> AudioOutputEngine.get().testCalibrationSweep());
        calStopButton = new NeonButton(0, 0, w, rowH, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cal_stop")), () -> AudioOutputEngine.get().stopCalibration());

        spatialOpenButton = new NeonButton(0, 0, w, rowH, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.spatial_open")), () -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new SpatialConfigScreen(this));
            }
        });

        instrumentsOpenButton = new NeonButton(0, 0, w, rowH, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.instruments_open_editor")), () -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new HapticInstrumentEditorScreen(this));
            }
        });
    }

    private @Nonnull Component pageLabel() {
        return Objects.requireNonNull(Component.literal("Page " + (pageIndex + 1) + "/" + pageCount));
    }

    private void switchPage(int delta) {
        int next = pageIndex + delta;
        if (next < 0) next = 0;
        if (next >= pageCount) next = pageCount - 1;
        if (next == pageIndex) return;
        pageIndex = next;
        this.clearWidgets();
        NeonStyle.initClient();
        Font font = Objects.requireNonNull(this.font, "font");
        buildUi(font, lastLeftX, lastCenterX, lastContentWidth, 6);
    }

    private void buildUi(@Nonnull Font font, int leftX, int centerX, int contentWidth, int rowGap) {
        this.addRenderableWidget(new StringWidget(
                centerX - 140,
                20,
                280,
                20,
                Objects.requireNonNull(tr("bassshakertelemetry.config.advanced_title")),
                font
        ));

        // Page label (under title)
        this.addRenderableWidget(new StringWidget(
                centerX - 140,
                36,
                280,
                14,
            Objects.requireNonNull(pageLabel()),
                font
        ));

        int y = 56;
        int w = contentWidth - 12;

        switch (pageIndex) {
            case 0 -> {
                y = addHeader(leftX, y, w, font, Objects.requireNonNull(Component.literal(tr("bassshakertelemetry.config.effect_volumes").getString() + " (1/3)")));
                y = addSliderWithTest(leftX, y, roadGainSlider, AudioOutputEngine.get()::testRoadTexture);
                y = addSliderWithTest(leftX, y, damageGainSlider, AudioOutputEngine.get()::testDamageBurst);
                y = addSliderWithTest(leftX, y, biomeGainSlider, AudioOutputEngine.get()::testBiomeChime);
            }
            case 1 -> {
                y = addHeader(leftX, y, w, font, Objects.requireNonNull(Component.literal(tr("bassshakertelemetry.config.effect_volumes").getString() + " (2/3)")));
                y = addSliderWithTest(leftX, y, accelGainSlider, AudioOutputEngine.get()::testAccelBump);
                y = addSliderWithTest(leftX, y, soundGainSlider, AudioOutputEngine.get()::testSoundHaptics);
                y = addSliderWithTest(leftX, y, gameplayGainSlider, AudioOutputEngine.get()::testGameplayHaptics);
            }
            case 2 -> {
                y = addHeader(leftX, y, w, font, Objects.requireNonNull(Component.literal(tr("bassshakertelemetry.config.effect_volumes").getString() + " (3/3)")));
                y = addSliderWithTest(leftX, y, footstepGainSlider, AudioOutputEngine.get()::testFootsteps);
                y = addSliderWithTest(leftX, y, mountedGainSlider, AudioOutputEngine.get()::testMountedHooves);
                y = addSliderWithTest(leftX, y, miningSwingGainSlider, AudioOutputEngine.get()::testMiningSwing);
            }
            case 3 -> {
                y = addHeader(leftX, y, w, font, tr("bassshakertelemetry.config.timing"));
                y = addSliderOnly(leftX, y, damageMsSlider);
                y = addSliderOnly(leftX, y, accelMsSlider);
                y = addSliderOnly(leftX, y, soundCooldownMsSlider);
                y = addSliderOnly(leftX, y, gameplayCooldownMsSlider);
                y = addSliderOnly(leftX, y, miningPeriodMsSlider);
            }
            case 4 -> {
                y = addHeader(leftX, y, w, font, tr("bassshakertelemetry.config.audio"));
                y = addButtonOnly(leftX, y, bufferButton);
                y = addButtonOnly(leftX, y, latencyTestButton);
                y = addButtonOnly(leftX, y, debugOverlayButton);
                y = addButtonOnly(leftX, y, demoButton);
            }
            case 5 -> {
                y = addHeader(leftX, y, w, font, tr("bassshakertelemetry.config.tone_shaping"));
                y = addButtonOnly(leftX, y, outputEqButton);
                y = addSliderOnly(leftX, y, outputEqFreqSlider);
                y = addSliderOnly(leftX, y, outputEqGainSlider);
                y += rowGap;
                y = addButtonOnly(leftX, y, smartVolumeButton);
                y = addSliderOnly(leftX, y, smartVolumeTargetSlider);
            }
            default -> {
                y = addHeader(leftX, y, w, font, tr("bassshakertelemetry.config.calibration"));
                y = addButtonOnly(leftX, y, calTone30Button);
                y = addButtonOnly(leftX, y, calTone60Button);
                y = addButtonOnly(leftX, y, calSweepButton);
                y = addButtonOnly(leftX, y, calStopButton);

                y += rowGap;
                y = addHeader(leftX, y, w, font, tr("bassshakertelemetry.config.spatial"));
                y = addButtonOnly(leftX, y, spatialOpenButton);

                y += rowGap;
                y = addHeader(leftX, y, w, font, tr("bassshakertelemetry.config.instruments"));
                y = addButtonOnly(leftX, y, instrumentsOpenButton);
            }
        }

        int buttonW = (contentWidth - 10) / 2;

        Button prevPageButton = new NeonButton(
                leftX,
                this.height - 52,
                buttonW,
                20,
            Objects.requireNonNull(Component.literal("< Prev")),
                () -> switchPage(-1)
        );
        prevPageButton.active = pageIndex > 0;
        this.addRenderableWidget(prevPageButton);

        Button nextPageButton = new NeonButton(
                leftX + buttonW + 10,
                this.height - 52,
                buttonW,
                20,
            Objects.requireNonNull(Component.literal("Next >")),
                () -> switchPage(1)
        );
        nextPageButton.active = pageIndex < (pageCount - 1);
        this.addRenderableWidget(nextPageButton);

        this.addRenderableWidget(new NeonButton(
                leftX,
                this.height - 28,
                buttonW,
                20,
                tr("bassshakertelemetry.config.done"),
                this::onDone
        ));

        this.addRenderableWidget(new NeonButton(
                leftX + buttonW + 10,
                this.height - 28,
                buttonW,
                20,
                tr("bassshakertelemetry.config.cancel"),
                this::onCancel
        ));
    }

    private int addHeader(int leftX, int y, int w, @Nonnull Font font, Component text) {
        this.addRenderableWidget(new StringWidget(leftX + 2, y, w, 12, Objects.requireNonNull(text), font));
        return y + 18;
    }

    private int addSliderWithTest(int leftX, int y, net.minecraft.client.gui.components.AbstractSliderButton slider, Runnable testAction) {
        slider.setX(leftX + 2);
        slider.setY(y);
        this.addRenderableWidget(slider);

        Button testButton = new NeonButton(leftX + 2, y + 22, 90, 20, Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.test")), Objects.requireNonNull(testAction));
        this.addRenderableWidget(testButton);
        return y + 44 + 6;
    }

    private static @Nonnull Component tr(@Nonnull String key) {
        return Objects.requireNonNull(Component.translatable(key));
    }

    private int addSliderOnly(int leftX, int y, net.minecraft.client.gui.components.AbstractSliderButton slider) {
        slider.setX(leftX + 2);
        slider.setY(y);
        this.addRenderableWidget(slider);
        return y + 20 + 6;
    }

    private int addButtonOnly(int leftX, int y, Button button) {
        button.setX(leftX + 2);
        button.setY(y);
        this.addRenderableWidget(button);
        return y + 20 + 6;
    }

    private void onDone() {
        stopLatencyTest();
        stopDemo();
        BstConfig.Data data = BstConfig.get();

        if (roadGainSlider != null) data.roadTextureGain = roadGainSlider.getRealValue();
        if (damageGainSlider != null) data.damageBurstGain = damageGainSlider.getRealValue();
        if (biomeGainSlider != null) data.biomeChimeGain = biomeGainSlider.getRealValue();
        if (accelGainSlider != null) data.accelBumpGain = accelGainSlider.getRealValue();
        if (soundGainSlider != null) data.soundHapticsGain = soundGainSlider.getRealValue();
        if (gameplayGainSlider != null) data.gameplayHapticsGain = gameplayGainSlider.getRealValue();
        if (footstepGainSlider != null) data.footstepHapticsGain = footstepGainSlider.getRealValue();
        if (mountedGainSlider != null) data.mountedHapticsGain = mountedGainSlider.getRealValue();
        if (miningSwingGainSlider != null) data.miningSwingHapticsGain = miningSwingGainSlider.getRealValue();

        if (damageMsSlider != null) data.damageBurstMs = damageMsSlider.getIntValue();
        if (accelMsSlider != null) data.accelBumpMs = accelMsSlider.getIntValue();
        if (soundCooldownMsSlider != null) data.soundHapticsCooldownMs = soundCooldownMsSlider.getIntValue();
        if (gameplayCooldownMsSlider != null) data.gameplayHapticsCooldownMs = gameplayCooldownMsSlider.getIntValue();
        if (miningPeriodMsSlider != null) data.gameplayMiningPulsePeriodMs = miningPeriodMsSlider.getIntValue();

        if (bufferButton != null) {
            data.javaSoundBufferMs = BUFFER_CHOICES_MS[clampIndex(bufferChoiceIndex)];
        }

        data.outputEqEnabled = outputEqEnabled;
        if (outputEqFreqSlider != null) data.outputEqFreqHz = outputEqFreqSlider.getIntValue();
        if (outputEqGainSlider != null) data.outputEqGainDb = outputEqGainSlider.getIntValue();

        data.smartVolumeEnabled = smartVolumeEnabled;
        if (smartVolumeTargetSlider != null) data.smartVolumeTargetPct = smartVolumeTargetSlider.getIntValue();

        data.debugOverlayEnabled = debugOverlayEnabled;

        BstConfig.set(data);
        AudioOutputEngine.get().startOrRestart();

        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void onCancel() {
        stopLatencyTest();
        stopDemo();
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
    public void tick() {
        super.tick();
        if (!latencyTestActive) {
            return;
        }

        latencyTestTicks++;
        boolean pulseNow = (latencyTestTicks % 10) == 0; // 2 Hz at 20 tps
        if (pulseNow) {
            AudioOutputEngine.get().testLatencyPulse();
        }
        if (latencyTestButton != null) {
            latencyTestButton.setMessage(Objects.requireNonNull(latencyButtonLabel(pulseNow)));
        }

        tickDemo();
    }

    private void toggleDebugOverlay() {
        debugOverlayEnabled = !debugOverlayEnabled;
        if (debugOverlayButton != null) {
            debugOverlayButton.setMessage(Objects.requireNonNull(debugOverlayLabel()));
        }
    }

    private Component debugOverlayLabel() {
        return debugOverlayEnabled
                ? Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.debug_overlay_on"))
                : Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.debug_overlay_off"));
    }

    private void toggleDemo() {
        demoActive = !demoActive;
        demoStep = 0;
        demoNextNanos = System.nanoTime();
        if (demoButton != null) {
            demoButton.setMessage(Objects.requireNonNull(demoLabel()));
        }
    }

    private void toggleOutputEq() {
        outputEqEnabled = !outputEqEnabled;
        if (outputEqButton != null) {
            outputEqButton.setMessage(Objects.requireNonNull(outputEqLabel()));
        }
    }

    private Component outputEqLabel() {
        return outputEqEnabled
                ? Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.output_eq_on"))
                : Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.output_eq_off"));
    }

    private void toggleSmartVolume() {
        smartVolumeEnabled = !smartVolumeEnabled;
        if (smartVolumeButton != null) {
            smartVolumeButton.setMessage(Objects.requireNonNull(smartVolumeLabel()));
        }
    }

    private Component smartVolumeLabel() {
        return smartVolumeEnabled
                ? Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.smart_volume_on"))
                : Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.smart_volume_off"));
    }

    private void stopDemo() {
        demoActive = false;
        demoStep = 0;
        demoNextNanos = 0L;
        if (demoButton != null) {
            demoButton.setMessage(Objects.requireNonNull(demoLabel()));
        }
    }

    private void tickDemo() {
        if (!demoActive) {
            return;
        }
        long now = System.nanoTime();
        if (now < demoNextNanos) {
            return;
        }

        // Simple repeatable sequence for tuning: explosion -> damage -> mining -> footsteps.
        // Uses explicit impulses so it works even outside of normal gameplay triggers.
        switch (demoStep) {
            case 0 -> {
                AudioOutputEngine.get().testLatencyPulse();
                demoNextNanos = now + 600_000_000L;
            }
            case 1 -> {
                AudioOutputEngine.get().testDamageBurst();
                demoNextNanos = now + 600_000_000L;
            }
            case 2 -> {
                AudioOutputEngine.get().testRoadTexture();
                demoNextNanos = now + 600_000_000L;
            }
            case 3 -> {
                AudioOutputEngine.get().testFootsteps();
                demoNextNanos = now + 350_000_000L;
            }
            case 4 -> {
                AudioOutputEngine.get().testMiningSwing();
                demoNextNanos = now + 450_000_000L;
            }
            default -> {
                demoStep = -1;
                demoNextNanos = now + 900_000_000L;
            }
        }
        demoStep++;

        if (demoButton != null) {
            demoButton.setMessage(Objects.requireNonNull(demoLabel()));
        }
    }

    private Component demoLabel() {
        return demoActive
                ? Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.demo_stop"))
                : Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.demo_run"));
    }

    private void toggleLatencyTest() {
        latencyTestActive = !latencyTestActive;
        latencyTestTicks = 0;
        if (latencyTestButton != null) {
            latencyTestButton.setMessage(Objects.requireNonNull(latencyButtonLabel(false)));
        }
    }

    private void stopLatencyTest() {
        latencyTestActive = false;
        latencyTestTicks = 0;
        if (latencyTestButton != null) {
            latencyTestButton.setMessage(Objects.requireNonNull(latencyButtonLabel(false)));
        }
    }

    private void cycleBufferChoice() {
        bufferChoiceIndex = (clampIndex(bufferChoiceIndex) + 1) % BUFFER_CHOICES_MS.length;
        if (bufferButton != null) {
            bufferButton.setMessage(Objects.requireNonNull(bufferButtonLabel()));
        }
    }

    private net.minecraft.network.chat.MutableComponent bufferButtonLabel() {
        int ms = BUFFER_CHOICES_MS[clampIndex(bufferChoiceIndex)];
        Component v = (ms <= 0)
                ? Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.buffer_auto"))
                : Objects.requireNonNull(Component.literal(ms + "ms"));
        return Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.output_buffer"))
                .append(": ")
                .append(v);
    }

    private net.minecraft.network.chat.MutableComponent latencyButtonLabel(boolean pulseNow) {
        net.minecraft.network.chat.MutableComponent base = latencyTestActive
                ? Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.latency_test_on"))
                : Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.latency_test_off"));
        if (latencyTestActive && pulseNow) {
            return Objects.requireNonNull(Component.translatable(
                latencyTestActive
                    ? "bassshakertelemetry.config.latency_test_on"
                    : "bassshakertelemetry.config.latency_test_off"
            )).append(" ").append(Objects.requireNonNull(Component.literal("*")));
        }
        return base;
    }

    private static int findBufferChoiceIndex(int currentMs) {
        for (int i = 0; i < BUFFER_CHOICES_MS.length; i++) {
            if (BUFFER_CHOICES_MS[i] == currentMs) {
                return i;
            }
        }
        // If config had an arbitrary value, pick the nearest.
        int bestI = 0;
        int bestD = Integer.MAX_VALUE;
        for (int i = 0; i < BUFFER_CHOICES_MS.length; i++) {
            int d = Math.abs(BUFFER_CHOICES_MS[i] - currentMs);
            if (d < bestD) {
                bestD = d;
                bestI = i;
            }
        }
        return bestI;
    }

    private static int clampIndex(int idx) {
        if (idx < 0) return 0;
        if (idx >= BUFFER_CHOICES_MS.length) return BUFFER_CHOICES_MS.length - 1;
        return idx;
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
