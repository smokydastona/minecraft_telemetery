package com.smoky.bassshakertelemetry.neoforge;

import com.smoky.bassshakertelemetry.config.BstConfig;
import com.smoky.bassshakertelemetry.config.BstHapticInstruments;
import com.smoky.bassshakertelemetry.config.BstVibrationProfiles;
import net.neoforged.fml.common.Mod;

@Mod(BassShakerTelemetryNeoForge.MOD_ID)
public final class BassShakerTelemetryNeoForge {
    public static final String MOD_ID = "bassshakertelemetry";

    public BassShakerTelemetryNeoForge() {
        BstConfig.load();
        BstVibrationProfiles.load();
        BstHapticInstruments.load();
    }
}