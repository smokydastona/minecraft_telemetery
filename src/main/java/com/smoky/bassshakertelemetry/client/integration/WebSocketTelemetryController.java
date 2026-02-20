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
    private static volatile boolean lastEnabled;
    private static volatile boolean lastRunning;
    private static volatile int lastPort;
    private static volatile int lastClientCount;
    private static volatile String lastError;

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

        lastEnabled = enabled;
        lastPort = port;

        if (!enabled) {
            stopServerIfRunning();
            TelemetryOut.setSink(TelemetryOutSink.NOOP);

            lastRunning = false;
            lastClientCount = 0;
            lastError = "";
            return;
        }

        if (port <= 0 || port > 65535) {
            port = 7117;
        }

        lastPort = port;

        if (server == null || !server.isRunning() || runningPort != port) {
            stopServerIfRunning();
            try {
                SimpleWebSocketServer s = new SimpleWebSocketServer(port);
                s.start();
                server = s;
                runningPort = port;
                lastError = "";
            } catch (Exception ignored) {
                server = null;
                runningPort = -1;
                TelemetryOut.setSink(TelemetryOutSink.NOOP);

                lastRunning = false;
                lastClientCount = 0;
                lastError = ignored.getClass().getSimpleName();
                return;
            }
        }

        TelemetryOut.setSink(sink);

        SimpleWebSocketServer s = server;
        lastRunning = (s != null && s.isRunning());
        lastClientCount = (s == null) ? 0 : s.clientCount();
    }

    public static String getOverlayStatusLine() {
        if (!lastEnabled) {
            return "ws=off";
        }
        if (!lastRunning) {
            String err = (lastError == null || lastError.isBlank()) ? "" : (" err=" + lastError);
            return "ws=failed port=" + lastPort + err;
        }

        var cfg = BstConfig.get();
        return "ws=127.0.0.1:" + lastPort
                + " clients=" + lastClientCount
                + " telem=" + (cfg.webSocketSendTelemetry ? "1" : "0")
                + " hapt=" + (cfg.webSocketSendHapticEvents ? "1" : "0");
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
