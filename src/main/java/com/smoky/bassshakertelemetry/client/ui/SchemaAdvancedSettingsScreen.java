package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonCycleButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonRangeSlider;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonStyle;
import com.smoky.bassshakertelemetry.client.ui.neon.schema.NeonUiSchema;
import com.smoky.bassshakertelemetry.client.ui.neon.schema.NeonUiSchemaLoader;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SchemaAdvancedSettingsScreen extends Screen {
    private static final String SCREEN_ID = "advanced_settings";

    private final Screen parent;

    private SettingsList settingsList;
    private final Map<String, Object> state = new HashMap<>();

    private Button bufferButton;
    private int bufferChoiceIndex;

    private Button latencyTestButton;
    private boolean latencyTestActive;
    private int latencyTestTicks;

    private Button demoButton;
    private boolean demoActive;
    private int demoStep;
    private long demoNextNanos;

    private static final int[] BUFFER_CHOICES_MS = new int[]{0, 10, 20, 30, 40, 60, 80, 100, 150, 200};

    public SchemaAdvancedSettingsScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.config.advanced_title"));
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
                this.minecraft.setScreen(new AdvancedSettingsScreen(parent));
            }
            return;
        }

        int centerX = this.width / 2;
        int contentWidth = Math.min(310, this.width - 40);
        int leftX = centerX - (contentWidth / 2);

        var font = Objects.requireNonNull(this.font, "font");
        this.addRenderableWidget(new StringWidget(
                centerX - 140,
                20,
                280,
                20,
                Component.translatable(Objects.requireNonNullElse(schema.titleKey, "bassshakertelemetry.config.advanced_title")),
                font
        ));

        Minecraft mc = Objects.requireNonNull(this.minecraft, "minecraft");
        int listTop = 50;
        int listBottom = this.height - 34;
        int listHeight = Math.max(80, listBottom - listTop);

        settingsList = new SettingsList(mc, contentWidth, listHeight, listTop, listTop + listHeight, 44, leftX);

        bufferChoiceIndex = findBufferChoiceIndex(BstConfig.get().javaSoundBufferMs);
        latencyTestActive = false;
        latencyTestTicks = 0;
        demoActive = false;
        demoStep = 0;
        demoNextNanos = 0L;

        loadBoundState(schema.root);
        addEntriesFromNode(schema.root, contentWidth);

        this.addRenderableWidget(settingsList);

        int buttonW = (contentWidth - 10) / 2;

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
        if (node instanceof NeonUiSchema.ButtonNode n) bind = n.bind;
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

    private void addEntriesFromNode(NeonUiSchema.NeonUiNode node, int contentWidth) {
        if (node == null) return;

        if (node instanceof NeonUiSchema.PanelNode p) {
            if ("horizontal".equalsIgnoreCase(Objects.requireNonNullElse(p.layout, "vertical"))) {
                SettingsEntry row = tryBuildHorizontalRowEntry(p, contentWidth);
                if (row != null) {
                    settingsList.addSettingEntry(row);
                }
                return;
            }

            if (p.children != null) {
                for (NeonUiSchema.NeonUiNode c : p.children) {
                    addEntriesFromNode(c, contentWidth);
                }
            }
            return;
        }

        if (node instanceof NeonUiSchema.SpacerNode) {
            settingsList.addSettingEntry(new SpacerEntry());
            return;
        }

        if (node instanceof NeonUiSchema.LabelNode n) {
            settingsList.addSettingEntry(new LabelEntry(resolveText(n.textKey, n.text)));
            return;
        }

        if (node instanceof NeonUiSchema.ButtonNode n) {
            Button b;
            switch (Objects.requireNonNullElse(n.action, "")) {
                case "cycleBufferChoice" -> {
                    bufferButton = new NeonButton(0, 0, contentWidth - 12, 20, bufferButtonLabel(), this::cycleBufferChoice);
                    b = bufferButton;
                }
                case "toggleLatencyTest" -> {
                    latencyTestButton = new NeonButton(0, 0, contentWidth - 12, 20, latencyButtonLabel(false), this::toggleLatencyTest);
                    b = latencyTestButton;
                }
                case "toggleDemo" -> {
                    demoButton = new NeonButton(0, 0, contentWidth - 12, 20, demoLabel(), this::toggleDemo);
                    b = demoButton;
                }
                default -> {
                    Component msg = resolveText(n.textKey, n.text);
                    b = new NeonButton(0, 0, contentWidth - 12, 20, msg, () -> handleAction(n.action));
                }
            }
            settingsList.addSettingEntry(new ButtonOnlyEntry(b));
            return;
        }

        if (node instanceof NeonUiSchema.ToggleNode n) {
            String bind = n.bind;
            if (bind == null || bind.isBlank()) return;
            boolean v = state.get(bind) instanceof Boolean b ? b : (n.value != null && n.value);

            settingsList.addSettingEntry(new SliderOnlyEntry(new NeonCycleButton<Boolean>(
                    0,
                    0,
                    contentWidth - 12,
                    20,
                    resolveText(n.textKey, n.text),
                    List.of(Boolean.TRUE, Boolean.FALSE),
                    v,
                    vv -> vv ? Component.translatable("options.on") : Component.translatable("options.off"),
                    vv -> {
                        state.put(bind, vv);
                    }
            )));
            return;
        }

        if (node instanceof NeonUiSchema.SliderNode n) {
            String bind = n.bind;
            if (bind == null || bind.isBlank()) return;

            double min = n.min == null ? 0.0 : n.min;
            double max = n.max == null ? 1.0 : n.max;
            double step = n.step == null ? 0.0 : n.step;
            double initial = readDoubleBind(bind, BstConfig.get(), (n.value == null ? min : n.value));
            String fmt = n.format;

            settingsList.addSettingEntry(new SliderOnlyEntry(new NeonRangeSlider(
                    0,
                    0,
                    contentWidth - 12,
                    20,
                    resolveText(n.textKey, n.text),
                    min,
                    max,
                    step,
                    fmt,
                    () -> state.get(bind) instanceof Number num ? num.doubleValue() : initial,
                    v -> state.put(bind, v)
            )));
            return;
        }

        if (node instanceof NeonUiSchema.CycleNode n) {
            if (n.options == null || n.options.isEmpty()) return;
            String bind = n.bind;
            if (bind == null || bind.isBlank()) return;

            int idx = state.get(bind) instanceof Number num ? num.intValue() : (n.value == null ? 0 : n.value);
            idx = Math.max(0, Math.min(n.options.size() - 1, idx));
            int initialIdx = idx;

            settingsList.addSettingEntry(new SliderOnlyEntry(new NeonCycleButton<Integer>(
                    0,
                    0,
                    contentWidth - 12,
                    20,
                    resolveText(n.textKey, n.text),
                    buildIndexList(n.options.size()),
                    initialIdx,
                    i -> Component.literal(Objects.requireNonNull(Objects.requireNonNullElse(n.options.get(i), ""), "option")),
                    i -> {
                        state.put(bind, i);
                    }
            )));
        }
    }

    private SettingsEntry tryBuildHorizontalRowEntry(NeonUiSchema.PanelNode panel, int contentWidth) {
        if (panel.children == null || panel.children.isEmpty()) return null;

        int spacing = panel.spacing == null ? 6 : Math.max(0, panel.spacing);
        var out = new ArrayList<RowChild>(panel.children.size());
        for (NeonUiSchema.NeonUiNode child : panel.children) {
            AbstractRowWidget built = tryBuildRowWidget(child, contentWidth);
            if (built == null || built.widget == null) continue;
            out.add(new RowChild(built.widget, built.explicitWidth));
        }
        if (out.isEmpty()) return null;
        return new RowEntry(out, spacing);
    }

    private AbstractRowWidget tryBuildRowWidget(NeonUiSchema.NeonUiNode node, int contentWidth) {
        if (node == null) return null;

        Integer explicitWidth = node.width;
        int w = (explicitWidth != null && explicitWidth > 0) ? explicitWidth : (contentWidth - 12);

        if (node instanceof NeonUiSchema.ButtonNode n) {
            Button b;
            switch (Objects.requireNonNullElse(n.action, "")) {
                case "cycleBufferChoice" -> {
                    bufferButton = new NeonButton(0, 0, w, 20, bufferButtonLabel(), this::cycleBufferChoice);
                    b = bufferButton;
                }
                case "toggleLatencyTest" -> {
                    latencyTestButton = new NeonButton(0, 0, w, 20, latencyButtonLabel(false), this::toggleLatencyTest);
                    b = latencyTestButton;
                }
                case "toggleDemo" -> {
                    demoButton = new NeonButton(0, 0, w, 20, demoLabel(), this::toggleDemo);
                    b = demoButton;
                }
                default -> {
                    Component msg = resolveText(n.textKey, n.text);
                    b = new NeonButton(0, 0, w, 20, msg, () -> handleAction(n.action));
                }
            }
            return new AbstractRowWidget(b, explicitWidth);
        }

        if (node instanceof NeonUiSchema.ToggleNode n) {
            String bind = n.bind;
            if (bind == null || bind.isBlank()) return null;
            boolean v = state.get(bind) instanceof Boolean b ? b : (n.value != null && n.value);

                var btn = new NeonCycleButton<Boolean>(
                    0,
                    0,
                    w,
                    20,
                    resolveText(n.textKey, n.text),
                    List.of(Boolean.TRUE, Boolean.FALSE),
                    v,
                    vv -> vv ? Component.translatable("options.on") : Component.translatable("options.off"),
                    vv -> {
                    state.put(bind, vv);
                    }
            );
            return new AbstractRowWidget(btn, explicitWidth);
        }

        if (node instanceof NeonUiSchema.SliderNode n) {
            String bind = n.bind;
            if (bind == null || bind.isBlank()) return null;

            double min = n.min == null ? 0.0 : n.min;
            double max = n.max == null ? 1.0 : n.max;
            double step = n.step == null ? 0.0 : n.step;
            double initial = readDoubleBind(bind, BstConfig.get(), (n.value == null ? min : n.value));
            String fmt = n.format;

            var slider = new NeonRangeSlider(
                    0,
                    0,
                    w,
                    20,
                    resolveText(n.textKey, n.text),
                    min,
                    max,
                    step,
                    fmt,
                    () -> state.get(bind) instanceof Number num ? num.doubleValue() : initial,
                    v -> state.put(bind, v)
            );
            return new AbstractRowWidget(slider, explicitWidth);
        }

        if (node instanceof NeonUiSchema.CycleNode n) {
            if (n.options == null || n.options.isEmpty()) return null;
            String bind = n.bind;
            if (bind == null || bind.isBlank()) return null;

            int idx = state.get(bind) instanceof Number num ? num.intValue() : (n.value == null ? 0 : n.value);
            idx = Math.max(0, Math.min(n.options.size() - 1, idx));
            int initialIdx = idx;

                var btn = new NeonCycleButton<Integer>(
                    0,
                    0,
                    w,
                    20,
                    resolveText(n.textKey, n.text),
                    buildIndexList(n.options.size()),
                    initialIdx,
                    i -> Component.literal(Objects.requireNonNull(Objects.requireNonNullElse(n.options.get(i), ""), "option")),
                    i -> {
                    state.put(bind, i);
                    }
            );
            return new AbstractRowWidget(btn, explicitWidth);
        }

        return null;
    }

    private void handleAction(String action) {
        if (action == null) return;
        Minecraft mc = this.minecraft;
        if (mc == null) return;

        switch (action) {
            case "testRoadTexture" -> AudioOutputEngine.get().testRoadTexture();
            case "testDamageBurst" -> AudioOutputEngine.get().testDamageBurst();
            case "testBiomeChime" -> AudioOutputEngine.get().testBiomeChime();
            case "testAccelBump" -> AudioOutputEngine.get().testAccelBump();
            case "testSoundHaptics" -> AudioOutputEngine.get().testSoundHaptics();
            case "testGameplayHaptics" -> AudioOutputEngine.get().testGameplayHaptics();
            case "testFootsteps" -> AudioOutputEngine.get().testFootsteps();
            case "testMountedHooves" -> AudioOutputEngine.get().testMountedHooves();
            case "testMiningSwing" -> AudioOutputEngine.get().testMiningSwing();

            case "testCalibrationTone30Hz" -> AudioOutputEngine.get().testCalibrationTone30Hz();
            case "testCalibrationTone60Hz" -> AudioOutputEngine.get().testCalibrationTone60Hz();
            case "testCalibrationSweep" -> AudioOutputEngine.get().testCalibrationSweep();
            case "stopCalibration" -> AudioOutputEngine.get().stopCalibration();

            case "openSpatial" -> {
                if (NeonUiSchemaLoader.hasActiveScreen("spatial_config")) {
                    mc.setScreen(new SchemaSpatialConfigScreen(this));
                } else {
                    mc.setScreen(new SpatialConfigScreen(this));
                }
            }
            case "openInstruments" -> {
                if (NeonUiSchemaLoader.hasActiveScreen("instrument_config")) {
                    mc.setScreen(new SchemaInstrumentConfigScreen(this));
                } else {
                    mc.setScreen(new HapticInstrumentEditorScreen(this));
                }
            }
            case "openInstrumentsEditor" -> mc.setScreen(new HapticInstrumentEditorScreen(this));
            default -> {
            }
        }
    }

    private void onDone() {
        stopLatencyTest();
        stopDemo();

        BstConfig.Data data = BstConfig.get();

        if (bufferButton != null) {
            data.javaSoundBufferMs = BUFFER_CHOICES_MS[clampIndex(bufferChoiceIndex)];
        }

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

    private void onCancel() {
        stopLatencyTest();
        stopDemo();
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

        int contentWidth = Math.min(330, this.width - 40);
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

    @Override
    @SuppressWarnings("null")
    public void tick() {
        super.tick();

        if (latencyTestActive) {
            latencyTestTicks++;
            boolean pulseNow = (latencyTestTicks % 10) == 0; // 2 Hz at 20 tps
            if (pulseNow) {
                AudioOutputEngine.get().testLatencyPulse();
            }
            if (latencyTestButton != null) {
                latencyTestButton.setMessage(Objects.requireNonNull(latencyButtonLabel(pulseNow), "latencyButtonLabel"));
            }
        }

        tickDemo();
    }

    private void toggleLatencyTest() {
        latencyTestActive = !latencyTestActive;
        latencyTestTicks = 0;
        if (latencyTestButton != null) {
            latencyTestButton.setMessage(Objects.requireNonNull(latencyButtonLabel(false), "latencyButtonLabel"));
        }
    }

    private void stopLatencyTest() {
        latencyTestActive = false;
        latencyTestTicks = 0;
        if (latencyTestButton != null) {
            latencyTestButton.setMessage(Objects.requireNonNull(latencyButtonLabel(false), "latencyButtonLabel"));
        }
    }

    @SuppressWarnings("null")
    private Component latencyButtonLabel(boolean pulseNow) {
        Component base = latencyTestActive
                ? Component.translatable("bassshakertelemetry.config.latency_test_on")
                : Component.translatable("bassshakertelemetry.config.latency_test_off");
        if (latencyTestActive && pulseNow) {
            return base.copy().append(" ").append(Component.literal("*"));
        }
        return base;
    }

    private void toggleDemo() {
        demoActive = !demoActive;
        demoStep = 0;
        demoNextNanos = System.nanoTime();
        if (demoButton != null) {
            demoButton.setMessage(Objects.requireNonNull(demoLabel(), "demoLabel"));
        }
    }

    private void stopDemo() {
        demoActive = false;
        demoStep = 0;
        demoNextNanos = 0L;
        if (demoButton != null) {
            demoButton.setMessage(Objects.requireNonNull(demoLabel(), "demoLabel"));
        }
    }

    private void tickDemo() {
        if (!demoActive) return;
        long now = System.nanoTime();
        if (now < demoNextNanos) return;

        switch (demoStep) {
            case 0 -> {
                AudioOutputEngine.get().testLatencyPulse();
                demoNextNanos = now + 600_000_000L;
            }
            case 1 -> {
                AudioOutputEngine.get().testDamageBurst();
                demoNextNanos = now + 600_000_000L;
            }
            case 2 -> {
                AudioOutputEngine.get().testRoadTexture();
                demoNextNanos = now + 600_000_000L;
            }
            case 3 -> {
                AudioOutputEngine.get().testFootsteps();
                demoNextNanos = now + 350_000_000L;
            }
            case 4 -> {
                AudioOutputEngine.get().testMiningSwing();
                demoNextNanos = now + 450_000_000L;
            }
            default -> {
                demoStep = -1;
                demoNextNanos = now + 900_000_000L;
            }
        }
        demoStep++;

        if (demoButton != null) {
            demoButton.setMessage(Objects.requireNonNull(demoLabel(), "demoLabel"));
        }
    }

    @SuppressWarnings("null")
    private Component demoLabel() {
        return demoActive
                ? Component.translatable("bassshakertelemetry.config.demo_stop")
                : Component.translatable("bassshakertelemetry.config.demo_run");
    }

    private void cycleBufferChoice() {
        bufferChoiceIndex = (clampIndex(bufferChoiceIndex) + 1) % BUFFER_CHOICES_MS.length;
        if (bufferButton != null) {
            bufferButton.setMessage(Objects.requireNonNull(bufferButtonLabel(), "bufferButtonLabel"));
        }
    }

    @SuppressWarnings("null")
    private Component bufferButtonLabel() {
        int ms = BUFFER_CHOICES_MS[clampIndex(bufferChoiceIndex)];
        Component v = (ms <= 0)
                ? Component.translatable("bassshakertelemetry.config.buffer_auto")
                : Component.literal(ms + "ms");
        return Component.translatable("bassshakertelemetry.config.output_buffer")
                .copy()
                .append(": ")
                .append(v);
    }

    private static int findBufferChoiceIndex(int currentMs) {
        for (int i = 0; i < BUFFER_CHOICES_MS.length; i++) {
            if (BUFFER_CHOICES_MS[i] == currentMs) {
                return i;
            }
        }
        int bestI = 0;
        int bestD = Integer.MAX_VALUE;
        for (int i = 0; i < BUFFER_CHOICES_MS.length; i++) {
            int d = Math.abs(BUFFER_CHOICES_MS[i] - currentMs);
            if (d < bestD) {
                bestD = d;
                bestI = i;
            }
        }
        return bestI;
    }

    private static int clampIndex(int idx) {
        if (idx < 0) return 0;
        if (idx >= BUFFER_CHOICES_MS.length) return BUFFER_CHOICES_MS.length - 1;
        return idx;
    }

    private static double readDoubleBind(String fieldName, BstConfig.Data data, double fallback) {
        Object v = readBind(fieldName, data);
        if (v instanceof Double d) return d;
        if (v instanceof Float f) return f.doubleValue();
        if (v instanceof Integer i) return i.doubleValue();
        return fallback;
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

    private static final class SettingsList extends ContainerObjectSelectionList<SettingsEntry> {
        private final int left;

        SettingsList(Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight, int left) {
            super(minecraft, width, height, y0, y1, itemHeight);
            this.left = left;
            this.setLeftPos(left);
            this.setRenderHeader(false, 0);
        }

        @Override
        public int getRowWidth() {
            return this.width;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.left + this.width - 6;
        }

        void addSettingEntry(SettingsEntry entry) {
            this.addEntry(Objects.requireNonNull(entry));
        }
    }

    private abstract static class SettingsEntry extends ContainerObjectSelectionList.Entry<SettingsEntry> {
    }

    private record AbstractRowWidget(net.minecraft.client.gui.components.AbstractWidget widget, Integer explicitWidth) {
    }

    private record RowChild(net.minecraft.client.gui.components.AbstractWidget widget, Integer explicitWidth) {
    }

    private static final class SpacerEntry extends SettingsEntry {
        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Collections.emptyList();
        }

        @Override
        @SuppressWarnings("null")
        public void render(GuiGraphics guiGraphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
        }
    }

    private static final class LabelEntry extends SettingsEntry {
        private final Component label;

        LabelEntry(Component label) {
            this.label = Objects.requireNonNullElse(label, Component.empty());
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Collections.emptyList();
        }

        @Override
        @SuppressWarnings("null")
        public void render(GuiGraphics guiGraphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            guiGraphics.drawString(
                    Objects.requireNonNull(Minecraft.getInstance().font),
                    label,
                    x + 2,
                    y + 6,
                    0xFFFFFF
            );
        }
    }

    private static final class SliderOnlyEntry extends SettingsEntry {
        private final net.minecraft.client.gui.components.AbstractWidget widget;

        SliderOnlyEntry(net.minecraft.client.gui.components.AbstractWidget widget) {
            this.widget = Objects.requireNonNull(widget);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.singletonList(widget);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Collections.singletonList(widget);
        }

        @Override
        @SuppressWarnings("null")
        public void render(GuiGraphics guiGraphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            int innerX = x + 2;
            widget.setX(innerX);
            widget.setY(y + 12);
            widget.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private static final class ButtonOnlyEntry extends SettingsEntry {
        private final Button button;

        ButtonOnlyEntry(Button button) {
            this.button = Objects.requireNonNull(button);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.singletonList(button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Collections.singletonList(button);
        }

        @Override
        @SuppressWarnings("null")
        public void render(GuiGraphics guiGraphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            int innerX = x + 2;
            button.setX(innerX);
            button.setY(y + 12);
            button.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private static final class RowEntry extends SettingsEntry {
        private final List<RowChild> widgets;
        private final int spacing;

        RowEntry(List<RowChild> widgets, int spacing) {
            this.widgets = List.copyOf(Objects.requireNonNull(widgets));
            this.spacing = Math.max(0, spacing);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            var out = new ArrayList<GuiEventListener>(widgets.size());
            for (RowChild w : widgets) {
                if (w != null && w.widget != null) out.add(w.widget);
            }
            return out;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            var out = new ArrayList<NarratableEntry>(widgets.size());
            for (RowChild w : widgets) {
                if (w != null && w.widget != null) out.add(w.widget);
            }
            return out;
        }

        @Override
        @SuppressWarnings("null")
        public void render(GuiGraphics guiGraphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            if (widgets.isEmpty()) return;

            int innerX = x + 2;
            int innerY = y + 12;
            int innerW = Math.max(20, rowWidth - 12);

            int count = widgets.size();
            int totalSpacing = spacing * Math.max(0, count - 1);

            int fixedW = 0;
            int fillCount = 0;
            for (RowChild w : widgets) {
                Integer ew = (w == null) ? null : w.explicitWidth;
                if (ew != null && ew > 0) fixedW += ew;
                else fillCount++;
            }

            int remaining = innerW - totalSpacing - fixedW;
            if (remaining < 0) remaining = 0;

            int fillW = fillCount > 0 ? Math.max(20, remaining / fillCount) : 0;
            int extra = fillCount > 0 ? Math.max(0, remaining - (fillW * fillCount)) : 0;

            int cursorX = innerX;
            for (RowChild child : widgets) {
                if (child == null || child.widget == null) continue;

                Integer ew = child.explicitWidth;
                int w = (ew != null && ew > 0) ? ew : (fillW + (extra-- > 0 ? 1 : 0));

                child.widget.setX(cursorX);
                child.widget.setY(innerY);
                child.widget.setWidth(w);
                child.widget.render(guiGraphics, mouseX, mouseY, partialTick);

                cursorX += w + spacing;
            }
        }
    }
}
