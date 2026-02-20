package com.smoky.bassshakertelemetry.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraph;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 2: "haptic soundfont" instruments.
 *
 * <p>Stored separately so modders can author reusable tactile instruments and reference them
 * from vibration profiles via {@code "instrument": "impact_heavy"}.
 */
public final class BstHapticInstruments {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "bassshakertelemetry_haptic_instruments.json";

    private static volatile Store INSTANCE = Store.defaults();

    private BstHapticInstruments() {
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

    /**
     * Sets the current in-memory instrument store without writing to disk.
     *
     * <p>Used by the in-game graph editor for preview/test before saving.
     */
    public static synchronized void setInMemory(Store store) {
        if (store == null) {
            return;
        }
        INSTANCE = store;
    }

    /**
     * Pretty-printed JSON for a single instrument definition.
     */
    public static String instrumentToJsonString(Instrument instrument) {
        if (instrument == null) {
            return "{}";
        }
        JsonObject root = new JsonObject();
        JsonObject inst = new JsonObject();
        JsonObject o = new JsonObject();
        o.add("defaults", instrument.defaults.toJson());
        o.add("graph", Store.graphToJson(instrument.graph));
        inst.add(instrument.id, o);
        root.add("instruments", inst);
        return GSON.toJson(root);
    }

    /**
     * Pretty-printed JSON for the full store.
     */
    public static String storeToJsonString(Store store) {
        if (store == null) {
            return "{}";
        }
        return GSON.toJson(store.toJson());
    }

    public static synchronized void save(Store store) {
        if (store == null) {
            return;
        }
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, store.toJson().toString(), StandardCharsets.UTF_8);
            INSTANCE = store;
        } catch (Exception ignored) {
        }
    }

    private static void saveDefaults(Path p) {
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, Store.defaultsJson(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    public static final class Instrument {
        public final String id;
        public final DspGraph graph;
        public final Defaults defaults;

        public Instrument(String id, DspGraph graph, Defaults defaults) {
            this.id = id;
            this.graph = graph;
            this.defaults = (defaults == null) ? new Defaults() : defaults;
        }
    }

    public static final class Defaults {
        public double frequencyHz = 55.0;
        public int durationMs = 120;
        public double intensity01 = 0.6;
        public int priority = 5;
        public boolean directional = false;

        static Defaults fromJson(JsonObject obj) {
            Defaults d = new Defaults();
            if (obj == null) return d;
            if (obj.has("frequencyHz")) d.frequencyHz = obj.get("frequencyHz").getAsDouble();
            if (obj.has("durationMs")) d.durationMs = obj.get("durationMs").getAsInt();
            if (obj.has("intensity01")) d.intensity01 = obj.get("intensity01").getAsDouble();
            if (obj.has("priority")) d.priority = obj.get("priority").getAsInt();
            if (obj.has("directional")) d.directional = obj.get("directional").getAsBoolean();
            return d;
        }

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("frequencyHz", frequencyHz);
            o.addProperty("durationMs", durationMs);
            o.addProperty("intensity01", intensity01);
            o.addProperty("priority", priority);
            o.addProperty("directional", directional);
            return o;
        }
    }

    public static final class Store {
        private final Map<String, Instrument> instruments;

        private Store(Map<String, Instrument> instruments) {
            this.instruments = instruments;
        }

        public Instrument get(String id) {
            if (id == null) return null;
            return instruments.get(id);
        }

        public Map<String, Instrument> all() {
            return instruments;
        }

        public List<String> ids() {
            List<String> ids = new ArrayList<>(instruments.keySet());
            ids.sort(String::compareToIgnoreCase);
            return ids;
        }

        public Store withInstrument(Instrument instrument) {
            if (instrument == null || instrument.id == null || instrument.id.isBlank()) {
                return this;
            }
            Map<String, Instrument> copy = new HashMap<>(this.instruments);
            copy.put(instrument.id, instrument);
            return new Store(copy);
        }

        static Store fromJson(JsonObject root) {
            if (root == null) return null;
            JsonObject inst = root.getAsJsonObject("instruments");
            Map<String, Instrument> out = new HashMap<>();
            if (inst != null) {
                for (Map.Entry<String, JsonElement> e : inst.entrySet()) {
                    if (e == null || e.getKey() == null) continue;
                    String id = e.getKey().trim();
                    if (id.isEmpty()) continue;
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject obj = e.getValue().getAsJsonObject();
                    Defaults defs = Defaults.fromJson(obj.getAsJsonObject("defaults"));
                    DspGraph graph = graphFromJson(obj.getAsJsonObject("graph"));
                    if (graph == null) {
                        graph = defaultGraphForId(id);
                    }
                    out.put(id, new Instrument(id, graph, defs));
                }
            }

            // Ensure defaults exist.
            for (Map.Entry<String, Instrument> d : defaults().instruments.entrySet()) {
                out.putIfAbsent(d.getKey(), d.getValue());
            }

            return new Store(out);
        }

        JsonObject toJson() {
            JsonObject root = new JsonObject();
            JsonObject inst = new JsonObject();
            for (Instrument i : instruments.values()) {
                JsonObject o = new JsonObject();
                o.add("defaults", i.defaults.toJson());
                o.add("graph", graphToJson(i.graph));
                inst.add(i.id, o);
            }
            root.add("instruments", inst);
            return root;
        }

        static Store defaults() {
            Map<String, Instrument> map = new HashMap<>();
            map.put("impact_heavy", new Instrument("impact_heavy", defaultGraphForId("impact_heavy"), defaultsFor("impact_heavy")));
            map.put("rumble_low", new Instrument("rumble_low", defaultGraphForId("rumble_low"), defaultsFor("rumble_low")));
            map.put("heartbeat_warden", new Instrument("heartbeat_warden", defaultGraphForId("heartbeat_warden"), defaultsFor("heartbeat_warden")));
            map.put("wind_elytra", new Instrument("wind_elytra", defaultGraphForId("wind_elytra"), defaultsFor("wind_elytra")));
            map.put("magic_pulse", new Instrument("magic_pulse", defaultGraphForId("magic_pulse"), defaultsFor("magic_pulse")));
            return new Store(map);
        }

        static String defaultsJson() {
            return defaults().toJson().toString();
        }

        private static Defaults defaultsFor(String id) {
            Defaults d = new Defaults();
            switch (id) {
                case "impact_heavy" -> {
                    d.frequencyHz = 55.0;
                    d.durationMs = 120;
                    d.intensity01 = 0.85;
                    d.priority = 8;
                    d.directional = true;
                }
                case "rumble_low" -> {
                    d.frequencyHz = 28.0;
                    d.durationMs = 450;
                    d.intensity01 = 0.45;
                    d.priority = 3;
                }
                case "heartbeat_warden" -> {
                    d.frequencyHz = 30.0;
                    d.durationMs = 170;
                    d.intensity01 = 0.35;
                    d.priority = 2;
                    d.directional = true;
                }
                case "wind_elytra" -> {
                    d.frequencyHz = 38.0;
                    d.durationMs = 260;
                    d.intensity01 = 0.35;
                    d.priority = 2;
                    d.directional = true;
                }
                case "magic_pulse" -> {
                    d.frequencyHz = 70.0;
                    d.durationMs = 220;
                    d.intensity01 = 0.55;
                    d.priority = 4;
                }
            }
            return d;
        }

        private static DspGraph defaultGraphForId(String id) {
            // A few small example graphs. These keep the node set within the Phase 2 list.
            List<DspGraph.NodeDef> nodes = new ArrayList<>();

            switch (id) {
                case "impact_heavy" -> {
                    nodes.add(node("osc", "harmonic", Map.of("fm", "rand"), Map.of("harmonics", 4, "rolloff", 0.55, "fmDepthHz", 4.0)));
                    nodes.add(node("rand", "randomizer", Map.of(), Map.of("rateHz", 18.0, "depth", 1.0)));
                    nodes.add(node("env", "envelope", Map.of("in", "osc"), Map.of("attackMs", 4, "decayMs", 40, "sustainLevel01", 0.20, "releaseMs", 70)));
                    nodes.add(node("noise", "noise", Map.of(), Map.of("color", "pink", "amp", 0.35)));
                    nodes.add(node("mix", "mixer", Map.of("a", "env", "b", "noise"), Map.of("mode", "mix", "gainA", 1.0, "gainB", 1.0)));
                    nodes.add(node("lpf", "filter", Map.of("in", "mix"), Map.of("mode", "lpf", "cutoffHz", 70.0, "q", 0.8)));
                    nodes.add(node("lim", "compressor", Map.of("in", "lpf"), Map.of("threshold", 0.75, "ratio", 6.0, "attackMs", 6, "releaseMs", 90)));
                    nodes.add(node("dir", "direction", Map.of("in", "lim"), Map.of("useProfileEncoding", 1, "band", "auto", "mix", 1.0)));
                    return new DspGraph(nodes, "dir");
                }
                case "rumble_low" -> {
                    nodes.add(node("osc", "harmonic", Map.of(), Map.of("harmonics", 2, "rolloff", 0.35)));
                    nodes.add(node("env", "envelope", Map.of("in", "osc"), Map.of("attackMs", 25, "decayMs", 80, "sustainLevel01", 0.75, "releaseMs", 120)));
                    nodes.add(node("lpf", "filter", Map.of("in", "env"), Map.of("mode", "lpf", "cutoffHz", 55.0, "q", 0.9)));
                    return new DspGraph(nodes, "lpf");
                }
                case "heartbeat_warden" -> {
                    nodes.add(node("osc", "harmonic", Map.of(), Map.of("harmonics", 1, "rolloff", 0.5)));
                    nodes.add(node("env", "envelope", Map.of("in", "osc"), Map.of("attackMs", 6, "decayMs", 35, "sustainLevel01", 0.15, "releaseMs", 55)));
                    nodes.add(node("notch", "filter", Map.of("in", "env"), Map.of("mode", "notch", "cutoffHz", 90.0, "q", 0.9)));
                    nodes.add(node("dir", "direction", Map.of("in", "notch"), Map.of("useProfileEncoding", 1, "band", "auto", "mix", 1.0)));
                    return new DspGraph(nodes, "dir");
                }
                case "wind_elytra" -> {
                    nodes.add(node("noise", "noise", Map.of(), Map.of("color", "brown", "amp", 0.9)));
                    nodes.add(node("hpf", "filter", Map.of("in", "noise"), Map.of("mode", "hpf", "cutoffHz", 18.0, "q", 0.8)));
                    nodes.add(node("env", "envelope", Map.of("in", "hpf"), Map.of("attackMs", 30, "decayMs", 50, "sustainLevel01", 0.8, "releaseMs", 90)));
                    nodes.add(node("dir", "direction", Map.of("in", "env"), Map.of("useProfileEncoding", 1, "band", "auto", "mix", 1.0)));
                    return new DspGraph(nodes, "dir");
                }
                case "magic_pulse" -> {
                    nodes.add(node("osc", "harmonic", Map.of("fm", "rand"), Map.of("harmonics", 3, "rolloff", 0.4, "fmDepthHz", 10.0)));
                    nodes.add(node("rand", "randomizer", Map.of(), Map.of("rateHz", 12.0, "depth", 1.0)));
                    nodes.add(node("env", "envelope", Map.of("in", "osc"), Map.of("attackMs", 10, "decayMs", 55, "sustainLevel01", 0.25, "releaseMs", 80)));
                    nodes.add(node("bpf", "filter", Map.of("in", "env"), Map.of("mode", "bpf", "cutoffHz", 75.0, "q", 1.2)));
                    return new DspGraph(nodes, "bpf");
                }
                default -> {
                    nodes.add(node("osc", "harmonic", Map.of(), Map.of("harmonics", 2, "rolloff", 0.5)));
                    nodes.add(node("env", "envelope", Map.of("in", "osc"), Map.of("attackMs", 6, "decayMs", 40, "sustainLevel01", 0.3, "releaseMs", 70)));
                    return new DspGraph(nodes, "env");
                }
            }
        }

        private static DspGraph.NodeDef node(String id, String type, Map<String, String> inputs, Map<String, Object> params) {
            return new DspGraph.NodeDef(id, type, inputs, params);
        }

        private static DspGraph graphFromJson(JsonObject obj) {
            if (obj == null) return null;
            JsonArray nodesArr = obj.getAsJsonArray("nodes");
            String output = obj.has("output") ? obj.get("output").getAsString() : "";
            if (nodesArr == null) return null;

            List<DspGraph.NodeDef> nodes = new ArrayList<>();
            for (JsonElement el : nodesArr) {
                if (el == null || !el.isJsonObject()) continue;
                JsonObject n = el.getAsJsonObject();
                String id = n.has("id") ? n.get("id").getAsString() : "";
                String type = n.has("type") ? n.get("type").getAsString() : "";
                if (id == null || id.isBlank()) continue;

                Map<String, String> inputs = new HashMap<>();
                JsonObject inObj = n.getAsJsonObject("inputs");
                if (inObj != null) {
                    for (Map.Entry<String, JsonElement> ie : inObj.entrySet()) {
                        if (ie.getKey() == null) continue;
                        inputs.put(ie.getKey(), (ie.getValue() == null) ? "" : ie.getValue().getAsString());
                    }
                }

                Map<String, Object> params = new HashMap<>();
                JsonObject pObj = n.getAsJsonObject("params");
                if (pObj != null) {
                    for (Map.Entry<String, JsonElement> pe : pObj.entrySet()) {
                        if (pe.getKey() == null) continue;
                        JsonElement pv = pe.getValue();
                        if (pv == null || pv.isJsonNull()) continue;
                        if (pv.isJsonPrimitive()) {
                            var prim = pv.getAsJsonPrimitive();
                            if (prim.isBoolean()) params.put(pe.getKey(), prim.getAsBoolean());
                            else if (prim.isNumber()) params.put(pe.getKey(), prim.getAsDouble());
                            else params.put(pe.getKey(), prim.getAsString());
                        } else {
                            // Keep as string for now.
                            params.put(pe.getKey(), pv.toString());
                        }
                    }
                }

                nodes.add(new DspGraph.NodeDef(id, type, inputs, params));
            }

            return new DspGraph(nodes, output);
        }

        private static JsonObject graphToJson(DspGraph g) {
            JsonObject o = new JsonObject();
            JsonArray nodes = new JsonArray();
            if (g != null) {
                for (DspGraph.NodeDef n : g.nodes()) {
                    JsonObject node = new JsonObject();
                    node.addProperty("id", n.id);
                    node.addProperty("type", n.type);
                    JsonObject in = new JsonObject();
                    for (Map.Entry<String, String> e : n.inputs.entrySet()) {
                        in.addProperty(e.getKey(), e.getValue());
                    }
                    node.add("inputs", in);

                    JsonObject params = new JsonObject();
                    for (Map.Entry<String, Object> e : n.params.entrySet()) {
                        Object v = e.getValue();
                        if (v instanceof Number num) {
                            params.addProperty(e.getKey(), num.doubleValue());
                        } else if (v instanceof Boolean b) {
                            params.addProperty(e.getKey(), b);
                        } else {
                            params.addProperty(e.getKey(), String.valueOf(v));
                        }
                    }
                    node.add("params", params);
                    nodes.add(node);
                }
                o.addProperty("output", g.outputNodeId());
            }
            o.add("nodes", nodes);
            return o;
        }
    }
}
