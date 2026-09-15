package com.smoky.bassshakertelemetry.neoforge;

import com.smoky.bassshakertelemetry.audio.AudioOutputEngine;
import com.smoky.bassshakertelemetry.config.BstConfig;
import com.smoky.bassshakertelemetry.config.BstVibrationProfiles;
import com.smoky.bassshakertelemetry.telemetryout.SimpleWebSocketServer;
import com.smoky.bassshakertelemetry.telemetryout.TelemetryOut;
import com.smoky.bassshakertelemetry.telemetryout.TelemetryOutSink;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = BassShakerTelemetryNeoForge.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeClientLifecycle {
    private static SimpleWebSocketServer webSocket;
    private static int webSocketPort = -1;
    private static double lastSpeed;
    private static float lastHealth = -1.0f;
    private static boolean lastDead;
    private static double lastX;
    private static double lastZ;
    private static double stepDistance;
    private static boolean movementInitialized;
    private static long lastMinePulseNanos;

    private static final TelemetryOutSink SINK = message -> {
        SimpleWebSocketServer active = webSocket;
        if (active != null && active.isRunning()) active.broadcastText(message);
    };

    private NeoForgeClientLifecycle() { }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        var config = BstConfig.get();
        updateWebSocket(config);
        if (!config.enabled || client.isPaused() || client.player == null || client.level == null) {
            AudioOutputEngine.get().setTelemetryLive(false);
            reset(client.player);
            return;
        }
        Player player = client.player;
        double speed = player.getDeltaMovement().length();
        double accel = speed - lastSpeed;
        lastSpeed = speed;
        AudioOutputEngine.get().setTelemetryLive(true);
        AudioOutputEngine.get().updateTelemetry(speed, accel, player.isFallFlying(), player.onGround(),
                player.isInWaterOrBubble(), player.isSwimming());
        detectDamage(player, config);
        detectMovement(player, config);
        if (config.webSocketEnabled && config.webSocketSendTelemetry) {
            TelemetryOut.emitTelemetry(speed, accel, player.isFallFlying());
        }
    }

    private static void updateWebSocket(BstConfig.Data config) {
        if (!config.enabled || !config.webSocketEnabled) {
            stopWebSocket();
            return;
        }
        int port = config.webSocketPort < 1 || config.webSocketPort > 65535 ? 7117 : config.webSocketPort;
        if (webSocket == null || !webSocket.isRunning() || webSocketPort != port) {
            stopWebSocket();
            try {
                webSocket = new SimpleWebSocketServer(port);
                webSocket.start();
                webSocketPort = port;
            } catch (Exception error) {
                webSocket = null;
                webSocketPort = -1;
                return;
            }
        }
        TelemetryOut.setSink(SINK);
    }

    private static void stopWebSocket() {
        TelemetryOut.setSink(TelemetryOutSink.NOOP);
        if (webSocket != null) webSocket.stop();
        webSocket = null;
        webSocketPort = -1;
    }

    private static void detectDamage(Player player, BstConfig.Data config) {
        float health = player.getHealth();
        boolean dead = player.isDeadOrDying() || health <= 0.0f;
        if (dead && !lastDead) triggerProfile("damage.death", config.damageBurstGain);
        else if (lastHealth >= 0.0f && health < lastHealth - 0.001f) triggerProfile("damage.generic", config.damageBurstGain);
        lastHealth = health;
        lastDead = dead;
    }

    private static void detectMovement(Player player, BstConfig.Data config) {
        double x = player.getX(), z = player.getZ();
        if (!movementInitialized) {
            movementInitialized = true;
            lastX = x;
            lastZ = z;
            return;
        }
        if (player.onGround()) {
            double dx = x - lastX, dz = z - lastZ;
            stepDistance += Math.sqrt(dx * dx + dz * dz);
            if (stepDistance >= 0.9) {
                stepDistance -= 0.9;
                triggerProfile("movement.footstep", config.footstepHapticsGain);
            }
        }
        lastX = x;
        lastZ = z;
        if (config.gameplayMiningPulseEnabled && Minecraft.getInstance().options.keyAttack.isDown()
                && Minecraft.getInstance().hitResult != null) {
            long now = System.nanoTime();
            if (now - lastMinePulseNanos >= Math.max(30, config.gameplayMiningPulsePeriodMs) * 1_000_000L) {
                lastMinePulseNanos = now;
                triggerProfile("gameplay.mine_pulse", config.gameplayHapticsGain);
            }
        }
    }

    private static void triggerProfile(String key, double gain) {
        var resolved = BstVibrationProfiles.get().resolve(key, 1.0, 1.0);
        if (resolved == null) return;
        AudioOutputEngine.get().triggerImpulse(resolved.frequencyHz(), resolved.durationMs(),
                Math.max(0.0, Math.min(1.0, resolved.intensity01() * gain)), resolved.noiseMix01(),
                resolved.pattern(), resolved.pulsePeriodMs(), resolved.pulseWidthMs(), resolved.priority(), 0, key);
    }

    private static void reset(Player player) {
        lastSpeed = 0.0;
        lastHealth = player == null ? -1.0f : player.getHealth();
        lastDead = false;
        movementInitialized = false;
        stepDistance = 0.0;
        lastMinePulseNanos = 0L;
    }
}