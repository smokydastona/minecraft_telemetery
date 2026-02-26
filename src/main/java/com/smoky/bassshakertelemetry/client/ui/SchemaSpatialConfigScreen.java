package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonCycleButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonLabel;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonRangeSlider;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonStyle;
import com.smoky.bassshakertelemetry.client.ui.neon.schema.NeonUiSchema;
import com.smoky.bassshakertelemetry.client.ui.neon.schema.NeonUiSchemaLoader;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SchemaSpatialConfigScreen extends Screen {
    private static final String SCREEN_ID = "spatial_config";

    private final Screen parent;
    private final Map<String, Object> state = new HashMap<>();

    public SchemaSpatialConfigScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.spatial.title"));
        this.parent = parent;
    }

    @Override
    @SuppressWarnings("null")
    protected void init() {
        super.init();
        NeonStyle.initClient();

        NeonUiSchema.ScreenSchema schema = NeonUiSchemaLoader.loadActiveScreenOrNull(SCREEN_ID);
        if (schema == null || schema.root == null) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new SpatialConfigScreen(parent));
            }
            return;
        }

        int centerX = this.width / 2;
        var font = Objects.requireNonNull(this.font, "font");

        int contentWidth = Math.min(310, this.width - 40);
        int leftX = centerX - (contentWidth / 2);
        int rowH = 20;
        int rowGap = 6;

        loadBoundState(schema.root);

        this.addRenderableWidget(new StringWidget(
                centerX - 140,
                20,
                280,
                20,
                Component.translatable(Objects.requireNonNullElse(schema.titleKey, "bassshakertelemetry.spatial.title")),
                font
        ));

        int contentTop = 50;
        int contentBottom = this.height - 80;
        int contentHeight = Math.max(60, contentBottom - contentTop);

        layout(schema.root, leftX, contentTop, contentWidth, contentHeight, rowH, rowGap);
        addWidgetsFromNode(schema.root, rowH);

        this.addRenderableWidget(new NeonButton(
                leftX,
                this.height - 52,
                contentWidth,
                20,
                Component.literal("Reload UI bundle"),
                () -> {
                    boolean ok = NeonStyle.reloadFromDiskBundleIfPresent();
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.displayClientMessage(
                                Component.literal(ok ? "UI bundle reloaded" : "UI bundle not found"),
                                true
                        );
                    }
                }
        ));

        int buttonW = (contentWidth - 10) / 2;

        this.addRenderableWidget(new NeonButton(
                leftX,
                this.height - 28,
                buttonW,
                20,
                Component.translatable("bassshakertelemetry.config.done"),
                this::onDone
        ));

        this.addRenderableWidget(new NeonButton(
                leftX + buttonW + 10,
                this.height - 28,
                buttonW,
                20,
                Component.translatable("bassshakertelemetry.config.cancel"),
                this::onCancel
        ));
    }

    private void loadBoundState(NeonUiSchema.NeonUiNode node) {
        BstConfig.Data data = BstConfig.get();
        collectBind(node, data);
    }

    private void collectBind(NeonUiSchema.NeonUiNode node, BstConfig.Data data) {
        if (node == null) return;

        String bind = null;
        if (node instanceof NeonUiSchema.ToggleNode n) bind = n.bind;
        if (node instanceof NeonUiSchema.SliderNode n) bind = n.bind;
        if (node instanceof NeonUiSchema.CycleNode n) bind = n.bind;

        if (bind != null && !bind.isBlank()) {
            Object v = readBind(bind, data);
            if (v != null) state.put(bind, v);
        }

        if (node instanceof NeonUiSchema.PanelNode p && p.children != null) {
            for (NeonUiSchema.NeonUiNode c : p.children) {
                collectBind(c, data);
            }
        }
    }

    private static Object readBind(String fieldName, BstConfig.Data data) {
        try {
            if (fieldName == null || fieldName.isBlank()) return null;
            Field f = BstConfig.Data.class.getField(fieldName);
            return f.get(data);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static double readDoubleBind(String fieldName, BstConfig.Data data, double fallback) {
        Object v = readBind(fieldName, data);
        if (v instanceof Double d) return d;
        if (v instanceof Float f) return f.doubleValue();
        if (v instanceof Integer i) return i.doubleValue();
        return fallback;
    }

    private void handleAction(String action) {
        if (action == null) return;
        Minecraft mc = this.minecraft;
        if (mc == null) return;

        switch (action) {
            case "openSoundscape" -> {
                if (NeonUiSchemaLoader.hasActiveScreen("soundscape_config")) {
                    mc.setScreen(new SchemaSoundscapeConfigScreen(this));
                } else {
                    mc.setScreen(new SoundScapeConfigScreen(this));
                }
            }
            case "openSpatialBusRouting" -> mc.setScreen(new SpatialBusRoutingScreen(this));
            case "openSpatialCalibration" -> mc.setScreen(new SpatialCalibrationScreen(this));
            case "openSpatialDebugger" -> mc.setScreen(new SpatialDebuggerScreen(this));
            default -> {
            }
        }
    }

    private void onDone() {
        BstConfig.Data data = BstConfig.get();
        applyAllBinds(data);
        BstConfig.set(data);

        AudioOutputEngine.get().startOrRestart();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void applyAllBinds(BstConfig.Data data) {
        for (var e : state.entrySet()) {
            applyBindIfPresent(data, e.getKey(), e.getValue());
        }
    }

    private static void applyBindIfPresent(BstConfig.Data data, String fieldName, Object v) {
        if (v == null) return;
        try {
            Field f = BstConfig.Data.class.getField(fieldName);
            Class<?> t = f.getType();
            if (t == boolean.class && v instanceof Boolean b) {
                f.setBoolean(data, b);
            } else if (t == double.class && v instanceof Number n) {
                f.setDouble(data, n.doubleValue());
            } else if (t == int.class && v instanceof Number n) {
                f.setInt(data, n.intValue());
            } else if (t == String.class && v instanceof String s) {
                f.set(data, s);
            }
        } catch (Throwable ignored) {
        }
    }

    private void layout(NeonUiSchema.NeonUiNode node, int x, int y, int w, int h, int rowH, int rowGap) {
        if (node == null) return;

        node.computedX = x;
        node.computedY = y;
        node.computedWidth = w;
        node.computedHeight = h;

        if (node instanceof NeonUiSchema.PanelNode panel) {
            int pad = panel.padding == null ? 0 : Math.max(0, panel.padding);
            int spacing = panel.spacing == null ? rowGap : Math.max(0, panel.spacing);
            String layout = panel.layout == null ? "vertical" : panel.layout;

            int innerX = x + pad;
            int innerY = y + pad;
            int innerW = Math.max(0, w - (pad * 2));
            int innerH = Math.max(0, h - (pad * 2));

            if (panel.children == null || panel.children.isEmpty()) return;

            if ("horizontal".equalsIgnoreCase(layout)) {
                int count = panel.children.size();
                int totalSpacing = spacing * Math.max(0, count - 1);
                int availableSlotsW = Math.max(0, innerW - totalSpacing);

                int fixedW = 0;
                int autoCount = 0;
                for (NeonUiSchema.NeonUiNode c : panel.children) {
                    int m = resolveMargin(c);
                    if (c != null && c.width != null && c.width > 0) {
                        fixedW += (c.width + (m * 2));
                    } else {
                        autoCount++;
                    }
                }

                int remainingW = availableSlotsW - fixedW;
                int autoSlotW = autoCount > 0 ? Math.max(20, remainingW / autoCount) : 0;

                int cx = innerX;
                for (NeonUiSchema.NeonUiNode c : panel.children) {
                    int m = resolveMargin(c);
                    int slotW = (c != null && c.width != null && c.width > 0) ? (c.width + (m * 2)) : autoSlotW;
                    slotW = Math.max(0, slotW);

                    int desiredW = c != null && c.width != null && c.width > 0 ? c.width : Math.max(0, slotW - (m * 2));
                    int childW = applyAlignWidth(c, Math.max(0, slotW - (m * 2)), desiredW);
                    int childX = alignX(cx, slotW, childW, m, c == null ? null : c.align);
                    int childY = innerY + m;
                    int ch = preferredHeight(c, rowH);
                    layout(c, childX, childY, childW, ch, rowH, rowGap);
                    cx += slotW + spacing;
                }
            } else {
                int cy = innerY;
                for (NeonUiSchema.NeonUiNode c : panel.children) {
                    int m = resolveMargin(c);
                    int ch = preferredHeight(c, rowH);
                    int slotW = innerW;
                    int desiredW = c != null && c.width != null && c.width > 0 ? c.width : Math.max(0, slotW - (m * 2));
                    int childW = applyAlignWidth(c, Math.max(0, slotW - (m * 2)), desiredW);
                    int childX = alignX(innerX, slotW, childW, m, c == null ? null : c.align);
                    int childY = cy + m;
                    layout(c, childX, childY, childW, ch, rowH, rowGap);
                    cy += ch + (m * 2) + spacing;
                    if (cy > innerY + innerH) break;
                }
            }
        }
    }

    private static int resolveMargin(NeonUiSchema.NeonUiNode node) {
        if (node == null) return 0;
        if (node.margin == null || node.margin <= 0) return 0;
        return Math.max(0, node.margin);
    }

    private static int applyAlignWidth(NeonUiSchema.NeonUiNode node, int availableW, int desiredW) {
        if (availableW <= 0) return 0;
        String align = node == null ? null : node.align;
        if ("stretch".equalsIgnoreCase(Objects.requireNonNullElse(align, ""))) {
            return availableW;
        }
        return Math.max(0, Math.min(availableW, desiredW));
    }

    private static int alignX(int slotX, int slotW, int childW, int margin, String align) {
        int innerX = slotX + Math.max(0, margin);
        int innerW = Math.max(0, slotW - (Math.max(0, margin) * 2));
        int w = Math.max(0, Math.min(innerW, childW));
        String a = Objects.requireNonNullElse(align, "");
        if ("center".equalsIgnoreCase(a)) {
            return innerX + Math.max(0, (innerW - w) / 2);
        }
        if ("right".equalsIgnoreCase(a)) {
            return innerX + Math.max(0, innerW - w);
        }
        return innerX;
    }

    private int preferredHeight(NeonUiSchema.NeonUiNode node, int rowH) {
        if (node == null) return rowH;
        if (node.height != null && node.height > 0) return node.height;
        if (node instanceof NeonUiSchema.LabelNode) return rowH;
        if (node instanceof NeonUiSchema.SpacerNode s) return (s.size == null ? rowH / 2 : Math.max(0, s.size));
        return rowH;
    }

    @SuppressWarnings("null")
    private void addWidgetsFromNode(NeonUiSchema.NeonUiNode node, int rowH) {
        if (node == null) return;
        if (node instanceof NeonUiSchema.PanelNode panel) {
            if (panel.children != null) {
                for (NeonUiSchema.NeonUiNode c : panel.children) {
                    addWidgetsFromNode(c, rowH);
                }
            }
            return;
        }

        if (node instanceof NeonUiSchema.LabelNode n) {
            Component msg = resolveText(n.textKey, n.text);
            this.addRenderableWidget(new NeonLabel(node.computedX, node.computedY, node.computedWidth, rowH, msg, false));
            return;
        }

        if (node instanceof NeonUiSchema.ButtonNode n) {
            Component msg = resolveText(n.textKey, n.text);
            this.addRenderableWidget(new NeonButton(
                    node.computedX,
                    node.computedY,
                    node.computedWidth,
                    rowH,
                    msg,
                    () -> handleAction(n.action)
            ));
            return;
        }

        if (node instanceof NeonUiSchema.ToggleNode n) {
            String bind = n.bind;
            boolean v = state.get(bind) instanceof Boolean b ? b : (n.value != null && n.value);
            this.addRenderableWidget(new NeonCycleButton<>(
                    node.computedX,
                    node.computedY,
                    node.computedWidth,
                    rowH,
                    resolveText(n.textKey, n.text),
                    List.of(Boolean.TRUE, Boolean.FALSE),
                    v,
                    vv -> vv ? Component.translatable("options.on") : Component.translatable("options.off"),
                    vv -> state.put(bind, vv)
            ));
            return;
        }

        if (node instanceof NeonUiSchema.SliderNode n) {
            String bind = n.bind;
            double min = n.min == null ? 0.0 : n.min;
            double max = n.max == null ? 1.0 : n.max;
            double step = n.step == null ? 0.0 : n.step;
            double initial = readDoubleBind(bind, BstConfig.get(), (n.value == null ? 0.0 : n.value));
            String fmt = n.format;

            this.addRenderableWidget(new NeonRangeSlider(
                    node.computedX,
                    node.computedY,
                    node.computedWidth,
                    rowH,
                    resolveText(n.textKey, n.text),
                    min,
                    max,
                    step,
                    fmt,
                    () -> state.get(bind) instanceof Number num ? num.doubleValue() : initial,
                    v -> state.put(bind, v)
            ));
            return;
        }

        if (node instanceof NeonUiSchema.CycleNode n) {
            if (n.options == null || n.options.isEmpty()) return;
            String bind = n.bind;
            int idx = state.get(bind) instanceof Number num ? num.intValue() : (n.value == null ? 0 : n.value);
            idx = Math.max(0, Math.min(n.options.size() - 1, idx));
            int initialIdx = idx;

            this.addRenderableWidget(new NeonCycleButton<>(
                    node.computedX,
                    node.computedY,
                    node.computedWidth,
                    rowH,
                    resolveText(n.textKey, n.text),
                    buildIndexList(n.options.size()),
                    initialIdx,
                    i -> Component.literal(n.options.get(i)),
                    i -> state.put(bind, i)
            ));
        }
    }

    private static List<Integer> buildIndexList(int count) {
        var out = new ArrayList<Integer>(Math.max(0, count));
        for (int i = 0; i < count; i++) out.add(i);
        return out;
    }

    private static Component resolveText(String textKey, String text) {
        if (textKey != null && !textKey.isBlank()) return Component.translatable(textKey);
        if (text != null && !text.isBlank()) return Component.literal(text);
        return Component.empty();
    }

    private void onCancel() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        onCancel();
    }

    @Override
    @SuppressWarnings("null")
    public void renderBackground(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, NeonStyle.get().background);
    }

    @Override
    @SuppressWarnings("null")
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        var font = this.font;
        if (font == null) return;

        int contentWidth = Math.min(310, this.width - 40);
        int leftX = (this.width / 2) - (contentWidth / 2);
        int y = this.height - 74;

        guiGraphics.drawString(
                font,
                uiBundleStatusLabel(),
                leftX,
                y,
                NeonStyle.get().textDim,
                false
        );
    }

    private static Component uiBundleStatusLabel() {
        return switch (NeonStyle.getActiveBundleSource()) {
            case DISK_OVERRIDE -> Component.literal("UI Bundle: Disk (override)");
            case DISK_REMOTE -> Component.literal("UI Bundle: Disk (remote)");
            case BUILT_IN -> Component.literal("UI Bundle: Built-in");
        };
    }
}
