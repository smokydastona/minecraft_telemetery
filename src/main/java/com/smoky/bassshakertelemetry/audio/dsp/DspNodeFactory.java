package com.smoky.bassshakertelemetry.audio.dsp;

import com.smoky.bassshakertelemetry.audio.dsp.nodes.*;

/**
 * Creates runtime nodes from graph definitions.
 */
public final class DspNodeFactory {
    public DspNode create(DspGraph.NodeDef def) {
        if (def == null) {
            return new ConstantNode(0.0);
        }
        String t = (def.type == null) ? "" : def.type.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (t) {
            case "harmonic", "harmonic_generator", "osc" -> new HarmonicGeneratorNode(def);
            case "noise", "noise_generator" -> new NoiseNode(def);
            case "envelope", "adsr" -> new EnvelopeNode(def);
            case "filter", "lpf", "hpf", "bpf", "notch" -> new FilterNode(def);
            case "randomizer", "random" -> new RandomizerNode(def);
            case "compressor", "limiter", "compressor_limiter" -> new CompressorLimiterNode(def);
            case "mixer", "mix" -> new MixerNode(def);
            default -> new ConstantNode(0.0);
        };
    }
}
