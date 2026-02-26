package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioDeviceUtil;
import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonCycleButton;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class SchemaSoundscapeConfigScreen extends Screen {
    private static final String SCREEN_ID = "soundscape_config";

    private static final List<String> KNOWN_CATEGORIES = List.of(
            BstConfig.SoundScapeCategories.ROAD,
            BstConfig.SoundScapeCategories.DAMAGE,
            BstConfig.SoundScapeCategories.BIOME_CHIME,
            BstConfig.SoundScapeCategories.ACCEL_BUMP,
            BstConfig.SoundScapeCategories.SOUND,
            BstConfig.SoundScapeCategories.GAMEPLAY,
            BstConfig.SoundScapeCategories.FOOTSTEPS,
            BstConfig.SoundScapeCategories.MOUNTED,
            BstConfig.SoundScapeCategories.MINING_SWING,
            BstConfig.SoundScapeCategories.CUSTOM
    );

    private final Screen parent;

    private SettingsList settingsList;
    private final Map<String, Object> state = new HashMap<>();

    /** categoryKey -> normalized target id (e.g. ch:FL, grp:All) */
    private final Map<String, String> routing = new HashMap<>();
    private final Map<String, Button> routingButtons = new HashMap<>();

    public SchemaSoundscapeConfigScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.soundscape.title"));
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
                this.minecraft.setScreen(new SoundScapeConfigScreen(parent));
            }
            return;
        }

        int centerX = this.width / 2;
        int contentWidth = Math.min(310, this.width - 40);
        int leftX = centerX - (contentWidth / 2);
        int rowH = 20;

        var font = Objects.requireNonNull(this.font, "font");

        this.addRenderableWidget(new StringWidget(
                centerX - 140,
                20,
                280,
                20,
                Component.translatable(Objects.requireNonNullElse(schema.titleKey, "bassshakertelemetry.soundscape.title")),
                font
        ));

        int listTop = 50;
        int listBottom = this.height - 34;
        int listHeight = Math.max(90, listBottom - listTop);

        Minecraft mc = Objects.requireNonNull(this.minecraft, "minecraft");
        settingsList = new SettingsList(mc, contentWidth, listHeight, listTop, listTop + listHeight, 28, leftX);

        loadBoundState(schema.root);
        loadInitialRouting();

        addEntriesFromNode(schema.root, contentWidth, rowH);

        this.addRenderableWidget(settingsList);

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

    private void loadInitialRouting() {
        routing.clear();

        Map<String, String> current = BstConfig.get().soundScapeCategoryRouting;
        if (current == null) return;

        for (String cat : KNOWN_CATEGORIES) {
            String raw = current.get(cat);
            routing.put(cat, normalizeTargetId(raw));
        }
    }

    private void addEntriesFromNode(NeonUiSchema.NeonUiNode node, int contentWidth, int rowH) {
        if (node == null) return;

        if (node instanceof NeonUiSchema.PanelNode p) {
            if (p.children != null) {
                for (NeonUiSchema.NeonUiNode c : p.children) {
                    addEntriesFromNode(c, contentWidth, rowH);
                }
            }
            return;
        }

        if (node instanceof NeonUiSchema.SpacerNode) {
            settingsList.addSettingEntry(new SpacerEntry());
            return;
        }

        if (node instanceof NeonUiSchema.LabelNode n) {
            Component msg = resolveText(n.textKey, n.text);
            settingsList.addSettingEntry(new LabelEntry(msg));
            return;
        }

        if (node instanceof NeonUiSchema.ToggleNode n) {
            String bind = n.bind;
            if (bind == null || bind.isBlank()) return;

            boolean v = state.get(bind) instanceof Boolean b ? b : (n.value != null && n.value);
            settingsList.addSettingEntry(new ButtonOnlyEntry(new NeonCycleButton<>(
                    0,
                    0,
                    contentWidth - 12,
                    rowH,
                    resolveText(n.textKey, n.text),
                    List.of(Boolean.TRUE, Boolean.FALSE),
                    v,
                    vv -> vv ? Component.translatable("options.on") : Component.translatable("options.off"),
                    vv -> state.put(bind, vv)
            )));
            return;
        }

        if (node instanceof NeonUiSchema.ButtonNode n) {
            Button b = tryBuildButtonFromNode(n, contentWidth, rowH);
            if (b != null) {
                settingsList.addSettingEntry(new ButtonOnlyEntry(b));
            }
        }
    }

    private Button tryBuildButtonFromNode(NeonUiSchema.ButtonNode n, int contentWidth, int rowH) {
        String action = Objects.requireNonNullElse(n.action, "");

        if (action.startsWith("cycleSoundscapeRoute:")) {
            String category = action.substring("cycleSoundscapeRoute:".length()).trim();
            if (category.isEmpty()) return null;

            Button btn = new NeonButton(
                    0,
                    0,
                    contentWidth - 12,
                    rowH,
                    routeLabel(resolveText(n.textKey, n.text), routing.getOrDefault(category, "grp:All")),
                    () -> cycleRoute(category, resolveText(n.textKey, n.text))
            );

            routingButtons.put(category, btn);
            return btn;
        }

        Component msg = resolveText(n.textKey, n.text);
        return new NeonButton(
                0,
                0,
                contentWidth - 12,
                rowH,
                msg,
                () -> handleAction(action)
        );
    }

    private void cycleRoute(String categoryKey, Component label) {
        List<String> options = buildTargetOptions();
        if (options.isEmpty()) return;

        String current = normalizeTargetId(routing.get(categoryKey));
        int idx = options.indexOf(current);
        if (idx < 0) idx = 0;
        idx = (idx + 1) % options.size();

        String next = options.get(idx);
        routing.put(categoryKey, next);

        Button btn = routingButtons.get(categoryKey);
        if (btn != null) {
            btn.setMessage(routeLabel(label, next));
        }
    }

    private void handleAction(String action) {
        if (action == null) return;
        if (this.minecraft == null) return;

        switch (action) {
            case "openSoundscapeGroups" -> {
                if (NeonUiSchemaLoader.hasActiveScreen("soundscape_groups") && NeonUiSchemaLoader.hasActiveScreen("soundscape_group_edit")) {
                    this.minecraft.setScreen(new SchemaSoundscapeGroupsScreen(this));
                } else {
                    this.minecraft.setScreen(new SoundScapeGroupsScreen(this));
                }
            }
            case "openSoundscapeOverrides" -> {
                if (NeonUiSchemaLoader.hasActiveScreen("soundscape_overrides") && NeonUiSchemaLoader.hasActiveScreen("soundscape_override_edit")) {
                    this.minecraft.setScreen(new SchemaSoundscapeOverridesScreen(this));
                } else {
                    this.minecraft.setScreen(new SoundScapeOverridesScreen(this));
                }
            }
            default -> {
            }
        }
    }

    private void onDone() {
        BstConfig.Data data = BstConfig.get();
        applyAllBinds(data);

        if (data.soundScapeCategoryRouting == null) {
            data.soundScapeCategoryRouting = new HashMap<>();
        }
        for (String cat : KNOWN_CATEGORIES) {
            String v = routing.get(cat);
            if (v != null && !v.isBlank()) {
                data.soundScapeCategoryRouting.put(cat, v);
            }
        }

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

    private static Component resolveText(String textKey, String text) {
        if (textKey != null && !textKey.isBlank()) return Component.translatable(textKey);
        if (text != null && !text.isBlank()) return Component.literal(text);
        return Component.empty();
    }

    private static Component routeLabel(Component label, String target) {
        String t = (target == null) ? "grp:All" : target;
        String display = displayTarget(t);

        return Objects.requireNonNull(label)
                .copy()
                .append(": ")
                .append(Component.literal(display));
    }

    private boolean hasAny8ChannelDevice() {
        try {
            String stored = BstConfig.get().outputDeviceName;
            if (stored != null && !stored.isBlank()) {
                return AudioDeviceUtil.findMixerByName(stored, AudioOutputEngine.get().format7_1()) != null;
            }
            return !AudioDeviceUtil.listOutputDeviceNames(AudioOutputEngine.get().format7_1()).isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<String> buildTargetOptions() {
        boolean allow8 = hasAny8ChannelDevice();
        List<String> out = new ArrayList<>();

        out.add("ch:FL");
        out.add("ch:FR");
        if (allow8) {
            out.add("ch:C");
            out.add("ch:LFE");
            out.add("ch:SL");
            out.add("ch:SR");
            out.add("ch:BL");
            out.add("ch:BR");
        }

        Map<String, List<String>> groups = BstConfig.get().soundScapeGroups;
        if (groups != null && !groups.isEmpty()) {
            List<String> names = new ArrayList<>(groups.keySet());
            names.sort(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT)));
            for (String n : names) {
                if (n == null || n.isBlank()) continue;
                out.add("grp:" + n.trim());
            }
        } else {
            out.add("grp:All");
        }

        if (!out.contains("grp:All")) {
            out.add("grp:All");
        }

        return out;
    }

    private static String normalizeTargetId(String raw) {
        if (raw == null) {
            return "grp:All";
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return "grp:All";
        }
        String lower = v.toLowerCase(Locale.ROOT);

        if (lower.startsWith("ch:")) {
            return "ch:" + normalizeChannel(v.substring(3));
        }
        if (lower.startsWith("grp:")) {
            String name = v.substring(4).trim();
            return name.isEmpty() ? "grp:All" : ("grp:" + name);
        }

        String ch = normalizeChannel(v);
        if (!ch.isEmpty()) {
            return "ch:" + ch;
        }

        return "grp:" + v;
    }

    private static String normalizeChannel(String raw) {
        if (raw == null) return "";
        String v = raw.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "L" -> "FL";
            case "R" -> "FR";
            case "FL", "FR", "C", "LFE", "SL", "SR", "BL", "BR" -> v;
            default -> "";
        };
    }

    private static String displayTarget(String target) {
        if (target == null) return "All";
        String v = target.trim();
        if (v.isEmpty()) return "All";
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.startsWith("ch:")) {
            return v.substring(3).toUpperCase(Locale.ROOT);
        }
        if (lower.startsWith("grp:")) {
            return v.substring(4).trim();
        }
        return v;
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

    private static final class LabelEntry extends SettingsEntry {
        private final Component text;

        LabelEntry(Component text) {
            this.text = Objects.requireNonNull(text);
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
                    text,
                    x + 2,
                    y + 6,
                    NeonStyle.get().text,
                    false
            );
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
            button.setY(y + 4);
            button.render(guiGraphics, mouseX, mouseY, partialTick);
        }
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
        public void render(GuiGraphics guiGraphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
        }
    }
}
