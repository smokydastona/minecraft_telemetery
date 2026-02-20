package com.smoky.bassshakertelemetry.audio.dsp.nodes;

import com.smoky.bassshakertelemetry.audio.dsp.DspContext;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraph;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraphInstance;
import com.smoky.bassshakertelemetry.audio.dsp.DspNode;

/**
 * Noise generator with basic color shaping.
 */
public final class NoiseNode implements DspNode {
    private final DspGraph.NodeDef def;

    // Brown
    private double brown;

    // Pink (very small filter approximation)
    private double p0, p1, p2;

    // Blue
    private double lastWhite;

    public NoiseNode(DspGraph.NodeDef def) {
        this.def = def;
    }

    @Override
    public double next(DspContext ctx, DspGraphInstance g) {
        String color = g.paramString(def, "color", "white").toLowerCase(java.util.Locale.ROOT);
        double amp = g.paramDouble(def, "amp", 1.0);

        double white = (ctx.random.nextDouble() * 2.0) - 1.0;
        double out;

        switch (color) {
            case "brown" -> {
                // Integrate and clamp.
                brown += white * 0.02;
                brown = clamp(brown, -1.0, 1.0);
                out = brown;
            }
            case "pink" -> {
                // Cheap-ish IIR approximation.
                p0 = (0.99765 * p0) + (white * 0.0990460);
                p1 = (0.96300 * p1) + (white * 0.2965164);
                p2 = (0.57000 * p2) + (white * 1.0526913);
                out = (p0 + p1 + p2 + (white * 0.1848)) * 0.05;
            }
            case "blue" -> {
                out = white - lastWhite;
                lastWhite = white;
            }
            default -> out = white;
        }

        return out * amp;
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
