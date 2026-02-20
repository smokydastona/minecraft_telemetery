package com.smoky.bassshakertelemetry.client.ui;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.audio.dsp.DspContext;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraph;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraphInstance;
import com.smoky.bassshakertelemetry.audio.dsp.DspNodeFactory;
import com.smoky.bassshakertelemetry.config.BstHapticInstruments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Phase 2: Minimal in-game node-graph editor for haptic instruments.
 *
 * <p>Scope intentionally small: drag nodes, connect ports, set output, save/reload, export JSON, and test-play.
 */
public final class HapticInstrumentEditorScreen extends Screen {
    private static final int MARGIN = 20;
    private static final int ROW_H = 20;

    private static final int NODE_W = 132;
    private static final int NODE_HEADER_H = 14;
    private static final int PORT_SPACING = 10;
    private static final int PORT_R = 3;

    private static final DspNodeFactory DSP_FACTORY = new DspNodeFactory();

    private final Screen parent;

    private BstHapticInstruments.Store workingStore;
    private List<String> instrumentIds = List.of();
    private String selectedInstrumentId = "";

    private CycleButton<String> instrumentCycle;
    private Button reloadButton;
    private CycleButton<String> addTypeCycle;
    private Button addNodeButton;
    private Button setOutputButton;
    private Button saveButton;
    private Button copyJsonButton;
    private Button testButton;

    private Button doneButton;

    private final List<EditableNode> nodes = new ArrayList<>();
    private String outputNodeId = "";

    private String selectedNodeId = "";
    private String connectSourceNodeId = "";
    private String draggingNodeId = "";
    private int dragOffX;
    private int dragOffY;

    private int canvasX0;
    private int canvasY0;
    private int canvasX1;
    private int canvasY1;

    private static final List<String> NODE_TYPES = List.of(
        "harmonic",
        "noise",
        "envelope",
        "filter",
        "randomizer",
        "compressor",
        "direction",
        "mixer",
        "constant"
    );

    public HapticInstrumentEditorScreen(Screen parent) {
        super(Component.translatable("bassshakertelemetry.instrument_editor.title"));
        this.parent = parent;
    }

