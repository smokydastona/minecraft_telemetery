package com.smoky.bassshakertelemetry.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FabricConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve(BassShakerTelemetryFabric.MOD_ID + ".fabric.json");
    private static volatile Data instance = new Data();

    private FabricConfig() {
    }

    public static Data get() {
        return instance;
    }

    public static Path path() {
        return PATH;
    }

    public static synchronized void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }

        try {
            Data parsed = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), Data.class);
            if (parsed != null) {
                instance = sanitize(parsed);
            }
        } catch (Exception ignored) {
            instance = new Data();
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(sanitize(instance)), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            BassShakerTelemetryFabric.LOGGER.warn("Unable to write Fabric config at {}", PATH);
        }
    }

    private static Data sanitize(Data data) {
        Data normalized = data == null ? new Data() : data;
        normalized.configVersion = Math.max(1, normalized.configVersion);
        normalized.webSocketPort = Math.max(1, Math.min(65535, normalized.webSocketPort));
        return normalized;
    }

    public static final class Data {
        private int configVersion = 1;
        private boolean enabled = true;
        private int webSocketPort = 7117;

        public int configVersion() {
            return configVersion;
        }

        public boolean enabled() {
            return enabled;
        }

        public int webSocketPort() {
            return webSocketPort;
        }
    }
}