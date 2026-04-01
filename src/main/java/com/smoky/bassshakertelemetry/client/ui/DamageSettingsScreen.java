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

public final class DamageSettingsScreen extends Screen {
    private final Screen parent;

    private boolean damageEnabled;
    private double damageGain;

    private boolean meleeEnabled;
    private double meleeGain;

    private boolean hitConfirmEnabled;
    private double hitConfirmGain;

    private NeonRangeSlider damageGainSlider;
    private NeonRangeSlider meleeGainSlider;
    private NeonRangeSlider hitConfirmGainSlider;

    public DamageSettingsScreen(Screen parent) {
        super(tr("bassshakertelemetry.config.damage_title"));
        this.parent = parent;

        BstConfig.Data cfg = BstConfig.get();
        this.damageEnabled = cfg.damageBurstEnabled;
        this.damageGain = clamp02(cfg.damageBurstGain);

        this.meleeEnabled = cfg.combatMeleeEnabled;
        this.meleeGain = clamp02(cfg.combatMeleeGain);

        this.hitConfirmEnabled = cfg.combatHitConfirmEnabled;
        this.hitConfirmGain = clamp02(cfg.combatHitConfirmGain);
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
            tr("bassshakertelemetry.config.damage_title"),
                font
        ));

        int y = 46;

        this.addRenderableWidget(new NeonCycleButton<>(
                leftX,
                y,
                contentWidth,
                rowH,
            tr("bassshakertelemetry.config.damage_incoming"),
                List.of(Boolean.TRUE, Boolean.FALSE),
                damageEnabled,
            v -> v ? tr("options.on") : tr("options.off"),
                v -> damageEnabled = v
        ));

        y += rowH + rowGap;

        damageGainSlider = new NeonRangeSlider(
                leftX,
                y,
                contentWidth,
                rowH,
            tr("bassshakertelemetry.config.damage_gain"),
                0.0,
            2.0,
                0.01,
                "percent",
                () -> damageGain,
            v -> damageGain = clamp02(v)
        );
        this.addRenderableWidget(damageGainSlider);

        y += rowH + (rowGap * 2);

        this.addRenderableWidget(new NeonCycleButton<>(
                leftX,
                y,
                contentWidth,
                rowH,
            tr("bassshakertelemetry.config.combat_melee"),
                List.of(Boolean.TRUE, Boolean.FALSE),
                meleeEnabled,
            v -> v ? tr("options.on") : tr("options.off"),
                v -> meleeEnabled = v
        ));

        y += rowH + rowGap;

        meleeGainSlider = new NeonRangeSlider(
                leftX,
                y,
                contentWidth,
                rowH,
            tr("bassshakertelemetry.config.combat_melee_gain"),
                0.0,
                2.0,
                0.01,
                "percent",
                () -> meleeGain,
                v -> meleeGain = clamp02(v)
        );
        this.addRenderableWidget(meleeGainSlider);

        y += rowH + (rowGap * 2);

        this.addRenderableWidget(new NeonCycleButton<>(
                leftX,
                y,
                contentWidth,
                rowH,
            tr("bassshakertelemetry.config.combat_hit_confirm"),
                List.of(Boolean.TRUE, Boolean.FALSE),
                hitConfirmEnabled,
            v -> v ? tr("options.on") : tr("options.off"),
                v -> hitConfirmEnabled = v
        ));

        y += rowH + rowGap;

        hitConfirmGainSlider = new NeonRangeSlider(
                leftX,
                y,
                contentWidth,
                rowH,
            tr("bassshakertelemetry.config.combat_hit_confirm_gain"),
                0.0,
                2.0,
                0.01,
                "percent",
                () -> hitConfirmGain,
                v -> hitConfirmGain = clamp02(v)
        );
        this.addRenderableWidget(hitConfirmGainSlider);

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
        data.damageBurstEnabled = damageEnabled;
        data.damageBurstGain = clamp02(damageGain);

        data.combatMeleeEnabled = meleeEnabled;
        data.combatMeleeGain = clamp02(meleeGain);

        data.combatHitConfirmEnabled = hitConfirmEnabled;
        data.combatHitConfirmGain = clamp02(hitConfirmGain);

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
        if (damageGainSlider != null) damageGainSlider.active = damageEnabled;
        if (meleeGainSlider != null) meleeGainSlider.active = meleeEnabled;
        if (hitConfirmGainSlider != null) hitConfirmGainSlider.active = hitConfirmEnabled;

        super.render(guiGraphics, mouseX, mouseY, partialTick);
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
