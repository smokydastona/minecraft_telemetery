package com.smoky.bassshakertelemetry.audio.dsp.nodes;

import com.smoky.bassshakertelemetry.audio.dsp.DspContext;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraph;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraphInstance;
import com.smoky.bassshakertelemetry.audio.dsp.DspNode;

/**
 * Randomizer / sample-and-hold LFO.
 *
 * Params:
 * - rateHz
 * - depth
 */
public final class RandomizerNode implements DspNode {
    private final DspGraph.NodeDef def;
    private double current;
    private int samplesUntilNext;

    public RandomizerNode(DspGraph.NodeDef def) {
        this.def = def;
        this.current = 0.0;
        this.samplesUntilNext = 0;
    }

    @Override
    public double next(DspContext ctx, DspGraphInstance g) {
        double rateHz = g.paramDouble(def, "rateHz", 10.0);
        double depth = g.paramDouble(def, "depth", 1.0);
        int period = (rateHz <= 0.0) ? Integer.MAX_VALUE : Math.max(1, (int) Math.round(DspContext.SAMPLE_RATE / rateHz));

        if (samplesUntilNext <= 0) {
            current = ((ctx.random.nextDouble() * 2.0) - 1.0) * depth;
            samplesUntilNext = period;
        }
        samplesUntilNext--;
        return current;
    }
}
