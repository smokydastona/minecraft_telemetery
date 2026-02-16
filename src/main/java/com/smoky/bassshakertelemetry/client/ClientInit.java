package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.client.ui.TelemetryConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;

public final class ClientInit {
    private ClientInit() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new TelemetryEventHandler());
        MinecraftForge.EVENT_BUS.register(new SoundHapticsHandler());
        MinecraftForge.EVENT_BUS.register(new GameplayHapticsHandler());

        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(ClientInit::createConfigScreen)
        );
    }

    private static Screen createConfigScreen(Minecraft minecraft, Screen parent) {
        return new TelemetryConfigScreen(parent);
    }
}
