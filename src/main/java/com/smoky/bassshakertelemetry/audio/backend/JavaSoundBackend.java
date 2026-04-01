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

    private static final int DEFAULT_LOW_LATENCY_BUFFER_MS = 20;
    private static final double WARN_IF_ACTUAL_BUFFER_MS_OVER = 250.0;
    private static final double HARD_REJECT_IF_ACTUAL_BUFFER_MS_OVER = 500.0;

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
        int rawBufferMs = clampInt((cfg == null) ? 0 : cfg.javaSoundBufferMs, 0, 500);
        int effectiveBufferMs = effectiveRequestedBufferMs(rawBufferMs);

        try {
            Mixer.Info mixerInfo = AudioDeviceUtil.findMixerByName(preferredDeviceId, format);
            if (mixerInfo == null) {
                return null;
            }
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            SourceDataLine line = (SourceDataLine) mixer.getLine(lineInfo);
            openLineLowLatency(line, format, effectiveBufferMs);
            logOpenedLine(line, preferredDeviceId, rawBufferMs, effectiveBufferMs, format);
            return line;
        } catch (Exception e) {
            LOGGER.warn("[BST] Failed to open preferred audio device: '{}'", preferredDeviceId, e);
            return null;
        }
    }

    private SourceDataLine openDefaultDeviceLineWithFormat(AudioFormat format, BstConfig.Data cfg) {
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);
        int rawBufferMs = clampInt((cfg == null) ? 0 : cfg.javaSoundBufferMs, 0, 500);
        int effectiveBufferMs = effectiveRequestedBufferMs(rawBufferMs);

        try {
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
            openLineLowLatency(line, format, effectiveBufferMs);
            logOpenedLine(line, "<Default>", rawBufferMs, effectiveBufferMs, format);
            return line;
        } catch (Exception e) {
            LOGGER.error("[BST] Failed to open default audio device", e);
            return null;
        }
    }

    private static int effectiveRequestedBufferMs(int rawBufferMs) {
        int raw = clampInt(rawBufferMs, 0, 500);
        // 0 means "auto" in config, but the JavaSound default often ends up high-latency.
        // For accessibility / tight feedback timing, treat it as a low-latency request.
        if (raw <= 0) return DEFAULT_LOW_LATENCY_BUFFER_MS;
        return Math.max(5, raw);
    }

    private static int requestedBufferBytes(AudioFormat format, int bufferMs) {
        int frameSize = format.getFrameSize();
        double sampleRate = (format == null) ? 48_000.0 : Math.max(1.0, format.getSampleRate());
        int requestedFrames = (bufferMs <= 0) ? 0 : (int) Math.round((bufferMs / 1000.0) * sampleRate);
        requestedFrames = clampInt(requestedFrames, 0, ((int) sampleRate) * 2);
        return (requestedFrames <= 0) ? 0 : Math.max(frameSize, requestedFrames * frameSize);
    }

    private static void openLineLowLatency(SourceDataLine line, AudioFormat format, int requestedBufferMs) throws LineUnavailableException {
        // Try a set of small buffer sizes first. Some devices throw if the buffer is too small,
        // and our previous fallback (line.open(format)) could pick a ~1s buffer.
        int[] candidatesMs = new int[] {
                clampInt(requestedBufferMs, 5, 500),
                10,
                15,
                20,
                30,
                40,
                60,
                80,
                120,
            200,
            300,
            400,
            500
        };

        Exception last = null;
        for (int ms : candidatesMs) {
            int clamped = clampInt(ms, 5, 500);
            int requestedBytes = requestedBufferBytes(format, clamped);

            try {
                line.open(format, requestedBytes);
            } catch (Exception e) {
                last = e;
                continue;
            }

            double actualMs = computeActualBufferMs(line, format);
            // Always accept a reasonable-size buffer even if it's above the "ideal" target.
            // But hard-reject extreme buffers because they create ~1s+ latency.
            if (actualMs > HARD_REJECT_IF_ACTUAL_BUFFER_MS_OVER) {
                try {
                    line.close();
                } catch (Exception ignored) {
                }
                continue;
            }

            if (actualMs > 0.0) {
                return;
            }

            // Buffer opened but ended up too large; close and try another request.
            try {
                line.close();
            } catch (Exception ignored) {
            }
        }

        // Last resort: open with device default.
        // We still validate the resulting buffer; if it is extreme, fail fast rather than silently
        // producing delayed haptics.
        try {
            line.open(format);
            double actualMs = computeActualBufferMs(line, format);
            if (actualMs > HARD_REJECT_IF_ACTUAL_BUFFER_MS_OVER) {
                try {
                    line.close();
                } catch (Exception ignored) {
                }
                throw new LineUnavailableException("Audio device forced extreme buffer (~" + String.format(java.util.Locale.ROOT, "%.1f", actualMs) + "ms). Select another device or raise 'javaSoundBufferMs' only as needed.");
            }
        } catch (LineUnavailableException e) {
            throw e;
        } catch (Exception e) {
            if (last instanceof RuntimeException re) throw re;
            throw new LineUnavailableException("Failed to open audio line (last error: " + (last == null ? "<none>" : last) + ")");
        }
    }

    private static double computeActualBufferMs(SourceDataLine line, AudioFormat format) {
        try {
            int bytes = line.getBufferSize();
            int frameSize = format.getFrameSize();
            int frames = (frameSize <= 0) ? 0 : (bytes / frameSize);
            double sampleRate = (format == null) ? 48_000.0 : Math.max(1.0, format.getSampleRate());
            return (frames * 1000.0) / sampleRate;
        } catch (Exception ignored) {
            return -1.0;
        }
    }

    private static void logOpenedLine(SourceDataLine line, String deviceLabel, int rawRequestedBufferMs, int effectiveRequestedBufferMs, AudioFormat format) {
        try {
            int bytes = line.getBufferSize();
            double ms = computeActualBufferMs(line, format);
            LOGGER.info("[BST] Opened audio line: device='{}' fmt={}ch requestedBufferMsRaw={} requestedBufferMs={} actualBufferMs~={} ({} bytes)",
                    deviceLabel,
                    format.getChannels(),
                    rawRequestedBufferMs,
                    effectiveRequestedBufferMs,
                    String.format(java.util.Locale.ROOT, "%.1f", ms),
                    bytes);
            if (ms > WARN_IF_ACTUAL_BUFFER_MS_OVER) {
                LOGGER.warn("[BST] Audio output latency is high (actualBufferMs~={}). Reduce 'javaSoundBufferMs' in Advanced settings and avoid Bluetooth devices.",
                        String.format(java.util.Locale.ROOT, "%.1f", ms));
            }
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
