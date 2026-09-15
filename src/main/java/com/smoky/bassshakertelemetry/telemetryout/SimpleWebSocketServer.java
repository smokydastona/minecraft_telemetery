package com.smoky.bassshakertelemetry.telemetryout;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Loopback-only RFC 6455 text broadcaster used by the optional telemetry output. */
public final class SimpleWebSocketServer {
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_CLIENTS = 8;
    private static final int MAX_HTTP_HEADER_BYTES = 16 * 1024;
    private static final int MAX_FRAME_BYTES = 1024 * 1024;

    private final int port;
    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private Thread acceptThread;

    public SimpleWebSocketServer(int port) {
        this.port = port;
    }

    public synchronized void start() throws IOException {
        if (running) return;
        ServerSocket socket = new ServerSocket(port, MAX_CLIENTS, InetAddress.getLoopbackAddress());
        socket.setReuseAddress(true);
        serverSocket = socket;
        running = true;
        acceptThread = new Thread(this::acceptLoop, "bst-ws-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public synchronized void stop() {
        running = false;
        ServerSocket socket = serverSocket;
        serverSocket = null;
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { }
        }
        for (Client client : clients) client.close();
        clients.clear();
    }

    public boolean isRunning() { return running; }
    public int clientCount() { return clients.size(); }

    public void broadcastText(String text) {
        if (!running || text == null) return;
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_FRAME_BYTES) return;
        byte[] frame = encodeFrame(0x1, payload);
        for (Client client : clients) {
            try { client.send(frame); }
            catch (IOException error) { client.close(); clients.remove(client); }
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                ServerSocket socket = serverSocket;
                if (socket == null) return;
                Socket clientSocket = socket.accept();
                clientSocket.setTcpNoDelay(true);
                if (clients.size() >= MAX_CLIENTS || !handshake(clientSocket)) {
                    clientSocket.close();
                    continue;
                }
                Client client = new Client(clientSocket);
                clients.add(client);
                client.startReader(() -> clients.remove(client));
            } catch (IOException error) {
                if (!running) return;
            }
        }
    }

    private static boolean handshake(Socket socket) {
        try {
            InputStream input = new BufferedInputStream(socket.getInputStream());
            OutputStream output = new BufferedOutputStream(socket.getOutputStream());
            String request = readHeader(input);
            if (request == null) return false;
            Map<String, String> headers = parseHeaders(request);
            if (!"websocket".equalsIgnoreCase(headers.get("upgrade"))
                    || !headers.getOrDefault("connection", "").toLowerCase(Locale.ROOT).contains("upgrade")
                    || !"13".equals(headers.get("sec-websocket-version"))) return false;
            String key = headers.get("sec-websocket-key");
            if (key == null || key.isBlank()) return false;
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            String accept = Base64.getEncoder().encodeToString(digest.digest(
                    (key.trim() + WS_GUID).getBytes(StandardCharsets.US_ASCII)));
            output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n"
                    + "Connection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            output.flush();
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private static String readHeader(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int previous3 = -1, previous2 = -1, previous1 = -1;
        for (int i = 0; i < MAX_HTTP_HEADER_BYTES; i++) {
            int value = input.read();
            if (value < 0) return null;
            bytes.write(value);
            if (previous3 == '\r' && previous2 == '\n' && previous1 == '\r' && value == '\n') {
                return bytes.toString(StandardCharsets.US_ASCII);
            }
            previous3 = previous2;
            previous2 = previous1;
            previous1 = value;
        }
        return null;
    }

    private static Map<String, String> parseHeaders(String request) {
        java.util.HashMap<String, String> headers = new java.util.HashMap<>();
        for (String line : request.split("\\r\\n")) {
            int separator = line.indexOf(':');
            if (separator > 0) headers.put(line.substring(0, separator).trim().toLowerCase(Locale.ROOT),
                    line.substring(separator + 1).trim());
        }
        return headers;
    }

    private static byte[] encodeFrame(int opcode, byte[] payload) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(payload.length + 10);
        output.write(0x80 | (opcode & 0x0f));
        if (payload.length <= 125) output.write(payload.length);
        else {
            output.write(126);
            output.write((payload.length >>> 8) & 0xff);
            output.write(payload.length & 0xff);
        }
        output.writeBytes(payload);
        return output.toByteArray();
    }

    private static final class Client {
        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;

        private Client(Socket socket) throws IOException {
            this.socket = socket;
            input = new BufferedInputStream(socket.getInputStream());
            output = new BufferedOutputStream(socket.getOutputStream());
        }

        private void startReader(Runnable onClose) {
            Thread reader = new Thread(() -> readLoop(onClose), "bst-ws-client");
            reader.setDaemon(true);
            reader.start();
        }

        private void send(byte[] frame) throws IOException {
            synchronized (output) { output.write(frame); output.flush(); }
        }

        private void close() {
            try { socket.close(); } catch (IOException ignored) { }
        }

        private void readLoop(Runnable onClose) {
            try {
                while (!socket.isClosed()) {
                    int first = input.read();
                    int second = input.read();
                    if (first < 0 || second < 0) break;
                    int opcode = first & 0x0f;
                    long length = second & 0x7f;
                    if (length == 126) length = (input.read() << 8) | input.read();
                    else if (length == 127) { for (int i = 0; i < 8; i++) input.read(); break; }
                    if (length > MAX_FRAME_BYTES) break;
                    boolean masked = (second & 0x80) != 0;
                    byte[] mask = masked ? input.readNBytes(4) : new byte[0];
                    if (masked && mask.length != 4) break;
                    byte[] payload = input.readNBytes((int) length);
                    if (payload.length != (int) length) break;
                    if (masked) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
                    if (opcode == 0x8) break;
                    if (opcode == 0x9) send(encodeFrame(0xA, payload));
                }
            } catch (IOException ignored) {
            } finally {
                close();
                if (onClose != null) onClose.run();
            }
        }
    }
}