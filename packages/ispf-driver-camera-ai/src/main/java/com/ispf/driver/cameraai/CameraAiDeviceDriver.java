package com.ispf.driver.cameraai;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Camera AI edge driver — HTTP/1.1 lab client for an on-prem inference endpoint.
 * <p>
 * Point mapping is an inference route (for example {@code /infer}, {@code detect}, or
 * {@code POST /v1/detect}). {@code readPoints} issues {@code GET} (or mapped method) and stores
 * the response body in {@code value} plus {@code status}/{@code path}. {@code writePoint} issues
 * {@code POST} with the record {@code value} as the request body (JSON/text lab payload).
 * <p>
 * Clean-room ISPF lab codec, Apache-2.0 — JDK sockets only. Not OpenCV/ONNX/vendor camera SDKs;
 * not a full HTTP client stack (no TLS/chunked/redirects in this lab dialect).
 */
public class CameraAiDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("cameraAiValue")
            .field("value", FieldType.STRING)
            .field("status", FieldType.STRING)
            .field("path", FieldType.STRING)
            .field("method", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "camera-ai",
            "Camera AI edge Driver",
            "0.1.0",
            "HTTP/1.1 lab client for edge vision/AI inference endpoints (GET read / POST write)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "8080",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 8080;
    private int timeoutMs = 3000;
    private final Map<String, String> routes = new ConcurrentHashMap<>();
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
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Camera AI HTTP lab ready for " + host + ":" + port);
    }

    @Override
    public void disconnect() {
        connected = false;
        routes.clear();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        ensureConnected();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String mapping = entry.getValue() == null || entry.getValue().isBlank() ? "/infer" : entry.getValue().trim();
            routes.put(pointId, mapping);
            ParsedRoute route = ParsedRoute.parse(mapping, "GET");
            HttpResponse response = exchange(route.method(), route.path(), null);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", response.body(),
                    "status", Integer.toString(response.status()),
                    "path", route.path(),
                    "method", route.method()
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        String mapping = routes.getOrDefault(pointId, pointId);
        ParsedRoute route = ParsedRoute.parse(mapping, "POST");
        String body = extractValue(value);
        HttpResponse response = exchange("POST", route.path(), body);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", response.body().isBlank() ? body : response.body(),
                "status", Integer.toString(response.status()),
                "path", route.path(),
                "method", "POST"
        )));
    }

    private HttpResponse exchange(String method, String path, String body) throws DriverException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            StringBuilder req = new StringBuilder();
            req.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
            req.append("Host: ").append(host).append(':').append(port).append("\r\n");
            req.append("Connection: close\r\n");
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                req.append("Content-Type: application/json\r\n");
                req.append("Content-Length: ").append(bytes.length).append("\r\n\r\n");
                out.write(req.toString().getBytes(StandardCharsets.US_ASCII));
                out.write(bytes);
            } else {
                req.append("\r\n");
                out.write(req.toString().getBytes(StandardCharsets.US_ASCII));
            }
            out.flush();
            return HttpResponse.parse(readAll(in));
        } catch (IOException e) {
            throw new DriverException("Camera AI HTTP exchange failed for " + host + ":" + port + path, e);
        }
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }

    private static String extractValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Object raw = value.firstRow().get("value");
        return raw == null ? "" : String.valueOf(raw);
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int n;
        while ((n = in.read(chunk)) >= 0) {
            buf.write(chunk, 0, n);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    record ParsedRoute(String method, String path) {
        static ParsedRoute parse(String mapping, String defaultMethod) {
            String trimmed = mapping.trim();
            String upper = trimmed.toUpperCase(Locale.ROOT);
            if (upper.startsWith("GET ") || upper.startsWith("POST ")) {
                int sp = trimmed.indexOf(' ');
                return new ParsedRoute(trimmed.substring(0, sp).toUpperCase(Locale.ROOT), normalizePath(trimmed.substring(sp + 1).trim()));
            }
            return new ParsedRoute(defaultMethod, normalizePath(trimmed));
        }

        private static String normalizePath(String path) {
            if (path.isBlank()) {
                return "/infer";
            }
            return path.startsWith("/") ? path : "/" + path;
        }
    }

    record HttpResponse(int status, String body) {
        static HttpResponse parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return new HttpResponse(0, "");
            }
            int split = raw.indexOf("\r\n\r\n");
            if (split < 0) {
                split = raw.indexOf("\n\n");
            }
            String head = split < 0 ? raw : raw.substring(0, split);
            String body = split < 0 ? "" : raw.substring(split).replaceFirst("^\r?\n\r?\n", "");
            int status = 0;
            String first = head.lines().findFirst().orElse("");
            String[] parts = first.split("\\s+");
            if (parts.length >= 2) {
                try {
                    status = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                    status = 0;
                }
            }
            return new HttpResponse(status, body.trim());
        }
    }
}
