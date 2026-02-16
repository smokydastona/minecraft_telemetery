package com.smoky.bassshakertelemetry.client;

import com.smoky.bassshakertelemetry.config.BstVibrationProfiles;

/**
 * Client-only directional encoding and playback glue.
 *
 * IMPORTANT: This class imports net.minecraft.client.* and must remain client-only.
 */
public final class ClientVibrationPlayback {
    private ClientVibrationPlayback() {
    }

    public static void playNetworkVibration(BstVibrationProfiles.Resolved resolved, double baseGain01, boolean hasSource, double sourceX, double sourceY, double sourceZ) {
        VibrationIngress.playNetworkVibrationWithKey("", resolved, baseGain01, hasSource, sourceX, sourceY, sourceZ);
    }
}
