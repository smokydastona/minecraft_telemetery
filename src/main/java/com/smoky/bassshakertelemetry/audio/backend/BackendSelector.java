package com.smoky.bassshakertelemetry.audio.backend;

import com.smoky.bassshakertelemetry.config.BstConfig;

/**
 * Backend selection helper.
 *
 * <p>Phase 1 foundation: config selects an id, but we currently ship JavaSound as the stable backend.
 * Other ids fall back to JavaSound for now.
 */
public final class BackendSelector {
    private BackendSelector() {
    }

    public static HapticAudioBackend select(BstConfig.Data cfg) {
        String raw = (cfg == null) ? null : cfg.audioBackend;
        String id = (raw == null) ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);

        // Future: openal/wasapi/asio.
        // For Phase 1 stabilization we keep a single proven backend.
        switch (id) {
            case "javasound":
            case "":
            default:
                return new JavaSoundBackend();
        }
    }
}
