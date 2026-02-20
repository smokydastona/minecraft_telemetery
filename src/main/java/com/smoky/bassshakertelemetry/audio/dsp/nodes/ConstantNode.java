package com.smoky.bassshakertelemetry.audio.dsp.nodes;

import com.smoky.bassshakertelemetry.audio.dsp.DspContext;
import com.smoky.bassshakertelemetry.audio.dsp.DspGraphInstance;
import com.smoky.bassshakertelemetry.audio.dsp.DspNode;

public final class ConstantNode implements DspNode {
    private final double value;

    public ConstantNode(double value) {
        this.value = value;
    }

    @Override
    public double next(DspContext ctx, DspGraphInstance g) {
        return value;
    }
}
