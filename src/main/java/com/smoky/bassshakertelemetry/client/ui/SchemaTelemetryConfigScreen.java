package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioDeviceUtil;
import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonCycleButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonStyle;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonVolumeSlider;
import com.smoky.bassshakertelemetry.client.ui.neon.schema.NeonUiSchema;
import com.smoky.bassshakertelemetry.client.ui.neon.schema.NeonUiSchemaLoader;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SchemaTelemetryConfigScreen extends Screen {
    private static final String SCREEN_ID = "telemetry_config";

    private final Screen parent;

    private Button outputDeviceButton;
    private List<String> devices = List.of("<Default>");
    private String selectedDevice = "<Default>";

    private final Map<String, Boolean> boolState = new HashMap<>();
    private NeonVolumeSlider volumeSlider;

    public SchemaTelemetryConfigScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.config.title"));
        this.parent = parent;
    }

    @Override
    @SuppressWarnings("null")
    protected void init() {
        super.init();
        NeonStyle.initClient();

        NeonUiSchema.ScreenSchema schema = NeonUiSchemaLoader.loadActiveScreenOrNull(SCREEN_ID);
        if (schema == null || schema.rows == null) {
            // Fallback (should be rare, because ClientInit checks before constructing this screen).
            if (this.minecraft != null) {
                this.minecraft.setScreen(new TelemetryConfigScreen(parent));
            }
            return;
        }

        int centerX = this.width / 2;
        var font = Objects.requireNonNull(this.font, "font");

        int contentWidth = Math.min(310, this.width - 40);
        int leftX = centerX - (contentWidth / 2);
        int rowH = 20;
        int rowGap = 6;

        loadDevices();
        loadBoundState(schema);

        this.addRenderableWidget(new StringWidget(
                centerX - 100,
                20,
                200,
                20,
                Component.translatable(Objects.requireNonNullElse(schema.titleKey, "bassshakertelemetry.config.title")),
                font
        ));

        int y = 50;
        for (NeonUiSchema.Node row : schema.rows) {
            if (row == null || row.type == null) continue;
            switch (row.type) {
                case "device_button" -> {
                    outputDeviceButton = new NeonButton(
                            leftX,
                            y,
                            contentWidth,
                            rowH,
                            deviceButtonLabel(),
                            () -> {
                                if (this.minecraft != null) {
                                    this.minecraft.setScreen(new OutputDeviceScreen(this, this::setSelectedDevice));
                                }
                            }
                    );
                    this.addRenderableWidget(outputDeviceButton);
                    y += rowH + rowGap;
                }
                case "slider" -> {
                    String bind = row.bind;
                    double initial = readDoubleBind(bind, BstConfig.get(), 0.35);
                    volumeSlider = new NeonVolumeSlider(
                            leftX,
                            y,
                            contentWidth,
                            rowH,
                            Component.translatable(Objects.requireNonNullElse(row.textKey, "bassshakertelemetry.config.master_volume")),
                            initial
                    );
                    this.addRenderableWidget(volumeSlider);
                    y += rowH + rowGap;
                }
                case "button" -> {
                    Component label = Component.translatable(Objects.requireNonNullElse(row.textKey, ""));
                    this.addRenderableWidget(new NeonButton(
                            leftX,
                            y,
                            contentWidth,
                            rowH,
                            label,
                            () -> handleAction(row.action)
                    ));
                    y += rowH + rowGap;
                }
                case "toggle" -> {
                    String bind = row.bind;
                    boolean v = boolState.getOrDefault(bind, false);
                    this.addRenderableWidget(new NeonCycleButton<>(
                            leftX,
                            y,
                            contentWidth,
                            rowH,
                            Component.translatable(Objects.requireNonNullElse(row.textKey, "")),
                            List.of(Boolean.TRUE, Boolean.FALSE),
                            v,
                            vv -> vv ? Component.translatable("options.on") : Component.translatable("options.off"),
                            vv -> boolState.put(bind, vv)
                    ));
                    y += rowH + rowGap;
                }
                case "hstack" -> {
                    int gap = Math.max(0, row.gap == null ? 10 : row.gap);
                    List<NeonUiSchema.Node> children = row.children;
                    if (children == null || children.isEmpty()) {
                        continue;
                    }

                    int count = children.size();
                    int colWidth = Math.max(60, (contentWidth - (gap * (count - 1))) / count);
                    int x = leftX;
                    for (int i = 0; i < count; i++) {
                        NeonUiSchema.Node child = children.get(i);
                        if (child == null || child.type == null) {
                            x += colWidth + gap;
                            continue;
                        }
                        if ("toggle".equals(child.type)) {
                            String bind = child.bind;
                            boolean v = boolState.getOrDefault(bind, false);
                            this.addRenderableWidget(new NeonCycleButton<>(
                                    x,
                                    y,
                                    colWidth,
                                    rowH,
                                    Component.translatable(Objects.requireNonNullElse(child.textKey, "")),
                                    List.of(Boolean.TRUE, Boolean.FALSE),
                                    v,
                                    vv -> vv ? Component.translatable("options.on") : Component.translatable("options.off"),
                                    vv -> boolState.put(bind, vv)
                            ));
                        } else if ("button".equals(child.type)) {
                            Component label = Component.translatable(Objects.requireNonNullElse(child.textKey, ""));
                            this.addRenderableWidget(new NeonButton(
                                    x,
                                    y,
                                    colWidth,
                                    rowH,
                                    label,
                                    () -> handleAction(child.action)
                            ));
                        }
                        x += colWidth + gap;
                    }
                    y += rowH + rowGap;
                }
                default -> {
                    // Unknown row type: ignore.
                }
            }
        }

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
                (contentWidth - 10) / 2,
                20,
                Component.translatable("bassshakertelemetry.config.done"),
                this::onDone
        ));

        this.addRenderableWidget(new NeonButton(
                leftX + ((contentWidth - 10) / 2) + 10,
                this.height - 28,
                (contentWidth - 10) / 2,
                20,
                Component.translatable("bassshakertelemetry.config.cancel"),
                this::onCancel
        ));
    }

    private void loadDevices() {
        List<String> deviceList = new ArrayList<>();
        deviceList.add("<Default>");
        deviceList.addAll(AudioDeviceUtil.listOutputDeviceNames(AudioOutputEngine.get().formatStereo()));
        this.devices = deviceList;

        String current = BstConfig.get().outputDeviceName;
        String currentDisplay = AudioDeviceUtil.resolveDisplayName(current, AudioOutputEngine.get().formatStereo());
        if (!this.devices.contains(currentDisplay)) currentDisplay = "<Default>";
        this.selectedDevice = currentDisplay;
    }

    private void loadBoundState(NeonUiSchema.ScreenSchema schema) {
        BstConfig.Data data = BstConfig.get();
        for (NeonUiSchema.Node row : schema.rows) {
            collectBind(row, data);
        }
    }

    private void collectBind(NeonUiSchema.Node node, BstConfig.Data data) {
        if (node == null) return;
        if (node.bind != null && !node.bind.isBlank()) {
            Object v = readBind(node.bind, data);
            if (v instanceof Boolean b) {
                boolState.put(node.bind, b);
            }
        }
        if (node.children != null) {
            for (NeonUiSchema.Node c : node.children) {
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

    private static double readDoubleBind(String fieldName, BstConfig.Data data, double fallback) {
        Object v = readBind(fieldName, data);
        if (v instanceof Double d) return d;
        if (v instanceof Float f) return f.doubleValue();
        if (v instanceof Integer i) return i.doubleValue();
        return fallback;
    }

    private void handleAction(String action) {
        if (action == null) return;
        if (this.minecraft == null) return;

        switch (action) {
            case "openAdvanced" -> this.minecraft.setScreen(new AdvancedSettingsScreen(this));
            case "openSoundscape" -> this.minecraft.setScreen(new SoundScapeConfigScreen(this));
            default -> {
            }
        }
    }

    private void onDone() {
        BstConfig.Data data = BstConfig.get();
        data.outputDeviceName = "<Default>".equals(selectedDevice) ? "" : selectedDevice;
        if (volumeSlider != null) {
            data.masterVolume = volumeSlider.getValue();
        }

        applyBoolIfPresent(data, "damageBurstEnabled");
        applyBoolIfPresent(data, "biomeChimeEnabled");
        applyBoolIfPresent(data, "roadTextureEnabled");
        applyBoolIfPresent(data, "soundHapticsEnabled");
        applyBoolIfPresent(data, "gameplayHapticsEnabled");
        applyBoolIfPresent(data, "accessibilityHudEnabled");

        BstConfig.set(data);

        AudioOutputEngine.get().startOrRestart();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void applyBoolIfPresent(BstConfig.Data data, String fieldName) {
        Boolean v = boolState.get(fieldName);
        if (v == null) return;
        try {
            Field f = BstConfig.Data.class.getField(fieldName);
            if (f.getType() == boolean.class) {
                f.setBoolean(data, v);
            }
        } catch (Throwable ignored) {
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

    private Component deviceButtonLabel() {
        return Component.translatable("bassshakertelemetry.config.output_device")
                .append(": ")
                .append(Component.literal(Objects.requireNonNull(selectedDevice)));
    }

    void setSelectedDevice(String displayDeviceId) {
        String v = Objects.requireNonNullElse(displayDeviceId, "<Default>");
        if (!devices.contains(v)) {
            v = "<Default>";
        }
        this.selectedDevice = v;

        if (outputDeviceButton != null) {
            outputDeviceButton.setMessage(deviceButtonLabel());
        }
    }
}
