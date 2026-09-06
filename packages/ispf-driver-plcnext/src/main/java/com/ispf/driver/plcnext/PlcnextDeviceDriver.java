package com.ispf.driver.plcnext;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phoenix Contact PLCnext driver — honest <strong>HTTP/JSON RSC-lab</strong> for symbol read/write.
 * <p>
 * Default TCP/HTTP port {@code 41100}. Point mappings are symbol paths such as
 * {@code Arp.Plc.Eclr/MainInstance.xMotor}.
 * <p>
 * Lab HTTP dialect (not full PLCnext Engineer RSC binary):
 * <pre>
 *   GET  /rsc/variables?path=&lt;symbol&gt;     → {"path":"…","value":"…"}
 *   PUT  /rsc/variables  {"path":"…","value":"…"} → {"path":"…","value":"…"}
 * </pre>
 * <strong>Honesty:</strong> this is an ISPF RSC-lab HTTP/JSON subset for interop testing. It does
 * <strong>not</strong> claim the full proprietary PLCnext Engineer / RSC binary Remoting protocol.
 * Clean-room ISPF code, Apache-2.0 — JDK {@code java.net.http} only; no vendor SDK / PLC4X / GPL.
 */
public class PlcnextDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("plcnextValue")
            .field("value", FieldType.STRING)
            .field("path", FieldType.STRING)
            .field("statusCode", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "plcnext",
            "PLCnext Driver",
            "0.1.0",
            "PLCnext RSC-lab HTTP/JSON symbol read/write on port 41100"
                    + " — not full PLCnext Engineer RSC binary",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "41100",
                    "basePath", "/rsc/variables",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private HttpClient client;
    private String host = "127.0.0.1";
    private int port = 41100;
    private String basePath = "/rsc/variables";
    private long timeoutMs = 3000;
    private final Map<String, PlcnextPoint> points = new ConcurrentHashMap<>();
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
            case "basePath" -> basePath = normalizeBasePath(value.trim());
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            default -> { }
        }
    }

    private static String normalizeBasePath(String path) {
        String trimmed = path.startsWith("/") ? path : "/" + path;
        if (trimmed.length() > 1 && trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    @Override
    public void connect() throws DriverException {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        connected = true;
        driverObject.log(DriverLogLevel.INFO,
                "PLCnext RSC-lab HTTP/JSON ready for http://" + host + ":" + port + basePath
                        + " (not full PLCnext Engineer RSC binary)");
    }

    @Override
    public void disconnect() {
        connected = false;
        client = null;
        points.clear();
    }

    @Override
    public boolean isConnected() {
        return connected && client != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            PlcnextPoint point = PlcnextPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), fetch(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        PlcnextPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        String payload = extractWriteValue(value);
        String body = PlcnextJson.object(point.path(), payload);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl()))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DriverException("PLCnext RSC-lab write failed: HTTP " + response.statusCode());
            }
            String returned = PlcnextJson.extractStringField(response.body(), "value");
            if (returned.isEmpty()) {
                returned = payload;
            }
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", returned,
                    "path", point.path(),
                    "statusCode", response.statusCode()
            )));
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("PLCnext RSC-lab write failed for " + point.path(), e);
        }
    }

    private DataRecord fetch(PlcnextPoint point) throws DriverException {
        String encoded = URLEncoder.encode(point.path(), StandardCharsets.UTF_8);
        String url = baseUrl() + "?path=" + encoded;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DriverException("PLCnext RSC-lab read failed: HTTP " + response.statusCode());
            }
            String value = PlcnextJson.extractStringField(response.body(), "value");
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", value,
                    "path", point.path(),
                    "statusCode", response.statusCode()
            ));
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("PLCnext RSC-lab read failed for " + point.path(), e);
        }
    }

    private String baseUrl() {
        return "http://" + host + ":" + port + basePath;
    }

    private static String extractWriteValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("PLCnext write requires a value");
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("raw", "value")) {
            Object candidate = row.get(key);
            if (candidate != null) {
                return String.valueOf(candidate);
            }
        }
        throw new IllegalArgumentException("PLCnext write requires raw/value field");
    }
}
