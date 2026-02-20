package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Minimal calibration wizard for per-transducer trims (gain + simple EQ) and test playback.
 */
public final class SpatialCalibrationScreen extends Screen {
    private static final List<String> CHANNEL_IDS = List.of("FL", "FR", "C", "LFE", "SL", "SR", "BL", "BR");

    private final Screen parent;

    private String selectedChannel = "FL";

    private int wizardStep = 0;
    private Button stepBack;
    private Button stepNext;

    private CycleButton<String> channelCycle;
    private GainDbSlider gainSlider;
    private ComfortSlider comfortSlider;
    private EqFreqSlider eqFreqSlider;
    private EqGainSlider eqGainSlider;
    private RmsTargetSlider rmsTargetSlider;

    public SpatialCalibrationScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.spatial_cal.title"));
        this.parent = parent;
    }

    @Override
    @SuppressWarnings("null")
    protected void init() {
        super.init();

        AudioOutputEngine.setDebugCaptureEnabled(true);

        int centerX = this.width / 2;
        int contentWidth = Math.min(310, this.width - 40);
        int leftX = centerX - (contentWidth / 2);

        int rowH = 20;
        int rowGap = 6;

        var font = Objects.requireNonNull(this.font, "font");

        this.addRenderableWidget(new StringWidget(
            centerX - 140,
            20,
            280,
            20,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_cal.title")),
            font
        ));

        // Pick initial selected channel from config if available.
        selectedChannel = normalizeChannelId(selectedChannel);

        var currentCal = getCalibration(selectedChannel);

        int y = 50;

        channelCycle = CycleButton.<String>builder(v -> Objects.requireNonNull(Component.literal(v)))
            .withValues(CHANNEL_IDS)
            .withInitialValue(selectedChannel)
            .create(leftX, y, contentWidth, rowH, Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_cal.channel")), (btn, v) -> {
                selectedChannel = normalizeChannelId(v);
                loadCalibrationIntoUi(selectedChannel);
            });
        this.addRenderableWidget(channelCycle);

        y += rowH + rowGap;

        gainSlider = new GainDbSlider(leftX, y, contentWidth, rowH, currentCal.gainDb);
        this.addRenderableWidget(gainSlider);

        y += rowH + rowGap;

        comfortSlider = new ComfortSlider(leftX, y, contentWidth, rowH, currentCal.comfortLimit01);
        this.addRenderableWidget(comfortSlider);

        y += rowH + rowGap;

        eqFreqSlider = new EqFreqSlider(leftX, y, contentWidth, rowH, currentCal.eqFreqHz);
        this.addRenderableWidget(eqFreqSlider);

        y += rowH + rowGap;

        eqGainSlider = new EqGainSlider(leftX, y, contentWidth, rowH, currentCal.eqGainDb);
        this.addRenderableWidget(eqGainSlider);

        y += rowH + rowGap;

        rmsTargetSlider = new RmsTargetSlider(leftX, y, contentWidth, rowH, 0.25);
        this.addRenderableWidget(rmsTargetSlider);

        y += rowH + rowGap;

        int colGap = 10;
        int colWidth = Math.max(60, (contentWidth - colGap) / 2);
        int colLeftX = leftX;
        int colRightX = leftX + colWidth + colGap;

        this.addRenderableWidget(Button.builder(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_cal.tone30")),
                b -> {
                    applyCalibrationFromUi();
                    AudioOutputEngine.get().testCalibrationToneOnChannel(selectedChannel, 30.0, 2000);
                })
            .bounds(colLeftX, y, colWidth, rowH)
            .build());

        this.addRenderableWidget(Button.builder(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_cal.tone60")),
                b -> {
                    applyCalibrationFromUi();
                    AudioOutputEngine.get().testCalibrationToneOnChannel(selectedChannel, 60.0, 2000);
                })
            .bounds(colRightX, y, colWidth, rowH)
            .build());

        y += rowH + rowGap;

        this.addRenderableWidget(Button.builder(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_cal.burst")),
                b -> {
                    applyCalibrationFromUi();
                    AudioOutputEngine.get().testCalibrationBurstOnChannel(selectedChannel);
                })
            .bounds(colLeftX, y, colWidth, rowH)
            .build());

        this.addRenderableWidget(Button.builder(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_cal.auto_trim")),
                b -> autoTrimToTargetRms())
            .bounds(colRightX, y, colWidth, rowH)
            .build());

        y += rowH + rowGap;

        this.addRenderableWidget(Button.builder(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_cal.comfort_tone")),
                b -> {
                    applyCalibrationFromUi();
                    AudioOutputEngine.get().testCalibrationToneOnChannel(selectedChannel, 60.0, 4000);
                })
            .bounds(colLeftX, y, colWidth, rowH)
            .build());

        this.addRenderableWidget(Button.builder(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_cal.capture_comfort")),
                b -> captureComfortLimitFromPeak())
            .bounds(colRightX, y, colWidth, rowH)
            .build());

        y += rowH + rowGap;

        this.addRenderableWidget(Button.builder(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_cal.sweep")),
                b -> {
                    applyCalibrationFromUi();
                    AudioOutputEngine.get().testCalibrationSweepOnChannel(selectedChannel);
                })
            .bounds(colLeftX, y, colWidth, rowH)
            .build());

        this.addRenderableWidget(Button.builder(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_cal.latency")),
                b -> AudioOutputEngine.get().testLatencyPulseOnChannel(selectedChannel))
            .bounds(colRightX, y, colWidth, rowH)
            .build());

        y += rowH + rowGap;

        this.addRenderableWidget(Button.builder(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cal_stop")),
                b -> AudioOutputEngine.get().stopCalibration())
            .bounds(leftX, y, contentWidth, rowH)
            .build());

        // Wizard navigation
        int navY = this.height - 52;
        int navW = (contentWidth - 10) / 2;
        stepBack = Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_cal.step_back")), b -> step(-1))
            .bounds(leftX, navY, navW, 20)
            .build();
        this.addRenderableWidget(stepBack);

        stepNext = Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_cal.step_next")), b -> step(1))
            .bounds(leftX + navW + 10, navY, navW, 20)
            .build();
        this.addRenderableWidget(stepNext);

        updateWizardButtons();

        int buttonW = (contentWidth - 10) / 2;
        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.done")), b -> onDone())
            .bounds(leftX, this.height - 28, buttonW, 20)
            .build());

        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cancel")), b -> onCancel())
            .bounds(leftX + buttonW + 10, this.height - 28, buttonW, 20)
            .build());
    }

    private void loadCalibrationIntoUi(String channelId) {
        var cal = getCalibration(channelId);
        if (gainSlider != null) {
            gainSlider.setGainDb(cal.gainDb);
        }
        if (comfortSlider != null) {
            comfortSlider.setComfortLimit01(cal.comfortLimit01);
        }
        if (eqFreqSlider != null) {
            eqFreqSlider.setFreqHz(cal.eqFreqHz);
        }
        if (eqGainSlider != null) {
            eqGainSlider.setGainDb(cal.eqGainDb);
        }
    }

    private void applyCalibrationFromUi() {
        String ch = normalizeChannelId(selectedChannel);
        selectedChannel = ch;

        BstConfig.Data data = BstConfig.get();
        if (data.soundScapeCalibration == null) {
            data.soundScapeCalibration = new java.util.HashMap<>();
        }

        BstConfig.Data.TransducerCalibration cal = data.soundScapeCalibration.get(ch);
        if (cal == null) {
            cal = new BstConfig.Data.TransducerCalibration();
            data.soundScapeCalibration.put(ch, cal);
        }

        cal.gainDb = gainSlider.getGainDb();
        cal.comfortLimit01 = comfortSlider.getComfortLimit01();
        cal.eqFreqHz = eqFreqSlider.getFreqHz();
        cal.eqGainDb = eqGainSlider.getGainDb();

        BstConfig.set(data);
    }

    private void onDone() {
        applyCalibrationFromUi();
        AudioOutputEngine.get().stopCalibration();
        AudioOutputEngine.setDebugCaptureEnabled(false);
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void onCancel() {
        AudioOutputEngine.get().stopCalibration();
        AudioOutputEngine.setDebugCaptureEnabled(false);
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        var font = Objects.requireNonNull(this.font, "font");

        // Wizard status + instructions
        String stepLabel = String.format(Locale.ROOT, "Step %d/4", wizardStep + 1);
        String help1;
        String help2;
        switch (wizardStep) {
            case 0 -> {
                help1 = "Pick a channel, then use Burst test.";
                help2 = "Confirm the correct transducer fires.";
            }
            case 1 -> {
                help1 = "RMS leveling: set Target RMS, then Auto-trim.";
                help2 = "Use Burst test to re-check.";
            }
            case 2 -> {
                help1 = "Comfort: adjust Comfort limit to max comfortable.";
                help2 = "Use Comfort tone, then Capture comfort limit.";
            }
            default -> {
                help1 = "Review: run Sweep + Latency pulse.";
                help2 = "Done when it feels right.";
            }
        }
        guiGraphics.drawString(font, stepLabel + "  —  " + help1, 12, 40, 0xCCCCCC);
        guiGraphics.drawString(font, help2, 12, 50, 0x999999);
        AudioOutputEngine.DebugSnapshot snap = AudioOutputEngine.getDebugSnapshot();
        if (snap == null || snap.rms01 == null || snap.peak01 == null) {
            return;
        }

        int idx = meterChannelIndex(selectedChannel, snap.channels);
        if (idx < 0 || idx >= snap.rms01.length || idx >= snap.peak01.length) {
            return;
        }

        float rms = snap.rms01[idx];
        float peak = snap.peak01[idx];
        String s = String.format(Locale.ROOT, "Meter (%s): RMS=%.3f  Peak=%.3f", selectedChannel, rms, peak);
        guiGraphics.drawString(font, s, 12, this.height - 44, 0xCCCCCC);
    }

    private void step(int delta) {
        int next = wizardStep + delta;
        if (next < 0) next = 0;
        if (next > 3) next = 3;
        wizardStep = next;
        updateWizardButtons();
    }

    private void updateWizardButtons() {
        if (stepBack != null) {
            stepBack.active = wizardStep > 0;
        }
        if (stepNext != null) {
            stepNext.active = wizardStep < 3;
        }
    }

    private void autoTrimToTargetRms() {
        applyCalibrationFromUi();

        AudioOutputEngine.DebugSnapshot snap = AudioOutputEngine.getDebugSnapshot();
        if (snap == null || snap.rms01 == null) {
            return;
        }
        int idx = meterChannelIndex(selectedChannel, snap.channels);
        if (idx < 0 || idx >= snap.rms01.length) {
            return;
        }

        double current = snap.rms01[idx];
        double target = (rmsTargetSlider == null) ? 0.25 : rmsTargetSlider.getTargetRms01();
        if (!Double.isFinite(current) || !Double.isFinite(target) || current <= 0.0005 || target <= 0.0005) {
            return;
        }

        double deltaDb = 20.0 * Math.log10(target / current);
        deltaDb = clamp(deltaDb, -6.0, 6.0);

        double newDb = gainSlider.getGainDb() + deltaDb;
        gainSlider.setGainDb(newDb);
        applyCalibrationFromUi();

        // Re-run a short burst so the user can immediately feel the new level.
        AudioOutputEngine.get().testCalibrationBurstOnChannel(selectedChannel);
    }

    private void captureComfortLimitFromPeak() {
        applyCalibrationFromUi();

        AudioOutputEngine.DebugSnapshot snap = AudioOutputEngine.getDebugSnapshot();
        if (snap == null || snap.peak01 == null) {
            return;
        }
        int idx = meterChannelIndex(selectedChannel, snap.channels);
        if (idx < 0 || idx >= snap.peak01.length) {
            return;
        }

        // Interpret comfort threshold as a peak cap. Use a conservative default.
        double targetPeak = 0.35;
        double currentPeak = snap.peak01[idx];
        if (!Double.isFinite(currentPeak) || currentPeak <= 0.0005) {
            return;
        }

        double scale = targetPeak / currentPeak;
        scale = clamp(scale, 0.10, 1.0);

        double newComfort = comfortSlider.getComfortLimit01() * scale;
        comfortSlider.setComfortLimit01(newComfort);
        applyCalibrationFromUi();

        // Quick confirmation burst.
        AudioOutputEngine.get().testCalibrationBurstOnChannel(selectedChannel);
    }

    private static int meterChannelIndex(String channelId, int engineChannels) {
        String ch = normalizeChannelId(channelId);
        if (engineChannels == 2) {
            return "FR".equalsIgnoreCase(ch) ? 1 : 0;
        }
        int idx = CHANNEL_IDS.indexOf(ch);
        return (idx < 0) ? 0 : idx;
    }

    private static double clamp(double v, double a, double b) {
        if (v < a) return a;
        if (v > b) return b;
        return v;
    }

    private static String normalizeChannelId(String channelId) {
        String ch = (channelId == null) ? "FL" : channelId.trim().toUpperCase(Locale.ROOT);
        if (!CHANNEL_IDS.contains(ch)) {
            return "FL";
        }
        return ch;
    }

    private static BstConfig.Data.TransducerCalibration getCalibration(String channelId) {
        String ch = normalizeChannelId(channelId);
        BstConfig.Data data = BstConfig.get();
        if (data.soundScapeCalibration == null) {
            return new BstConfig.Data.TransducerCalibration();
        }
        BstConfig.Data.TransducerCalibration cal = data.soundScapeCalibration.get(ch);
        return (cal == null) ? new BstConfig.Data.TransducerCalibration() : cal;
    }

    private static final class GainDbSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        private static final double MIN_DB = -12.0;
        private static final double MAX_DB = 12.0;

        GainDbSlider(int x, int y, int width, int height, double initialDb) {
            super(x, y, width, height, Component.empty(), to01(initialDb));
            updateMessage();
        }

        @Override
        @SuppressWarnings("null")
        protected void updateMessage() {
            double db = from01(this.value);
            this.setMessage(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_cal.gain"))
                    .append(": ")
                    .append(Objects.requireNonNull(Component.literal(String.format(Locale.ROOT, "%.1f dB", db))))
            );
        }

        @Override
        protected void applyValue() {
        }

        double getGainDb() {
            return from01(this.value);
        }

        void setGainDb(double db) {
            this.value = to01(db);
            updateMessage();
        }

        private static double to01(double db) {
            double v = (clamp(db, MIN_DB, MAX_DB) - MIN_DB) / (MAX_DB - MIN_DB);
            return clamp(v, 0.0, 1.0);
        }

        private static double from01(double v01) {
            double v = clamp(v01, 0.0, 1.0);
            return MIN_DB + (v * (MAX_DB - MIN_DB));
        }

        private static double clamp(double v, double a, double b) {
            if (v < a) return a;
            if (v > b) return b;
            return v;
        }
    }

    private static final class ComfortSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        ComfortSlider(int x, int y, int width, int height, double initial01) {
            super(x, y, width, height, Component.empty(), clamp01(initial01));
            updateMessage();
        }

        @Override
        @SuppressWarnings("null")
        protected void updateMessage() {
            int pct = (int) Math.round(getComfortLimit01() * 100.0);
            this.setMessage(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_cal.comfort"))
                    .append(": ")
                    .append(Objects.requireNonNull(Component.literal(pct + "%")))
            );
        }

        @Override
        protected void applyValue() {
        }

        double getComfortLimit01() {
            return clamp01(this.value);
        }

        void setComfortLimit01(double v01) {
            this.value = clamp01(v01);
            updateMessage();
        }

        private static double clamp01(double v) {
            if (!Double.isFinite(v)) return 1.0;
            if (v < 0.0) return 0.0;
            if (v > 1.0) return 1.0;
            return v;
        }
    }

    private static final class RmsTargetSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        RmsTargetSlider(int x, int y, int width, int height, double initial01) {
            super(x, y, width, height, Component.empty(), clamp01(initial01));
            updateMessage();
        }

        @Override
        @SuppressWarnings("null")
        protected void updateMessage() {
            int pct = (int) Math.round(getTargetRms01() * 100.0);
            this.setMessage(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_cal.rms_target"))
                    .append(": ")
                    .append(Objects.requireNonNull(Component.literal(pct + "%")))
            );
        }

        @Override
        protected void applyValue() {
        }

        double getTargetRms01() {
            return clamp01(this.value);
        }

        private static double clamp01(double v) {
            if (!Double.isFinite(v)) return 0.25;
            if (v < 0.01) return 0.01;
            if (v > 0.80) return 0.80;
            return v;
        }
    }

    private static final class EqFreqSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        private static final int MIN_HZ = 20;
        private static final int MAX_HZ = 120;

        EqFreqSlider(int x, int y, int width, int height, int initialHz) {
            super(x, y, width, height, Component.empty(), to01(initialHz));
            updateMessage();
        }

        @Override
        @SuppressWarnings("null")
        protected void updateMessage() {
            this.setMessage(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_cal.eq_freq"))
                    .append(": ")
                    .append(Objects.requireNonNull(Component.literal(getFreqHz() + " Hz")))
            );
        }

        @Override
        protected void applyValue() {
        }

        int getFreqHz() {
            return from01(this.value);
        }

        void setFreqHz(int hz) {
            this.value = to01(hz);
            updateMessage();
        }

        private static double to01(int hz) {
            int h = clampInt(hz, MIN_HZ, MAX_HZ);
            return (h - MIN_HZ) / (double) (MAX_HZ - MIN_HZ);
        }

        private static int from01(double v01) {
            double v = clamp(v01, 0.0, 1.0);
            int hz = (int) Math.round(MIN_HZ + (v * (MAX_HZ - MIN_HZ)));
            return clampInt(hz, MIN_HZ, MAX_HZ);
        }

        private static int clampInt(int v, int a, int b) {
            if (v < a) return a;
            if (v > b) return b;
            return v;
        }

        private static double clamp(double v, double a, double b) {
            if (v < a) return a;
            if (v > b) return b;
            return v;
        }
    }

    private static final class EqGainSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        private static final int MIN_DB = -12;
        private static final int MAX_DB = 12;

        EqGainSlider(int x, int y, int width, int height, int initialDb) {
            super(x, y, width, height, Component.empty(), to01(initialDb));
            updateMessage();
        }

        @Override
        @SuppressWarnings("null")
        protected void updateMessage() {
            int db = getGainDb();
            this.setMessage(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_cal.eq_gain"))
                    .append(": ")
                    .append(Objects.requireNonNull(Component.literal(db + " dB")))
            );
        }

        @Override
        protected void applyValue() {
        }

        int getGainDb() {
            return from01(this.value);
        }

        void setGainDb(int db) {
            this.value = to01(db);
            updateMessage();
        }

        private static double to01(int db) {
            int d = clampInt(db, MIN_DB, MAX_DB);
            return (d - MIN_DB) / (double) (MAX_DB - MIN_DB);
        }

        private static int from01(double v01) {
            double v = clamp(v01, 0.0, 1.0);
            int db = (int) Math.round(MIN_DB + (v * (MAX_DB - MIN_DB)));
            return clampInt(db, MIN_DB, MAX_DB);
        }

        private static int clampInt(int v, int a, int b) {
            if (v < a) return a;
            if (v > b) return b;
            return v;
        }

        private static double clamp(double v, double a, double b) {
            if (v < a) return a;
            if (v > b) return b;
            return v;
        }
    }
}
