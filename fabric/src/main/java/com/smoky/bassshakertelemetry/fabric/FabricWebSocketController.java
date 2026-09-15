package com.smoky.bassshakertelemetry.fabric;

import com.smoky.bassshakertelemetry.telemetryout.SimpleWebSocketServer;
import com.smoky.bassshakertelemetry.telemetryout.TelemetryOut;
import com.smoky.bassshakertelemetry.telemetryout.TelemetryOutSink;

/** Owns the optional loopback telemetry server on the Fabric client thread. */
final class FabricWebSocketController {
    private SimpleWebSocketServer server;
    private int runningPort = -1;

    private final TelemetryOutSink sink = message -> {
        SimpleWebSocketServer active = server;
        if (active != null && active.isRunning()) active.broadcastText(message);
    };

    void tick() {
        var config = com.smoky.bassshakertelemetry.config.BstConfig.get();
        if (!config.enabled || !config.webSocketEnabled) {
            stop();
            return;
        }

        int port = config.webSocketPort;
        if (port < 1 || port > 65535) port = 7117;
        if (server == null || !server.isRunning() || runningPort != port) {
            stop();
            try {
                server = new SimpleWebSocketServer(port);
                server.start();
                runningPort = port;
            } catch (Exception error) {
                BassShakerTelemetryFabric.LOGGER.warn("Unable to start Fabric telemetry WebSocket on {}", port, error);
                server = null;
                runningPort = -1;
                TelemetryOut.setSink(TelemetryOutSink.NOOP);
                return;
            }
        }
        TelemetryOut.setSink(sink);
    }

    void stop() {
        TelemetryOut.setSink(TelemetryOutSink.NOOP);
        SimpleWebSocketServer active = server;
        server = null;
        runningPort = -1;
        if (active != null) active.stop();
    }
}