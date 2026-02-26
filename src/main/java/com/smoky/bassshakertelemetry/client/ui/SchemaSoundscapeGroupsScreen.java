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

public final class SchemaSoundscapeGroupsScreen extends Screen {
    private static final String SCREEN_ID = "soundscape_groups";

    private final Screen parent;

    private Map<String, List<String>> groups;
    private GroupsList list;

    public SchemaSoundscapeGroupsScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.soundscape.groups_title"));
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
                this.minecraft.setScreen(new SoundScapeGroupsScreen(parent));
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
                Component.translatable(Objects.requireNonNullElse(schema.titleKey, "bassshakertelemetry.soundscape.groups_title")),
                font
        ));

        groups = deepCopy(BstConfig.get().soundScapeGroups);
        if (groups.isEmpty()) {
            groups.put("All", List.of("FL", "FR", "C", "LFE", "SL", "SR", "BL", "BR"));
        }

        Minecraft mc = Objects.requireNonNull(this.minecraft, "minecraft");
        int listTop = 50;
        int listBottom = this.height - 56;
        int listHeight = Math.max(90, listBottom - listTop);

        list = new GroupsList(mc, contentWidth, listHeight, listTop, listTop + listHeight, 24, leftX);
        refreshList();
        this.addRenderableWidget(list);

        this.addRenderableWidget(new NeonButton(
                leftX,
                this.height - 50,
                contentWidth,
                20,
                Component.translatable("bassshakertelemetry.soundscape.group_add"),
                this::onAdd
        ));

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

        ArrayList<String> names = new ArrayList<>(groups.keySet());
        names.sort(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT)));

        for (String n : names) {
            if (n == null || n.isBlank()) continue;
            list.addGroup(new GroupEntry(n));
        }
    }

    private void onAdd() {
        String base = "Group";
        int i = 1;
        while (groups.containsKey(base + " " + i)) {
            i++;
        }
        String name = base + " " + i;
        groups.put(name, List.of("FL", "FR"));
        if (this.minecraft != null) {
            if (NeonUiSchemaLoader.hasActiveScreen("soundscape_group_edit")) {
                this.minecraft.setScreen(new SchemaSoundscapeGroupEditScreen(this, groups, name));
            } else {
                this.minecraft.setScreen(new SoundScapeGroupsScreen(parent));
            }
        }
    }

    void onGroupEdited() {
        refreshList();
    }

    private void onDone() {
        BstConfig.Data data = BstConfig.get();
        data.soundScapeGroups = deepCopy(groups);
        BstConfig.set(data);
        AudioOutputEngine.get().startOrRestart();

        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
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

    private static Map<String, List<String>> deepCopy(Map<String, List<String>> in) {
        Map<String, List<String>> out = new HashMap<>();
        if (in == null) {
            return out;
        }
        for (Map.Entry<String, List<String>> e : in.entrySet()) {
            if (e == null) continue;
            String k = e.getKey();
            if (k == null || k.isBlank()) continue;
            List<String> v = e.getValue();
            out.put(k, v == null ? List.of() : new ArrayList<>(v));
        }
        return out;
    }

    private final class GroupsList extends ContainerObjectSelectionList<GroupEntry> {
        private final int left;

        GroupsList(Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight, int left) {
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

        void addGroup(GroupEntry entry) {
            this.addEntry(Objects.requireNonNull(entry));
        }

        void clearAll() {
            this.clearEntries();
        }
    }

    private final class GroupEntry extends ContainerObjectSelectionList.Entry<GroupEntry> {
        private final String groupName;
        private final Button edit;
        private final Button delete;

        GroupEntry(String groupName) {
            this.groupName = Objects.requireNonNull(groupName);

            this.edit = new NeonButton(
                    0,
                    0,
                    70,
                    20,
                    Component.translatable("bassshakertelemetry.soundscape.group_edit"),
                    () -> {
                        Minecraft mc = SchemaSoundscapeGroupsScreen.this.minecraft;
                        if (mc != null) {
                            if (NeonUiSchemaLoader.hasActiveScreen("soundscape_group_edit")) {
                                mc.setScreen(new SchemaSoundscapeGroupEditScreen(SchemaSoundscapeGroupsScreen.this, groups, this.groupName));
                            } else {
                                mc.setScreen(new SoundScapeGroupsScreen(parent));
                            }
                        }
                    }
            );

            this.delete = new NeonButton(
                    0,
                    0,
                    70,
                    20,
                    Component.translatable("bassshakertelemetry.soundscape.group_delete"),
                    () -> {
                        if (!"All".equalsIgnoreCase(this.groupName)) {
                            groups.remove(this.groupName);
                            refreshList();
                        }
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
            guiGraphics.drawString(
                    Objects.requireNonNull(SchemaSoundscapeGroupsScreen.this.font),
                    Component.literal(groupName),
                    x + 4,
                    y + 7,
                    NeonStyle.get().text,
                    false
            );

            int right = x + rowWidth - 4;
            delete.setX(right - 70);
            delete.setY(y + 2);
            edit.setX(right - 70 - 6 - 70);
            edit.setY(y + 2);

            delete.active = !"All".equalsIgnoreCase(groupName);

            edit.render(guiGraphics, mouseX, mouseY, partialTick);
            delete.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }
}
