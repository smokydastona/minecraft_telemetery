package com.smoky.bassshakertelemetry.client.ui.neon.schema;

import java.util.List;
import java.util.Map;

public final class NeonUiSchema {
    public int version;
    public Map<String, ScreenSchema> screens;

    public static final class ScreenSchema {
        public String titleKey;
        public List<Node> rows;
    }

    public static final class Node {
        public String type;
        public String id;
        public String textKey;
        public String action;
        public String bind;
        public Integer gap;
        public List<Node> children;
    }
}
