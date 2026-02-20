package com.smoky.bassshakertelemetry.audio.dsp.nodes;

import com.smoky.bassshakertelemetry.audio.dsp.DspContext;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraph;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraphInstance;
import com.smoky.bassshakertelemetry.audio.dsp.DspNode;

/**
 * Multi-segment envelope applied to its input.
 *
 * Params:
 * - attackMs, decayMs, sustainLevel01, releaseMs (ADSR-style)
 */
public final class EnvelopeNode implements DspNode {
    private final DspGraph.NodeDef def;

    public EnvelopeNode(DspGraph.NodeDef def) {
        this.def = def;
    }

    @Override
    public double next(DspContext ctx, DspGraphInstance g) {
        double in = g.input(ctx, def, "in");
        double env = adsr(ctx,
                g.paramInt(def, "attackMs", 6),
                g.paramInt(def, "decayMs", 30),
                g.paramDouble(def, "sustainLevel01", 0.35),
                g.paramInt(def, "releaseMs", 60)
        );
        return in * env;
    }

    private static double adsr(DspContext ctx, int attackMs, int decayMs, double sustain, int releaseMs) {
        int a = Math.max(0, msToSamples(attackMs));
        int d = Math.max(0, msToSamples(decayMs));
        int r = Math.max(0, msToSamples(releaseMs));

        int total = Math.max(1, ctx.totalSamples);
        int i = Math.max(0, Math.min(ctx.sampleIndex, total - 1));

        // Keep release at the end.
        int sustainEnd = Math.max(0, total - r);

        if (a > 0 && i < a) {
            double p = i / (double) a;
            return smooth(p);
        }

        int afterA = i - a;
        if (d > 0 && afterA >= 0 && afterA < d) {
            double p = afterA / (double) d;
            return lerp(1.0, clamp01(sustain), smooth(p));
        }

        if (i < sustainEnd) {
            return clamp01(sustain);
        }

        if (r <= 0) {
            return 0.0;
        }

        double pr = (i - sustainEnd) / (double) r;
        return clamp01(sustain) * (1.0 - smooth(clamp01(pr)));
    }

    private static int msToSamples(int ms) {
        return (int) Math.round((Math.max(0, ms) / 1000.0) * DspContext.SAMPLE_RATE);
    }

    private static double smooth(double x) {
        // Smoothstep
        x = clamp01(x);
        return x * x * (3.0 - (2.0 * x));
    }

    private static double lerp(double a, double b, double t) {
        return a + ((b - a) * t);
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
