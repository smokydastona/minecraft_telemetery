package com.smoky.bassshakertelemetry.client.ui.schema;

import java.util.Objects;

/**
 * Minimal schema-driven UI data model.
 *
 * Not yet wired into screens (Neon v1 is still hardcoded).
 */
public final class UiSchema {
    public final UiSchemaNode root;

    public UiSchema(UiSchemaNode root) {
        this.root = Objects.requireNonNull(root, "root");
    }
}
