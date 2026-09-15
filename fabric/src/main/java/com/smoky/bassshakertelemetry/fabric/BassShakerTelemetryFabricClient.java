package com.smoky.bassshakertelemetry.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import com.mojang.blaze3d.platform.InputConstants;

public final class BassShakerTelemetryFabricClient implements ClientModInitializer {
    private static KeyMapping configKey;
    private static final FabricWebSocketController WEB_SOCKET = new FabricWebSocketController();

    @Override
    public void onInitializeClient() {
        if (BstConfig.get().enabled) {
            AudioOutputEngine.get().startOrRestart();
        }
        configKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.bassshakertelemetry.config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                "key.categories.bassshakertelemetry"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WEB_SOCKET.tick();
            while (configKey.consumeClick()) {
                client.setScreen(new FabricConfigScreen(client.screen));
            }
            FabricClientLifecycle.onEndTick(client);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> WEB_SOCKET.stop());
    }
}