package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.client.VibrationIngress;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal spatial debugger: shows last event + spatial hint + engine status string.
 */
public final class SpatialDebuggerScreen extends Screen {
    private final Screen parent;

    public SpatialDebuggerScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.spatial_dbg.title"));
        this.parent = parent;
    }

    @Override
    @SuppressWarnings("null")
    protected void init() {
        super.init();

        AudioOutputEngine.setDebugCaptureEnabled(true);

        int centerX = this.width / 2;
        int contentWidth = Math.min(310, this.width - 40);
        int leftX = centerX - (contentWidth / 2);

        this.addRenderableWidget(Button.builder(
                Objects.requireNonNull(Component.translatable("bassshakertelemetry.config.done")),
                b -> onDone())
            .bounds(leftX, this.height - 28, contentWidth, 20)
            .build());
    }

    private void onDone() {
        AudioOutputEngine.setDebugCaptureEnabled(false);
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        onDone();
    }

    @Override
    @SuppressWarnings("null")
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        var font = Objects.requireNonNull(this.font, "font");
        int x = 12;
        int y = 16;

        guiGraphics.drawString(font, Objects.requireNonNull(Component.translatable("bassshakertelemetry.spatial_dbg.title")), x, y, 0xFFFFFF);
        y += 18;

        guiGraphics.drawString(font, VibrationIngress.getOverlayLine1(), x, y, 0xCCCCCC);
        y += 10;
        guiGraphics.drawString(font, VibrationIngress.getOverlayLine2(), x, y, 0xCCCCCC);
        y += 10;

        String l3 = VibrationIngress.getOverlayLine3();
        if (!l3.isBlank()) {
            guiGraphics.drawString(font, l3, x, y, 0xFFCC66);
            y += 10;
        }

        guiGraphics.drawString(font, VibrationIngress.getOverlayLine4(), x, y, 0x99FF99);
        y += 12;

        boolean ss = BstConfig.get().soundScapeEnabled;
        boolean sp = BstConfig.get().soundScapeSpatialEnabled;
        double dist = BstConfig.get().soundScapeSpatialDistanceAttenStrength;
        guiGraphics.drawString(font, "Sound Scape=" + (ss ? "ON" : "OFF") + "  Spatial=" + (sp ? "ON" : "OFF") + "  DistAtten=" + (int) Math.round(dist * 100.0) + "%", x, y, 0x66CCFF);
        y += 12;

        Map<String, String> bus = BstConfig.get().soundScapeBusRouting;
        if (bus != null && !bus.isEmpty()) {
            String ui = displayTarget(bus.get("ui"));
            String danger = displayTarget(bus.get("danger"));
            String env = displayTarget(bus.get("environmental"));
            String cont = displayTarget(bus.get("continuous"));
            String impact = displayTarget(bus.get("impact"));
            String modded = displayTarget(bus.get("modded"));
            guiGraphics.drawString(font, "Bus routing: ui=" + ui + " danger=" + danger + " env=" + env, x, y, 0x66CCFF);
            y += 10;
            guiGraphics.drawString(font, "            cont=" + cont + " impact=" + impact + " modded=" + modded, x, y, 0x66CCFF);
            y += 14;
        } else {
            y += 14;
        }

        AudioOutputEngine.DebugSnapshot snap = AudioOutputEngine.getDebugSnapshot();
        if (snap == null) {
            guiGraphics.drawString(font, "No audio debug snapshot yet.", x, y, 0xFFCC66);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        // Latency / buffer stats
        String lat = String.format(Locale.ROOT,
                "Device buffer: %.1fms (queued~%.1fms)  bytes=%d avail=%d",
                snap.deviceBufferMs,
                snap.queuedMs,
                snap.deviceBufferBytes,
                snap.deviceAvailableBytes);
        guiGraphics.drawString(font, lat, x, y, 0x66CCFF);
        y += 14;

        // Per-channel meters
        final String[] ids2 = new String[]{"L", "R"};
        final String[] ids8 = new String[]{"FL", "FR", "C", "LFE", "SL", "SR", "BL", "BR"};
        int barW = Math.min(150, this.width - 70);
        int barH = 6;
        int barX = x + 28;
        for (int c = 0; c < snap.channels; c++) {
            String id = (snap.channels == 2) ? ids2[Math.min(c, 1)] : ids8[Math.min(c, 7)];
            float rms = (snap.rms01 != null && c < snap.rms01.length) ? snap.rms01[c] : 0.0f;
            float peak = (snap.peak01 != null && c < snap.peak01.length) ? snap.peak01[c] : 0.0f;

            guiGraphics.drawString(font, id, x, y - 1, 0xCCCCCC);
            guiGraphics.fill(barX, y, barX + barW, y + barH, 0xFF202020);
            int rmsW = (int) Math.round(barW * clamp01(rms));
            guiGraphics.fill(barX, y, barX + rmsW, y + barH, 0xFF3A6DFF);
            int peakX = barX + (int) Math.round(barW * clamp01(peak));
            guiGraphics.fill(peakX, y, peakX + 1, y + barH, 0xFFFFFFFF);
            y += 10;
        }

        // Waveform (mono)
        int waveW = Math.min(256, this.width - 24);
        int waveH = 40;
        int waveX = x;
        int waveY = y + 6;
        guiGraphics.drawString(font, "Waveform", waveX, y - 2, 0xCCCCCC);
        guiGraphics.fill(waveX, waveY, waveX + waveW, waveY + waveH, 0xFF101010);
        int midY = waveY + (waveH / 2);
        guiGraphics.fill(waveX, midY, waveX + waveW, midY + 1, 0xFF303030);
        float[] wave = snap.waveform;
        if (wave != null && wave.length >= 2) {
            for (int i = 0; i < wave.length; i++) {
                int px = waveX + (int) Math.round((i / (double) (wave.length - 1)) * (waveW - 1));
                double v = clamp(wave[i], -1.0, 1.0);
                int py = midY - (int) Math.round(v * (waveH * 0.45));
                if (py < waveY) py = waveY;
                if (py >= (waveY + waveH)) py = waveY + waveH - 1;
                guiGraphics.fill(px, py, px + 1, py + 1, 0xFFFFFFFF);
            }
        }
        y = waveY + waveH + 10;

        // Spectrogram (low-frequency bins)
        guiGraphics.drawString(font, "Spectrogram", x, y - 2, 0xCCCCCC);
        int specCell = 2;
        int specCols = Math.max(1, snap.spectrogramCols);
        int specBins = Math.max(1, snap.spectrogramBins);
        int specW = Math.min(this.width - 24, specCols * specCell);
        int specH = Math.min(120, specBins * specCell);
        int specX = x;
        int specY = y + 6;
        guiGraphics.fill(specX, specY, specX + specW, specY + specH, 0xFF101010);
        float[] spec = snap.spectrogram;
        if (spec != null && spec.length >= (specCols * specBins)) {
            int colStart = snap.spectrogramWriteCol;
            int drawCols = Math.min(specCols, specW / specCell);
            int drawBins = Math.min(specBins, specH / specCell);
            for (int dc = 0; dc < drawCols; dc++) {
                int sc = (colStart + dc) % specCols;
                int base = sc * specBins;
                int dx = specX + (dc * specCell);
                for (int rb = 0; rb < drawBins; rb++) {
                    // Draw low -> high bottom-up.
                    int b = rb;
                    float vv = spec[base + b];
                    int g = (int) Math.round(255.0 * clamp01(vv));
                    int col = 0xFF000000 | (g << 16) | (g << 8) | g;
                    int dy = specY + (specH - ((rb + 1) * specCell));
                    guiGraphics.fill(dx, dy, dx + specCell, dy + specCell, col);
                }
            }
        }
        y = specY + specH + 10;

        // Event timeline
        guiGraphics.drawString(font, "Recent events", x, y, 0xCCCCCC);
        y += 12;
        AudioOutputEngine.DebugEvent[] events = AudioOutputEngine.getRecentDebugEvents(10);
        long nowNs = System.nanoTime();
        for (AudioOutputEngine.DebugEvent ev : events) {
            if (ev == null) {
                continue;
            }
            long ageMs = Math.max(0, (nowNs - ev.createdNanos) / 1_000_000L);
            String key = (ev.debugKey == null || ev.debugKey.isBlank()) ? "(none)" : ev.debugKey;
            if (key.length() > 26) {
                key = key.substring(0, 26);
            }
            String line = String.format(Locale.ROOT,
                    "%4dms  %-26s  bus=%s  az=%.0f  d=%.1f",
                    ageMs,
                    key,
                    (ev.bus == null) ? "?" : ev.bus.name().toLowerCase(Locale.ROOT),
                    ev.azimuthDeg,
                    ev.distanceM);
            guiGraphics.drawString(font, line, x, y, 0xCCCCCC);
            y += 10;
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static float clamp01(float v) {
        if (v < 0.0f) return 0.0f;
        if (v > 1.0f) return 1.0f;
        return v;
    }

    private static String displayTarget(String target) {
        if (target == null || target.isBlank()) {
            return "All";
        }
        String v = target.trim();
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.startsWith("ch:")) {
            return v.substring(3).toUpperCase(Locale.ROOT);
        }
        if (lower.startsWith("grp:")) {
            String name = v.substring(4).trim();
            return name.isEmpty() ? "All" : name;
        }
        return v;
    }
}
