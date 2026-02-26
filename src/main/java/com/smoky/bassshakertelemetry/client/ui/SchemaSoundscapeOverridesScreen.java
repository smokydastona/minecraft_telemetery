package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonButton;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class SchemaSoundscapeOverridesScreen extends Screen {
    private static final String SCREEN_ID = "soundscape_overrides";

    private final Screen parent;

    private Map<String, String> overrides;
    private OverridesList list;

    public SchemaSoundscapeOverridesScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.soundscape.overrides_title"));
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
                this.minecraft.setScreen(new SoundScapeOverridesScreen(parent));
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
                Component.translatable(Objects.requireNonNullElse(schema.titleKey, "bassshakertelemetry.soundscape.overrides_title")),
                font
        ));

        this.addRenderableWidget(new StringWidget(
                centerX - 140,
                38,
                280,
                20,
                Component.translatable("bassshakertelemetry.soundscape.overrides_desc"),
                font
        ));

        overrides = deepCopy(BstConfig.get().soundScapeOverrides);

        Minecraft mc = Objects.requireNonNull(this.minecraft, "minecraft");
        int listTop = 58;
        int listBottom = this.height - 80;
        int listHeight = Math.max(90, listBottom - listTop);

        list = new OverridesList(mc, contentWidth, listHeight, listTop, listTop + listHeight, 28, leftX);
        refreshList();
        this.addRenderableWidget(list);

        this.addRenderableWidget(new NeonButton(
                leftX,
                this.height - 74,
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
                this.height - 50,
                contentWidth,
                20,
                Component.translatable("bassshakertelemetry.soundscape.override_add"),
                this::onAdd
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

    private void refreshList() {
        if (list == null) return;
        list.clearAll();

        ArrayList<String> keys = new ArrayList<>(overrides.keySet());
        keys.sort(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT)));

        for (String k : keys) {
            if (k == null || k.isBlank()) continue;
            list.addOverride(new OverrideEntry(k));
        }
    }

    private void onAdd() {
        String base = "custom.rule";
        int i = 1;
        while (containsKeyIgnoreCase(overrides, base + "." + i)) {
            i++;
        }
        String key = base + "." + i;
        overrides.put(key, "grp:All");

        Minecraft mc = this.minecraft;
        if (mc != null) {
            if (NeonUiSchemaLoader.hasActiveScreen("soundscape_override_edit")) {
                mc.setScreen(new SchemaSoundscapeOverrideEditScreen(this, overrides, key));
            } else {
                mc.setScreen(new SoundScapeOverridesScreen(parent));
            }
        }
    }

    void onOverrideEdited() {
        refreshList();
    }

    private void onDone() {
        BstConfig.Data data = BstConfig.get();
        data.soundScapeOverrides = deepCopy(overrides);
        BstConfig.set(data);
        AudioOutputEngine.get().startOrRestart();

        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(parent);
        }
    }

    private void onCancel() {
        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(parent);
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
        int y = this.height - 96;

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

    private static boolean containsKeyIgnoreCase(Map<String, String> map, String key) {
        if (map == null || key == null) return false;
        for (String k : map.keySet()) {
            if (k != null && k.equalsIgnoreCase(key)) return true;
        }
        return false;
    }

    private static Map<String, String> deepCopy(Map<String, String> in) {
        Map<String, String> out = new HashMap<>();
        if (in == null) {
            return out;
        }
        for (Map.Entry<String, String> e : in.entrySet()) {
            if (e == null) continue;
            String k = e.getKey();
            if (k == null || k.isBlank()) continue;
            String v = e.getValue();
            out.put(k.trim(), v == null ? "grp:All" : v.trim());
        }
        return out;
    }

    private final class OverridesList extends ContainerObjectSelectionList<OverrideEntry> {
        private final int left;

        OverridesList(Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight, int left) {
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

        void addOverride(OverrideEntry entry) {
            this.addEntry(Objects.requireNonNull(entry));
        }

        void clearAll() {
            this.clearEntries();
        }
    }

    private final class OverrideEntry extends ContainerObjectSelectionList.Entry<OverrideEntry> {
        private final String key;
        private final Button edit;
        private final Button delete;

        OverrideEntry(String key) {
            this.key = Objects.requireNonNull(key);

            this.edit = new NeonButton(
                    0,
                    0,
                    70,
                    20,
                    Component.translatable("bassshakertelemetry.soundscape.override_edit"),
                    () -> {
                        Minecraft mc = SchemaSoundscapeOverridesScreen.this.minecraft;
                        if (mc != null) {
                            if (NeonUiSchemaLoader.hasActiveScreen("soundscape_override_edit")) {
                                mc.setScreen(new SchemaSoundscapeOverrideEditScreen(SchemaSoundscapeOverridesScreen.this, overrides, this.key));
                            } else {
                                mc.setScreen(new SoundScapeOverridesScreen(parent));
                            }
                        }
                    }
            );

            this.delete = new NeonButton(
                    0,
                    0,
                    70,
                    20,
                    Component.translatable("bassshakertelemetry.soundscape.override_delete"),
                    () -> {
                        overrides.remove(this.key);
                        refreshList();
                    }
            );
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(edit, delete);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(edit, delete);
        }

        @Override
        @SuppressWarnings("null")
        public void render(GuiGraphics guiGraphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            String rawTarget = overrides.getOrDefault(key, "grp:All");
            String displayTarget = SchemaSoundscapeOverrideEditScreen.displayTarget(rawTarget);

            guiGraphics.drawString(
                    Objects.requireNonNull(SchemaSoundscapeOverridesScreen.this.font),
                    Component.literal(key),
                    x + 4,
                    y + 5,
                    NeonStyle.get().text,
                    false
            );
            guiGraphics.drawString(
                    Objects.requireNonNull(SchemaSoundscapeOverridesScreen.this.font),
                    Component.literal(displayTarget),
                    x + 4,
                    y + 5 + 10,
                    NeonStyle.get().textDim,
                    false
            );

            int right = x + rowWidth - 4;
            delete.setX(right - 70);
            delete.setY(y + 4);
            edit.setX(right - 70 - 6 - 70);
            edit.setY(y + 4);

            edit.render(guiGraphics, mouseX, mouseY, partialTick);
            delete.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }
}
