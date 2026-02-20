package com.smoky.bassshakertelemetry.audio.dsp;

import java.util.Random;

/**
 * Runtime context for DSP patch evaluation.
 */
public final class DspContext {
    public static final float SAMPLE_RATE = 48_000f;

    public final Random random;

    public double startFreqHz;
    public double endFreqHz;
    public int totalSamples;

    public int sampleIndex;

    public DspContext(long seed, double startFreqHz, double endFreqHz, int totalSamples) {
        this.random = new Random(seed);
        this.startFreqHz = startFreqHz;
        this.endFreqHz = endFreqHz;
        this.totalSamples = Math.max(1, totalSamples);
        this.sampleIndex = 0;
    }

    public void retune(double startFreqHz, double endFreqHz) {
        this.startFreqHz = startFreqHz;
        this.endFreqHz = endFreqHz;
    }

    public void resize(int totalSamples) {
        this.totalSamples = Math.max(1, totalSamples);
    }

    public double overallProgress01() {
        if (totalSamples <= 1) {
            return 1.0;
        }
        return clamp01(sampleIndex / (double) (totalSamples - 1));
    }

    public double frequencyHz() {
        double p = overallProgress01();
        return startFreqHz + ((endFreqHz - startFreqHz) * p);
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