    @Override
    @SuppressWarnings("null")
    protected void init() {
        super.init();

        var font = Objects.requireNonNull(this.font, "font");
        int centerX = this.width / 2;
        int contentW = this.width - (MARGIN * 2);
        int x0 = MARGIN;
        int y = 18;

        this.addRenderableWidget(new StringWidget(
            centerX - 140,
            y,
            280,
            20,
            Objects.requireNonNull(Component.translatable("bassshakertelemetry.instrument_editor.title")),
            font
        ));

        y = 44;

        this.workingStore = BstHapticInstruments.get();
        this.instrumentIds = (workingStore == null) ? List.of() : workingStore.ids();
        if (instrumentIds.isEmpty()) {
            this.instrumentIds = List.of("impact_heavy");
        }

        if (selectedInstrumentId == null || selectedInstrumentId.isBlank() || !instrumentIds.contains(selectedInstrumentId)) {
            selectedInstrumentId = instrumentIds.get(0);
        }

        int reloadW = 90;
        int cycleW = Math.max(160, contentW - reloadW - 10);

        instrumentCycle = CycleButton.builder((String s) -> Component.literal(s))
            .withValues(instrumentIds)
            .withInitialValue(selectedInstrumentId)
            .create(x0, y, cycleW, ROW_H, Component.translatable("bassshakertelemetry.instrument_editor.instrument"),
                (btn, val) -> selectInstrument(val));
        this.addRenderableWidget(instrumentCycle);

        reloadButton = Button.builder(Component.translatable("bassshakertelemetry.instrument_editor.reload"), b -> reloadFromDisk())
            .bounds(x0 + cycleW + 10, y, reloadW, ROW_H)
            .build();
        this.addRenderableWidget(reloadButton);

        y += 24;

        int addW = 90;
        int typeW = Math.max(160, contentW - addW - 10);
        addTypeCycle = CycleButton.builder((String s) -> Component.literal(s))
            .withValues(NODE_TYPES)
            .withInitialValue(NODE_TYPES.get(0))
            .create(x0, y, typeW, ROW_H, Component.translatable("bassshakertelemetry.instrument_editor.add_type"),
                (btn, val) -> {
                });
        this.addRenderableWidget(addTypeCycle);

        addNodeButton = Button.builder(Component.translatable("bassshakertelemetry.instrument_editor.add"), b -> addNode())
            .bounds(x0 + typeW + 10, y, addW, ROW_H)
            .build();
        this.addRenderableWidget(addNodeButton);

        y += 24;

        int smallW = (contentW - 30) / 4;
        setOutputButton = Button.builder(Component.translatable("bassshakertelemetry.instrument_editor.set_output"), b -> setOutputToSelected())
            .bounds(x0, y, smallW, ROW_H)
            .build();
        this.addRenderableWidget(setOutputButton);

        saveButton = Button.builder(Component.translatable("bassshakertelemetry.instrument_editor.save"), b -> saveToDisk())
            .bounds(x0 + smallW + 10, y, smallW, ROW_H)
            .build();
        this.addRenderableWidget(saveButton);

        copyJsonButton = Button.builder(Component.translatable("bassshakertelemetry.instrument_editor.copy_json"), b -> copySelectedInstrumentJson())
            .bounds(x0 + (smallW + 10) * 2, y, smallW, ROW_H)
            .build();
        this.addRenderableWidget(copyJsonButton);

        testButton = Button.builder(Component.translatable("bassshakertelemetry.instrument_editor.test"), b -> testPlay())
            .bounds(x0 + (smallW + 10) * 3, y, smallW, ROW_H)
            .build();
        this.addRenderableWidget(testButton);

        int doneW = Math.min(200, contentW);
        doneButton = Button.builder(Component.translatable("bassshakertelemetry.config.done"), b -> onDone())
            .bounds(centerX - (doneW / 2), this.height - 28, doneW, 20)
            .build();
        this.addRenderableWidget(doneButton);

        // Canvas bounds (below controls, above Done)
        canvasX0 = x0;
        canvasY0 = y + 30;
        canvasX1 = x0 + contentW;
        canvasY1 = this.height - 40;

        loadInstrumentIntoEditor(selectedInstrumentId);
    }

    private void selectInstrument(String id) {
        if (id == null || id.isBlank()) return;
        selectedInstrumentId = id;
        loadInstrumentIntoEditor(id);
    }

    private void loadInstrumentIntoEditor(String id) {
        nodes.clear();
        selectedNodeId = "";
        connectSourceNodeId = "";
        draggingNodeId = "";

        BstHapticInstruments.Store store = (workingStore != null) ? workingStore : BstHapticInstruments.get();
        BstHapticInstruments.Instrument inst = (store == null) ? null : store.get(id);
        DspGraph g = (inst == null) ? null : inst.graph;
        if (g == null) {
            outputNodeId = "";
            return;
        }

        outputNodeId = g.outputNodeId();

        // Seed basic layout.
        int colW = NODE_W + 30;
        int rowH = 54;
        int i = 0;
        for (DspGraph.NodeDef def : g.nodes()) {
            int col = i % 3;
            int row = i / 3;
            int x = clampInt(canvasX0 + 10 + (col * colW), canvasX0 + 4, canvasX1 - NODE_W - 4);
            int y = clampInt(canvasY0 + 10 + (row * rowH), canvasY0 + 4, canvasY1 - 30);
            nodes.add(EditableNode.from(def, x, y));
            i++;
        }

        if (outputNodeId == null || outputNodeId.isBlank()) {
            if (!nodes.isEmpty()) outputNodeId = nodes.get(nodes.size() - 1).id;
        }
    }

