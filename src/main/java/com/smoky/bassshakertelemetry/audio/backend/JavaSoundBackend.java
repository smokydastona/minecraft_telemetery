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
        SourceDataLine line = openLineWithFormat(preferred, cfg);
        if (line != null) {
            return line;
        }

        // Fallback: if we asked for 8ch but couldn't open, try stereo.
        if (preferred.getChannels() != 2) {
            LOGGER.warn("[BST] Falling back to stereo output (device does not support 8-channel output)");
            AudioFormat stereo = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    preferred.getSampleRate(),
                    16,
                    2,
                    2 * 2,
                    preferred.getSampleRate(),
                    false
            );
            return openLineWithFormat(stereo, cfg);
        }

        return null;
    }

    private SourceDataLine openLineWithFormat(AudioFormat format, BstConfig.Data cfg) {
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);

        String preferred = (cfg == null) ? null : cfg.outputDeviceName;
        int bufferMs = clampInt((cfg == null) ? 0 : cfg.javaSoundBufferMs, 0, 500);

        int frameSize = format.getFrameSize();
        int requestedFrames = (bufferMs <= 0) ? 0 : (int) Math.round((bufferMs / 1000.0) * SAMPLE_RATE);
        requestedFrames = clampInt(requestedFrames, 0, ((int) SAMPLE_RATE) * 2);
        int requestedBytes = (requestedFrames <= 0) ? 0 : Math.max(frameSize, requestedFrames * frameSize);

        if (preferred != null && !preferred.isBlank()) {
            try {
                Mixer.Info mixerInfo = AudioDeviceUtil.findMixerByName(preferred, format);
                if (mixerInfo != null) {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    SourceDataLine line = (SourceDataLine) mixer.getLine(lineInfo);
                    openLine(line, format, requestedBytes);
                    logOpenedLine(line, preferred, bufferMs, format);
                    return line;
                } else {
                    LOGGER.warn("[BST] Preferred device not found or unsupported: '{}'", preferred);
                }
            } catch (Exception e) {
                LOGGER.warn("[BST] Failed to open preferred audio device: '{}'", preferred, e);
            }
        }

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
