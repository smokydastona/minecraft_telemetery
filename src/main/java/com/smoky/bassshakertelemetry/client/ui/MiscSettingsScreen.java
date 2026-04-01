package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonCycleButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonRangeSlider;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonStyle;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonToggleButton;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

import java.util.List;
import java.util.Objects;

public final class MiscSettingsScreen extends Screen {
    private final Screen parent;

    private int pageIndex = 0;
    private int pageCount = 1;

    private boolean soundHapticsEnabled;
    private double soundHapticsGain;

    private boolean gameplayHapticsEnabled;
    private double gameplayHapticsGain;

    private boolean biomeChimeEnabled;
    private double biomeChimeGain;

    private boolean accessibilityHudEnabled;

    private NeonRangeSlider soundGainSlider;
    private NeonRangeSlider gameplayGainSlider;
    private NeonRangeSlider biomeChimeGainSlider;

    // Tools moved from Advanced
    private Button latencyTestButton;
    private boolean latencyTestActive;
    private int latencyTestTicks;

    private Button demoButton;
    private boolean demoActive;
    private int demoStep;
    private long demoNextNanos;

    private Button debugOverlayButton;
    private boolean debugOverlayEnabled;

    public MiscSettingsScreen(Screen parent) {
        super(tr("bassshakertelemetry.config.misc_title"));
        this.parent = parent;

        BstConfig.Data cfg = BstConfig.get();
        this.soundHapticsEnabled = cfg.soundHapticsEnabled;
        this.soundHapticsGain = clamp02(cfg.soundHapticsGain);

        this.gameplayHapticsEnabled = cfg.gameplayHapticsEnabled;
        this.gameplayHapticsGain = clamp02(cfg.gameplayHapticsGain);

        this.biomeChimeEnabled = cfg.biomeChimeEnabled;
        this.biomeChimeGain = clamp01(cfg.biomeChimeGain);

        this.accessibilityHudEnabled = cfg.accessibilityHudEnabled;

        this.debugOverlayEnabled = cfg.debugOverlayEnabled;
    }

