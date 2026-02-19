package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioDeviceUtil;
import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Sound Scape routing UI (7.1 style).
 *
 * Lets the user route haptic categories to specific channels or named groups.
 */
public final class SoundScapeConfigScreen extends Screen {
    private final Screen parent;

    private SettingsList settingsList;

    private Button enabledButton;
    private boolean soundScapeEnabled;

    private final Map<String, RoutingButton> routingButtons = new HashMap<>();

    public SoundScapeConfigScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.soundscape.title"));
        this.parent = parent;
    }

    @Override
    @SuppressWarnings("null")
    protected void init() {
        super.init();

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
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.title")),
                font
        ));

        soundScapeEnabled = BstConfig.get().soundScapeEnabled;

        int listTop = 50;
        int listBottom = this.height - 34;
        int listHeight = Math.max(90, listBottom - listTop);

        var mc = Objects.requireNonNull(this.minecraft, "minecraft");
        settingsList = new SettingsList(mc, contentWidth, listHeight, listTop, listTop + listHeight, 28, leftX);

        settingsList.addSettingEntry(new LabelEntry("bassshakertelemetry.soundscape.section.mode"));

        enabledButton = Button.builder(Objects.requireNonNull(enabledLabel()), b -> toggleEnabled())
                .bounds(0, 0, contentWidth - 12, rowH)
                .build();
        settingsList.addSettingEntry(new ButtonOnlyEntry(enabledButton));

        settingsList.addSettingEntry(new LabelEntry("bassshakertelemetry.soundscape.section.status"));
        settingsList.addSettingEntry(new TextEntry(statusLine()));

        Button groupsButton = Button.builder(
                        Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.groups")),
                        b -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(new SoundScapeGroupsScreen(this));
                            }
                        })
                .bounds(0, 0, contentWidth - 12, rowH)
                .build();
        settingsList.addSettingEntry(new ButtonOnlyEntry(groupsButton));

        Button overridesButton = Button.builder(
                        Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.overrides")),
                        b -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(new SoundScapeOverridesScreen(this));
                            }
                        })
                .bounds(0, 0, contentWidth - 12, rowH)
                .build();
        settingsList.addSettingEntry(new ButtonOnlyEntry(overridesButton));

        settingsList.addSettingEntry(new LabelEntry("bassshakertelemetry.soundscape.section.routing"));

        routingButtons.clear();
        addRoutingEntry(contentWidth, rowH, BstConfig.SoundScapeCategories.ROAD, "bassshakertelemetry.soundscape.route.road");
        addRoutingEntry(contentWidth, rowH, BstConfig.SoundScapeCategories.DAMAGE, "bassshakertelemetry.soundscape.route.damage");
        addRoutingEntry(contentWidth, rowH, BstConfig.SoundScapeCategories.BIOME_CHIME, "bassshakertelemetry.soundscape.route.biome");
        addRoutingEntry(contentWidth, rowH, BstConfig.SoundScapeCategories.ACCEL_BUMP, "bassshakertelemetry.soundscape.route.accel");
        addRoutingEntry(contentWidth, rowH, BstConfig.SoundScapeCategories.SOUND, "bassshakertelemetry.soundscape.route.sound");
        addRoutingEntry(contentWidth, rowH, BstConfig.SoundScapeCategories.GAMEPLAY, "bassshakertelemetry.soundscape.route.gameplay");
        addRoutingEntry(contentWidth, rowH, BstConfig.SoundScapeCategories.FOOTSTEPS, "bassshakertelemetry.soundscape.route.footsteps");
        addRoutingEntry(contentWidth, rowH, BstConfig.SoundScapeCategories.MOUNTED, "bassshakertelemetry.soundscape.route.mounted");
        addRoutingEntry(contentWidth, rowH, BstConfig.SoundScapeCategories.MINING_SWING, "bassshakertelemetry.soundscape.route.mining");
        addRoutingEntry(contentWidth, rowH, BstConfig.SoundScapeCategories.CUSTOM, "bassshakertelemetry.soundscape.route.custom");

        this.addRenderableWidget(settingsList);

        int buttonW = (contentWidth - 10) / 2;
        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.done")), b -> onDone())
                .bounds(leftX, this.height - 28, buttonW, 20)
                .build());

        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cancel")), b -> onCancel())
                .bounds(leftX + buttonW + 10, this.height - 28, buttonW, 20)
                .build());
    }

    private void addRoutingEntry(int contentWidth, int rowH, String categoryKey, String labelKey) {
        List<String> options = buildTargetOptions();
        String current = normalizeTargetId(BstConfig.get().soundScapeCategoryRouting.get(categoryKey));
        int idx = options.indexOf(current);
        if (idx < 0) idx = 0;

        int[] indexRef = new int[]{idx};

        Button b = Button.builder(Objects.requireNonNull(routeLabel(labelKey, options.get(indexRef[0]))), btn -> {
                    indexRef[0] = (indexRef[0] + 1) % options.size();
                    btn.setMessage(Objects.requireNonNull(routeLabel(labelKey, options.get(indexRef[0]))));
                })
                .bounds(0, 0, contentWidth - 12, rowH)
                .build();

        RoutingButton rb = new RoutingButton(categoryKey, options, indexRef, b);
        routingButtons.put(categoryKey, rb);
        settingsList.addSettingEntry(new ButtonOnlyEntry(b));
    }

    private Component enabledLabel() {
        return soundScapeEnabled
                ? Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.enabled_on"))
                : Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.enabled_off"));
    }

    private void toggleEnabled() {
        soundScapeEnabled = !soundScapeEnabled;
        if (enabledButton != null) {
            enabledButton.setMessage(Objects.requireNonNull(enabledLabel()));
        }
    }

    private void onDone() {
        BstConfig.Data data = BstConfig.get();
        data.soundScapeEnabled = soundScapeEnabled;

        if (data.soundScapeCategoryRouting == null) {
            data.soundScapeCategoryRouting = new HashMap<>();
        }
        for (RoutingButton rb : routingButtons.values()) {
            if (rb == null) continue;
            String v = rb.current();
            if (v != null && !v.isBlank()) {
                data.soundScapeCategoryRouting.put(rb.categoryKey, v);
            }
        }

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

    private Component routeLabel(String labelKey, String target) {
        String lk = Objects.requireNonNull(labelKey, "labelKey");
        String t = (target == null) ? "grp:All" : target;
        String display = Objects.requireNonNull(displayTarget(t), "displayTarget");

        return Objects.requireNonNull(Component.translatable(lk))
                .append(": ")
            .append(Objects.requireNonNull(Component.literal(display), "literal"));
    }

    private Component statusLine() {
        int requested = soundScapeEnabled ? 8 : 2;
        boolean has8 = hasAny8ChannelDevice();

        Component req = Objects.requireNonNull(Component.literal(requested == 8 ? "8" : "2"));
        Component ok = has8
            ? Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.status.multichannel_ok"))
            : Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.status.multichannel_missing"));

        return Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.status"))
                .append(": ")
                .append(req)
                .append("  ")
                .append(ok);
    }

    private boolean hasAny8ChannelDevice() {
        try {
            // If a specific device is selected, check that; otherwise, check whether the system has *any* 8ch output.
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
        // Force stereo UI when multichannel devices are unavailable.
        boolean allow8 = hasAny8ChannelDevice();
        List<String> out = new ArrayList<>();

        // Channels
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

        // Groups
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

        // Ensure stable fallback.
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

        // Allow bare channel ids.
        String ch = normalizeChannel(v);
        if (!ch.isEmpty()) {
            return "ch:" + ch;
        }

        // Otherwise treat as group name.
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

    private record RoutingButton(String categoryKey, List<String> options, int[] indexRef, Button button) {
        String current() {
            int idx = indexRef[0];
            if (idx < 0 || idx >= options.size()) {
                idx = 0;
            }
            return options.get(idx);
        }
    }

    private static final class SettingsList extends ContainerObjectSelectionList<SettingsEntry> {
        private final int left;

        SettingsList(net.minecraft.client.Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight, int left) {
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
        private final String key;

        LabelEntry(String key) {
            this.key = Objects.requireNonNull(key);
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
        public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            guiGraphics.drawString(
                    Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font),
                    Objects.requireNonNull(Component.translatable(key)),
                    x + 2,
                    y + 6,
                    0xFFFFFF
            );
        }
    }

    private static final class TextEntry extends SettingsEntry {
        private final Component text;

        TextEntry(Component text) {
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
        public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            guiGraphics.drawString(
                    Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font),
                    text,
                    x + 2,
                    y + 6,
                    0xE0E0E0
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
        public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            int innerX = x + 2;
            button.setX(innerX);
            button.setY(y + 4);
            button.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }
}
