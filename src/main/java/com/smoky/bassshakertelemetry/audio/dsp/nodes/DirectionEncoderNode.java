package com.smoky.bassshakertelemetry.audio.dsp.nodes;

import com.smoky.bassshakertelemetry.audio.dsp.DspContext;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraph;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraphInstance;
import com.smoky.bassshakertelemetry.audio.dsp.DspNode;
import com.smoky.bassshakertelemetry.config.BstVibrationProfiles;

/**
 * Phase 2: Direction encoder node.
 *
 * <p>This node is intentionally minimal and stable: it applies a micro-delay to the input
 * and scales intensity. When {@code useProfileEncoding=true}, the delay/intensity come from
 * the root-level {@code encoding.*} bands in {@link BstVibrationProfiles}.
 *
 * Inputs:
 * - in
 *
 * Params:
 * - useProfileEncoding (boolean)
 * - band (center|front|rear|left|right)
 * - timeOffsetMs (number, used when useProfileEncoding=false)
 * - intensityMul (number, used when useProfileEncoding=false)
 * - mix (0..1): 0 = passthrough, 1 = fully delayed
 */
public final class DirectionEncoderNode implements DspNode {
    private final DspGraph.NodeDef def;

    // Ring buffer delay line (supports small delays; sized defensively).
    private final double[] delay;
    private int write;

    public DirectionEncoderNode(DspGraph.NodeDef def) {
        this.def = def;
        // 12ms @ 48k = 576 samples. Give a little extra headroom.
        this.delay = new double[768];
        this.write = 0;
    }

    @Override
    public double next(DspContext ctx, DspGraphInstance g) {
        double in = g.input(ctx, def, "in");

        boolean useProfile = g.paramInt(def, "useProfileEncoding", 1) != 0;
        String bandName = g.paramString(def, "band", "center").toLowerCase(java.util.Locale.ROOT);

        int timeOffsetMs;
        double intensityMul;
        if (useProfile) {
            BstVibrationProfiles.Store store = BstVibrationProfiles.get();
            BstVibrationProfiles.Encoding enc = (store == null) ? null : store.encoding;
            BstVibrationProfiles.Encoding.Band b = band(enc, bandName);
            timeOffsetMs = (b == null) ? 0 : b.timeOffsetMs;
            intensityMul = (b == null) ? 1.0 : b.intensityMul;
        } else {
            timeOffsetMs = (int) Math.round(g.paramDouble(def, "timeOffsetMs", 0.0));
            intensityMul = g.paramDouble(def, "intensityMul", 1.0);
        }

        timeOffsetMs = clampInt(timeOffsetMs, 0, 12);
        intensityMul = clamp(intensityMul, 0.0, 2.0);

        double mix = clamp(g.paramDouble(def, "mix", 1.0), 0.0, 1.0);

        double delayed = readDelay(timeOffsetMs);

        // Write after read to avoid a 1-sample off-by-one for 0ms.
        delay[write] = in;
        write = (write + 1) % delay.length;

        double out = lerp(in, delayed, mix);
        return out * intensityMul;
    }

    private double readDelay(int timeOffsetMs) {
        if (timeOffsetMs <= 0) {
            return delay[(write - 1 + delay.length) % delay.length];
        }

        double samples = (timeOffsetMs / 1000.0) * DspContext.SAMPLE_RATE;
        samples = clamp(samples, 0.0, delay.length - 2.0);

        int whole = (int) Math.floor(samples);
        double frac = samples - whole;

        int i0 = write - 1 - whole;
        int i1 = i0 - 1;
        while (i0 < 0) i0 += delay.length;
        while (i1 < 0) i1 += delay.length;
        i0 %= delay.length;
        i1 %= delay.length;

        double a = delay[i0];
        double b = delay[i1];
        // Linear interpolation between adjacent samples.
        return (a * (1.0 - frac)) + (b * frac);
    }

    private static BstVibrationProfiles.Encoding.Band band(BstVibrationProfiles.Encoding enc, String bandName) {
        if (enc == null) {
            return null;
        }
        return switch (bandName) {
            case "front" -> enc.front;
            case "rear" -> enc.rear;
            case "left" -> enc.left;
            case "right" -> enc.right;
            default -> enc.center;
        };
    }

    private static double lerp(double a, double b, double t) {
        return a + ((b - a) * t);
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
