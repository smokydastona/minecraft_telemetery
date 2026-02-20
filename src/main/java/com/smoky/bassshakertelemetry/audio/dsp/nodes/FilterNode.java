package com.smoky.bassshakertelemetry.audio.dsp.nodes;

import com.smoky.bassshakertelemetry.audio.dsp.DspContext;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraph;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraphInstance;
import com.smoky.bassshakertelemetry.audio.dsp.DspNode;

/**
 * Biquad filter node.
 *
 * Params:
 * - mode: lpf|hpf|bpf|notch
 * - cutoffHz
 * - q
 */
public final class FilterNode implements DspNode {
    private final DspGraph.NodeDef def;

    // State
    private double x1, x2, y1, y2;

    // Coeffs
    private double b0, b1, b2, a1, a2;
    private int lastCutoff = Integer.MIN_VALUE;
    private String lastMode = "";
    private int lastQx1000 = Integer.MIN_VALUE;

    public FilterNode(DspGraph.NodeDef def) {
        this.def = def;
    }

    @Override
    public double next(DspContext ctx, DspGraphInstance g) {
        double in = g.input(ctx, def, "in");

        String mode = g.paramString(def, "mode", def.type).toLowerCase(java.util.Locale.ROOT);
        int cutoffHz = (int) Math.round(g.paramDouble(def, "cutoffHz", 65.0));
        double q = g.paramDouble(def, "q", 0.707);

        cutoffHz = clampInt(cutoffHz, 5, 220);
        int qx = (int) Math.round(clamp(q, 0.15, 5.0) * 1000.0);

        if (cutoffHz != lastCutoff || qx != lastQx1000 || !mode.equals(lastMode)) {
            lastCutoff = cutoffHz;
            lastQx1000 = qx;
            lastMode = mode;
            updateCoeffs(mode, cutoffHz, qx / 1000.0);
            // Reset a little when retuning to keep things stable.
            x1 = x2 = y1 = y2 = 0.0;
        }

        double y = (b0 * in) + (b1 * x1) + (b2 * x2) - (a1 * y1) - (a2 * y2);
        x2 = x1;
        x1 = in;
        y2 = y1;
        y1 = y;
        return y;
    }

    private void updateCoeffs(String mode, double cutoffHz, double q) {
        double w0 = (2.0 * Math.PI * cutoffHz) / DspContext.SAMPLE_RATE;
        double cos = Math.cos(w0);
        double sin = Math.sin(w0);
        double alpha = sin / (2.0 * q);

        double bb0, bb1, bb2, aa0, aa1, aa2;

        switch (mode) {
            case "hpf" -> {
                bb0 = (1.0 + cos) / 2.0;
                bb1 = -(1.0 + cos);
                bb2 = (1.0 + cos) / 2.0;
                aa0 = 1.0 + alpha;
                aa1 = -2.0 * cos;
                aa2 = 1.0 - alpha;
            }
            case "bpf" -> {
                bb0 = sin / 2.0;
                bb1 = 0.0;
                bb2 = -sin / 2.0;
                aa0 = 1.0 + alpha;
                aa1 = -2.0 * cos;
                aa2 = 1.0 - alpha;
            }
            case "notch" -> {
                bb0 = 1.0;
                bb1 = -2.0 * cos;
                bb2 = 1.0;
                aa0 = 1.0 + alpha;
                aa1 = -2.0 * cos;
                aa2 = 1.0 - alpha;
            }
            default -> {
                // lpf
                bb0 = (1.0 - cos) / 2.0;
                bb1 = 1.0 - cos;
                bb2 = (1.0 - cos) / 2.0;
                aa0 = 1.0 + alpha;
                aa1 = -2.0 * cos;
                aa2 = 1.0 - alpha;
            }
        }

        b0 = bb0 / aa0;
        b1 = bb1 / aa0;
        b2 = bb2 / aa0;
        a1 = aa1 / aa0;
        a2 = aa2 / aa0;
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
