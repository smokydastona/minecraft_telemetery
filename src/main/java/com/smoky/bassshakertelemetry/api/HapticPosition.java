package com.smoky.bassshakertelemetry.api;

/**
 * Simple position payload for telemetry/event streams.
 *
 * <p>This avoids any dependency on Minecraft client-only vector classes.
 */
public record HapticPosition(double x, double y, double z) {
}
