package com.smoky.bassshakertelemetry.client.accessibility;

import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HudCueManager {
    private static final HudCueManager INSTANCE = new HudCueManager();

    public static HudCueManager get() {
        return INSTANCE;
    }

    private final ArrayList<Cue> cues = new ArrayList<>();
    private final Map<HudCueType, Long> lastFireMsByType = new HashMap<>();

    private HudCueManager() {
    }

    public void push(HudCueType type, Component text) {
        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled || !cfg.accessibilityHudEnabled || !cfg.accessibilityHudCuesEnabled) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        int ttlMs = Math.max(250, cfg.accessibilityHudCueMs);

        cues.add(new Cue(type, text, nowMs + ttlMs));
        pruneAndTrim(nowMs, cfg.accessibilityHudMaxLines);
    }

    public boolean canFire(HudCueType type, int cooldownMs) {
        long nowMs = System.currentTimeMillis();
        long cd = Math.max(0, cooldownMs);
        Long last = lastFireMsByType.get(type);
        if (last != null && (nowMs - last) < cd) {
            return false;
        }
        lastFireMsByType.put(type, nowMs);
        return true;
    }

    public List<CueLine> getLines() {
        BstConfig.Data cfg = BstConfig.get();
        if (!cfg.enabled || !cfg.accessibilityHudEnabled || !cfg.accessibilityHudCuesEnabled) {
            return List.of();
        }

        long nowMs = System.currentTimeMillis();
        pruneAndTrim(nowMs, cfg.accessibilityHudMaxLines);

        ArrayList<CueLine> out = new ArrayList<>(cues.size());
        for (int i = 0; i < cues.size(); i++) {
            Cue c = cues.get(i);
            out.add(new CueLine(c.type, c.text));
        }
        return out;
    }

    private void pruneAndTrim(long nowMs, int maxLines) {
        int i = 0;
        while (i < cues.size()) {
            if (cues.get(i).expiresAtMs <= nowMs) {
                cues.remove(i);
            } else {
                i++;
            }
        }

        int max = Math.max(1, maxLines);
        while (cues.size() > max) {
            cues.remove(0);
        }
    }

    private record Cue(HudCueType type, Component text, long expiresAtMs) {
    }

    public record CueLine(HudCueType type, Component text) {
    }
}