    @Override
    protected void init() {
        Font font = Objects.requireNonNull(this.font, "font");
        NeonStyle.initClient();

        int contentWidth = Math.min(320, this.width - 40);
        int leftX = (this.width - contentWidth) / 2;

        int rowH = 20;
        int rowGap = 6;

        // Clear per-init widget refs
        soundGainSlider = null;
        gameplayGainSlider = null;
        biomeChimeGainSlider = null;

        latencyTestButton = null;
        demoButton = null;
        debugOverlayButton = null;

        pageCount = 3; // Haptics, Accessibility, Tools
        if (pageIndex < 0) pageIndex = 0;
        if (pageIndex >= pageCount) pageIndex = pageCount - 1;

        this.addRenderableWidget(new StringWidget(
                leftX,
                18,
                contentWidth,
                20,
            tr("bassshakertelemetry.config.misc_title"),
                font
        ));

        // Page label
        this.addRenderableWidget(new StringWidget(
                leftX,
                36,
                contentWidth,
                14,
                pageLabel(),
                font
        ));

        int y = 46;

        if (pageIndex == 0) {
            // Haptics

            this.addRenderableWidget(new NeonCycleButton<>(
                    leftX,
                    y,
                    contentWidth,
                    rowH,
                    tr("bassshakertelemetry.config.sound_haptics"),
                    List.of(Boolean.TRUE, Boolean.FALSE),
                    soundHapticsEnabled,
                    v -> v ? tr("options.on") : tr("options.off"),
                    v -> soundHapticsEnabled = v
            ));

            y += rowH + rowGap;

            soundGainSlider = new NeonRangeSlider(
                    leftX,
                    y,
                    contentWidth,
                    rowH,
                    tr("bassshakertelemetry.config.sound_gain"),
                    0.0,
                    2.0,
                    0.01,
                    "percent",
                    () -> soundHapticsGain,
                    v -> soundHapticsGain = clamp02(v)
            );
            this.addRenderableWidget(soundGainSlider);

            y += rowH + (rowGap * 2);

            this.addRenderableWidget(new NeonCycleButton<>(
                    leftX,
                    y,
                    contentWidth,
                    rowH,
                    tr("bassshakertelemetry.config.gameplay_haptics"),
                    List.of(Boolean.TRUE, Boolean.FALSE),
                    gameplayHapticsEnabled,
                    v -> v ? tr("options.on") : tr("options.off"),
                    v -> gameplayHapticsEnabled = v
            ));

            y += rowH + rowGap;

            gameplayGainSlider = new NeonRangeSlider(
                    leftX,
                    y,
                    contentWidth,
                    rowH,
                    tr("bassshakertelemetry.config.gameplay_gain"),
                    0.0,
                    2.0,
                    0.01,
                    "percent",
                    () -> gameplayHapticsGain,
                    v -> gameplayHapticsGain = clamp02(v)
            );
            this.addRenderableWidget(gameplayGainSlider);

            y += rowH + (rowGap * 2);

            this.addRenderableWidget(new NeonCycleButton<>(
                    leftX,
                    y,
                    contentWidth,
                    rowH,
                    tr("bassshakertelemetry.config.biome_enabled"),
                    List.of(Boolean.TRUE, Boolean.FALSE),
                    biomeChimeEnabled,
                    v -> v ? tr("options.on") : tr("options.off"),
                    v -> biomeChimeEnabled = v
            ));

            y += rowH + rowGap;

            biomeChimeGainSlider = new NeonRangeSlider(
                    leftX,
                    y,
                    contentWidth,
                    rowH,
                    tr("bassshakertelemetry.config.biome_gain"),
                    0.0,
                    1.0,
                    0.01,
                    "percent",
                    () -> biomeChimeGain,
                    v -> biomeChimeGain = clamp01(v)
            );
            this.addRenderableWidget(biomeChimeGainSlider);
        } else if (pageIndex == 1) {
            // Accessibility
            this.addRenderableWidget(new NeonCycleButton<>(
                    leftX,
                    y,
                    contentWidth,
                    rowH,
                    tr("bassshakertelemetry.config.accessibility_hud"),
                    List.of(Boolean.TRUE, Boolean.FALSE),
                    accessibilityHudEnabled,
                    v -> v ? tr("options.on") : tr("options.off"),
                    v -> accessibilityHudEnabled = v
            ));
        } else {
            // Tools
            latencyTestButton = new NeonButton(
                    leftX,
                    y,
                    contentWidth,
                    rowH,
                    Objects.requireNonNull(latencyButtonLabel(false)),
                    this::toggleLatencyTest
            );
            this.addRenderableWidget(latencyTestButton);

            y += rowH + rowGap;

            debugOverlayButton = new NeonToggleButton(
                    leftX,
                    y,
                    contentWidth,
                    rowH,
                    Objects.requireNonNull(debugOverlayLabel()),
                    () -> debugOverlayEnabled,
                    this::toggleDebugOverlay
            );
            this.addRenderableWidget(debugOverlayButton);

            y += rowH + rowGap;

            demoButton = new NeonButton(
                    leftX,
                    y,
                    contentWidth,
                    rowH,
                    Objects.requireNonNull(demoLabel()),
                    this::toggleDemo
            );
            this.addRenderableWidget(demoButton);
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

    private void onDone() {
        stopLatencyTest();
        stopDemo();

        BstConfig.Data data = BstConfig.get();

        data.soundHapticsEnabled = soundHapticsEnabled;
        data.soundHapticsGain = clamp02(soundHapticsGain);

        data.gameplayHapticsEnabled = gameplayHapticsEnabled;
        data.gameplayHapticsGain = clamp02(gameplayHapticsGain);

        data.biomeChimeEnabled = biomeChimeEnabled;
        data.biomeChimeGain = clamp01(biomeChimeGain);

        data.accessibilityHudEnabled = accessibilityHudEnabled;

        data.debugOverlayEnabled = debugOverlayEnabled;

        BstConfig.set(data);
        AudioOutputEngine.get().startOrRestart();

        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(parent);
        }
    }

    private void onCancel() {
        stopLatencyTest();
        stopDemo();
        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        onCancel();
    }

    @Override
    public void tick() {
        super.tick();

        if (latencyTestActive) {
            latencyTestTicks++;
            boolean pulseNow = (latencyTestTicks % 10) == 0; // 2 Hz at 20 tps
            if (pulseNow) {
                AudioOutputEngine.get().testLatencyPulse();
            }
            if (latencyTestButton != null) {
                latencyTestButton.setMessage(Objects.requireNonNull(latencyButtonLabel(pulseNow)));
            }
        }

        tickDemo();
    }

    @Override
    @SuppressWarnings("null")
    public void renderBackground(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, NeonStyle.get().background);
    }

    @Override
    @SuppressWarnings("null")
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (soundGainSlider != null) soundGainSlider.active = soundHapticsEnabled;
        if (gameplayGainSlider != null) gameplayGainSlider.active = gameplayHapticsEnabled;
        if (biomeChimeGainSlider != null) biomeChimeGainSlider.active = biomeChimeEnabled;

        super.render(guiGraphics, mouseX, mouseY, partialTick);
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
        this.init();
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

    private static double clamp01(double v) {
        if (!Double.isFinite(v)) return 0.0;
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static double clamp02(double v) {
        if (!Double.isFinite(v)) return 0.0;
        if (v < 0.0) return 0.0;
        if (v > 2.0) return 2.0;
        return v;
    }

    @Nonnull
    private static Component tr(@Nonnull String key) {
        return Objects.requireNonNull(Component.translatable(key));
    }
}
