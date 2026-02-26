package com.smoky.bassshakertelemetry.client.ui.schema;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a schema JSON object into a node tree.
 *
 * Expected shape (example):
 * {
 *   "type": "panel",
 *   "id": "root",
 *   "props": { "layout": "vertical" },
 *   "children": [ { "type": "label", "props": { "text": "Hello" } } ]
 * }
 */
public final class UiSchemaParser {
    private static final Gson GSON = new Gson();

    private UiSchemaParser() {
    }

    public static UiSchema parse(JsonObject root) {
        UiSchemaNode node = parseNode(root);
        return node == null ? null : new UiSchema(node);
    }

    public static UiSchema parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null) return null;
            return parse(obj);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static UiSchemaNode parseNode(JsonObject obj) {
        if (obj == null) return null;

        String type = getString(obj, "type");
        if (type == null || type.isBlank()) return null;

        String id = getString(obj, "id");
        JsonObject props = obj.has("props") && obj.get("props").isJsonObject() ? obj.getAsJsonObject("props") : new JsonObject();

        List<UiSchemaNode> children = new ArrayList<>();
        JsonArray arr = obj.has("children") && obj.get("children").isJsonArray() ? obj.getAsJsonArray("children") : null;
        if (arr != null) {
            for (JsonElement el : arr) {
                if (el == null || !el.isJsonObject()) continue;
                UiSchemaNode child = parseNode(el.getAsJsonObject());
                if (child != null) {
                    children.add(child);
                }
            }
        }

        return new UiSchemaNode(type.trim(), (id == null ? null : id.trim()), props, List.copyOf(children));
    }

    private static String getString(JsonObject obj, String key) {
        try {
            JsonElement e = obj.get(key);
            if (e == null || !e.isJsonPrimitive()) return null;
            return e.getAsString();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
