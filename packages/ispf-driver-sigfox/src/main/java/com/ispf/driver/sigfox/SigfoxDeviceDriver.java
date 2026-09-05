package com.ispf.driver.sigfox;

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
 * Sigfox backend callback driver — HTTP/1.1 lab client for device uplink/downlink callbacks.
 * <p>
 * Point mapping is a device id or path ({@code DEVICE123}, {@code /devices/DEVICE123/messages}).
 * {@code readPoints} GETs the last uplink payload; {@code writePoint} POSTs a downlink body from
 * record {@code value}.
 * <p>
 * Clean-room ISPF lab, Apache-2.0 — JDK sockets only. Not Sigfox Backend API SDK; no TLS in lab.
 */
public class SigfoxDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("sigfoxValue")
            .field("value", FieldType.STRING)
            .field("status", FieldType.STRING)
            .field("deviceId", FieldType.STRING)
            .field("path", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "sigfox",
            "Sigfox Driver",
            "0.1.0",
            "Sigfox backend callback HTTP/1.1 lab: GET uplink / POST downlink (not Backend SDK / TLS)",
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
        driverObject.log(DriverLogLevel.INFO, "Sigfox HTTP lab ready for " + host + ":" + port);
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
            String mapping = entry.getValue() == null || entry.getValue().isBlank() ? pointId : entry.getValue().trim();
            routes.put(pointId, mapping);
            String deviceId = deviceIdOf(mapping);
            String path = pathOf(mapping);
            HttpResponse response = exchange("GET", path, null);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", response.body(),
                    "status", Integer.toString(response.status()),
                    "deviceId", deviceId,
                    "path", path
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        String mapping = routes.getOrDefault(pointId, pointId);
        String deviceId = deviceIdOf(mapping);
        String path = pathOf(mapping);
        String body = extractValue(value);
        HttpResponse response = exchange("POST", path, body);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", response.body().isBlank() ? body : response.body(),
                "status", Integer.toString(response.status()),
                "deviceId", deviceId,
                "path", path
        )));
    }

    static String deviceIdOf(String mapping) {
        String t = mapping.trim();
        if (t.contains("/")) {
            String[] parts = t.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (!parts[i].isBlank() && !parts[i].equalsIgnoreCase("messages")
                        && !parts[i].equalsIgnoreCase("devices")) {
                    return parts[i];
                }
            }
        }
        return t.toUpperCase(Locale.ROOT);
    }

    static String pathOf(String mapping) {
        String t = mapping.trim();
        if (t.startsWith("/")) {
            return t;
        }
        return "/devices/" + t + "/messages";
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
            throw new DriverException("Sigfox HTTP exchange failed for " + host + ":" + port + path, e);
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
