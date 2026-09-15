package com.smoky.bassshakertelemetry.fabric;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class FabricConfigScreen extends Screen {
    private final Screen parent;

    FabricConfigScreen(Screen parent) {
        super(Component.literal("Bass Shaker Telemetry"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        BstConfig.Data config = BstConfig.get();
        int center = width / 2;
        addRenderableWidget(Button.builder(enabledLabel(config), button -> {
            config.enabled = !config.enabled;
            BstConfig.set(config);
            if (config.enabled) {
                AudioOutputEngine.get().startOrRestart();
            } else {
                AudioOutputEngine.get().stop();
            }
            button.setMessage(enabledLabel(config));
        }).bounds(center - 100, height / 2 - 30, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Test haptic"), button ->
                AudioOutputEngine.get().testDamageBurst()).bounds(center - 100, height / 2, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(center - 100, height / 2 + 30, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 70, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal("Config, profiles, instruments, and JavaSound are active."),
                width / 2, height / 2 - 52, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        BstConfig.save();
        Minecraft.getInstance().setScreen(parent);
    }

    private static Component enabledLabel(BstConfig.Data config) {
        return Component.literal("Haptics: " + (config.enabled ? "Enabled" : "Disabled"));
    }
}