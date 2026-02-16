package com.smoky.bassshakertelemetry.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Data-driven vibration profiles for the chair/shaker.
 *
 * This is intentionally separate from {@link BstConfig} so users can tune tactile feel
 * (freq/intensity/duration/pattern/falloff) without recompiling.
 */
public final class BstVibrationProfiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "bassshakertelemetry_vibration_profiles.json";

    private static volatile Store INSTANCE = Store.defaults();

    private BstVibrationProfiles() {
    }

    public static Store get() {
        return INSTANCE;
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    public static synchronized void load() {
        Path p = path();
        if (!Files.exists(p)) {
            saveDefaults(p);
            INSTANCE = Store.defaults();
            return;
        }

        try {
            String json = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                INSTANCE = Store.defaults();
                return;
            }

            Store store = Store.fromJson(root);
            INSTANCE = (store != null) ? store : Store.defaults();
        } catch (Exception ignored) {
            INSTANCE = Store.defaults();
        }
    }

    private static void saveDefaults(Path p) {
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, Store.defaultsJson(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    public static final class Store {
        public final Global global;
        private final Map<String, Profile> profiles;

        private Store(Global global, Map<String, Profile> profiles) {
            this.global = global;
            this.profiles = profiles;
        }

        public Profile getProfile(String key) {
            return profiles.get(key);
        }

        public Resolved resolve(String key, double scale01, double distanceScale01) {
            Profile p = profiles.get(key);
            if (p == null) {
                return null;
            }

            double freq = clamp(p.frequency, global.minFrequency, global.maxFrequency);
            int dur = Math.max(10, p.duration);
            double intensity = clamp(p.intensity, 0.0, global.maxIntensity);

            if (p.scaleByDamage || p.scaleByFallDistance) {
                intensity *= clamp(scale01, 0.0, 1.0);
            }

            if (p.falloff != null && !p.falloff.isBlank() && !"none".equalsIgnoreCase(p.falloff)) {
                intensity *= clamp(distanceScale01, 0.0, 1.0);
            }

            intensity = clamp(intensity, 0.0, global.maxIntensity);

            return new Resolved(freq, dur, intensity, clamp(p.noiseMix, 0.0, 1.0), p.pattern);
        }

        public static Store fromJson(JsonObject root) {
            Global global = Global.fromJson(root.getAsJsonObject("global"));
            if (global == null) {
                global = new Global();
            }

            Map<String, Profile> map = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                if ("global".equals(e.getKey())) {
                    continue;
                }
                flatten(map, e.getKey(), e.getValue());
            }

            // Always keep at least the defaults so missing keys don't break existing feel.
            for (Map.Entry<String, Profile> d : defaults().profiles.entrySet()) {
                map.putIfAbsent(d.getKey(), d.getValue());
            }

            return new Store(global, map);
        }

        private static void flatten(Map<String, Profile> out, String prefix, JsonElement el) {
            if (el == null || el.isJsonNull()) {
                return;
            }

            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (looksLikeProfile(obj)) {
                    Profile p = Profile.fromJson(obj);
                    if (p != null) {
                        out.put(prefix, p);
                    }
                    return;
                }

                for (Map.Entry<String, JsonElement> child : obj.entrySet()) {
                    flatten(out, prefix + "." + child.getKey(), child.getValue());
                }
            }
        }

        private static boolean looksLikeProfile(JsonObject obj) {
            return obj.has("frequency") && obj.has("intensity") && obj.has("duration");
        }

        public static Store defaults() {
            Map<String, Profile> map = new HashMap<>();

            // Damage
            map.put("damage.generic", new Profile(55.0, 0.40, 120, 0.25, "single", "none", true, false));
            map.put("damage.fall", new Profile(45.0, 0.30, 180, 0.30, "single", "none", false, true));
            map.put("damage.death", new Profile(30.0, 0.80, 1200, 0.55, "fade_out", "none", false, false));

            // Periodic dangers
            map.put("damage.fire", new Profile(34.0, 0.22, 80, 0.45, "single", "none", false, false));
            map.put("damage.drowning", new Profile(30.0, 0.22, 110, 0.55, "single", "none", false, false));
            map.put("damage.poison", new Profile(40.0, 0.16, 60, 0.30, "single", "none", false, false));
            map.put("damage.wither", new Profile(32.0, 0.18, 85, 0.40, "single", "none", false, false));

            // Explosions (sound-driven today)
            map.put("explosion.tnt", new Profile(28.0, 0.90, 800, 0.70, "shockwave", "log_distance", false, false));
            map.put("explosion.creeper", new Profile(26.0, 1.00, 900, 0.75, "shockwave", "log_distance", false, false));
            map.put("explosion.bed", new Profile(35.0, 0.80, 600, 0.55, "shockwave", "log_distance", false, false));

            // Combat
            map.put("combat.hit", new Profile(70.0, 0.25, 60, 0.10, "single", "none", false, false));
            map.put("combat.critical", new Profile(85.0, 0.40, 80, 0.10, "single", "none", false, false));
            map.put("combat.shield_block", new Profile(40.0, 0.35, 100, 0.20, "single", "none", false, false));

            // Mining
            map.put("mining.stone", new Profile(65.0, 0.25, 90, 0.22, "single", "none", false, false));
            map.put("mining.ore", new Profile(75.0, 0.35, 120, 0.22, "single", "none", false, false));
            map.put("mining.obsidian", new Profile(50.0, 0.60, 200, 0.25, "single", "none", false, false));
            map.put("mining.swing", new Profile(60.0, 0.28, 90, 0.18, "single", "none", false, false));

            // Movement
            map.put("movement.land", new Profile(50.0, 0.30, 140, 0.28, "single", "none", false, true));
            map.put("movement.footstep", new Profile(55.0, 0.22, 70, 0.42, "single", "none", false, false));

            // Boss
            map.put("boss.dragon_wing", new Profile(32.0, 0.70, 400, 0.50, "pulse_loop", "log_distance", false, false));
            map.put("boss.wither_spawn", new Profile(24.0, 1.00, 1500, 0.70, "fade_out", "log_distance", false, false));

            return new Store(new Global(), map);
        }

        public static String defaultsJson() {
            return "{\n" +
                    "  \"global\": {\n" +
                    "    \"maxIntensity\": 1.0,\n" +
                    "    \"minFrequency\": 20,\n" +
                    "    \"maxFrequency\": 90\n" +
                    "  },\n\n" +
                    "  \"damage\": {\n" +
                    "    \"generic\": {\n" +
                    "      \"frequency\": 55,\n" +
                    "      \"intensity\": 0.4,\n" +
                    "      \"duration\": 120,\n" +
                    "      \"pattern\": \"single\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.25,\n" +
                    "      \"scaleByDamage\": true\n" +
                    "    },\n" +
                    "    \"fall\": {\n" +
                    "      \"frequency\": 45,\n" +
                    "      \"intensity\": 0.3,\n" +
                    "      \"duration\": 180,\n" +
                    "      \"pattern\": \"single\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.30,\n" +
                    "      \"scaleByFallDistance\": true\n" +
                    "    },\n" +
                    "    \"death\": {\n" +
                    "      \"frequency\": 30,\n" +
                    "      \"intensity\": 0.8,\n" +
                    "      \"duration\": 1200,\n" +
                    "      \"pattern\": \"fade_out\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.55\n" +
                    "    },\n" +
                    "    \"fire\": {\n" +
                    "      \"frequency\": 34,\n" +
                    "      \"intensity\": 0.22,\n" +
                    "      \"duration\": 80,\n" +
                    "      \"pattern\": \"single\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.45\n" +
                    "    },\n" +
                    "    \"drowning\": {\n" +
                    "      \"frequency\": 30,\n" +
                    "      \"intensity\": 0.22,\n" +
                    "      \"duration\": 110,\n" +
                    "      \"pattern\": \"single\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.55\n" +
                    "    },\n" +
                    "    \"poison\": {\n" +
                    "      \"frequency\": 40,\n" +
                    "      \"intensity\": 0.16,\n" +
                    "      \"duration\": 60,\n" +
                    "      \"pattern\": \"single\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.30\n" +
                    "    },\n" +
                    "    \"wither\": {\n" +
                    "      \"frequency\": 32,\n" +
                    "      \"intensity\": 0.18,\n" +
                    "      \"duration\": 85,\n" +
                    "      \"pattern\": \"single\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.40\n" +
                    "    }\n" +
                    "  },\n\n" +
                    "  \"movement\": {\n" +
                    "    \"land\": {\n" +
                    "      \"frequency\": 50,\n" +
                    "      \"intensity\": 0.3,\n" +
                    "      \"duration\": 140,\n" +
                    "      \"pattern\": \"single\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.28,\n" +
                    "      \"scaleByFallDistance\": true\n" +
                    "    },\n" +
                    "    \"footstep\": {\n" +
                    "      \"frequency\": 55,\n" +
                    "      \"intensity\": 0.22,\n" +
                    "      \"duration\": 70,\n" +
                    "      \"pattern\": \"single\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.42\n" +
                    "    }\n" +
                    "  },\n\n" +
                    "  \"mining\": {\n" +
                    "    \"swing\": {\n" +
                    "      \"frequency\": 60,\n" +
                    "      \"intensity\": 0.28,\n" +
                    "      \"duration\": 90,\n" +
                    "      \"pattern\": \"single\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.18\n" +
                    "    }\n" +
                    "  }\n" +
                    "}\n";
        }

        private static double clamp(double v, double lo, double hi) {
            if (v < lo) return lo;
            if (v > hi) return hi;
            return v;
        }
    }

    public static final class Global {
        public double maxIntensity = 1.0;
        public double minFrequency = 20.0;
        public double maxFrequency = 90.0;

        public static Global fromJson(JsonObject obj) {
            if (obj == null) {
                return null;
            }
            Global g = new Global();
            if (obj.has("maxIntensity")) g.maxIntensity = obj.get("maxIntensity").getAsDouble();
            if (obj.has("minFrequency")) g.minFrequency = obj.get("minFrequency").getAsDouble();
            if (obj.has("maxFrequency")) g.maxFrequency = obj.get("maxFrequency").getAsDouble();
            return g;
        }
    }

    public static final class Profile {
        public double frequency;
        public double intensity;
        public int duration;
        public double noiseMix;
        public String pattern;
        public String falloff;
        public boolean scaleByDamage;
        public boolean scaleByFallDistance;

        public Profile(double frequency, double intensity, int duration, double noiseMix, String pattern, String falloff, boolean scaleByDamage, boolean scaleByFallDistance) {
            this.frequency = frequency;
            this.intensity = intensity;
            this.duration = duration;
            this.noiseMix = noiseMix;
            this.pattern = pattern;
            this.falloff = falloff;
            this.scaleByDamage = scaleByDamage;
            this.scaleByFallDistance = scaleByFallDistance;
        }

        public static Profile fromJson(JsonObject obj) {
            try {
                double f = obj.get("frequency").getAsDouble();
                double i = obj.get("intensity").getAsDouble();
                int d = obj.get("duration").getAsInt();
                String pattern = obj.has("pattern") ? obj.get("pattern").getAsString() : "single";
                String falloff = obj.has("falloff") ? obj.get("falloff").getAsString() : "none";
                double noise = obj.has("noiseMix") ? obj.get("noiseMix").getAsDouble() : 0.25;
                boolean sbd = obj.has("scaleByDamage") && obj.get("scaleByDamage").getAsBoolean();
                boolean sbf = obj.has("scaleByFallDistance") && obj.get("scaleByFallDistance").getAsBoolean();
                return new Profile(f, i, d, noise, pattern, falloff, sbd, sbf);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    public record Resolved(double frequencyHz, int durationMs, double intensity01, double noiseMix01, String pattern) {
    }
}
