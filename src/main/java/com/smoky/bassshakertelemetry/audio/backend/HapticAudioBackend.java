package com.smoky.bassshakertelemetry.audio.backend;

import com.smoky.bassshakertelemetry.config.BstConfig;

import javax.sound.sampled.AudioFormat;

/**
 * Audio backend abstraction.
 *
 * <p>Phase 1 foundation: separate the mixer/render loop from the actual device API.
 * Future backends (OpenAL Soft, WASAPI, ASIO) can implement this without changing
 * gameplay/event code.
 */
public interface HapticAudioBackend {
    String id();

    AudioOutputDevice open(AudioFormat preferredFormat, BstConfig.Data cfg);
}
