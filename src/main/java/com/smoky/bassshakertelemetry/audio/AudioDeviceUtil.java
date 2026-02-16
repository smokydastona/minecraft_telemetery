package com.smoky.bassshakertelemetry.audio;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

public final class AudioDeviceUtil {
    private AudioDeviceUtil() {
    }

    public static List<String> listOutputDeviceNames(AudioFormat format) {
        List<String> out = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);
                if (mixer.isLineSupported(lineInfo)) {
                    out.add(info.getName());
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    public static Mixer.Info findMixerByName(String preferredName, AudioFormat format) {
        if (preferredName == null || preferredName.isBlank()) {
            return null;
        }
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (!info.getName().equalsIgnoreCase(preferredName)) {
                continue;
            }
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);
                if (mixer.isLineSupported(lineInfo)) {
                    return info;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
