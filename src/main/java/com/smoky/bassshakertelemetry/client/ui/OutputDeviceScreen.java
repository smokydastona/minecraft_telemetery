package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioDeviceUtil;
import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonButton;
import com.smoky.bassshakertelemetry.client.ui.neon.NeonStyle;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class OutputDeviceScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> onSelectDevice;
    private final String initialSelectedDevice;

    private List<String> devices = List.of("<Default>");
    private String selectedDevice = "<Default>";

    public OutputDeviceScreen(Screen parent, Consumer<String> onSelectDevice) {
        this(parent, onSelectDevice, null);
    }

    public OutputDeviceScreen(Screen parent, Consumer<String> onSelectDevice, String initialSelectedDevice) {
        super(Component.translatable("bassshakertelemetry.config.output_device_title"));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.onSelectDevice = Objects.requireNonNull(onSelectDevice, "onSelectDevice");
        this.initialSelectedDevice = initialSelectedDevice;
    }

    @Override
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
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.output_device_title")),
                font
        ));

        List<String> deviceNames = new ArrayList<>();
        deviceNames.add("<Default>");
        deviceNames.addAll(AudioDeviceUtil.listOutputDeviceNames(AudioOutputEngine.get().formatStereo()));
        this.devices = deviceNames;

        // Prefer the selection from the parent screen (may be unsaved) so highlighting is accurate.
        String preferred = Objects.requireNonNullElse(initialSelectedDevice, "").trim();
        if (!preferred.isEmpty() && this.devices.contains(preferred)) {
            this.selectedDevice = preferred;
        } else {
            String current = BstConfig.get().outputDeviceName;
            String currentDisplay = AudioDeviceUtil.resolveDisplayName(current, AudioOutputEngine.get().formatStereo());
            if (!this.devices.contains(currentDisplay)) currentDisplay = "<Default>";
            this.selectedDevice = currentDisplay;
        }

        Minecraft mc = Objects.requireNonNull(this.minecraft, "minecraft");
        int listTop = 50;
        int listBottom = this.height - 34;
        int listHeight = Math.max(90, listBottom - listTop);

        DeviceList list = new DeviceList(mc, contentWidth, listHeight, listTop, listTop + listHeight, 18, leftX);
        for (String d : this.devices) {
            list.addDevice(new DeviceEntry(d));
        }
        this.addRenderableWidget(list);

        int buttonW = (contentWidth - 10) / 2;
        this.addRenderableWidget(UiTooltip.withLabelKey(new NeonButton(
            leftX,
            this.height - 28,
            buttonW,
            20,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.done")),
            this::onDone
        ), "bassshakertelemetry.config.done"));

        this.addRenderableWidget(UiTooltip.withLabelKey(new NeonButton(
            leftX + buttonW + 10,
            this.height - 28,
            buttonW,
            20,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cancel")),
            this::onCancel
        ), "bassshakertelemetry.config.cancel"));
    }

    private void onDone() {
        // Apply selection back to the parent; actual persistence happens when the user hits Done on the main config screen.
        onSelectDevice.accept(selectedDevice);
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

    private final class DeviceList extends ContainerObjectSelectionList<DeviceEntry> {
        private final int left;

        DeviceList(Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight, int left) {
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

        void addDevice(DeviceEntry entry) {
            this.addEntry(Objects.requireNonNull(entry));
        }
    }

    private final class DeviceEntry extends ContainerObjectSelectionList.Entry<DeviceEntry> {
        private final String deviceId;

        DeviceEntry(String deviceId) {
            this.deviceId = Objects.requireNonNull(Objects.requireNonNullElse(deviceId, "<Default>"));
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
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            selectedDevice = deviceId;
            return true;
        }

        @Override
        @SuppressWarnings("null")
        public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            var style = NeonStyle.get();
            int color = (Objects.equals(deviceId, selectedDevice)) ? style.text : style.textDim;
            guiGraphics.drawString(Objects.requireNonNull(OutputDeviceScreen.this.font), Objects.requireNonNull(deviceId), x + 4, y + 5, color);
        }

    }
}
