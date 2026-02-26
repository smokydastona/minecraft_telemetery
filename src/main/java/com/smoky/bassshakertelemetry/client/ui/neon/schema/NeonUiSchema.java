package com.smoky.bassshakertelemetry.client.ui.neon.schema;

import java.util.Map;

public final class NeonUiSchema {
    public int version;
    public Map<String, ScreenSchema> screens;

    public static final class ScreenSchema {
        public String titleKey;
        public NeonUiNode root;
    }

    /** Base type for all nodes (parsed manually by NeonUiSchemaLoader). */
    public abstract static class NeonUiNode {
        public String type;

        // Layout results (not serialized)
        public transient int computedX;
        public transient int computedY;
        public transient int computedWidth;
        public transient int computedHeight;

        // Common optional layout hints
        public Integer padding;
        public Integer spacing;
        public Integer margin;
        public Integer width;
        public Integer height;
        public String align;
    }

    public static final class PanelNode extends NeonUiNode {
        public String layout; // "vertical" | "horizontal"
        public java.util.List<NeonUiNode> children;
    }

    public static final class LabelNode extends NeonUiNode {
        public String text;
        public String textKey;
        public String style;
    }

    public static final class ButtonNode extends NeonUiNode {
        public String id;
        public String text;
        public String textKey;
        public String action;
        public String bind;
    }

    public static final class ToggleNode extends NeonUiNode {
        public String id;
        public String text;
        public String textKey;
        public String bind;
        public Boolean value;
    }

    public static final class SliderNode extends NeonUiNode {
        public String id;
        public String text;
        public String textKey;
        public String bind;
        public Double min;
        public Double max;
        public Double step;
        public Double value;
        public String format; // e.g. "percent", "number"
    }

    public static final class CycleNode extends NeonUiNode {
        public String id;
        public String text;
        public String textKey;
        public String bind;
        public java.util.List<String> options;
        public Integer value;
    }

    public static final class SpacerNode extends NeonUiNode {
        public Integer size;
    }
}
