package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.client.ui.neon.NeonButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonStyle;
import com.smoky.bassshakertelemetry.client.ui.neon.schema.NeonUiSchema;
import com.smoky.bassshakertelemetry.client.ui.neon.schema.NeonUiSchemaLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Objects;

public final class SchemaInstrumentConfigScreen extends Screen {
    private static final String SCREEN_ID = "instrument_config";

    private final Screen parent;
    private SettingsList settingsList;

    public SchemaInstrumentConfigScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.config.instruments"));
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
                this.minecraft.setScreen(new HapticInstrumentEditorScreen(parent));
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
                Component.translatable(Objects.requireNonNullElse(schema.titleKey, "bassshakertelemetry.config.instruments")),
                font
        ));

        Minecraft mc = Objects.requireNonNull(this.minecraft, "minecraft");
        int listTop = 50;
        int listBottom = this.height - 34;
        int listHeight = Math.max(80, listBottom - listTop);

        settingsList = new SettingsList(mc, contentWidth, listHeight, listTop, listTop + listHeight, 44, leftX);
        addEntriesFromNode(schema.root, contentWidth, 20);
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

        if (node instanceof NeonUiSchema.ButtonNode n) {
            Component msg = resolveText(n.textKey, n.text);
            Button b = new NeonButton(0, 0, contentWidth - 12, rowH, msg, () -> handleAction(n.action));
            settingsList.addSettingEntry(new ButtonOnlyEntry(b));
        }
    }

    private void handleAction(String action) {
        if (action == null) return;
        Minecraft mc = this.minecraft;
        if (mc == null) return;

        switch (action) {
            case "openInstrumentsEditor" -> mc.setScreen(new HapticInstrumentEditorScreen(this));
            default -> {
            }
        }
    }

    private static Component resolveText(String textKey, String text) {
        if (textKey != null && !textKey.isBlank()) return Component.translatable(textKey);
        if (text != null && !text.isBlank()) return Component.literal(text);
        return Component.empty();
    }

    private void onDone() {
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

    private static final class SettingsList extends ContainerObjectSelectionList<SettingsEntry> {
        private final int leftX;

        public SettingsList(Minecraft mc, int width, int height, int y0, int y1, int itemHeight, int leftX) {
            super(mc, width, height, y0, y1, itemHeight);
            this.leftX = leftX;
            this.setLeftPos(leftX);
        }

        @Override
        public int getRowLeft() {
            return leftX;
        }

        @Override
        public int getRowWidth() {
            return this.width;
        }

        public void addSettingEntry(SettingsEntry e) {
            this.addEntry(Objects.requireNonNull(e));
        }
    }

    private abstract static class SettingsEntry extends ContainerObjectSelectionList.Entry<SettingsEntry> {
        @Override
        public java.util.List<? extends NarratableEntry> narratables() {
            return java.util.List.of();
        }
    }

    private static final class SpacerEntry extends SettingsEntry {
        @Override
        public java.util.List<? extends GuiEventListener> children() {
            return java.util.List.of();
        }

        @Override
        @SuppressWarnings("null")
        public void render(GuiGraphics gfx, int idx, int top, int left, int w, int h, int mouseX, int mouseY, boolean hovered, float partialTick) {
        }
    }

    private static final class LabelEntry extends SettingsEntry {
        private final Component msg;

        private LabelEntry(Component msg) {
            this.msg = Objects.requireNonNullElse(msg, Component.empty());
        }

        @Override
        public java.util.List<? extends GuiEventListener> children() {
            return java.util.List.of();
        }

        @Override
        @SuppressWarnings("null")
        public void render(GuiGraphics gfx, int idx, int top, int left, int w, int h, int mouseX, int mouseY, boolean hovered, float partialTick) {
            var font = Objects.requireNonNull(Minecraft.getInstance().font, "font");
            gfx.drawString(font, msg, left + 4, top + 14, NeonStyle.get().text, false);
        }
    }

    private static final class ButtonOnlyEntry extends SettingsEntry {
        private final Button button;

        private ButtonOnlyEntry(Button button) {
            this.button = Objects.requireNonNull(button);
        }

        @Override
        public java.util.List<? extends GuiEventListener> children() {
            return java.util.List.of(button);
        }

        @Override
        @SuppressWarnings("null")
        public void render(GuiGraphics gfx, int idx, int top, int left, int w, int h, int mouseX, int mouseY, boolean hovered, float partialTick) {
            button.setX(left);
            button.setY(top);
            button.setWidth(w);
            button.render(gfx, mouseX, mouseY, partialTick);
        }
    }
}
