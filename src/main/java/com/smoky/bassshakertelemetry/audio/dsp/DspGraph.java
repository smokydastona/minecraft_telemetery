package com.smoky.bassshakertelemetry.audio.dsp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable DSP graph definition.
 *
 * <p>Phase 2 foundation: kept intentionally small and deterministic.
 */
public final class DspGraph {
    public static final class NodeDef {
        public final String id;
        public final String type;
        public final Map<String, String> inputs;
        public final Map<String, Object> params;

        public NodeDef(String id, String type, Map<String, String> inputs, Map<String, Object> params) {
            this.id = id;
            this.type = type;
            this.inputs = (inputs == null) ? Map.of() : Map.copyOf(inputs);
            this.params = (params == null) ? Map.of() : Map.copyOf(params);
        }
    }

    private final List<NodeDef> nodes;
    private final String outputNodeId;

    public DspGraph(List<NodeDef> nodes, String outputNodeId) {
        this.nodes = (nodes == null) ? List.of() : List.copyOf(nodes);
        this.outputNodeId = (outputNodeId == null) ? "" : outputNodeId;
    }

    public List<NodeDef> nodes() {
        return nodes;
    }

    public String outputNodeId() {
        return outputNodeId;
    }

    public DspGraphInstance instantiate(DspNodeFactory factory) {
        Map<String, Integer> indexById = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            indexById.put(nodes.get(i).id, i);
        }

        int outIndex = indexById.getOrDefault(outputNodeId, -1);
        if (outIndex < 0 && !nodes.isEmpty()) {
            outIndex = nodes.size() - 1;
        }

        List<DspNode> runtime = new ArrayList<>(nodes.size());
        for (NodeDef def : nodes) {
            runtime.add(factory.create(def));
        }

        return new DspGraphInstance(nodes, runtime, indexById, outIndex);
    }
}
