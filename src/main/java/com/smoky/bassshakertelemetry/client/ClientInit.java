package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.client.accessibility.HudCueOverlayHandler;
import com.smoky.bassshakertelemetry.client.accessibility.HudCueTickHandler;
import com.smoky.bassshakertelemetry.client.integration.WebSocketTelemetryController;
import com.smoky.bassshakertelemetry.client.ui.SchemaTelemetryConfigScreen;
import com.smoky.bassshakertelemetry.client.ui.TelemetryConfigScreen;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonStyle;
import com.smoky.bassshakertelemetry.client.ui.neon.UiBundleAutoUpdater;
import com.smoky.bassshakertelemetry.client.ui.neon.schema.NeonUiSchemaLoader;
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
        MinecraftForge.EVENT_BUS.register(new DamageHapticsHandler());
        MinecraftForge.EVENT_BUS.register(new SoundHapticsHandler());
        MinecraftForge.EVENT_BUS.register(new GameplayHapticsHandler());
        MinecraftForge.EVENT_BUS.register(new MovementHapticsHandler());
        MinecraftForge.EVENT_BUS.register(new MountedHapticsHandler());
        MinecraftForge.EVENT_BUS.register(new WardenHeartbeatHapticsHandler());
        MinecraftForge.EVENT_BUS.register(new MiningSwingHapticsHandler());
        MinecraftForge.EVENT_BUS.register(new DebugOverlayHandler());

        MinecraftForge.EVENT_BUS.register(new HudCueTickHandler());
        MinecraftForge.EVENT_BUS.register(new HudCueOverlayHandler());

        MinecraftForge.EVENT_BUS.register(new WebSocketTelemetryController());

        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(ClientInit::createConfigScreen)
        );

        UiBundleAutoUpdater.startIfEnabled();
        NeonStyle.initClient();
    }

    private static Screen createConfigScreen(Minecraft minecraft, Screen parent) {
        if (NeonUiSchemaLoader.hasActiveScreen("telemetry_config")) {
            return new SchemaTelemetryConfigScreen(parent);
        }
        return new TelemetryConfigScreen(parent);
    }
}
