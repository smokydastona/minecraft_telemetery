package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioDeviceUtil;
import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class SoundScapeOverrideEditScreen extends Screen {
    private final SoundScapeOverridesScreen parent;
    private final Map<String, String> overrides;
    private final String originalKey;

    private EditBox keyBox;

    private Button targetButton;
    private List<String> targetOptions;
    private int targetIndex;

    public SoundScapeOverrideEditScreen(SoundScapeOverridesScreen parent, Map<String, String> overrides, String originalKey) {
        super(Component.translatable("bassshakertelemetry.soundscape.override_edit_title"));
        this.parent = parent;
        this.overrides = Objects.requireNonNull(overrides);
        this.originalKey = Objects.requireNonNull(originalKey);
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
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.override_edit_title")),
                font
        ));

        this.addRenderableWidget(new StringWidget(
                leftX,
                45,
                contentWidth,
                14,
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.override_key")),
                font
        ));

        keyBox = new EditBox(font, leftX, 60, contentWidth, 20, Component.empty());
        keyBox.setValue(originalKey);
        keyBox.setMaxLength(64);
        this.addRenderableWidget(keyBox);

        this.addRenderableWidget(new StringWidget(
                leftX,
                88,
                contentWidth,
                14,
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.override_target")),
                font
        ));

        targetOptions = buildTargetOptions();
        String current = normalizeTargetId(overrides.get(originalKey));
        targetIndex = targetOptions.indexOf(current);
        if (targetIndex < 0) targetIndex = 0;

        targetButton = Button.builder(Objects.requireNonNull(targetLabel(targetOptions.get(targetIndex))), b -> {
                    targetIndex = (targetIndex + 1) % targetOptions.size();
                    b.setMessage(Objects.requireNonNull(targetLabel(targetOptions.get(targetIndex))));
                })
                .bounds(leftX, 104, contentWidth, 20)
                .build();
        this.addRenderableWidget(targetButton);

        int buttonW = (contentWidth - 10) / 2;
        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.done")), b -> onDone())
                .bounds(leftX, this.height - 28, buttonW, 20)
                .build());

        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cancel")), b -> onCancel())
                .bounds(leftX + buttonW + 10, this.height - 28, buttonW, 20)
                .build());
    }

    private Component targetLabel(String rawTarget) {
        String display = Objects.requireNonNull(displayTarget(rawTarget), "displayTarget");
        return Objects.requireNonNull(Component.translatable("bassshakertelemetry.soundscape.override_target_value"))
                .append(": ")
                .append(Objects.requireNonNull(Component.literal(display), "literal"));
    }

    private void onDone() {
        String newKey = (keyBox == null) ? originalKey : keyBox.getValue();
        newKey = (newKey == null) ? "" : newKey.trim();
        if (newKey.isEmpty()) {
            newKey = originalKey;
        }

        // Avoid collisions (case-insensitive).
        for (String k : overrides.keySet()) {
            if (k == null) continue;
            if (k.equalsIgnoreCase(newKey) && !k.equalsIgnoreCase(originalKey)) {
                newKey = originalKey;
                break;
            }
        }

        String target = (targetOptions == null || targetOptions.isEmpty())
                ? "grp:All"
                : targetOptions.get(Math.max(0, Math.min(targetIndex, targetOptions.size() - 1)));

        // Rename if needed.
        if (!newKey.equals(originalKey)) {
            overrides.remove(originalKey);
        }
        overrides.put(newKey, target);

        net.minecraft.client.Minecraft mc = this.minecraft;
        if (mc != null) {
            parent.onOverrideEdited();
            mc.setScreen(parent);
        }
    }

    private void onCancel() {
        net.minecraft.client.Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        onCancel();
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

    static String displayTarget(String target) {
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
}
