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

        // JavaSound buffer size. 0 = auto/default line buffer.
        // Larger buffers tend to be more stable but add latency.
        public int javaSoundBufferMs = 0;

        // Developer tools
        public boolean debugOverlayEnabled = false;

        // Telemetry-to-signal mapping toggles
        public boolean damageBurstEnabled = true;
        public boolean biomeChimeEnabled = true;

        // Simulated road texture (low-frequency rumble layer)
        public boolean roadTextureEnabled = false;

        // Sound-to-haptics (treat common game sounds as rumble events)
        public boolean soundHapticsEnabled = true;
        public double soundHapticsGain = 1.0;
        public int soundHapticsCooldownMs = 35;

        // Gameplay-to-haptics (explicitly non-sexual, game-only)
        public boolean gameplayHapticsEnabled = true;
        public double gameplayHapticsGain = 1.0;
        public int gameplayHapticsCooldownMs = 80;

        // On-foot movement haptics (not car-like)
        public boolean footstepHapticsEnabled = true;
        public double footstepHapticsGain = 0.55;

        // Mounted haptics (hooves / flying mount wind)
        public boolean mountedHapticsEnabled = true;
        public double mountedHapticsGain = 0.55;

        // Swing-synced mining haptics (matches the on-screen arm swing timing)
        public boolean miningSwingHapticsEnabled = true;
        public double miningSwingHapticsGain = 0.55;

        public boolean gameplayAttackClickEnabled = true;
        public boolean gameplayUseClickEnabled = true;
        // Legacy periodic mining pulse (kept, but swing-synced mining is preferred)
        public boolean gameplayMiningPulseEnabled = false;
        public int gameplayMiningPulsePeriodMs = 120;
        public boolean gameplayXpEnabled = true;

        // Output safety / mixing
        // Headroom scales the final output before int16 conversion to reduce clipping when effects stack.
        public double outputHeadroom = 0.85;
        // Soft-limiter drive. Higher = more saturation and less peak clipping.
        public double limiterDrive = 2.5;

        public int damageBurstMs = 90;
        public double damageBurstGain = 0.8;

        // Biome chime tuning
        public double biomeChimeGain = 0.35;

        // Road texture tuning
        public double roadTextureGain = 0.18;
        public double roadTextureCutoffHz = 30.0;

        // Accel bump (short one-shot thump derived from acceleration spikes)
        public boolean accelBumpEnabled = false;
        public double accelBumpThreshold = 0.14;
        public int accelBumpMs = 60;
        public double accelBumpGain = 0.65;

        public boolean enabled() {
            return enabled;
        }
    }
}
