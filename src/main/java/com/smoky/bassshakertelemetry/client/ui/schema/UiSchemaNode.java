package com.smoky.bassshakertelemetry.client.ui.schema;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * A generic UI node. We keep this intentionally generic at first:
 * the renderer can switch on {@link #type()} and interpret {@link #props()}.
 */
public record UiSchemaNode(
        String type,
        String id,
        JsonObject props,
        List<UiSchemaNode> children
) {
}
