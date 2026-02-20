package com.smoky.bassshakertelemetry.client.integration;

import com.smoky.bassshakertelemetry.config.BstConfig;
import com.smoky.bassshakertelemetry.telemetryout.TelemetryOut;
import com.smoky.bassshakertelemetry.telemetryout.TelemetryOutSink;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client-side controller for the built-in WebSocket telemetry server.
 *
 * <p>Runs a simple poll on client tick to start/stop/restart the server based on config.
 */
public final class WebSocketTelemetryController {
    private volatile SimpleWebSocketServer server;
    private volatile int runningPort = -1;

    private final TelemetryOutSink sink = message -> {
        SimpleWebSocketServer s = server;
        if (s == null || !s.isRunning()) {
            return;
        }
        s.broadcastText(message);
    };

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        var cfg = BstConfig.get();
        boolean enabled = cfg.webSocketEnabled && cfg.enabled();
        int port = cfg.webSocketPort;

        if (!enabled) {
            stopServerIfRunning();
            TelemetryOut.setSink(TelemetryOutSink.NOOP);
            return;
        }

        if (port <= 0 || port > 65535) {
            port = 7117;
        }

        if (server == null || !server.isRunning() || runningPort != port) {
            stopServerIfRunning();
            try {
                SimpleWebSocketServer s = new SimpleWebSocketServer(port);
                s.start();
                server = s;
                runningPort = port;
            } catch (Exception ignored) {
                server = null;
                runningPort = -1;
                TelemetryOut.setSink(TelemetryOutSink.NOOP);
                return;
            }
        }

        TelemetryOut.setSink(sink);
    }

    private void stopServerIfRunning() {
        SimpleWebSocketServer s = server;
        server = null;
        runningPort = -1;
        if (s != null) {
            s.stop();
        }
    }
}
