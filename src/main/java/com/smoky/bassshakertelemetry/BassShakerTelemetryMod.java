package com.smoky.bassshakertelemetry;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.client.ClientInit;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BassShakerTelemetryMod.MODID)
public final class BassShakerTelemetryMod {
    public static final String MODID = "bassshakertelemetry";

    public BassShakerTelemetryMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        BstConfig.load();

        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientInit::init);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (BstConfig.get().enabled()) {
                AudioOutputEngine.get().startOrRestart();
            }
        });
    }
}
