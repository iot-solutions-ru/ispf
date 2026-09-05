package com.ispf.driver.websocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal RFC6455 WebSocket client: handshake plus masked text and close frames.
 * Clean-room ISPF code (JDK only) — no Jetty/Netty/Tyrus/OkHttp.
 */
final class Rfc6455Client implements AutoCloseable {

    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int OPCODE_TEXT = 0x1;
    private static final int OPCODE_CLOSE = 0x8;
    private static final int OPCODE_PING = 0x9;
    private static final int OPCODE_PONG = 0xA;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final SecureRandom random = new SecureRandom();
    private volatile boolean open;

    private Rfc6455Client(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.open = true;
    }

    static Rfc6455Client connect(String host, int port, String path, int timeoutMs) throws IOException {
        String requestPath = normalizePath(path);
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        socket.setTcpNoDelay(true);

        byte[] keyBytes = new byte[16];
        new SecureRandom().nextBytes(keyBytes);
        String secKey = Base64.getEncoder().encodeToString(keyBytes);

        String handshake = "GET " + requestPath + " HTTP/1.1\r\n"
                + "Host: " + host + ":" + port + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + secKey + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(handshake.getBytes(StandardCharsets.US_ASCII));
        out.flush();

        InputStream in = socket.getInputStream();
        String statusLine = readHttpLine(in);
        if (statusLine == null || !statusLine.toUpperCase(Locale.ROOT).contains("101")) {
            socket.close();
            throw new IOException("WebSocket handshake failed: " + statusLine);
        }
        String acceptHeader = null;
        while (true) {
            String line = readHttpLine(in);
            if (line == null || line.isEmpty()) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if ("Sec-WebSocket-Accept".equalsIgnoreCase(name)) {
                    acceptHeader = value;
                }
            }
        }
        String expected = acceptKey(secKey);
        if (acceptHeader == null || !expected.equals(acceptHeader)) {
            socket.close();
            throw new IOException("Invalid Sec-WebSocket-Accept");
        }
        return new Rfc6455Client(socket);
    }

    boolean isOpen() {
        return open && !socket.isClosed();
    }

    synchronized void sendText(String text) throws IOException {
        ensureOpen();
        writeFrame(OPCODE_TEXT, text.getBytes(StandardCharsets.UTF_8), true);
    }

    synchronized String readText() throws IOException {
        ensureOpen();
        while (true) {
            Frame frame = readFrame();
            switch (frame.opcode) {
                case OPCODE_TEXT -> {
                    return new String(frame.payload, StandardCharsets.UTF_8);
                }
                case OPCODE_PING -> writeFrame(OPCODE_PONG, frame.payload, true);
                case OPCODE_PONG -> { }
                case OPCODE_CLOSE -> {
                    open = false;
                    try {
                        writeFrame(OPCODE_CLOSE, frame.payload, true);
                    } catch (IOException ignored) {
                        // peer already closing
                    }
                    throw new IOException("WebSocket closed by peer");
                }
                default -> throw new IOException("Unsupported WebSocket opcode: " + frame.opcode);
            }
        }
    }

    @Override
    public synchronized void close() {
        if (!open) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // already closed
            }
            return;
        }
        open = false;
        try {
            writeFrame(OPCODE_CLOSE, new byte[0], true);
        } catch (IOException ignored) {
            // best-effort close
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    private void ensureOpen() throws IOException {
        if (!isOpen()) {
            throw new IOException("WebSocket is closed");
        }
    }

    private void writeFrame(int opcode, byte[] payload, boolean mask) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(payload.length + 14);
        buf.write(0x80 | (opcode & 0x0F));
        int len = payload.length;
        int maskBit = mask ? 0x80 : 0;
        if (len < 126) {
            buf.write(maskBit | len);
        } else if (len <= 0xFFFF) {
            buf.write(maskBit | 126);
            buf.write((len >>> 8) & 0xFF);
            buf.write(len & 0xFF);
        } else {
            buf.write(maskBit | 127);
            for (int i = 7; i >= 0; i--) {
                buf.write((int) ((len >>> (8 * i)) & 0xFF));
            }
        }
        byte[] maskKey = null;
        if (mask) {
            maskKey = new byte[4];
            random.nextBytes(maskKey);
            buf.write(maskKey);
        }
        if (maskKey != null) {
            byte[] masked = new byte[payload.length];
            for (int i = 0; i < payload.length; i++) {
                masked[i] = (byte) (payload[i] ^ maskKey[i % 4]);
            }
            buf.writeBytes(masked);
        } else {
            buf.writeBytes(payload);
        }
        out.write(buf.toByteArray());
        out.flush();
    }

    private Frame readFrame() throws IOException {
        int b0 = in.read();
        if (b0 < 0) {
            open = false;
            throw new IOException("EOF reading WebSocket frame");
        }
        int opcode = b0 & 0x0F;
        int b1 = in.read();
        if (b1 < 0) {
            throw new IOException("EOF reading WebSocket frame length");
        }
        boolean masked = (b1 & 0x80) != 0;
        long len = b1 & 0x7F;
        if (len == 126) {
            len = readUnsignedShort();
        } else if (len == 127) {
            len = readUnsignedLong();
        }
        if (len > Integer.MAX_VALUE) {
            throw new IOException("WebSocket frame too large");
        }
        byte[] maskKey = null;
        if (masked) {
            maskKey = in.readNBytes(4);
            if (maskKey.length < 4) {
                throw new IOException("Truncated mask key");
            }
        }
        byte[] payload = in.readNBytes((int) len);
        if (payload.length < len) {
            throw new IOException("Truncated WebSocket payload");
        }
        if (maskKey != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ maskKey[i % 4]);
            }
        }
        return new Frame(opcode, payload);
    }

    private int readUnsignedShort() throws IOException {
        int hi = in.read();
        int lo = in.read();
        if (hi < 0 || lo < 0) {
            throw new IOException("EOF reading extended length");
        }
        return (hi << 8) | lo;
    }

    private long readUnsignedLong() throws IOException {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            int b = in.read();
            if (b < 0) {
                throw new IOException("EOF reading 64-bit length");
            }
            value = (value << 8) | b;
        }
        return value;
    }

    static String acceptKey(String secKey) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((secKey + GUID).getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    static String getRequest(String point) {
        return "{\"op\":\"get\",\"point\":\"" + escapeJson(point) + "\"}";
    }

    static String setRequest(String point, String value) {
        return "{\"op\":\"set\",\"point\":\"" + escapeJson(point) + "\",\"value\":\"" + escapeJson(value) + "\"}";
    }

    static String extractJsonField(String json, String field) {
        if (json == null || field == null || field.isBlank()) {
            return null;
        }
        String needle = "\"" + field + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length()) {
            return null;
        }
        char start = json.charAt(i);
        if (start == '"') {
            StringBuilder sb = new StringBuilder();
            i++;
            while (i < json.length()) {
                char c = json.charAt(i++);
                if (c == '\\' && i < json.length()) {
                    sb.append(json.charAt(i++));
                    continue;
                }
                if (c == '"') {
                    break;
                }
                sb.append(c);
            }
            return sb.toString();
        }
        int end = i;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                break;
            }
            end++;
        }
        return json.substring(i, end);
    }

    static String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String readHttpLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int ch = in.read();
            if (ch < 0) {
                if (line.size() == 0) {
                    return null;
                }
                break;
            }
            if (ch == '\n') {
                break;
            }
            if (ch != '\r') {
                line.write(ch);
            }
        }
        return line.toString(StandardCharsets.US_ASCII);
    }

    private record Frame(int opcode, byte[] payload) {
    }

    /** Server-side helpers used by loopback tests (unmasked frames). */
    static final class ServerHelpers {
        private ServerHelpers() {
        }

        static void writeUnmaskedText(OutputStream out, String text) throws IOException {
            byte[] payload = text.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream buf = new ByteArrayOutputStream(payload.length + 10);
            buf.write(0x80 | OPCODE_TEXT);
            if (payload.length < 126) {
                buf.write(payload.length);
            } else if (payload.length <= 0xFFFF) {
                buf.write(126);
                buf.write((payload.length >>> 8) & 0xFF);
                buf.write(payload.length & 0xFF);
            } else {
                throw new IOException("Frame too large for test helper");
            }
            buf.writeBytes(payload);
            out.write(buf.toByteArray());
            out.flush();
        }

        static String readMaskedText(InputStream in) throws IOException {
            int b0 = in.read();
            if (b0 < 0) {
                throw new IOException("EOF");
            }
            int opcode = b0 & 0x0F;
            int b1 = in.read();
            if (b1 < 0) {
                throw new IOException("EOF length");
            }
            boolean masked = (b1 & 0x80) != 0;
            int len = b1 & 0x7F;
            if (len == 126) {
                int hi = in.read();
                int lo = in.read();
                len = (hi << 8) | lo;
            } else if (len == 127) {
                throw new IOException("64-bit frames not supported in test helper");
            }
            byte[] mask = masked ? in.readNBytes(4) : null;
            byte[] payload = in.readNBytes(len);
            if (mask != null) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] = (byte) (payload[i] ^ mask[i % 4]);
                }
            }
            if (opcode == OPCODE_CLOSE) {
                throw new IOException("client close");
            }
            if (opcode == OPCODE_PING) {
                return readMaskedText(in);
            }
            if (opcode != OPCODE_TEXT) {
                throw new IOException("expected text, got opcode " + opcode);
            }
            return new String(payload, StandardCharsets.UTF_8);
        }

        static void completeHandshake(InputStream in, OutputStream out, Map<String, String> requestHeaders)
                throws IOException {
            String status = readHttpLine(in);
            if (status == null || !status.startsWith("GET ")) {
                throw new IOException("Expected GET upgrade, got: " + status);
            }
            String secKey = null;
            while (true) {
                String line = readHttpLine(in);
                if (line == null || line.isEmpty()) {
                    break;
                }
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String name = line.substring(0, colon).trim();
                    String value = line.substring(colon + 1).trim();
                    requestHeaders.put(name.toLowerCase(Locale.ROOT), value);
                    if ("Sec-WebSocket-Key".equalsIgnoreCase(name)) {
                        secKey = value;
                    }
                }
            }
            if (secKey == null) {
                throw new IOException("Missing Sec-WebSocket-Key");
            }
            String accept = acceptKey(secKey);
            String response = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n"
                    + "\r\n";
            out.write(response.getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }
    }
}