    private void reloadFromDisk() {
        BstHapticInstruments.load();
        this.workingStore = BstHapticInstruments.get();
        this.instrumentIds = (workingStore == null) ? List.of() : workingStore.ids();
        if (instrumentCycle != null) {
            // Re-init to refresh cycle options safely.
            this.clearWidgets();
            this.init();
        }
    }

    private void addNode() {
        String type = (addTypeCycle == null) ? "constant" : String.valueOf(addTypeCycle.getValue());
        String id = newUniqueId(type);
        Map<String, String> inputs = defaultInputsForType(type);
        Map<String, Object> params = defaultParamsForType(type);

        int x = clampInt((canvasX0 + canvasX1) / 2 - (NODE_W / 2), canvasX0 + 4, canvasX1 - NODE_W - 4);
        int y = clampInt((canvasY0 + canvasY1) / 2 - 20, canvasY0 + 4, canvasY1 - 30);
        nodes.add(new EditableNode(id, type, inputs, params, x, y));

        selectedNodeId = id;
        if (outputNodeId == null || outputNodeId.isBlank()) {
            outputNodeId = id;
        }
    }

    private void setOutputToSelected() {
        if (selectedNodeId == null || selectedNodeId.isBlank()) return;
        if (findNodeById(selectedNodeId) == null) return;
        outputNodeId = selectedNodeId;
    }

    private void saveToDisk() {
        applyEditsToWorkingStore(true);
    }

    private void testPlay() {
        applyEditsToWorkingStore(false);

        BstHapticInstruments.Instrument inst = (workingStore == null) ? null : workingStore.get(selectedInstrumentId);
        if (inst == null) return;

        AudioOutputEngine.get().triggerInstrumentImpulse(
            selectedInstrumentId,
            inst.defaults.frequencyHz,
            inst.defaults.durationMs,
            inst.defaults.intensity01,
            "single",
            160,
            60,
            inst.defaults.priority,
            0,
            "ui.instrument_test"
        );
    }

    private void copySelectedInstrumentJson() {
        if (workingStore == null) return;
        BstHapticInstruments.Instrument inst = workingStore.get(selectedInstrumentId);
        if (inst == null) return;

        String json = BstHapticInstruments.instrumentToJsonString(inst);
        Minecraft mc = Objects.requireNonNull(this.minecraft, "minecraft");
        mc.keyboardHandler.setClipboard(Objects.requireNonNull(json, "json"));
    }

    private void applyEditsToWorkingStore(boolean persistToDisk) {
        if (workingStore == null) {
            workingStore = BstHapticInstruments.get();
        }
        if (workingStore == null) return;

        BstHapticInstruments.Instrument before = workingStore.get(selectedInstrumentId);
        if (before == null) return;

        DspGraph updated = toGraph();
        BstHapticInstruments.Instrument after = new BstHapticInstruments.Instrument(before.id, updated, before.defaults);
        workingStore = workingStore.withInstrument(after);

        BstHapticInstruments.setInMemory(workingStore);
        if (persistToDisk) {
            BstHapticInstruments.save(workingStore);
        }
    }

    private DspGraph toGraph() {
        List<DspGraph.NodeDef> defs = new ArrayList<>();
        for (EditableNode n : nodes) {
            Map<String, String> inputs = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : n.inputs.entrySet()) {
                if (e.getKey() == null) continue;
                String v = (e.getValue() == null) ? "" : e.getValue().trim();
                if (v.isEmpty()) continue;
                inputs.put(e.getKey(), v);
            }
            defs.add(new DspGraph.NodeDef(n.id, n.type, inputs, new LinkedHashMap<>(n.params)));
        }

