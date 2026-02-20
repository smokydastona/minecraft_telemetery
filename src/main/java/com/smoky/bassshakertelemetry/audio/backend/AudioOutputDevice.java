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

    /**
     * Optional: total internal output buffer size, in bytes.
     *
     * <p>Returns a non-positive value when not supported.
     */
    default int bufferSizeBytes() {
        return -1;
    }

    /**
     * Optional: number of bytes that can be written without blocking.
     *
     * <p>Returns a negative value when not supported.
     */
    default int availableBytes() {
        return -1;
    }

    void start();

    void write(byte[] buffer, int offset, int length);

    void stopFlushClose();
}
