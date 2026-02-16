package com.smoky.bassshakertelemetry.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.smoky.bassshakertelemetry.BassShakerTelemetryMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BstConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = BassShakerTelemetryMod.MODID + ".json";
    private static volatile Data INSTANCE = new Data();

    private BstConfig() {
    }

    public static Data get() {
        return INSTANCE;
    }

    public static synchronized void set(Data data) {
        INSTANCE = data;
        save();
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    public static synchronized void load() {
        var path = path();
        if (!Files.exists(path)) {
            save();
            return;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Data parsed = GSON.fromJson(json, Data.class);
            if (parsed != null) {
                INSTANCE = parsed;
            }
        } catch (Exception ignored) {
            // If config is corrupt, keep defaults.
        }
    }

    public static synchronized void save() {
        var path = path();
        try {
            Files.createDirectories(path.getParent());
            String json = GSON.toJson(INSTANCE);
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static final class Data {
        // Master switches
        public boolean enabled = true;

        // Audio output
        public String outputDeviceName = ""; // empty = default
        public double masterVolume = 0.35;

        // Telemetry-to-signal mapping toggles
        public boolean speedToneEnabled = true;
        public boolean damageBurstEnabled = true;
        public boolean biomeChimeEnabled = true;

        // Simulated road texture (low-frequency rumble layer)
        public boolean roadTextureEnabled = true;

        // Signal parameters
        public double speedToneBaseHz = 35.0;
        public double speedToneHzPerSpeed = 90.0; // speed is blocks/tick-ish from delta movement
        public double accelToAmp = 3.0;

        // Output safety / mixing
        // Headroom scales the final output before int16 conversion to reduce clipping when effects stack.
        public double outputHeadroom = 0.85;
        // Soft-limiter drive. Higher = more saturation and less peak clipping.
        public double limiterDrive = 2.5;

        public int damageBurstMs = 90;
        public double damageBurstGain = 0.8;

        // Road texture tuning
        public double roadTextureGain = 0.22;
        public double roadTextureCutoffHz = 30.0;

        // Accel bump (short one-shot thump derived from acceleration spikes)
        public boolean accelBumpEnabled = true;
        public double accelBumpThreshold = 0.075;
        public int accelBumpMs = 60;
        public double accelBumpGain = 0.65;

        public boolean enabled() {
            return enabled;
        }
    }
}
