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

import javax.annotation.Nonnull;

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
        String titleKey = (schema.titleKey == null || schema.titleKey.isBlank())
            ? "bassshakertelemetry.soundscape.groups_title"
            : Objects.requireNonNull(schema.titleKey, "schema.titleKey");

        this.addRenderableWidget(new StringWidget(
                centerX - 140,
                20,
                280,
                20,
            Objects.requireNonNull(Component.translatable(titleKey), "title"),
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
        this.addRenderableWidget(Objects.requireNonNull(list, "list"));

        this.addRenderableWidget(UiTooltip.withLabelKey(new NeonButton(
                leftX,
                this.height - 50,
                contentWidth,
                20,
                Component.translatable("bassshakertelemetry.soundscape.group_add"),
                this::onAdd
        ), "bassshakertelemetry.soundscape.group_add"));

        int buttonW = (contentWidth - 10) / 2;
        this.addRenderableWidget(UiTooltip.withLabelKey(new NeonButton(
                leftX,
                this.height - 28,
                buttonW,
                20,
                Component.translatable("bassshakertelemetry.config.done"),
                this::onDone
        ), "bassshakertelemetry.config.done"));

        this.addRenderableWidget(UiTooltip.withLabelKey(new NeonButton(
                leftX + buttonW + 10,
                this.height - 28,
                buttonW,
                20,
                Component.translatable("bassshakertelemetry.config.cancel"),
                this::onCancel
        ), "bassshakertelemetry.config.cancel"));
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
        Minecraft mc = this.minecraft;
        if (mc == null) return;
        if (NeonUiSchemaLoader.hasActiveScreen("soundscape_group_edit")) {
            mc.setScreen(new SchemaSoundscapeGroupEditScreen(this, groups, name));
        } else {
            mc.setScreen(new SoundScapeGroupsScreen(parent));
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
    public void renderBackground(@Nonnull GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, NeonStyle.get().background);
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

            this.edit = UiTooltip.withLabelKey(new NeonButton(
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
            ), "bassshakertelemetry.soundscape.group_edit");

            this.delete = UiTooltip.withLabelKey(new NeonButton(
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
            ), "bassshakertelemetry.soundscape.group_delete");
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
        @SuppressWarnings("all")
        public void render(@Nonnull GuiGraphics guiGraphics, int index, int y, int x, int rowWidth, int rowHeight,
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
