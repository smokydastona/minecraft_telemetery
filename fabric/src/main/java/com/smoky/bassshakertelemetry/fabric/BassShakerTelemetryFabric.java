package com.smoky.bassshakertelemetry.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import com.smoky.bassshakertelemetry.config.BstHapticInstruments;
import com.smoky.bassshakertelemetry.config.BstVibrationProfiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BassShakerTelemetryFabric implements ModInitializer {
    public static final String MOD_ID = "bassshakertelemetry";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        System.setProperty("bst.config.dir", FabricLoader.getInstance().getConfigDir().toString());
        BstConfig.load();
        BstVibrationProfiles.load();
        BstHapticInstruments.load();
        LOGGER.info("Bass Shaker Telemetry Fabric initialized; config path is {}", BstConfig.path());
    }
}