package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.client.ui.neon.NeonButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class SchemaSoundscapeGroupEditScreen extends Screen {
    private final SchemaSoundscapeGroupsScreen parent;
    private final Map<String, List<String>> groups;
    private final String originalName;

    private EditBox nameBox;

    private final String[] channels = new String[]{"FL", "FR", "C", "LFE", "SL", "SR", "BL", "BR"};
    private final boolean[] enabled = new boolean[channels.length];
    private final NeonButton[] channelButtons = new NeonButton[channels.length];

    public SchemaSoundscapeGroupEditScreen(SchemaSoundscapeGroupsScreen parent, Map<String, List<String>> groups, String groupName) {
        super(Component.translatable("bassshakertelemetry.soundscape.group_edit_title"));
        this.parent = Objects.requireNonNull(parent);
        this.groups = Objects.requireNonNull(groups);
        this.originalName = Objects.requireNonNull(groupName);

        var initial = groups.get(groupName);
        HashSet<String> set = new HashSet<>();
        if (initial != null) {
            for (String s : initial) {
                if (s != null) set.add(s.trim().toUpperCase(Locale.ROOT));
            }
        }
        for (int i = 0; i < channels.length; i++) {
            enabled[i] = set.contains(channels[i]);
        }

        if (!anyEnabled()) {
            enabled[0] = true;
        }
     }

    @Override
    @SuppressWarnings("null")
    protected void init() {
        super.init();
        NeonStyle.initClient();

        int centerX = this.width / 2;
        int contentWidth = Math.min(310, this.width - 40);
        int leftX = centerX - (contentWidth / 2);

        var font = Objects.requireNonNull(this.font, "font");

        this.addRenderableWidget(new StringWidget(
                centerX - 140,
                20,
                280,
                20,
            Component.translatable("bassshakertelemetry.soundscape.group_edit_title"),
                font
        ));

        nameBox = new EditBox(font, leftX, 50, contentWidth, 20, Component.empty());
        nameBox.setValue(originalName);
        nameBox.setMaxLength(32);
        this.addRenderableWidget(nameBox);

        int y = 78;
        int rowH = 20;
        int gap = 6;

        int colGap = 10;
        int colW = (contentWidth - colGap) / 2;
        int leftColX = leftX;
        int rightColX = leftX + colW + colGap;

        for (int i = 0; i < channels.length; i++) {
            int colX = (i % 2 == 0) ? leftColX : rightColX;
            int rowY = y + (i / 2) * (rowH + gap);

            int idx = i;
            NeonButton b = new NeonButton(
                    colX,
                    rowY,
                    colW,
                    rowH,
                    channelLabel(idx),
                    () -> toggleChannel(idx)
            );
            channelButtons[i] = b;
            this.addRenderableWidget(b);
        }

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

    private void toggleChannel(int idx) {
        enabled[idx] = !enabled[idx];
        if (!anyEnabled()) {
            enabled[idx] = true;
        }
        if (channelButtons[idx] != null) {
            channelButtons[idx].setMessage(channelLabel(idx));
        }
    }

    private boolean anyEnabled() {
        for (boolean b : enabled) {
            if (b) return true;
        }
        return false;
    }

    private Component channelLabel(int idx) {
        String ch = channels[idx];
        boolean on = enabled[idx];
        return Component.literal(ch + ": " + (on ? "ON" : "OFF"));
    }

    private void onDone() {
        String newName = (nameBox == null) ? originalName : nameBox.getValue();
        newName = (newName == null) ? "" : newName.trim();
        if (newName.isEmpty()) {
            newName = originalName;
        }

        if ("All".equalsIgnoreCase(originalName)) {
            newName = "All";
        }

        ArrayList<String> members = new ArrayList<>();
        for (int i = 0; i < channels.length; i++) {
            if (enabled[i]) {
                members.add(channels[i]);
            }
        }
        if (members.isEmpty()) {
            members.add("FL");
        }

        if (!newName.equals(originalName)) {
            if (groups.containsKey(newName)) {
                newName = originalName;
            } else {
                groups.remove(originalName);
            }
        }

        groups.put(newName, members);

        Minecraft mc = this.minecraft;
        if (mc != null) {
            parent.onGroupEdited();
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
}
