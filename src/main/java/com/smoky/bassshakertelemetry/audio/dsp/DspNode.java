package com.smoky.bassshakertelemetry.audio.dsp;

/**
 * A stateful DSP node evaluated once per sample.
 */
public interface DspNode {
    /**
     * Compute the node output for the current sample.
     */
    double next(DspContext ctx, DspGraphInstance g);
}
