package com.smoky.bassshakertelemetry.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.smoky.bassshakertelemetry.BassShakerTelemetryMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BstConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = BassShakerTelemetryMod.MODID + ".json";
    private static volatile Data INSTANCE = new Data();

    private BstConfig() {
    }

    public static Data get() {
        return INSTANCE;
    }

    public static synchronized void set(Data data) {
        INSTANCE = data;
        save();
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    public static synchronized void load() {
        var path = path();
        if (!Files.exists(path)) {
            save();
            return;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Data parsed = GSON.fromJson(json, Data.class);
            if (parsed != null) {
                INSTANCE = sanitize(parsed);
            }
        } catch (Exception ignored) {
            // If config is corrupt, keep defaults.
        }
    }

    public static synchronized void save() {
        var path = path();
        try {
            Files.createDirectories(path.getParent());
            INSTANCE = sanitize(INSTANCE);
            String json = GSON.toJson(INSTANCE);
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static Data sanitize(Data data) {
        Data d = (data == null) ? new Data() : data;

        // --- Sound Scape defaults ---
        if (d.soundScapeGroups == null) {
            d.soundScapeGroups = new HashMap<>();
        }
        if (!d.soundScapeGroups.containsKey("All")) {
            d.soundScapeGroups.put("All", defaultAllChannels());
        } else {
            // Ensure the group isn't empty (empty groups are confusing and break routing).
            List<String> v = d.soundScapeGroups.get("All");
            if (v == null || v.isEmpty()) {
                d.soundScapeGroups.put("All", defaultAllChannels());
            }
        }

        if (d.soundScapeCategoryRouting == null) {
            d.soundScapeCategoryRouting = new HashMap<>();
        }
        // Default all known categories to All.
        for (String cat : SoundScapeCategories.KNOWN_CATEGORIES) {
            d.soundScapeCategoryRouting.putIfAbsent(cat, "grp:All");
        }

        if (d.soundScapeOverrides == null) {
            d.soundScapeOverrides = new HashMap<>();
        }

        // Normalize group channel ids.
        Map<String, List<String>> normalizedGroups = new HashMap<>();
        for (Map.Entry<String, List<String>> e : d.soundScapeGroups.entrySet()) {
            if (e == null) continue;
            String name = (e.getKey() == null) ? "" : e.getKey().trim();
            if (name.isEmpty()) continue;

            List<String> channels = e.getValue();
            if (channels == null) {
                channels = List.of();
            }
            ArrayList<String> out = new ArrayList<>();
            for (String ch : channels) {
                String n = normalizeChannelId(ch);
                if (!n.isEmpty() && !out.contains(n)) {
                    out.add(n);
                }
            }
            if (!out.isEmpty()) {
                normalizedGroups.put(name, out);
            }
        }
        // Always re-apply All.
        normalizedGroups.putIfAbsent("All", defaultAllChannels());
        d.soundScapeGroups = normalizedGroups;

        // Normalize routing targets.
        d.soundScapeCategoryRouting = normalizeRoutingMap(d.soundScapeCategoryRouting);
        d.soundScapeOverrides = normalizeRoutingMap(d.soundScapeOverrides);

        // Clamp channel count to supported values (currently 2 or 8).
        if (d.soundScapeChannels != 2 && d.soundScapeChannels != 8) {
            d.soundScapeChannels = 8;
        }

        return d;
    }

    private static Map<String, String> normalizeRoutingMap(Map<String, String> in) {
        Map<String, String> out = new HashMap<>();
        if (in == null) {
            return out;
        }
        for (Map.Entry<String, String> e : in.entrySet()) {
            if (e == null) continue;
            String key = (e.getKey() == null) ? "" : e.getKey().trim();
            if (key.isEmpty()) continue;
            String v = normalizeTargetId(e.getValue());
            if (!v.isEmpty()) {
                out.put(key, v);
            }
        }
        return out;
    }

    private static List<String> defaultAllChannels() {
        // 7.1 channel ids. Order here doesn't matter for a group; it's a set.
        return List.of("FL", "FR", "C", "LFE", "SL", "SR", "BL", "BR");
    }

    private static String normalizeChannelId(String raw) {
        if (raw == null) return "";
        String v = raw.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "L" -> "FL";
            case "R" -> "FR";
            case "FL", "FR", "C", "LFE", "SL", "SR", "BL", "BR" -> v;
            default -> "";
        };
    }

    private static String normalizeTargetId(String raw) {
        if (raw == null) {
            return "";
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return "";
        }

        // Allow bare channel ids (legacy-ish).
        String ch = normalizeChannelId(v);
        if (!ch.isEmpty()) {
            return "ch:" + ch;
        }

        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.startsWith("ch:")) {
            String n = normalizeChannelId(v.substring(3));
            return n.isEmpty() ? "" : ("ch:" + n);
        }
        if (lower.startsWith("grp:")) {
            String name = v.substring(4).trim();
            return name.isEmpty() ? "" : ("grp:" + name);
        }

        // Treat anything else as a group name.
        String name = v.trim();
        return name.isEmpty() ? "" : ("grp:" + name);
    }

    public static final class Data {
        // Master switches
        public boolean enabled = true;

        // Audio output
        public String outputDeviceName = ""; // empty = default
        public double masterVolume = 0.35;

        // JavaSound buffer size. 0 = auto/default line buffer.
        // Larger buffers tend to be more stable but add latency.
        public int javaSoundBufferMs = 0;

        // Developer tools
        public boolean debugOverlayEnabled = false;

        // Telemetry-to-signal mapping toggles
        public boolean damageBurstEnabled = true;
        public boolean biomeChimeEnabled = true;

        // Simulated road texture (low-frequency rumble layer)
        public boolean roadTextureEnabled = false;

        // Sound-to-haptics (treat common game sounds as rumble events)
        public boolean soundHapticsEnabled = true;
        public double soundHapticsGain = 1.0;
        public int soundHapticsCooldownMs = 35;

        // Gameplay-to-haptics (explicitly non-sexual, game-only)
        public boolean gameplayHapticsEnabled = true;
        public double gameplayHapticsGain = 1.0;
        public int gameplayHapticsCooldownMs = 80;

        // On-foot movement haptics (not car-like)
        public boolean footstepHapticsEnabled = true;
        public double footstepHapticsGain = 0.55;

        // Mounted haptics (hooves / flying mount wind)
        public boolean mountedHapticsEnabled = true;
        public double mountedHapticsGain = 0.55;

        // Swing-synced mining haptics (matches the on-screen arm swing timing)
        public boolean miningSwingHapticsEnabled = true;
        public double miningSwingHapticsGain = 0.55;

        public boolean gameplayAttackClickEnabled = true;
        public boolean gameplayUseClickEnabled = true;
        // Legacy periodic mining pulse (kept, but swing-synced mining is preferred)
        public boolean gameplayMiningPulseEnabled = false;
        public int gameplayMiningPulsePeriodMs = 120;
        public boolean gameplayXpEnabled = true;

        // Output safety / mixing
        // Headroom scales the final output before int16 conversion to reduce clipping when effects stack.
        public double outputHeadroom = 0.85;
        // Soft-limiter drive. Higher = more saturation and less peak clipping.
        public double limiterDrive = 2.5;

        public int damageBurstMs = 90;
        public double damageBurstGain = 0.8;

        // Biome chime tuning
        public double biomeChimeGain = 0.35;

        // Road texture tuning
        public double roadTextureGain = 0.18;
        public double roadTextureCutoffHz = 30.0;

        // Accel bump (short one-shot thump derived from acceleration spikes)
        public boolean accelBumpEnabled = false;
        public double accelBumpThreshold = 0.14;
        public int accelBumpMs = 60;
        public double accelBumpGain = 0.65;

        // --- Sound Scape (multi-transducer routing) ---
        // When enabled, the audio engine will attempt to open an 8-channel (7.1) JavaSound output line.
        // If the selected device does not support 8 channels, it will fall back to stereo.
        public boolean soundScapeEnabled = false;

        // Reserved for future: currently supported values are 2 or 8.
        public int soundScapeChannels = 8;

        // Category routing: category key -> target id ("ch:FL" or "grp:All").
        public Map<String, String> soundScapeCategoryRouting = new HashMap<>();

        // Groups: group name -> list of channel ids (FL, FR, C, LFE, SL, SR, BL, BR).
        public Map<String, List<String>> soundScapeGroups = new HashMap<>();

        // Optional per-effect overrides: debugKey/bucket -> target id.
        public Map<String, String> soundScapeOverrides = new HashMap<>();

        public boolean enabled() {
            return enabled;
        }
    }

    /**
     * Stable string category keys used by the Sound Scape routing UI and engine.
     * Keep these stable for config compatibility.
     */
    public static final class SoundScapeCategories {
        private SoundScapeCategories() {
        }

        public static final String ROAD = "road";
        public static final String DAMAGE = "damage";
        public static final String BIOME_CHIME = "biome_chime";
        public static final String ACCEL_BUMP = "accel_bump";
        public static final String SOUND = "sound";
        public static final String GAMEPLAY = "gameplay";
        public static final String FOOTSTEPS = "footsteps";
        public static final String MOUNTED = "mounted";
        public static final String MINING_SWING = "mining_swing";
        public static final String CUSTOM = "custom";

        private static final List<String> KNOWN_CATEGORIES = List.of(
                ROAD,
                DAMAGE,
                BIOME_CHIME,
                ACCEL_BUMP,
                SOUND,
                GAMEPLAY,
                FOOTSTEPS,
                MOUNTED,
                MINING_SWING,
                CUSTOM
        );
    }
}
