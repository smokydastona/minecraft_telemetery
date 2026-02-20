package com.smoky.bassshakertelemetry.client.integration;

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

/**
 * Minimal dependency-free WebSocket server (RFC6455 subset) for broadcasting text JSON frames.
 *
 * <p>Supports:
 * - HTTP Upgrade handshake
 * - server->client text frames
 * - ping/pong handling
 * - client close detection
 */
public final class SimpleWebSocketServer {
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final int port;
    private final List<Client> clients = new CopyOnWriteArrayList<>();

    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private Thread acceptThread;

    public SimpleWebSocketServer(int port) {
        this.port = port;
    }

    public int port() {
        return port;
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        ServerSocket ss = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
        ss.setReuseAddress(true);
        serverSocket = ss;
        running = true;

        acceptThread = new Thread(this::acceptLoop, "bst-ws-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public synchronized void stop() {
        running = false;

        ServerSocket ss = serverSocket;
        serverSocket = null;
        if (ss != null) {
            try {
                ss.close();
            } catch (IOException ignored) {
            }
        }

        for (Client c : clients) {
            c.close();
        }
        clients.clear();
    }

    public boolean isRunning() {
        return running;
    }

    public int clientCount() {
        return clients.size();
    }

    public void broadcastText(String text) {
        if (!running) {
            return;
        }
        if (text == null) {
            return;
        }

        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        byte[] frame = encodeTextFrame(payload);

        for (Client c : clients) {
            try {
                c.send(frame);
            } catch (IOException e) {
                c.close();
                clients.remove(c);
            }
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                ServerSocket ss = serverSocket;
                if (ss == null) {
                    break;
                }
                Socket socket = ss.accept();
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(0);

                if (!tryHandshake(socket)) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                    continue;
                }

                Client client = new Client(socket);
                clients.add(client);
                client.startReaderThread(() -> clients.remove(client));

            } catch (IOException e) {
                // Expected during stop().
                if (!running) {
                    break;
                }
            }
        }
    }

    private boolean tryHandshake(Socket socket) {
        try {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());

            String request = readHttpHeader(in, 16 * 1024);
            if (request == null || request.isBlank()) {
                return false;
            }

            Map<String, String> headers = HttpHeaderParser.parseHeaders(request);
            String upgrade = header(headers, "upgrade");
            String connection = header(headers, "connection");
            String key = header(headers, "sec-websocket-key");
            String version = header(headers, "sec-websocket-version");

            if (upgrade == null || !upgrade.equalsIgnoreCase("websocket")) {
                return false;
            }
            if (connection == null || !connection.toLowerCase(Locale.ROOT).contains("upgrade")) {
                return false;
            }
            if (key == null || key.isBlank()) {
                return false;
            }
            if (version == null || !version.trim().equals("13")) {
                return false;
            }

            String accept = computeAcceptKey(key.trim());
            String response = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n"
                    + "\r\n";
            out.write(response.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return true;

        } catch (Exception ignored) {
            return false;
        }
    }

    private static String readHttpHeader(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int prev3 = -1, prev2 = -1, prev1 = -1;

        for (int i = 0; i < maxBytes; i++) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            buf.write(b);

            // detect \r\n\r\n
            if (prev3 == '\r' && prev2 == '\n' && prev1 == '\r' && b == '\n') {
                return buf.toString(StandardCharsets.US_ASCII);
            }

            prev3 = prev2;
            prev2 = prev1;
            prev1 = b;
        }

        return null;
    }

    private static String header(Map<String, String> headers, String key) {
        return headers.get(key.toLowerCase(Locale.ROOT));
    }

    private static String computeAcceptKey(String secWebSocketKey) throws Exception {
        String in = secWebSocketKey + WS_GUID;
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] hash = sha1.digest(in.getBytes(StandardCharsets.US_ASCII));
        return Base64.getEncoder().encodeToString(hash);
    }

    private static byte[] encodeTextFrame(byte[] payload) {
        int len = payload.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(len + 14);

        out.write(0x81); // FIN + text

        if (len <= 125) {
            out.write(len);
        } else if (len <= 65535) {
            out.write(126);
            out.write((len >>> 8) & 0xFF);
            out.write(len & 0xFF);
        } else {
            out.write(127);
            // 8-byte length (network order)
            long l = len & 0xFFFFFFFFL;
            out.write(0);
            out.write(0);
            out.write(0);
            out.write(0);
            out.write((int) ((l >>> 24) & 0xFF));
            out.write((int) ((l >>> 16) & 0xFF));
            out.write((int) ((l >>> 8) & 0xFF));
            out.write((int) (l & 0xFF));
        }

        out.writeBytes(payload);
        return out.toByteArray();
    }

    private static final class Client {
        private final Socket socket;
        private final OutputStream out;
        private final InputStream in;
        private Thread reader;

        Client(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new BufferedOutputStream(socket.getOutputStream());
            this.in = new BufferedInputStream(socket.getInputStream());
        }

        void startReaderThread(Runnable onClose) {
            reader = new Thread(() -> readLoop(onClose), "bst-ws-client");
            reader.setDaemon(true);
            reader.start();
        }

        void send(byte[] frame) throws IOException {
            synchronized (out) {
                out.write(frame);
                out.flush();
            }
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }

        private void readLoop(Runnable onClose) {
            try {
                while (!socket.isClosed()) {
                    int b0 = in.read();
                    if (b0 < 0) break;
                    int b1 = in.read();
                    if (b1 < 0) break;

                    int opcode = b0 & 0x0F;
                    boolean masked = (b1 & 0x80) != 0;
                    long len = b1 & 0x7F;

                    if (len == 126) {
                        len = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
                    } else if (len == 127) {
                        // read 8-byte length
                        long l = 0;
                        for (int i = 0; i < 8; i++) {
                            l = (l << 8) | (in.read() & 0xFF);
                        }
                        len = l;
                    }

                    byte[] mask = null;
                    if (masked) {
                        mask = in.readNBytes(4);
                        if (mask.length != 4) break;
                    }

                    if (len > Integer.MAX_VALUE) {
                        break;
                    }

                    byte[] payload = in.readNBytes((int) len);
                    if (payload.length != (int) len) {
                        break;
                    }

                    if (masked && mask != null) {
                        for (int i = 0; i < payload.length; i++) {
                            payload[i] = (byte) (payload[i] ^ mask[i % 4]);
                        }
                    }

                    // close
                    if (opcode == 0x8) {
                        break;
                    }

                    // ping -> pong
                    if (opcode == 0x9) {
                        byte[] pong = encodeControlFrame(0xA, payload);
                        send(pong);
                    }
                }
            } catch (IOException ignored) {
            } finally {
                close();
                if (onClose != null) {
                    onClose.run();
                }
            }
        }

        private static byte[] encodeControlFrame(int opcode, byte[] payload) {
            int len = (payload == null) ? 0 : payload.length;
            ByteArrayOutputStream out = new ByteArrayOutputStream(len + 4);
            out.write(0x80 | (opcode & 0x0F));
            out.write(len);
            if (len > 0) {
                out.writeBytes(payload);
            }
            return out.toByteArray();
        }
    }

    private static final class HttpHeaderParser {
        private HttpHeaderParser() {
        }

        static Map<String, String> parseHeaders(String request) {
            String[] lines = request.split("\\r\\n");
            java.util.HashMap<String, String> out = new java.util.HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (line == null || line.isBlank()) {
                    break;
                }
                int idx = line.indexOf(':');
                if (idx <= 0) continue;
                String k = line.substring(0, idx).trim().toLowerCase(Locale.ROOT);
                String v = line.substring(idx + 1).trim();
                if (!k.isEmpty() && !v.isEmpty()) {
                    out.put(k, v);
                }
            }
            return out;
        }
    }
}
