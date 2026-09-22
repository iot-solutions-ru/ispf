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
 * Sigfox backend callback driver — plain HTTP/1.1 GET {@code /uplink} (no TLS).
 * <p>
 * Point mapping is a device id label (for example {@code DEVICE123}). Reads issue a fixed
 * uplink GET; writes POST a downlink body from record {@code value} to {@code /downlink}.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only. Not a Sigfox Backend API SDK.
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
            "Sigfox backend callback over plain HTTP/1.1 GET /uplink (no TLS; not Backend SDK)",
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
        driverObject.log(DriverLogLevel.INFO, "Sigfox HTTP/1.1 ready for " + host + ":" + port);
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
            String mapping = entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId : entry.getValue().trim();
            routes.put(pointId, mapping);
            String deviceId = deviceIdOf(mapping);
            HttpResponse response = exchange(buildUplinkGetRequest(host), "/uplink");
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", response.body(),
                    "status", Integer.toString(response.status()),
                    "deviceId", deviceId,
                    "path", "/uplink"
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        String mapping = routes.getOrDefault(pointId, pointId);
        String deviceId = deviceIdOf(mapping);
        String body = extractValue(value);
        String request = "POST /downlink HTTP/1.1\r\n"
                + "Host: " + host + "\r\n"
                + "Connection: close\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                + "\r\n";
        HttpResponse response = exchangeWithBody(request, body, "/downlink");
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", response.body().isBlank() ? body : response.body(),
                "status", Integer.toString(response.status()),
                "deviceId", deviceId,
                "path", "/downlink"
        )));
    }

    /**
     * Exact HTTP/1.1 uplink GET used on the wire (Host without port).
     */
    static String buildUplinkGetRequest(String hostHeader) {
        return "GET /uplink HTTP/1.1\r\n"
                + "Host: " + hostHeader + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
    }

    static String deviceIdOf(String mapping) {
        String t = mapping.trim();
        int slash = t.lastIndexOf('/');
        if (slash >= 0) {
            String last = t.substring(slash + 1).trim();
            if (!last.isBlank()
                    && !last.equalsIgnoreCase("messages")
                    && !last.equalsIgnoreCase("devices")
                    && !last.equalsIgnoreCase("uplink")
                    && !last.equalsIgnoreCase("downlink")) {
                return last;
            }
        }
        return t.toUpperCase(Locale.ROOT);
    }

    private HttpResponse exchange(String request, String path) throws DriverException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return HttpResponse.parse(readAll(in));
        } catch (IOException e) {
            throw new DriverException("Sigfox HTTP exchange failed for " + host + ":" + port + path, e);
        }
    }

    private HttpResponse exchangeWithBody(String headers, String body, String path) throws DriverException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(headers.getBytes(StandardCharsets.US_ASCII));
            out.write(body.getBytes(StandardCharsets.UTF_8));
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

    static final class HttpResponse {
        private final int status;
        private final String body;

        HttpResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }

        int status() {
            return status;
        }

        String body() {
            return body;
        }

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
            int firstSpace = first.indexOf(' ');
            int secondSpace = firstSpace < 0 ? -1 : first.indexOf(' ', firstSpace + 1);
            if (firstSpace >= 0 && secondSpace > firstSpace) {
                try {
                    status = Integer.parseInt(first.substring(firstSpace + 1, secondSpace));
                } catch (NumberFormatException ignored) {
                    status = 0;
                }
            }
            return new HttpResponse(status, body.trim());
        }
    }
}
