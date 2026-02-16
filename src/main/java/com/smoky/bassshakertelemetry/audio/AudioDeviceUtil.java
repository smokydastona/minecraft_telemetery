package com.smoky.bassshakertelemetry.audio;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
                    out.add(toDeviceId(info));
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /**
     * Finds a mixer by a stored device id.
     * <p>
     * New format: "name — description" (more unique on Windows)
     * Legacy format: "name" (used by earlier versions)
     */
    public static Mixer.Info findMixerByName(String preferredName, AudioFormat format) {
        if (preferredName == null || preferredName.isBlank()) {
            return null;
        }

        String preferred = preferredName.trim();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            String id = toDeviceId(info);
            boolean matches = id.equalsIgnoreCase(preferred) || info.getName().equalsIgnoreCase(preferred);
            if (!matches) continue;
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

    public static String resolveDisplayName(String stored, AudioFormat format) {
        if (stored == null || stored.isBlank()) {
            return "<Default>";
        }
        Mixer.Info info = findMixerByName(stored, format);
        if (info == null) {
            return "<Default>";
        }
        return toDeviceId(info);
    }

    private static String toDeviceId(Mixer.Info info) {
        String name = Objects.requireNonNullElse(info.getName(), "").trim();
        String desc = Objects.requireNonNullElse(info.getDescription(), "").trim();
        if (name.isEmpty()) {
            name = "<Unknown>";
        }
        if (desc.isEmpty() || desc.equalsIgnoreCase(name)) {
            return name;
        }
        return name + " — " + desc;
    }
}
