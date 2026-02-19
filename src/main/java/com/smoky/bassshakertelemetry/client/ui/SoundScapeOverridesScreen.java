package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
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

public final class SoundScapeOverridesScreen extends Screen {
    private final Screen parent;

    private Map<String, String> overrides;
    private OverridesList list;

    public SoundScapeOverridesScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.soundscape.overrides_title"));
        this.parent = parent;
    }

    @Override
    @SuppressWarnings("null")
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int contentWidth = Math.min(310, this.width - 40);
        int leftX = centerX - (contentWidth / 2);

        var font = Objects.requireNonNull(this.font, "font");

        this.addRenderableWidget(new StringWidget(
                centerX - 140,
                20,
                280,
                20,
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.overrides_title")),
                font
        ));

        this.addRenderableWidget(new StringWidget(
                centerX - 140,
                38,
                280,
                20,
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.overrides_desc")),
                font
        ));

        overrides = deepCopy(BstConfig.get().soundScapeOverrides);

        Minecraft mc = Objects.requireNonNull(this.minecraft, "minecraft");
        int listTop = 58;
        int listBottom = this.height - 56;
        int listHeight = Math.max(90, listBottom - listTop);

        list = new OverridesList(mc, contentWidth, listHeight, listTop, listTop + listHeight, 24, leftX);
        refreshList();
        this.addRenderableWidget(list);

        this.addRenderableWidget(Button.builder(
                        Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.override_add")),
                        b -> onAdd())
                .bounds(leftX, this.height - 50, contentWidth, 20)
                .build());

        int buttonW = (contentWidth - 10) / 2;
        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.done")), b -> onDone())
                .bounds(leftX, this.height - 28, buttonW, 20)
                .build());

        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cancel")), b -> onCancel())
                .bounds(leftX + buttonW + 10, this.height - 28, buttonW, 20)
                .build());
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
            mc.setScreen(new SoundScapeOverrideEditScreen(this, overrides, key));
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

            this.edit = Button.builder(
                            Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.override_edit")),
                            b -> {
                                Minecraft mc = SoundScapeOverridesScreen.this.minecraft;
                                if (mc != null) {
                                    mc.setScreen(new SoundScapeOverrideEditScreen(SoundScapeOverridesScreen.this, overrides, this.key));
                                }
                            })
                    .bounds(0, 0, 70, 20)
                    .build();

            this.delete = Button.builder(
                            Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.override_delete")),
                            b -> {
                                overrides.remove(this.key);
                                refreshList();
                            })
                    .bounds(0, 0, 70, 20)
                    .build();
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
        public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            String rawTarget = overrides.getOrDefault(key, "grp:All");
            String displayTarget = SoundScapeOverrideEditScreen.displayTarget(rawTarget);

            guiGraphics.drawString(
                    Objects.requireNonNull(SoundScapeOverridesScreen.this.font),
                    Component.literal(key),
                    x + 4,
                    y + 5,
                    0xFFFFFF
            );
            guiGraphics.drawString(
                    Objects.requireNonNull(SoundScapeOverridesScreen.this.font),
                    Component.literal(displayTarget),
                    x + 4,
                    y + 5 + 10,
                    0xA0A0A0
            );

            int right = x + rowWidth - 4;
            delete.setX(right - 70);
            delete.setY(y + 2);
            edit.setX(right - 70 - 6 - 70);
            edit.setY(y + 2);

            edit.render(guiGraphics, mouseX, mouseY, partialTick);
            delete.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }
}
