package com.ispf.driver.stomp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

/**
 * RFC 6455 client used as the STOMP WebSocket transport. Text frames only.
 * A {@code caCertPath} replaces the default trust anchors with those certificates.
 * The TLS hostname is still checked against the certificate.
 * Clean-room JDK sockets, no third-party WebSocket library.
 */
final class StompWebSocket implements AutoCloseable {

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

    private StompWebSocket(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.open = true;
    }

    record SocketSecurity(boolean tls, String caCertPath) {
    }

    static StompWebSocket connect(
            String host,
            int port,
            String path,
            int timeoutMs,
            SocketSecurity security,
            Map<String, String> httpHeaders
    ) throws IOException {
        String requestPath = normalizePath(path);
        Socket socket = openSocket(host, port, timeoutMs, security);
        byte[] keyBytes = new byte[16];
        new SecureRandom().nextBytes(keyBytes);
        String secKey = Base64.getEncoder().encodeToString(keyBytes);
        String handshake = "GET " + requestPath + " HTTP/1.1\r\n"
                + "Host: " + hostHeader(host, port, security.tls()) + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + secKey + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + extraHeaders(httpHeaders)
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
            if (colon > 0 && "Sec-WebSocket-Accept".equalsIgnoreCase(line.substring(0, colon).trim())) {
                acceptHeader = line.substring(colon + 1).trim();
            }
        }
        if (acceptHeader == null || !acceptKey(secKey).equals(acceptHeader)) {
            socket.close();
            throw new IOException("Invalid Sec-WebSocket-Accept");
        }
        return new StompWebSocket(socket);
    }

    boolean isOpen() {
        return open && !socket.isClosed();
    }

    void setSoTimeout(int timeoutMs) throws IOException {
        socket.setSoTimeout(timeoutMs);
    }

    synchronized void send(byte[] payload) throws IOException {
        if (!isOpen()) {
            throw new IOException("WebSocket is closed");
        }
        writeFrame(OPCODE_TEXT, payload, true);
    }

    synchronized byte[] read() throws IOException {
        if (!isOpen()) {
            throw new IOException("WebSocket is closed");
        }
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        boolean collecting = false;
        while (true) {
            Frame frame = readFrame();
            switch (frame.opcode) {
                case OPCODE_TEXT, 0 -> {
                    if (frame.opcode == 0 && !collecting) {
                        throw new IOException("Unexpected WebSocket continuation frame");
                    }
                    if (frame.opcode == OPCODE_TEXT && collecting) {
                        throw new IOException("Unexpected WebSocket text frame inside a fragmented message");
                    }
                    message.writeBytes(frame.payload);
                    collecting = true;
                    if (frame.fin) {
                        return message.toByteArray();
                    }
                }
                case OPCODE_PING -> writeFrame(OPCODE_PONG, frame.payload, true);
                case OPCODE_PONG -> { }
                case OPCODE_CLOSE -> {
                    open = false;
                    throw new IOException("WebSocket closed by peer");
                }
                default -> throw new IOException("Unsupported WebSocket opcode: " + frame.opcode);
            }
        }
    }

    @Override
    public synchronized void close() {
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

    private static String hostHeader(String host, int port, boolean tls) {
        if (tls && port == 443) {
            return host;
        }
        return host + ":" + port;
    }

    static javax.net.ssl.SSLContext sslContext(SocketSecurity security) throws IOException {
        try {
            return clientContext(security.caCertPath());
        } catch (GeneralSecurityException e) {
            throw new IOException("WebSocket TLS context failed", e);
        }
    }

    private static String extraHeaders(Map<String, String> httpHeaders) {
        if (httpHeaders == null || httpHeaders.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> header : httpHeaders.entrySet()) {
            String name = header.getKey() == null ? "" : header.getKey();
            String value = header.getValue() == null ? "" : header.getValue();
            if (name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0 || name.indexOf(':') >= 0
                    || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                throw new IllegalArgumentException("WebSocket header contains a line break");
            }
            out.append(name).append(": ").append(value).append("\r\n");
        }
        return out.toString();
    }

    private static Socket openSocket(String host, int port, int timeoutMs, SocketSecurity security) throws IOException {
        Socket socket;
        if (security.tls()) {
            SSLSocket ssl;
            try {
                ssl = (SSLSocket) clientContext(security.caCertPath()).getSocketFactory().createSocket();
            } catch (GeneralSecurityException e) {
                throw new IOException("WebSocket TLS context failed", e);
            }
            ssl.connect(new InetSocketAddress(host, port), timeoutMs);
            requireHttpsEndpoint(ssl);
            ssl.startHandshake();
            socket = ssl;
        } else {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
        }
        socket.setSoTimeout(timeoutMs);
        socket.setTcpNoDelay(true);
        return socket;
    }

    static void requireHttpsEndpoint(SSLSocket ssl) {
        SSLParameters parameters = ssl.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        ssl.setSSLParameters(parameters);
    }

    private static SSLContext clientContext(String caCertPath) throws GeneralSecurityException, IOException {
        if (caCertPath == null || caCertPath.isBlank()) {
            return SSLContext.getDefault();
        }
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> extra;
        try (InputStream in = Files.newInputStream(Path.of(caCertPath))) {
            extra = factory.generateCertificates(in);
        }
        if (extra.isEmpty()) {
            throw new IOException("No certificates in " + caCertPath);
        }
        KeyStore extraStore = KeyStore.getInstance(KeyStore.getDefaultType());
        extraStore.load(null, null);
        int index = 0;
        for (Certificate certificate : extra) {
            extraStore.setCertificateEntry("ca-" + index, certificate);
            index++;
        }
        TrustManagerFactory extraFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        extraFactory.init(extraStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, extraFactory.getTrustManagers(), null);
        return context;
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
        if (mask) {
            byte[] maskKey = new byte[4];
            random.nextBytes(maskKey);
            buf.write(maskKey);
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
        boolean fin = (b0 & 0x80) != 0;
        int opcode = b0 & 0x0F;
        int b1 = in.read();
        if (b1 < 0) {
            throw new IOException("EOF reading WebSocket frame length");
        }
        boolean masked = (b1 & 0x80) != 0;
        long len = b1 & 0x7F;
        if (len == 126) {
            len = readUnsigned(2);
        } else if (len == 127) {
            len = readUnsigned(8);
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
        return new Frame(fin, opcode, payload);
    }

    private long readUnsigned(int bytes) throws IOException {
        long value = 0;
        for (int i = 0; i < bytes; i++) {
            int b = in.read();
            if (b < 0) {
                throw new IOException("EOF reading WebSocket length");
            }
            value = (value << 8) | b;
        }
        return value;
    }

    private static String readHttpLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                return line.size() == 0 ? null : line.toString(StandardCharsets.US_ASCII);
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                line.write(b);
            }
        }
        return line.toString(StandardCharsets.US_ASCII);
    }

    private record Frame(boolean fin, int opcode, byte[] payload) {
    }
}
