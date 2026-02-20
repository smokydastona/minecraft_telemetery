package com.smoky.bassshakertelemetry.audio.backend;

import javax.sound.sampled.AudioFormat;

/**
 * Low-level PCM output device abstraction.
 *
 * <p>Backends stream interleaved PCM_SIGNED 16-bit little-endian frames.
 */
public interface AudioOutputDevice {
    AudioFormat format();

    int channels();

    void start();

    void write(byte[] buffer, int offset, int length);

    void stopFlushClose();
}
