package com.smoky.bassshakertelemetry.net;

import com.smoky.bassshakertelemetry.BassShakerTelemetryMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BstNet {
    private static final String PROTOCOL_VERSION = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(BassShakerTelemetryMod.MODID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static boolean initialized;

    private BstNet() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        int id = 0;
        CHANNEL.messageBuilder(PlayVibrationMessage.class, id++)
                .encoder(PlayVibrationMessage::encode)
                .decoder(PlayVibrationMessage::decode)
                .consumerMainThread(PlayVibrationMessage::handle)
                .add();
    }
}
