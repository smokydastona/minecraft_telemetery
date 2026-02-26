package com.smoky.bassshakertelemetry.client.ui.neon.schema;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NeonUiSchemaLoader {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation DEFAULT_SCHEMA = new ResourceLocation("bassshakertelemetry", "neon/neon_schema.json");
    private static final Logger LOGGER = LogManager.getLogger("bassshakertelemetry");
    private static final int SUPPORTED_SCHEMA_VERSION = 1;

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
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                return parseSchemaOrNull(root);
            }
        } catch (JsonParseException ignored) {
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static NeonUiSchema tryLoadFromResources(ResourceManager rm) {
        try {
            @SuppressWarnings("null")
            var opt = rm.getResource(DEFAULT_SCHEMA);
            if (opt.isEmpty()) return null;
            Resource res = opt.get();
            try (var inStream = res.open();
                 var reader = new InputStreamReader(inStream, StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                return parseSchemaOrNull(root);
            }
        } catch (JsonParseException ignored) {
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static NeonUiSchema parseSchemaOrNull(JsonObject root) {
        try {
            if (root == null) return null;
            NeonUiSchema schema = new NeonUiSchema();
            schema.version = getInt(root, "version", 0);
            if (schema.version <= 0) return null;

            if (schema.version != SUPPORTED_SCHEMA_VERSION) {
                LOGGER.warn("[BST] Neon UI schema version mismatch: got={} supported={}. Will attempt to load anyway.", schema.version, SUPPORTED_SCHEMA_VERSION);
            }

            JsonObject screens = getObj(root, "screens");
            if (screens == null) return null;

            java.util.Map<String, NeonUiSchema.ScreenSchema> map = new java.util.HashMap<>();
            for (var e : screens.entrySet()) {
                String id = e.getKey();
                JsonObject screenObj = asObj(e.getValue());
                if (id == null || id.isBlank() || screenObj == null) continue;

                NeonUiSchema.ScreenSchema screen = new NeonUiSchema.ScreenSchema();
                screen.titleKey = getString(screenObj, "titleKey", null);
                JsonObject rootNodeObj = getObj(screenObj, "root");
                if (rootNodeObj != null) {
                    screen.root = parseNodeOrNull(rootNodeObj);
                } else {
                    screen.root = parseLegacyRootFromRowsOrNull(screenObj);
                }
                if (screen.root == null) continue;
                map.put(id, screen);
            }

            if (map.isEmpty()) return null;
            schema.screens = map;
            return schema;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static NeonUiSchema.NeonUiNode parseNodeOrNull(JsonObject obj) {
        try {
            String type = getString(obj, "type", "");
            if (type.isBlank()) return null;

            NeonUiSchema.NeonUiNode node;
            switch (type) {
                case "panel" -> {
                    var p = new NeonUiSchema.PanelNode();
                    p.layout = getString(obj, "layout", "vertical");
                    p.children = parseChildren(obj);
                    node = p;
                }
                case "label" -> {
                    var n = new NeonUiSchema.LabelNode();
                    n.text = getString(obj, "text", null);
                    n.textKey = getString(obj, "textKey", null);
                    n.style = getString(obj, "style", null);
                    node = n;
                }
                case "button" -> {
                    var n = new NeonUiSchema.ButtonNode();
                    n.id = getString(obj, "id", null);
                    n.text = getString(obj, "text", null);
                    n.textKey = getString(obj, "textKey", null);
                    n.action = getString(obj, "action", null);
                    n.bind = getString(obj, "bind", null);
                    node = n;
                }
                case "toggle" -> {
                    var n = new NeonUiSchema.ToggleNode();
                    n.id = getString(obj, "id", null);
                    n.text = getString(obj, "text", null);
                    n.textKey = getString(obj, "textKey", null);
                    n.bind = getString(obj, "bind", null);
                    n.value = getBoolObj(obj, "value");
                    node = n;
                }
                case "slider" -> {
                    var n = new NeonUiSchema.SliderNode();
                    n.id = getString(obj, "id", null);
                    n.text = getString(obj, "text", null);
                    n.textKey = getString(obj, "textKey", null);
                    n.bind = getString(obj, "bind", null);
                    n.min = getDoubleObj(obj, "min");
                    n.max = getDoubleObj(obj, "max");
                    n.step = getDoubleObj(obj, "step");
                    n.value = getDoubleObj(obj, "value");
                    n.format = getString(obj, "format", null);
                    node = n;
                }
                case "cycle" -> {
                    var n = new NeonUiSchema.CycleNode();
                    n.id = getString(obj, "id", null);
                    n.text = getString(obj, "text", null);
                    n.textKey = getString(obj, "textKey", null);
                    n.bind = getString(obj, "bind", null);
                    n.options = parseStringArray(obj, "options");
                    n.value = getIntObj(obj, "value");
                    node = n;
                }
                case "spacer" -> {
                    var n = new NeonUiSchema.SpacerNode();
                    n.size = getIntObj(obj, "size");
                    node = n;
                }
                default -> {
                    return null;
                }
            }

            node.type = type;
            node.padding = getIntObj(obj, "padding");
            node.spacing = getIntObj(obj, "spacing");
            node.margin = getIntObj(obj, "margin");
            node.width = getIntObj(obj, "width");
            node.height = getIntObj(obj, "height");
            node.align = getString(obj, "align", null);
            return node;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Back-compat: MVP schema used `rows` + `hstack`. Convert to a root panel tree.
     * This keeps older cached bundles functional while auto-update rolls out.
     */
    private static NeonUiSchema.NeonUiNode parseLegacyRootFromRowsOrNull(JsonObject screenObj) {
        try {
            JsonElement rowsEl = screenObj.get("rows");
            if (rowsEl == null || !rowsEl.isJsonArray()) return null;

            var root = new NeonUiSchema.PanelNode();
            root.type = "panel";
            root.layout = "vertical";
            root.spacing = 6;
            root.padding = 0;
            root.children = new java.util.ArrayList<>();

            for (var rowEl : rowsEl.getAsJsonArray()) {
                JsonObject rowObj = asObj(rowEl);
                if (rowObj == null) continue;
                NeonUiSchema.NeonUiNode node = parseLegacyNodeOrNull(rowObj);
                if (node != null) root.children.add(node);
            }

            if (root.children.isEmpty()) return null;
            return root;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static NeonUiSchema.NeonUiNode parseLegacyNodeOrNull(JsonObject obj) {
        try {
            String type = getString(obj, "type", "");
            if (type.isBlank()) return null;

            switch (type) {
                case "hstack" -> {
                    int gap = getInt(obj, "gap", 10);
                    var p = new NeonUiSchema.PanelNode();
                    p.type = "panel";
                    p.layout = "horizontal";
                    p.spacing = Math.max(0, gap);
                    p.children = new java.util.ArrayList<>();

                    JsonElement childrenEl = obj.get("children");
                    if (childrenEl != null && childrenEl.isJsonArray()) {
                        for (var childEl : childrenEl.getAsJsonArray()) {
                            JsonObject childObj = asObj(childEl);
                            if (childObj == null) continue;
                            NeonUiSchema.NeonUiNode child = parseLegacyNodeOrNull(childObj);
                            if (child != null) p.children.add(child);
                        }
                    }

                    return p.children.isEmpty() ? null : p;
                }
                case "device_button" -> {
                    var n = new NeonUiSchema.ButtonNode();
                    n.type = "button";
                    n.id = getString(obj, "id", null);
                    n.textKey = getString(obj, "textKey", null);
                    n.action = getString(obj, "action", "openOutputDevice");
                    // Legacy schemas didn't have bind; treat as output device field.
                    n.bind = "outputDeviceName";
                    return n;
                }
                case "button" -> {
                    var n = new NeonUiSchema.ButtonNode();
                    n.type = "button";
                    n.id = getString(obj, "id", null);
                    n.textKey = getString(obj, "textKey", null);
                    n.action = getString(obj, "action", null);
                    n.bind = getString(obj, "bind", null);
                    return n;
                }
                case "toggle" -> {
                    var n = new NeonUiSchema.ToggleNode();
                    n.type = "toggle";
                    n.id = getString(obj, "id", null);
                    n.textKey = getString(obj, "textKey", null);
                    n.bind = getString(obj, "bind", null);
                    n.value = getBoolObj(obj, "value");
                    return n;
                }
                case "slider" -> {
                    var n = new NeonUiSchema.SliderNode();
                    n.type = "slider";
                    n.id = getString(obj, "id", null);
                    n.textKey = getString(obj, "textKey", null);
                    n.bind = getString(obj, "bind", null);
                    // Provide sane defaults for legacy-only schemas.
                    n.min = getDoubleObj(obj, "min");
                    n.max = getDoubleObj(obj, "max");
                    n.step = getDoubleObj(obj, "step");
                    n.format = getString(obj, "format", null);
                    if (n.min == null) n.min = 0.0;
                    if (n.max == null) n.max = 1.0;
                    if (n.step == null) n.step = 0.01;
                    if (n.format == null) n.format = "percent";
                    return n;
                }
                default -> {
                    return null;
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static java.util.List<NeonUiSchema.NeonUiNode> parseChildren(JsonObject obj) {
        try {
            JsonElement el = obj.get("children");
            if (el == null || !el.isJsonArray()) return java.util.List.of();
            var out = new java.util.ArrayList<NeonUiSchema.NeonUiNode>();
            for (var childEl : el.getAsJsonArray()) {
                JsonObject childObj = asObj(childEl);
                if (childObj == null) continue;
                NeonUiSchema.NeonUiNode child = parseNodeOrNull(childObj);
                if (child != null) out.add(child);
            }
            return out;
        } catch (Throwable ignored) {
            return java.util.List.of();
        }
    }

    private static java.util.List<String> parseStringArray(JsonObject obj, String key) {
        try {
            JsonElement el = obj.get(key);
            if (el == null || !el.isJsonArray()) return null;
            var out = new java.util.ArrayList<String>();
            for (var v : el.getAsJsonArray()) {
                if (v == null || !v.isJsonPrimitive()) continue;
                out.add(v.getAsString());
            }
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static JsonObject getObj(JsonObject obj, String key) {
        return asObj(obj == null ? null : obj.get(key));
    }

    private static JsonObject asObj(JsonElement el) {
        if (el == null || !el.isJsonObject()) return null;
        return el.getAsJsonObject();
    }

    private static String getString(JsonObject obj, String key, String def) {
        try {
            JsonElement el = obj.get(key);
            if (el == null || !el.isJsonPrimitive()) return def;
            return el.getAsString();
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static int getInt(JsonObject obj, String key, int def) {
        Integer v = getIntObj(obj, key);
        return v == null ? def : v;
    }

    private static Integer getIntObj(JsonObject obj, String key) {
        try {
            JsonElement el = obj.get(key);
            if (el == null || !el.isJsonPrimitive()) return null;
            return el.getAsInt();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Double getDoubleObj(JsonObject obj, String key) {
        try {
            JsonElement el = obj.get(key);
            if (el == null || !el.isJsonPrimitive()) return null;
            return el.getAsDouble();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Boolean getBoolObj(JsonObject obj, String key) {
        try {
            JsonElement el = obj.get(key);
            if (el == null || !el.isJsonPrimitive()) return null;
            return el.getAsBoolean();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
