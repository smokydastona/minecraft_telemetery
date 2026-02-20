package com.smoky.bassshakertelemetry.audio.dsp;

import java.util.List;
import java.util.Map;

/**
 * Runtime instance of a graph. Holds node state and per-sample cache.
 */
public final class DspGraphInstance {
    private final List<DspGraph.NodeDef> defs;
    private final List<DspNode> nodes;
    private final Map<String, Integer> indexById;
    private final int outputIndex;

    private final double[] cache;
    private final int[] cacheSample;

    public DspGraphInstance(List<DspGraph.NodeDef> defs,
                            List<DspNode> nodes,
                            Map<String, Integer> indexById,
                            int outputIndex) {
        this.defs = defs;
        this.nodes = nodes;
        this.indexById = indexById;
        this.outputIndex = outputIndex;
        this.cache = new double[nodes.size()];
        this.cacheSample = new int[nodes.size()];
        for (int i = 0; i < cacheSample.length; i++) {
            cacheSample[i] = Integer.MIN_VALUE;
        }
    }

    public double out(DspContext ctx) {
        if (outputIndex < 0 || outputIndex >= nodes.size()) {
            return 0.0;
        }
        return evalIndex(ctx, outputIndex);
    }

    public double input(DspContext ctx, DspGraph.NodeDef def, String inputName) {
        if (def == null || inputName == null) {
            return 0.0;
        }
        String fromId = def.inputs.get(inputName);
        if (fromId == null || fromId.isBlank()) {
            return 0.0;
        }
        Integer idx = indexById.get(fromId);
        if (idx == null) {
            return 0.0;
        }
        return evalIndex(ctx, idx);
    }

    public Object param(DspGraph.NodeDef def, String key) {
        return (def == null || key == null) ? null : def.params.get(key);
    }

    public String paramString(DspGraph.NodeDef def, String key, String fallback) {
        Object v = param(def, key);
        if (v == null) return fallback;
        String s = String.valueOf(v);
        return (s == null || s.isBlank()) ? fallback : s;
    }

    public double paramDouble(DspGraph.NodeDef def, String key, double fallback) {
        Object v = param(def, key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return (v == null) ? fallback : Double.parseDouble(String.valueOf(v));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public int paramInt(DspGraph.NodeDef def, String key, int fallback) {
        Object v = param(def, key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return (v == null) ? fallback : Integer.parseInt(String.valueOf(v));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private double evalIndex(DspContext ctx, int index) {
        if (index < 0 || index >= nodes.size()) {
            return 0.0;
        }
        if (cacheSample[index] == ctx.sampleIndex) {
            return cache[index];
        }
        cacheSample[index] = ctx.sampleIndex;
        double v = nodes.get(index).next(ctx, this);
        cache[index] = v;
        return v;
    }

    public DspGraph.NodeDef defById(String id) {
        Integer idx = indexById.get(id);
        if (idx == null) return null;
        if (idx < 0 || idx >= defs.size()) return null;
        return defs.get(idx);
    }
}
