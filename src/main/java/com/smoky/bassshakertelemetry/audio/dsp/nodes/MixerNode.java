package com.smoky.bassshakertelemetry.audio.dsp.nodes;

import com.smoky.bassshakertelemetry.audio.dsp.DspContext;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraph;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraphInstance;
import com.smoky.bassshakertelemetry.audio.dsp.DspNode;

/**
 * Simple mixer node.
 *
 * Inputs:
 * - a, b
 * Params:
 * - mode: mix|mul
 * - gainA, gainB
 */
public final class MixerNode implements DspNode {
    private final DspGraph.NodeDef def;

    public MixerNode(DspGraph.NodeDef def) {
        this.def = def;
    }

    @Override
    public double next(DspContext ctx, DspGraphInstance g) {
        double a = g.input(ctx, def, "a");
        double b = g.input(ctx, def, "b");

        double ga = g.paramDouble(def, "gainA", 1.0);
        double gb = g.paramDouble(def, "gainB", 1.0);

        String mode = g.paramString(def, "mode", "mix").toLowerCase(java.util.Locale.ROOT);
        if ("mul".equals(mode)) {
            return (a * ga) * (b * gb);
        }
        return (a * ga) + (b * gb);
    }
}
