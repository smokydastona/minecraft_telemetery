package com.smoky.bassshakertelemetry.audio.backend;

import com.smoky.bassshakertelemetry.audio.AudioDeviceUtil;
import com.smoky.bassshakertelemetry.config.BstConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.*;

/**
 * Current stable backend using JavaSound {@link SourceDataLine}.
 */
public final class JavaSoundBackend implements HapticAudioBackend {
    private static final Logger LOGGER = LogManager.getLogger("bassshakertelemetry");

    private static final float SAMPLE_RATE = 48_000f;

    @Override
    public String id() {
        return "javasound";
    }

    @Override
    public AudioOutputDevice open(AudioFormat preferredFormat, BstConfig.Data cfg) {
        SourceDataLine line = openLinePreferred(preferredFormat, cfg);
        if (line == null) {
            return null;
        }
        return new JavaSoundOutputDevice(line);
    }

    private SourceDataLine openLinePreferred(AudioFormat preferred, BstConfig.Data cfg) {
        String preferredDevice = (cfg == null) ? null : cfg.outputDeviceName;

        // If a specific device is selected, treat it as authoritative:
        // try the requested format on that device, then fall back to stereo *on that same device*.
        if (preferredDevice != null && !preferredDevice.isBlank()) {
            SourceDataLine line = openPreferredDeviceLineWithFormat(preferred, cfg, preferredDevice);
            if (line != null) {
                return line;
            }

            if (preferred.getChannels() != 2) {
                AudioFormat stereo = stereoFormatFor(preferred);
                LOGGER.warn("[BST] Preferred device '{}' does not support {}ch; trying stereo on preferred device", preferredDevice, preferred.getChannels());
                line = openPreferredDeviceLineWithFormat(stereo, cfg, preferredDevice);
                if (line != null) {
                    return line;
                }
            }

            // If the user explicitly picked a device but we cannot open it at all,
            // fall back to default as a last resort so the mod still works.
            LOGGER.warn("[BST] Unable to open preferred device '{}' - falling back to default device", preferredDevice);
        }

        // No preferred device (or it failed): use system default.
        SourceDataLine line = openDefaultDeviceLineWithFormat(preferred, cfg);
        if (line != null) {
            return line;
        }

        if (preferred.getChannels() != 2) {
            AudioFormat stereo = stereoFormatFor(preferred);
            LOGGER.warn("[BST] Falling back to stereo output on default device (could not open {}ch)", preferred.getChannels());
            return openDefaultDeviceLineWithFormat(stereo, cfg);
        }

        return null;
    }

    private static AudioFormat stereoFormatFor(AudioFormat basis) {
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                basis.getSampleRate(),
                16,
                2,
                2 * 2,
                basis.getSampleRate(),
                false
        );
    }

    private SourceDataLine openPreferredDeviceLineWithFormat(AudioFormat format, BstConfig.Data cfg, String preferredDeviceId) {
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);
        int bufferMs = clampInt((cfg == null) ? 0 : cfg.javaSoundBufferMs, 0, 500);
        int requestedBytes = requestedBufferBytes(format, bufferMs);

        try {
            Mixer.Info mixerInfo = AudioDeviceUtil.findMixerByName(preferredDeviceId, format);
            if (mixerInfo == null) {
                return null;
            }
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            SourceDataLine line = (SourceDataLine) mixer.getLine(lineInfo);
            openLine(line, format, requestedBytes);
            logOpenedLine(line, preferredDeviceId, bufferMs, format);
            return line;
        } catch (Exception e) {
            LOGGER.warn("[BST] Failed to open preferred audio device: '{}'", preferredDeviceId, e);
            return null;
        }
    }

    private SourceDataLine openDefaultDeviceLineWithFormat(AudioFormat format, BstConfig.Data cfg) {
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);
        int bufferMs = clampInt((cfg == null) ? 0 : cfg.javaSoundBufferMs, 0, 500);
        int requestedBytes = requestedBufferBytes(format, bufferMs);

        try {
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
            openLine(line, format, requestedBytes);
            logOpenedLine(line, "<Default>", bufferMs, format);
            return line;
        } catch (Exception e) {
            LOGGER.error("[BST] Failed to open default audio device", e);
            return null;
        }
    }

    private static int requestedBufferBytes(AudioFormat format, int bufferMs) {
        int frameSize = format.getFrameSize();
        int requestedFrames = (bufferMs <= 0) ? 0 : (int) Math.round((bufferMs / 1000.0) * SAMPLE_RATE);
        requestedFrames = clampInt(requestedFrames, 0, ((int) SAMPLE_RATE) * 2);
        return (requestedFrames <= 0) ? 0 : Math.max(frameSize, requestedFrames * frameSize);
    }

    private static void openLine(SourceDataLine line, AudioFormat format, int requestedBytes) throws LineUnavailableException {
        if (requestedBytes > 0) {
            try {
                line.open(format, requestedBytes);
                return;
            } catch (Exception ignored) {
                // fallback below
            }
        }
        line.open(format);
    }

    private static void logOpenedLine(SourceDataLine line, String deviceLabel, int requestedBufferMs, AudioFormat format) {
        try {
            int bytes = line.getBufferSize();
            int frameSize = format.getFrameSize();
            int frames = (frameSize <= 0) ? 0 : (bytes / frameSize);
            double ms = (frames * 1000.0) / SAMPLE_RATE;
            LOGGER.info("[BST] Opened audio line: device='{}' fmt={}ch requestedBufferMs={} actualBufferMs~={} ({} bytes)",
                    deviceLabel,
                    format.getChannels(),
                    requestedBufferMs,
                    String.format(java.util.Locale.ROOT, "%.1f", ms),
                    bytes);
        } catch (Exception ignored) {
        }
    }

    private static int clampInt(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static final class JavaSoundOutputDevice implements AudioOutputDevice {
        private final SourceDataLine line;

        private JavaSoundOutputDevice(SourceDataLine line) {
            this.line = line;
        }

        @Override
        public AudioFormat format() {
            return line.getFormat();
        }

        @Override
        public int channels() {
            try {
                return Math.max(1, line.getFormat().getChannels());
            } catch (Exception ignored) {
                return 2;
            }
        }

        @Override
        public int bufferSizeBytes() {
            try {
                return line.getBufferSize();
            } catch (Exception ignored) {
                return -1;
            }
        }

        @Override
        public int availableBytes() {
            try {
                return line.available();
            } catch (Exception ignored) {
                return -1;
            }
        }

        @Override
        public void start() {
            line.start();
        }

        @Override
        public void write(byte[] buffer, int offset, int length) {
            line.write(buffer, offset, length);
        }

        @Override
        public void stopFlushClose() {
            try {
                line.stop();
            } catch (Exception ignored) {
            }
            try {
                line.flush();
            } catch (Exception ignored) {
            }
            try {
                line.close();
            } catch (Exception ignored) {
            }
        }
    }
}
