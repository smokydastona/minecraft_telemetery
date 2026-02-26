package com.smoky.bassshakertelemetry.client.ui.neon;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public final class NeonAnimationTokens {
    private static final Gson GSON = new Gson();

    public final float brightnessIdle;
    public final float brightnessHover;
    public final float brightnessPressed;

    public final float hoverSpeed;
    public final float pressSpeed;
    public final float idleSpeed;

    private NeonAnimationTokens(
            float brightnessIdle,
            float brightnessHover,
            float brightnessPressed,
            float hoverSpeed,
            float pressSpeed,
            float idleSpeed
    ) {
        this.brightnessIdle = brightnessIdle;
        this.brightnessHover = brightnessHover;
        this.brightnessPressed = brightnessPressed;
        this.hoverSpeed = hoverSpeed;
        this.pressSpeed = pressSpeed;
        this.idleSpeed = idleSpeed;
    }

    public static NeonAnimationTokens defaults() {
        return new NeonAnimationTokens(1.0f, 1.15f, 0.9f, 0.25f, 0.18f, 0.2f);
    }

    @SuppressWarnings("null")
    public static NeonAnimationTokens loadOrDefault(ResourceManager resourceManager, ResourceLocation location) {
        try {
            var opt = resourceManager.getResource(location);
            if (opt.isEmpty()) return defaults();
            Resource res = opt.get();
            try (var reader = new InputStreamReader(res.open(), StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null) return defaults();
                return parse(root);
            }
        } catch (Exception ignored) {
            return defaults();
        }
    }

    public static NeonAnimationTokens loadOrDefaultFromDisk(Path jsonPath) {
        try {
            if (jsonPath == null) return defaults();
            if (!Files.isRegularFile(jsonPath)) return defaults();

            try (var reader = Files.newBufferedReader(jsonPath, StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null) return defaults();
                return parse(root);
            }
        } catch (Exception ignored) {
            return defaults();
        }
    }

    private static NeonAnimationTokens parse(JsonObject root) {
        NeonAnimationTokens d = defaults();

        JsonObject brightness = getObject(root, "brightness");
        float bIdle = getFloat(brightness, "idle", d.brightnessIdle);
        float bHover = getFloat(brightness, "hover", d.brightnessHover);
        float bPressed = getFloat(brightness, "pressed", d.brightnessPressed);

        JsonObject timing = getObject(root, "timing");
        float hoverSpeed = clamp01(getFloat(timing, "hover_speed", d.hoverSpeed));
        float pressSpeed = clamp01(getFloat(timing, "press_speed", d.pressSpeed));
        float idleSpeed = clamp01(getFloat(timing, "idle_speed", d.idleSpeed));

        return new NeonAnimationTokens(bIdle, bHover, bPressed, hoverSpeed, pressSpeed, idleSpeed);
    }

    private static JsonObject getObject(JsonObject obj, String key) {
        if (obj == null) return null;
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonObject()) return null;
        try {
            return e.getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static float getFloat(JsonObject obj, String key, float fallback) {
        if (obj == null) return fallback;
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonPrimitive()) return fallback;
        try {
            return e.getAsFloat();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float clamp01(float v) {
        if (v < 0.0f) return 0.0f;
        if (v > 1.0f) return 1.0f;
        return v;
    }
}