        String out = (outputNodeId == null) ? "" : outputNodeId.trim();
        if (!out.isEmpty() && findNodeById(out) == null) {
            out = nodes.isEmpty() ? "" : nodes.get(nodes.size() - 1).id;
        }
        return new DspGraph(defs, out);
    }

    private EditableNode findNodeById(String id) {
        if (id == null) return null;
        for (EditableNode n : nodes) {
            if (id.equals(n.id)) return n;
        }
        return null;
    }

    private String newUniqueId(String type) {
        String base = (type == null) ? "node" : type.trim().toLowerCase(Locale.ROOT);
        if (base.isEmpty()) base = "node";
        base = switch (base) {
            case "harmonic" -> "osc";
            case "envelope" -> "env";
            case "filter" -> "f";
            case "randomizer" -> "rand";
            case "compressor" -> "comp";
            case "mixer" -> "mix";
            default -> base;
        };

        int i = 1;
        while (true) {
            String id = base + i;
            if (findNodeById(id) == null) return id;
            i++;
            if (i > 9999) return base + System.nanoTime();
        }
    }

    private static Map<String, String> defaultInputsForType(String type) {
        String t = (type == null) ? "" : type.trim().toLowerCase(Locale.ROOT);
        Map<String, String> m = new LinkedHashMap<>();
        switch (t) {
            case "envelope", "filter", "compressor", "direction" -> m.put("in", "");
            case "harmonic" -> m.put("fm", "");
            case "mixer" -> {
                m.put("a", "");
                m.put("b", "");
            }
        }
        return m;
    }

    private static Map<String, Object> defaultParamsForType(String type) {
        String t = (type == null) ? "" : type.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> p = new HashMap<>();
        switch (t) {
            case "harmonic" -> {
                p.put("harmonics", 2.0);
                p.put("rolloff", 0.5);
            }
            case "noise" -> {
                p.put("color", "pink");
                p.put("amp", 0.35);
            }
            case "envelope" -> {
                p.put("attackMs", 6.0);
                p.put("decayMs", 40.0);
                p.put("sustainLevel01", 0.3);
                p.put("releaseMs", 70.0);
            }
            case "filter" -> {
                p.put("mode", "lpf");
                p.put("cutoffHz", 60.0);
                p.put("q", 0.9);
            }
            case "randomizer" -> {
                p.put("rateHz", 12.0);
                p.put("depth", 1.0);
            }
            case "compressor" -> {
                p.put("threshold", 0.75);
                p.put("ratio", 6.0);
                p.put("attackMs", 6.0);
                p.put("releaseMs", 90.0);
            }
            case "direction" -> {
                p.put("useProfileEncoding", 1.0);
                p.put("band", "center");
                p.put("timeOffsetMs", 0.0);
                p.put("intensityMul", 1.0);
                p.put("mix", 1.0);
            }
            case "mixer" -> {
                p.put("mode", "mix");
                p.put("gainA", 1.0);
                p.put("gainB", 1.0);
            }
            case "constant" -> p.put("value", 0.0);
        }
        return p;
    }

    @Override
    @SuppressWarnings("null")
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        drawCanvas(guiGraphics);
        drawConnections(guiGraphics);
        drawNodes(guiGraphics);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        drawStatus(guiGraphics);
    }

    private void drawCanvas(GuiGraphics g) {
        // Panel background + border
        g.fill(canvasX0, canvasY0, canvasX1, canvasY1, 0xAA000000);
        g.fill(canvasX0, canvasY0, canvasX1, canvasY0 + 1, 0xFF606060);
        g.fill(canvasX0, canvasY1 - 1, canvasX1, canvasY1, 0xFF606060);
        g.fill(canvasX0, canvasY0, canvasX0 + 1, canvasY1, 0xFF606060);
        g.fill(canvasX1 - 1, canvasY0, canvasX1, canvasY1, 0xFF606060);
    }

    private void drawConnections(GuiGraphics g) {
        for (EditableNode n : nodes) {
            List<String> ins = inputNames(n);
            for (int i = 0; i < ins.size(); i++) {
                String inputName = ins.get(i);
                String from = n.inputs.getOrDefault(inputName, "");
                if (from == null || from.isBlank()) continue;
                EditableNode src = findNodeById(from);
                if (src == null) continue;

                int srcOutX = src.x + NODE_W - 6;
                int srcOutY = src.y + (NODE_HEADER_H / 2);

                int inX = n.x + 2;
                int inY = n.y + NODE_HEADER_H + (i * PORT_SPACING) + 3;

                int color = from.equals(connectSourceNodeId) ? 0xFF00FFAA : 0xFFB0B0B0;

                // L-shaped wire: src -> mid -> dst
                int midX = (srcOutX + inX) / 2;
                fillLine(g, srcOutX, srcOutY, midX, srcOutY, color);
                fillLine(g, midX, srcOutY, midX, inY, color);
                fillLine(g, midX, inY, inX, inY, color);
            }
        }
    }

    private void drawNodes(GuiGraphics g) {
        var font = Objects.requireNonNull(this.font, "font");

        Map<String, Double> debugValues = computeDebugPreviewValues();

        for (EditableNode n : nodes) {
            int h = nodeHeight(n);
            boolean isSelected = n.id.equals(selectedNodeId);
            boolean isOutput = n.id.equals(outputNodeId);

            int bg = isSelected ? 0xFF2E2E2E : 0xFF1D1D1D;
            int border = isOutput ? 0xFFFFD166 : 0xFF606060;
            g.fill(n.x, n.y, n.x + NODE_W, n.y + h, bg);
            g.fill(n.x, n.y, n.x + NODE_W, n.y + 1, border);
            g.fill(n.x, n.y + h - 1, n.x + NODE_W, n.y + h, border);
            g.fill(n.x, n.y, n.x + 1, n.y + h, border);
            g.fill(n.x + NODE_W - 1, n.y, n.x + NODE_W, n.y + h, border);

            String title = n.id + " : " + n.type;
            g.drawString(font, title, n.x + 6, n.y + 3, 0xFFFFFF);

            Double v = debugValues.get(n.id);
            if (v != null) {
                String sv = Objects.requireNonNull(String.format(Locale.ROOT, "%.3f", v), "sv");
                int w = font.width(Objects.requireNonNull(sv, "sv"));
                g.drawString(font, Objects.requireNonNull(sv, "sv"), n.x + NODE_W - 10 - w, n.y + 3, 0xB0B0B0);
            }

            // Output port
            int outX = n.x + NODE_W - 6;
            int outY = n.y + (NODE_HEADER_H / 2);
            int outColor = n.id.equals(connectSourceNodeId) ? 0xFF00FFAA : 0xFFCCCCCC;
            g.fill(outX - PORT_R, outY - PORT_R, outX + PORT_R + 1, outY + PORT_R + 1, outColor);

            List<String> ins = inputNames(n);
            for (int i = 0; i < ins.size(); i++) {
                String inputName = ins.get(i);
                int inX = n.x + 2;
                int inY = n.y + NODE_HEADER_H + (i * PORT_SPACING) + 3;

                boolean connected = !String.valueOf(n.inputs.getOrDefault(inputName, "")).isBlank();
                int inColor = connected ? 0xFF9AD0EC : 0xFFCCCCCC;
                g.fill(inX - PORT_R, inY - PORT_R, inX + PORT_R + 1, inY + PORT_R + 1, inColor);

                g.drawString(font, Objects.requireNonNull(inputName, "inputName"), n.x + 10, inY - 4, 0xE0E0E0);
            }
        }
    }

    private Map<String, Double> computeDebugPreviewValues() {
        if (nodes.isEmpty()) {
            return Map.of();
        }

        BstHapticInstruments.Store store = (workingStore != null) ? workingStore : BstHapticInstruments.get();
        BstHapticInstruments.Instrument inst = (store == null) ? null : store.get(selectedInstrumentId);
        double f = (inst == null) ? 55.0 : inst.defaults.frequencyHz;
        int ms = (inst == null) ? 120 : inst.defaults.durationMs;

        int samples = (int) Math.round((Math.max(10, ms) / 1000.0) * DspContext.SAMPLE_RATE);
        samples = Math.max(1, samples);

        try {
            DspGraph graph = toGraph();
            if (graph == null) {
                return Map.of();
            }

            DspGraphInstance gi = graph.instantiate(DSP_FACTORY);
            DspContext ctx = new DspContext(1337L, f, f, samples);
            ctx.sampleIndex = Math.max(0, samples / 2);

            Map<String, Double> out = new HashMap<>();
            for (EditableNode n : nodes) {
                if (n == null || n.id == null || n.id.isBlank()) continue;
                out.put(n.id, gi.evalById(ctx, n.id));
            }
            return out;
        } catch (Throwable ignored) {
            return Map.of();
        }
    }

    private void drawStatus(GuiGraphics g) {
        var font = Objects.requireNonNull(this.font, "font");
        int x = MARGIN;
        int y = canvasY1 + 2;

        String sel = (selectedNodeId == null || selectedNodeId.isBlank()) ? "-" : selectedNodeId;
        String src = (connectSourceNodeId == null || connectSourceNodeId.isBlank()) ? "-" : connectSourceNodeId;
        String out = (outputNodeId == null || outputNodeId.isBlank()) ? "-" : outputNodeId;

        g.drawString(font, Objects.requireNonNull(Component.translatable("bassshakertelemetry.instrument_editor.status", sel, src, out)), x, y, 0xE0E0E0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (!inCanvas(mouseX, mouseY)) {
            return false;
        }

        PortHit port = findPortHit(mouseX, mouseY);
        if (port != null) {
            if (port.isOutput) {
                connectSourceNodeId = port.nodeId;
                selectedNodeId = port.nodeId;
                return true;
            }
            if (connectSourceNodeId != null && !connectSourceNodeId.isBlank()) {
                EditableNode n = findNodeById(port.nodeId);
                if (n != null && port.inputName != null && !port.inputName.isBlank()) {
                    n.inputs.put(port.inputName, connectSourceNodeId);
                }
                return true;
            }
        }

        EditableNode node = findNodeBodyAt(mouseX, mouseY);
        if (node != null) {
            selectedNodeId = node.id;
            draggingNodeId = node.id;
            dragOffX = (int) mouseX - node.x;
            dragOffY = (int) mouseY - node.y;
            return true;
        }

        selectedNodeId = "";
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (draggingNodeId == null || draggingNodeId.isBlank()) {
            return false;
        }
        EditableNode n = findNodeById(draggingNodeId);
        if (n == null) {
            draggingNodeId = "";
            return false;
        }

        int nx = clampInt((int) mouseX - dragOffX, canvasX0 + 4, canvasX1 - NODE_W - 4);
        int ny = clampInt((int) mouseY - dragOffY, canvasY0 + 4, canvasY1 - nodeHeight(n) - 4);
        n.x = nx;
        n.y = ny;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingNodeId = "";
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private EditableNode findNodeBodyAt(double mouseX, double mouseY) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            EditableNode n = nodes.get(i);
            int h = nodeHeight(n);
            if (mouseX >= n.x && mouseX <= n.x + NODE_W && mouseY >= n.y && mouseY <= n.y + h) {
                // Ignore port hits here.
                if (findPortHit(mouseX, mouseY) != null) return null;
                return n;
            }
        }
        return null;
    }

    private PortHit findPortHit(double mouseX, double mouseY) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            EditableNode n = nodes.get(i);

            // Output port
            int outX = n.x + NODE_W - 6;
            int outY = n.y + (NODE_HEADER_H / 2);
            if (dist2(mouseX, mouseY, outX, outY) <= (PORT_R + 2) * (PORT_R + 2)) {
                return PortHit.output(n.id);
            }

            // Input ports
            List<String> ins = inputNames(n);
            for (int pi = 0; pi < ins.size(); pi++) {
                String inputName = ins.get(pi);
                int inX = n.x + 2;
                int inY = n.y + NODE_HEADER_H + (pi * PORT_SPACING) + 3;
                if (dist2(mouseX, mouseY, inX, inY) <= (PORT_R + 2) * (PORT_R + 2)) {
                    return PortHit.input(n.id, inputName);
                }
            }
        }
        return null;
    }

    private boolean inCanvas(double mouseX, double mouseY) {
        return mouseX >= canvasX0 && mouseX < canvasX1 && mouseY >= canvasY0 && mouseY < canvasY1;
    }

    private static int nodeHeight(EditableNode n) {
        int ins = inputNames(n).size();
        return Math.max(28, NODE_HEADER_H + 6 + (ins * PORT_SPACING));
    }

    private static List<String> inputNames(EditableNode n) {
        if (n == null || n.inputs == null || n.inputs.isEmpty()) return List.of();
        List<String> keys = new ArrayList<>(n.inputs.keySet());
        keys.sort(Comparator.naturalOrder());
        return keys;
    }

    private static void fillLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        if (x0 == x1 && y0 == y1) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            return;
        }
        if (y0 == y1) {
            int a = Math.min(x0, x1);
            int b = Math.max(x0, x1);
            g.fill(a, y0, b + 1, y0 + 1, color);
            return;
        }
        if (x0 == x1) {
            int a = Math.min(y0, y1);
            int b = Math.max(y0, y1);
            g.fill(x0, a, x0 + 1, b + 1, color);
            return;
        }
        // Fallback: draw a tiny box.
        g.fill(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1) + 1, Math.max(y0, y1) + 1, color);
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double dist2(double ax, double ay, double bx, double by) {
        double dx = ax - bx;
        double dy = ay - by;
        return (dx * dx) + (dy * dy);
    }

    private void onDone() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        onDone();
    }

    private static final class EditableNode {
        final String id;
        final String type;
        final Map<String, String> inputs;
        final Map<String, Object> params;
        int x;
        int y;

        EditableNode(String id, String type, Map<String, String> inputs, Map<String, Object> params, int x, int y) {
            this.id = Objects.requireNonNullElse(id, "");
            this.type = Objects.requireNonNullElse(type, "constant");
            this.inputs = (inputs == null) ? new LinkedHashMap<>() : inputs;
            this.params = (params == null) ? new LinkedHashMap<>() : params;
            this.x = x;
            this.y = y;
        }

        static EditableNode from(DspGraph.NodeDef def, int x, int y) {
            Map<String, String> in = new LinkedHashMap<>();
            if (def != null && def.inputs != null) {
                for (Map.Entry<String, String> e : def.inputs.entrySet()) {
                    if (e.getKey() == null) continue;
                    in.put(e.getKey(), Objects.requireNonNullElse(e.getValue(), ""));
                }
            }
            Map<String, Object> p = new LinkedHashMap<>();
            if (def != null && def.params != null) {
                p.putAll(def.params);
            }
            return new EditableNode(def == null ? "" : def.id, def == null ? "constant" : def.type, in, p, x, y);
        }
    }

    private static final class PortHit {
        final String nodeId;
        final boolean isOutput;
        final String inputName;

        private PortHit(String nodeId, boolean isOutput, String inputName) {
            this.nodeId = nodeId;
            this.isOutput = isOutput;
            this.inputName = inputName;
        }

        static PortHit output(String nodeId) {
            return new PortHit(nodeId, true, null);
        }

        static PortHit input(String nodeId, String inputName) {
            return new PortHit(nodeId, false, inputName);
        }
    }
}
