package com.smoky.bassshakertelemetry.audio.dsp.nodes;

import com.smoky.bassshakertelemetry.audio.dsp.DspContext;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraph;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraphInstance;
import com.smoky.bassshakertelemetry.audio.dsp.DspNode;

/**
 * Simple compressor/limiter.
 *
 * Params:
 * - threshold (0..1)
 * - ratio (>=1)
 * - attackMs
 * - releaseMs
 */
public final class CompressorLimiterNode implements DspNode {
    private final DspGraph.NodeDef def;

    private double env;

    public CompressorLimiterNode(DspGraph.NodeDef def) {
        this.def = def;
        this.env = 0.0;
    }

    @Override
    public double next(DspContext ctx, DspGraphInstance g) {
        double in = g.input(ctx, def, "in");

        double thr = clamp(g.paramDouble(def, "threshold", 0.75), 0.05, 1.0);
        double ratio = Math.max(1.0, g.paramDouble(def, "ratio", 4.0));
        int aMs = Math.max(0, g.paramInt(def, "attackMs", 8));
        int rMs = Math.max(1, g.paramInt(def, "releaseMs", 70));

        double a = 1.0 - Math.exp(-1.0 / Math.max(1.0, (aMs / 1000.0) * DspContext.SAMPLE_RATE));
        double r = 1.0 - Math.exp(-1.0 / Math.max(1.0, (rMs / 1000.0) * DspContext.SAMPLE_RATE));

        double x = Math.abs(in);
        if (x > env) {
            env += (x - env) * a;
        } else {
            env += (x - env) * r;
        }

        double gain = 1.0;
        if (env > thr) {
            double over = env / thr;
            double compressed = Math.pow(over, (1.0 / ratio));
            gain = (thr * compressed) / env;
        }

        return in * gain;
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
