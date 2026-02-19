package com.smoky.bassshakertelemetry.client.ui;

import net.minecraft.client.gui.components.Button;
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

public final class SoundScapeGroupEditScreen extends Screen {
    private final SoundScapeGroupsScreen parent;
    private final Map<String, List<String>> groups;
    private final String originalName;

    private EditBox nameBox;

    private final String[] channels = new String[]{"FL", "FR", "C", "LFE", "SL", "SR", "BL", "BR"};
    private final boolean[] enabled = new boolean[channels.length];
    private final Button[] channelButtons = new Button[channels.length];

    public SoundScapeGroupEditScreen(SoundScapeGroupsScreen parent, Map<String, List<String>> groups, String groupName) {
        super(Component.translatable("bassshakertelemetry.soundscape.group_edit_title"));
        this.parent = parent;
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

        // Always keep at least one channel.
        if (!anyEnabled()) {
            enabled[0] = true;
        }
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
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.group_edit_title")),
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
            Button b = Button.builder(channelLabel(idx), btn -> toggleChannel(idx))
                    .bounds(colX, rowY, colW, rowH)
                    .build();
            channelButtons[i] = b;
            this.addRenderableWidget(b);
        }

        int buttonW = (contentWidth - 10) / 2;
        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.done")), b -> onDone())
                .bounds(leftX, this.height - 28, buttonW, 20)
                .build());

        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cancel")), b -> onCancel())
                .bounds(leftX + buttonW + 10, this.height - 28, buttonW, 20)
                .build());
    }

    private void toggleChannel(int idx) {
        enabled[idx] = !enabled[idx];
        if (!anyEnabled()) {
            enabled[idx] = true;
        }
        if (channelButtons[idx] != null) {
            channelButtons[idx].setMessage(Objects.requireNonNull(channelLabel(idx)));
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

        // Keep All stable.
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

        // Rename if needed.
        if (!newName.equals(originalName)) {
            // Avoid overwriting existing groups accidentally.
            if (groups.containsKey(newName)) {
                newName = originalName;
            } else {
                groups.remove(originalName);
            }
        }

        groups.put(newName, members);

        net.minecraft.client.Minecraft mc = this.minecraft;
        if (mc != null) {
            parent.onGroupEdited();
            mc.setScreen(parent);
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
}
