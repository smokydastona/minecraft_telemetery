package com.smoky.bassshakertelemetry.audio.dsp.nodes;

import com.smoky.bassshakertelemetry.audio.dsp.DspContext;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraph;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraphInstance;
import com.smoky.bassshakertelemetry.audio.dsp.DspNode;

/**
 * Harmonic generator (additive sine).
 */
public final class HarmonicGeneratorNode implements DspNode {
    private final DspGraph.NodeDef def;
    private double phase;

    public HarmonicGeneratorNode(DspGraph.NodeDef def) {
        this.def = def;
        this.phase = 0.0;
    }

    @Override
    public double next(DspContext ctx, DspGraphInstance g) {
        double freqHz = ctx.frequencyHz();
        // Optional frequency modulation input.
        double fm = g.input(ctx, def, "fm");
        double fmDepth = g.paramDouble(def, "fmDepthHz", 0.0);
        freqHz = Math.max(0.0, freqHz + (fm * fmDepth));

        double baseAmp = g.paramDouble(def, "amp", 1.0);

        int harmonics = Math.max(1, g.paramInt(def, "harmonics", 3));
        double rolloff = g.paramDouble(def, "rolloff", 0.45);

        double step = (2.0 * Math.PI * freqHz) / DspContext.SAMPLE_RATE;
        double sum = 0.0;
        double amp = 1.0;
        for (int h = 1; h <= harmonics; h++) {
            sum += Math.sin(phase * h) * amp;
            amp *= rolloff;
        }

        phase += step;
        if (phase > (2.0 * Math.PI)) {
            phase -= (2.0 * Math.PI);
        }

        return sum * baseAmp;
    }
}
