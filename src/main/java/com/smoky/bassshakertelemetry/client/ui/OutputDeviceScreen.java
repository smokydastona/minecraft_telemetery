package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioDeviceUtil;
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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class OutputDeviceScreen extends Screen {
    private final TelemetryConfigScreen parent;

    private List<String> devices = List.of("<Default>");
    private String selectedDevice = "<Default>";

    public OutputDeviceScreen(TelemetryConfigScreen parent) {
        super(Component.translatable("bassshakertelemetry.config.output_device_title"));
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
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.output_device_title")),
                font
        ));

        List<String> deviceNames = new ArrayList<>();
        deviceNames.add("<Default>");
        deviceNames.addAll(AudioDeviceUtil.listOutputDeviceNames(AudioOutputEngine.get().formatStereo()));
        this.devices = deviceNames;

        String current = BstConfig.get().outputDeviceName;
        String currentDisplay = AudioDeviceUtil.resolveDisplayName(current, AudioOutputEngine.get().formatStereo());
        if (!this.devices.contains(currentDisplay)) currentDisplay = "<Default>";
        this.selectedDevice = currentDisplay;

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
        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.done")), b -> onDone())
                .bounds(leftX, this.height - 28, buttonW, 20)
                .build());

        this.addRenderableWidget(Button.builder(Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.cancel")), b -> onCancel())
                .bounds(leftX + buttonW + 10, this.height - 28, buttonW, 20)
                .build());
    }

    private void onDone() {
        // Apply selection back to parent; actual persistence happens when the user hits Done on the main config screen.
        parent.setSelectedDevice(selectedDevice);
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
            int color = (Objects.equals(deviceId, selectedDevice)) ? 0xFFFFFF : 0xE0E0E0;
            guiGraphics.drawString(Objects.requireNonNull(OutputDeviceScreen.this.font), Objects.requireNonNull(deviceId), x + 4, y + 5, color);
        }

    }
}
