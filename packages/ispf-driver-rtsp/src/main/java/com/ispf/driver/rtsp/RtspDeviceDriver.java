package com.ispf.driver.rtsp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RTSP/1.0 DESCRIBE client over TCP — clean-room ISPF codec, Apache-2.0.
 * <p>
 * On connect the driver opens TCP and issues an RFC 2326 {@code DESCRIBE} for the configured
 * stream path (default {@code /stream}). Session and Content-Base response headers are retained
 * for diagnostics. This is not an RTP/RTCP media player and not interleaved binary framing.
 * <p>
 * Point mapping (read):
 * <ul>
 *   <li>{@code DESCRIBE}, {@code OPTIONS} — method against the default stream path</li>
 *   <li>{@code DESCRIBE /path}, {@code OPTIONS /path} — method + path</li>
 *   <li>{@code /path} or {@code rtsp://host/path} — DESCRIBE that path</li>
 * </ul>
 * Record fields: {@code status}, {@code cseq}, {@code body}, {@code value}, {@code method},
 * {@code path}, {@code session}, {@code contentBase}.
 * <p>
 * Point mapping (write): {@code TEARDOWN} / {@code SET_PARAMETER} (optional path).
 */
public class RtspDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("rtspValue")
            .field("value", FieldType.STRING)
            .field("body", FieldType.STRING)
            .field("status", FieldType.STRING)
            .field("cseq", FieldType.STRING)
            .field("method", FieldType.STRING)
            .field("path", FieldType.STRING)
            .field("session", FieldType.STRING)
            .field("contentBase", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "rtsp",
            "RTSP Driver",
            "0.1.0",
            "RTSP/1.0 TCP DESCRIBE client with Session/Content-Base parsing "
                    + "(not RTP media, not full session stack)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "554",
                    "timeoutMs", "3000",
                    "streamPath", "/stream"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 554;
    private int timeoutMs = 3000;
    private String streamPath = "/stream";

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private final AtomicInteger nextCseq = new AtomicInteger(1);
    private final Map<String, String> points = new ConcurrentHashMap<>();
    private volatile String session = "";
    private volatile String contentBase = "";
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            case "streamPath", "path" -> streamPath = normalizePath(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(timeoutMs);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            RtspResponse describe = exchange("DESCRIBE", streamPath, null);
            if (describe.statusCode < 200 || describe.statusCode >= 300) {
                throw new DriverException("RTSP DESCRIBE failed: " + describe.statusLine);
            }
            rememberHeaders(describe);
            connected = true;
            driverObject.log(DriverLogLevel.INFO,
                    "RTSP connected to " + host + ":" + port + " (" + describe.statusLine + ")");
        } catch (IOException e) {
            disconnect();
            throw new DriverException("RTSP connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        points.clear();
        session = "";
        contentBase = "";
        closeQuietly(socket);
        socket = null;
        in = null;
        out = null;
    }

    @Override
    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        ensureConnected();
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String mapping = entry.getValue() == null || entry.getValue().isBlank()
                    ? "DESCRIBE" : entry.getValue().trim();
            points.put(pointId, mapping);
            MethodPath mp = parseReadMapping(mapping);
            RtspResponse response = exchange(mp.method, mp.path, null);
            rememberHeaders(response);
            String body = truncate(response.body, 2048);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", body,
                    "body", body,
                    "status", response.statusLine,
                    "cseq", String.valueOf(response.cseq),
                    "method", mp.method,
                    "path", mp.path,
                    "session", session,
                    "contentBase", contentBase
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        String mapping = points.getOrDefault(pointId, pointId);
        MethodPath mp = parseWriteMapping(mapping);
        String body = extractValue(value);
        RtspResponse response = exchange(mp.method, mp.path, "SET_PARAMETER".equals(mp.method) ? body : null);
        rememberHeaders(response);
        String excerpt = truncate(response.body.isEmpty() ? body : response.body, 2048);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", excerpt,
                "body", excerpt,
                "status", response.statusLine,
                "cseq", String.valueOf(response.cseq),
                "method", mp.method,
                "path", mp.path,
                "session", session,
                "contentBase", contentBase
        )));
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw new DriverException("RTSP " + mp.method + " failed: " + response.statusLine);
        }
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }

    private MethodPath parseReadMapping(String mapping) {
        String upper = mapping.toUpperCase(Locale.ROOT);
        if (upper.equals("OPTIONS") || upper.equals("DESCRIBE")) {
            return new MethodPath(upper, streamPath);
        }
        if (upper.startsWith("OPTIONS ") || upper.startsWith("DESCRIBE ")) {
            int space = mapping.indexOf(' ');
            return new MethodPath(mapping.substring(0, space).trim().toUpperCase(Locale.ROOT),
                    normalizePath(mapping.substring(space + 1).trim()));
        }
        return new MethodPath("DESCRIBE", normalizePath(mapping));
    }

    private MethodPath parseWriteMapping(String mapping) {
        String upper = mapping.toUpperCase(Locale.ROOT);
        if (upper.equals("TEARDOWN") || upper.equals("SET_PARAMETER")) {
            return new MethodPath(upper, streamPath);
        }
        if (upper.startsWith("TEARDOWN ") || upper.startsWith("SET_PARAMETER ")) {
            int space = mapping.indexOf(' ');
            return new MethodPath(mapping.substring(0, space).trim().toUpperCase(Locale.ROOT),
                    normalizePath(mapping.substring(space + 1).trim()));
        }
        return new MethodPath("SET_PARAMETER", normalizePath(mapping));
    }

    /**
     * Builds an RTSP/1.0 request without port in the URI and without extra headers.
     * Default DESCRIBE for {@code 127.0.0.1/stream} with CSeq 1 is exactly:
     * {@code DESCRIBE rtsp://127.0.0.1/stream RTSP/1.0\r\nCSeq: 1\r\n\r\n}.
     */
    static String buildRequest(String method, String host, String path, int cseq, String body) {
        String requestUri = "rtsp://" + host + path;
        StringBuilder req = new StringBuilder();
        req.append(method).append(' ').append(requestUri).append(" RTSP/1.0\r\n");
        req.append("CSeq: ").append(cseq).append("\r\n");
        if (body != null) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            req.append("Content-Type: text/parameters\r\n");
            req.append("Content-Length: ").append(bytes.length).append("\r\n");
            req.append("\r\n");
            req.append(body);
        } else {
            req.append("\r\n");
        }
        return req.toString();
    }

    private RtspResponse exchange(String method, String path, String body) throws DriverException {
        int cseq = nextCseq.getAndIncrement();
        String request = buildRequest(method, host, path, cseq, body);
        try {
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return readResponse(cseq);
        } catch (IOException e) {
            connected = false;
            throw new DriverException("RTSP I/O failed for " + method + " " + path, e);
        }
    }

    private void rememberHeaders(RtspResponse response) {
        String sess = response.headers.get("session");
        if (sess != null && !sess.isBlank()) {
            int semi = sess.indexOf(';');
            session = semi >= 0 ? sess.substring(0, semi).trim() : sess.trim();
        }
        String base = response.headers.get("content-base");
        if (base != null && !base.isBlank()) {
            contentBase = base.trim();
        }
    }

    private RtspResponse readResponse(int expectedCseq) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        String statusLine = readLine(in);
        if (statusLine == null || statusLine.isBlank()) {
            throw new IOException("EOF reading RTSP status line");
        }
        while (true) {
            String line = readLine(in);
            if (line == null) {
                throw new IOException("EOF reading RTSP headers");
            }
            if (line.isEmpty()) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).trim());
            }
        }
        int contentLength = 0;
        String cl = headers.get("content-length");
        if (cl != null && !cl.isBlank()) {
            contentLength = Integer.parseInt(cl.trim());
        }
        String body = "";
        if (contentLength > 0) {
            byte[] buf = in.readNBytes(contentLength);
            if (buf.length < contentLength) {
                throw new IOException("Truncated RTSP body");
            }
            body = new String(buf, StandardCharsets.UTF_8);
        }
        int statusCode = 0;
        String[] parts = statusLine.split("\\s+", 3);
        if (parts.length >= 2) {
            try {
                statusCode = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        int cseq = expectedCseq;
        String cseqHeader = headers.get("cseq");
        if (cseqHeader != null) {
            try {
                cseq = Integer.parseInt(cseqHeader.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return new RtspResponse(statusLine, statusCode, cseq, headers, body);
    }

    static String readLine(InputStream in) throws IOException {
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
        return line.toString(StandardCharsets.UTF_8);
    }

    private static String normalizePath(String raw) {
        String value = raw.trim();
        if (value.toLowerCase(Locale.ROOT).startsWith("rtsp://")) {
            int scheme = value.indexOf("://");
            int pathStart = value.indexOf('/', scheme + 3);
            if (pathStart < 0) {
                return "/";
            }
            value = value.substring(pathStart);
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        return value;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    private static String extractValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "body", "payload", "data", "text", "raw")) {
            Object candidate = row.get(key);
            if (candidate != null) {
                return String.valueOf(candidate);
            }
        }
        if (row.size() == 1) {
            return String.valueOf(row.values().iterator().next());
        }
        return row.toString();
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private record MethodPath(String method, String path) {
    }

    private static final class RtspResponse {
        final String statusLine;
        final int statusCode;
        final int cseq;
        final Map<String, String> headers;
        final String body;

        RtspResponse(String statusLine, int statusCode, int cseq, Map<String, String> headers, String body) {
            this.statusLine = statusLine;
            this.statusCode = statusCode;
            this.cseq = cseq;
            this.headers = headers;
            this.body = body;
        }
    }
}
