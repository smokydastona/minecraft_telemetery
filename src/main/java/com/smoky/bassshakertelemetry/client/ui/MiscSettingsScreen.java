package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonCycleButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonRangeSlider;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonStyle;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

import java.util.List;
import java.util.Objects;

public final class MiscSettingsScreen extends Screen {
    private final Screen parent;

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
    }

    @Override
    protected void init() {
        Font font = Objects.requireNonNull(this.font, "font");
        NeonStyle.initClient();

        int contentWidth = Math.min(320, this.width - 40);
        int leftX = (this.width - contentWidth) / 2;

        int rowH = 20;
        int rowGap = 6;

        this.addRenderableWidget(new StringWidget(
                leftX,
                18,
                contentWidth,
                20,
            tr("bassshakertelemetry.config.misc_title"),
                font
        ));

        int y = 46;

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

        y += rowH + (rowGap * 2);

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

        int buttonW = (contentWidth - 10) / 2;
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
        BstConfig.Data data = BstConfig.get();

        data.soundHapticsEnabled = soundHapticsEnabled;
        data.soundHapticsGain = clamp02(soundHapticsGain);

        data.gameplayHapticsEnabled = gameplayHapticsEnabled;
        data.gameplayHapticsGain = clamp02(gameplayHapticsGain);

        data.biomeChimeEnabled = biomeChimeEnabled;
        data.biomeChimeGain = clamp01(biomeChimeGain);

        data.accessibilityHudEnabled = accessibilityHudEnabled;

        BstConfig.set(data);
        AudioOutputEngine.get().startOrRestart();

        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(parent);
        }
    }

    private void onCancel() {
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
