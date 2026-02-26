package com.smoky.bassshakertelemetry.client.ui.neon.schema;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NeonUiSchemaLoader {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation DEFAULT_SCHEMA = new ResourceLocation("bassshakertelemetry", "neon/neon_schema.json");

    private NeonUiSchemaLoader() {
    }

    public static boolean hasActiveScreen(String screenId) {
        return loadActiveScreenOrNull(screenId) != null;
    }

    public static NeonUiSchema.ScreenSchema loadActiveScreenOrNull(String screenId) {
        NeonUiSchema schema = loadActiveOrNull();
        if (schema == null || schema.screens == null) return null;
        return schema.screens.get(screenId);
    }

    public static NeonUiSchema loadActiveOrNull() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return null;

            // Order: disk override -> disk remote -> built-in.
            NeonUiSchema fromOverride = tryLoadFromDiskRoot(NeonStyle.diskOverrideRoot());
            if (fromOverride != null) return fromOverride;

            NeonUiSchema fromRemote = tryLoadFromDiskRoot(NeonStyle.diskRemoteRoot());
            if (fromRemote != null) return fromRemote;

            ResourceManager rm = mc.getResourceManager();
            if (rm == null) return null;

            return tryLoadFromResources(rm);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static NeonUiSchema tryLoadFromDiskRoot(Path bundleRoot) {
        try {
            if (bundleRoot == null) return null;
            Path schemaPath = bundleRoot
                    .resolve("assets")
                    .resolve("bassshakertelemetry")
                    .resolve("neon")
                    .resolve("neon_schema.json");
            if (!Files.isRegularFile(schemaPath)) return null;

            try (var reader = Files.newBufferedReader(schemaPath, StandardCharsets.UTF_8)) {
                NeonUiSchema schema = GSON.fromJson(reader, NeonUiSchema.class);
                if (schema == null || schema.screens == null) return null;
                return schema;
            }
        } catch (JsonParseException ignored) {
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static NeonUiSchema tryLoadFromResources(ResourceManager rm) {
        try {
            var opt = rm.getResource(DEFAULT_SCHEMA);
            if (opt.isEmpty()) return null;
            Resource res = opt.get();
            try (var inStream = res.open();
                 var reader = new InputStreamReader(inStream, StandardCharsets.UTF_8)) {
                NeonUiSchema schema = GSON.fromJson(reader, NeonUiSchema.class);
                if (schema == null || schema.screens == null) return null;
                return schema;
            }
        } catch (JsonParseException ignored) {
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
