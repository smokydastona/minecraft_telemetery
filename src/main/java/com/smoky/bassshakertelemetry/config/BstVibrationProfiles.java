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
        public final Encoding encoding;
        private final Map<String, Profile> profiles;

        private Store(Global global, Encoding encoding, Map<String, Profile> profiles) {
            this.global = global;
            this.encoding = encoding;
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

            double freq = clamp(p.baseFrequency, global.minFrequency, global.maxFrequency);
            int dur = Math.max(10, p.duration);
            double intensity = clamp(p.intensity, 0.0, global.maxIntensity);

            if (p.scaleByDamage || p.scaleByFallDistance) {
                intensity *= clamp(scale01, 0.0, 1.0);
            }

            if (p.falloff != null && !p.falloff.isBlank() && !"none".equalsIgnoreCase(p.falloff)) {
                intensity *= clamp(distanceScale01, 0.0, 1.0);
            }

            intensity = clamp(intensity, 0.0, global.maxIntensity);

            int periodMs = (p.pulsePeriodMs > 0) ? p.pulsePeriodMs : 160;
            int widthMs = (p.pulseWidthMs > 0) ? p.pulseWidthMs : 60;
            int pri = clampInt(p.priority, 0, 100);
            return new Resolved(freq, dur, intensity, clamp(p.noiseMix, 0.0, 1.0), p.pattern, periodMs, widthMs, p.directional, pri);
        }

        public static Store fromJson(JsonObject root) {
            Global global = Global.fromJson(root.getAsJsonObject("global"));
            if (global == null) {
                global = new Global();
            }

            Encoding encoding = Encoding.fromJson(root.getAsJsonObject("encoding"));
            if (encoding == null) {
                encoding = Encoding.defaults();
            }

            Map<String, Profile> map = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                if ("global".equals(e.getKey())) {
                    continue;
                }
                if ("encoding".equals(e.getKey())) {
                    continue;
                }
                flatten(map, e.getKey(), e.getValue());
            }

            // Always keep at least the defaults so missing keys don't break existing feel.
            for (Map.Entry<String, Profile> d : defaults().profiles.entrySet()) {
                map.putIfAbsent(d.getKey(), d.getValue());
            }

            return new Store(global, encoding, map);
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
            return (obj.has("frequency") || obj.has("baseFrequency")) && obj.has("intensity") && obj.has("duration");
        }

        public static Store defaults() {
            Map<String, Profile> map = new HashMap<>();

            // Damage
            map.put("damage.generic", new Profile(66.0, 0.60, 82, 0.14, "punch", "none", 160, 60, true, 10, true, false));
            map.put("damage.fall", new Profile(45.0, 0.30, 180, 0.30, "single", "none", 160, 60, true, 7, false, true));
            map.put("damage.death", new Profile(30.0, 0.80, 1200, 0.55, "fade_out", "none", 160, 60, false, 10, false, false));

            // Periodic dangers
            map.put("damage.fire", new Profile(34.0, 0.22, 80, 0.45, "single", "none", 160, 60, false, 3, false, false));
            map.put("damage.drowning", new Profile(30.0, 0.22, 110, 0.55, "single", "none", 160, 60, false, 3, false, false));
            map.put("damage.poison", new Profile(40.0, 0.16, 60, 0.30, "single", "none", 160, 60, false, 3, false, false));
            map.put("damage.wither", new Profile(32.0, 0.18, 85, 0.40, "single", "none", 160, 60, false, 3, false, false));

            // Explosions (sound-driven today)
            map.put("explosion.generic", new Profile(28.0, 0.85, 700, 0.70, "shockwave", "log_distance", 160, 60, true, 10, false, false));
            map.put("explosion.tnt", new Profile(28.0, 0.90, 800, 0.70, "shockwave", "log_distance", 160, 60, true, 10, false, false));
            map.put("explosion.creeper", new Profile(26.0, 1.00, 900, 0.75, "shockwave", "log_distance", 160, 60, true, 10, false, false));
            map.put("explosion.bed", new Profile(35.0, 0.80, 600, 0.55, "shockwave", "log_distance", 160, 60, true, 10, false, false));

            // World
            map.put("world.block_break", new Profile(52.0, 0.22, 65, 0.22, "single", "none", 160, 60, true, 4, false, false));

            // Combat
            map.put("combat.hit", new Profile(70.0, 0.25, 60, 0.10, "single", "none", 160, 60, false, 6, false, false));
            map.put("combat.critical", new Profile(85.0, 0.40, 80, 0.10, "single", "none", 160, 60, false, 7, false, false));
            map.put("combat.shield_block", new Profile(40.0, 0.35, 100, 0.20, "single", "none", 160, 60, false, 6, false, false));

            // Mining
            map.put("mining.stone", new Profile(65.0, 0.25, 90, 0.22, "single", "none", 160, 60, false, 4, false, false));
            map.put("mining.ore", new Profile(75.0, 0.35, 120, 0.22, "single", "none", 160, 60, false, 4, false, false));
            map.put("mining.obsidian", new Profile(50.0, 0.60, 200, 0.25, "single", "none", 160, 60, false, 4, false, false));
            map.put("mining.swing", new Profile(60.0, 0.28, 90, 0.18, "single", "none", 160, 60, false, 4, false, false));

            // Movement
            map.put("movement.land", new Profile(46.0, 0.22, 85, 0.18, "shockwave", "none", 160, 60, true, 4, false, true));
            map.put("movement.footstep", new Profile(58.0, 0.24, 45, 0.12, "shockwave", "none", 160, 60, false, 2, false, false));

            // Boss
            map.put("boss.dragon_wing", new Profile(32.0, 0.70, 400, 0.50, "pulse_loop", "log_distance", 170, 70, true, 9, false, false));
            map.put("boss.wither_spawn", new Profile(24.0, 1.00, 1500, 0.70, "fade_out", "log_distance", 160, 60, true, 10, false, false));

            return new Store(new Global(), Encoding.defaults(), map);
        }

        public static String defaultsJson() {
            return "{\n" +
                    "  \"global\": {\n" +
                    "    \"maxIntensity\": 1.0,\n" +
                    "    \"minFrequency\": 20,\n" +
                    "    \"maxFrequency\": 90\n" +
                    "  },\n\n" +
                    "  \"encoding\": {\n" +
                    "    \"center\": { \"frequencyBiasHz\": 0, \"timeOffsetMs\": 0, \"intensityMul\": 1.0 },\n" +
                    "    \"front\":  { \"frequencyBiasHz\": 6, \"timeOffsetMs\": 0, \"intensityMul\": 1.0 },\n" +
                    "    \"rear\":   { \"frequencyBiasHz\": -6, \"timeOffsetMs\": 0, \"intensityMul\": 1.0 },\n" +
                    "    \"left\":   { \"frequencyBiasHz\": 0, \"timeOffsetMs\": 1, \"intensityMul\": 1.0 },\n" +
                    "    \"right\":  { \"frequencyBiasHz\": 0, \"timeOffsetMs\": 2, \"intensityMul\": 1.0 }\n" +
                    "  },\n\n" +
                    "  \"damage\": {\n" +
                    "    \"generic\": {\n" +
                    "      \"frequency\": 62,\n" +
                    "      \"intensity\": 0.48,\n" +
                    "      \"duration\": 95,\n" +
                    "      \"pattern\": \"shockwave\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.18,\n" +
                    "      \"directional\": true,\n" +
                    "      \"priority\": 9,\n" +
                    "      \"scaleByDamage\": true\n" +
                    "    },\n" +
                    "    \"fall\": {\n" +
                    "      \"frequency\": 45,\n" +
                    "      \"intensity\": 0.3,\n" +
                    "      \"duration\": 180,\n" +
                    "      \"pattern\": \"single\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.30,\n" +
                    "      \"directional\": true,\n" +
                    "      \"priority\": 7,\n" +
                    "      \"scaleByFallDistance\": true\n" +
                    "    },\n" +
                    "    \"death\": {\n" +
                    "      \"frequency\": 30,\n" +
                    "      \"intensity\": 0.8,\n" +
                    "      \"duration\": 1200,\n" +
                    "      \"pattern\": \"fade_out\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.55,\n" +
                    "      \"priority\": 10\n" +
                    "    },\n" +
                    "    \"fire\": {\n" +
                    "      \"frequency\": 34,\n" +
                    "      \"intensity\": 0.22,\n" +
                    "      \"duration\": 80,\n" +
                    "      \"pattern\": \"single\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.45,\n" +
                    "      \"priority\": 3\n" +
                    "    },\n" +
                    "    \"drowning\": {\n" +
                    "      \"frequency\": 30,\n" +
                    "      \"intensity\": 0.22,\n" +
                    "      \"duration\": 110,\n" +
                    "      \"pattern\": \"single\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.55,\n" +
                    "      \"priority\": 3\n" +
                    "    },\n" +
                    "    \"poison\": {\n" +
                    "      \"frequency\": 40,\n" +
                    "      \"intensity\": 0.16,\n" +
                    "      \"duration\": 60,\n" +
                    "      \"pattern\": \"single\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.30,\n" +
                    "      \"priority\": 3\n" +
                    "    },\n" +
                    "    \"wither\": {\n" +
                    "      \"frequency\": 32,\n" +
                    "      \"intensity\": 0.18,\n" +
                    "      \"duration\": 85,\n" +
                    "      \"pattern\": \"single\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.40,\n" +
                    "      \"priority\": 3\n" +
                    "    }\n" +
                    "  },\n\n" +
                    "  \"movement\": {\n" +
                    "    \"land\": {\n" +
                    "      \"frequency\": 46,\n" +
                    "      \"intensity\": 0.22,\n" +
                    "      \"duration\": 85,\n" +
                    "      \"pattern\": \"shockwave\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.18,\n" +
                    "      \"directional\": true,\n" +
                    "      \"priority\": 4,\n" +
                    "      \"scaleByFallDistance\": true\n" +
                    "    },\n" +
                    "    \"footstep\": {\n" +
                    "      \"frequency\": 58,\n" +
                    "      \"intensity\": 0.24,\n" +
                    "      \"duration\": 45,\n" +
                    "      \"pattern\": \"shockwave\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.12,\n" +
                    "      \"priority\": 2\n" +
                    "    }\n" +
                    "  },\n\n" +
                    "  \"mining\": {\n" +
                    "    \"swing\": {\n" +
                    "      \"frequency\": 60,\n" +
                    "      \"intensity\": 0.28,\n" +
                    "      \"duration\": 90,\n" +
                    "      \"pattern\": \"single\",\n" +
                    "      \"falloff\": \"none\",\n" +
                    "      \"noiseMix\": 0.18,\n" +
                    "      \"priority\": 4\n" +
                    "    }\n" +
                    "  }\n" +
                    "}\n";
        }

        private static double clamp(double v, double lo, double hi) {
            if (v < lo) return lo;
            if (v > hi) return hi;
            return v;
        }

        private static int clampInt(int v, int lo, int hi) {
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

    /**
     * Root-level direction encoding rules for the "encoded mono surround" concept.
     *
     * Not all callers use this yet, but we parse and retain it so we can apply
     * frequency/time/pulse shaping consistently across the pipeline.
     */
    public static final class Encoding {
        public Band center = new Band();
        public Band front = new Band();
        public Band rear = new Band();
        public Band left = new Band();
        public Band right = new Band();

        public static Encoding defaults() {
            Encoding e = new Encoding();
            e.center = new Band(0.0, 0, 1.0);
            e.front = new Band(+6.0, 0, 1.0);
            e.rear = new Band(-6.0, 0, 1.0);
            e.left = new Band(0.0, 1, 1.0);
            e.right = new Band(0.0, 2, 1.0);
            return e;
        }

        public static Encoding fromJson(JsonObject obj) {
            if (obj == null) {
                return null;
            }

            Encoding e = defaults();
            if (obj.has("center")) e.center = Band.fromJson(obj.getAsJsonObject("center"), e.center);
            if (obj.has("front")) e.front = Band.fromJson(obj.getAsJsonObject("front"), e.front);
            if (obj.has("rear")) e.rear = Band.fromJson(obj.getAsJsonObject("rear"), e.rear);
            if (obj.has("left")) e.left = Band.fromJson(obj.getAsJsonObject("left"), e.left);
            if (obj.has("right")) e.right = Band.fromJson(obj.getAsJsonObject("right"), e.right);
            return e;
        }

        public static final class Band {
            /**
             * Frequency bias (Hz) applied on top of the profile baseFrequency.
             */
            public double frequencyBiasHz = 0.0;

            /**
             * Optional small time offset (ms) to help encode direction.
             */
            public int timeOffsetMs = 0;

            /**
             * Optional multiplier for final intensity after biasing.
             */
            public double intensityMul = 1.0;

            public Band() {
            }

            public Band(double frequencyBiasHz, int timeOffsetMs, double intensityMul) {
                this.frequencyBiasHz = frequencyBiasHz;
                this.timeOffsetMs = timeOffsetMs;
                this.intensityMul = intensityMul;
            }

            private static Band fromJson(JsonObject obj, Band fallback) {
                if (obj == null) {
                    return fallback;
                }

                Band b = new Band(fallback.frequencyBiasHz, fallback.timeOffsetMs, fallback.intensityMul);
                if (obj.has("frequencyBiasHz")) b.frequencyBiasHz = obj.get("frequencyBiasHz").getAsDouble();
                if (obj.has("timeOffsetMs")) b.timeOffsetMs = obj.get("timeOffsetMs").getAsInt();
                if (obj.has("intensityMul")) b.intensityMul = obj.get("intensityMul").getAsDouble();
                return b;
            }
        }
    }

    public static final class Profile {
        // Backward compatible: accept either "frequency" or "baseFrequency".
        public double frequency;
        public double baseFrequency;

        public double intensity;
        public int duration;
        public double noiseMix;
        public String pattern;
        public String falloff;

        public int pulsePeriodMs;
        public int pulseWidthMs;

        public boolean directional;
        public int priority;

        public boolean scaleByDamage;
        public boolean scaleByFallDistance;

        public Profile(double baseFrequency, double intensity, int duration, double noiseMix, String pattern, String falloff, int pulsePeriodMs, int pulseWidthMs, boolean directional, int priority, boolean scaleByDamage, boolean scaleByFallDistance) {
            this.frequency = baseFrequency;
            this.baseFrequency = baseFrequency;
            this.intensity = intensity;
            this.duration = duration;
            this.noiseMix = noiseMix;
            this.pattern = pattern;
            this.falloff = falloff;
            this.pulsePeriodMs = pulsePeriodMs;
            this.pulseWidthMs = pulseWidthMs;
            this.directional = directional;
            this.priority = priority;
            this.scaleByDamage = scaleByDamage;
            this.scaleByFallDistance = scaleByFallDistance;
        }

        public static Profile fromJson(JsonObject obj) {
            try {
                double f = obj.has("baseFrequency") ? obj.get("baseFrequency").getAsDouble() : obj.get("frequency").getAsDouble();
                double i = obj.get("intensity").getAsDouble();
                int d = obj.get("duration").getAsInt();
                String pattern = obj.has("pattern") ? obj.get("pattern").getAsString() : "single";
                String falloff = obj.has("falloff") ? obj.get("falloff").getAsString() : "none";
                double noise = obj.has("noiseMix") ? obj.get("noiseMix").getAsDouble() : 0.25;
                int period = obj.has("pulsePeriodMs") ? obj.get("pulsePeriodMs").getAsInt() : 160;
                int width = obj.has("pulseWidthMs") ? obj.get("pulseWidthMs").getAsInt() : 60;
                boolean directional = obj.has("directional") && obj.get("directional").getAsBoolean();
                int priority = obj.has("priority") ? obj.get("priority").getAsInt() : 5;
                boolean sbd = obj.has("scaleByDamage") && obj.get("scaleByDamage").getAsBoolean();
                boolean sbf = obj.has("scaleByFallDistance") && obj.get("scaleByFallDistance").getAsBoolean();
                return new Profile(f, i, d, noise, pattern, falloff, period, width, directional, priority, sbd, sbf);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    public record Resolved(double frequencyHz, int durationMs, double intensity01, double noiseMix01, String pattern, int pulsePeriodMs, int pulseWidthMs, boolean directional, int priority) {
    }
}
