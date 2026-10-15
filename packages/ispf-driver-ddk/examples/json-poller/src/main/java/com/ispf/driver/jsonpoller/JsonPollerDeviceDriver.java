package com.ispf.driver.jsonpoller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.DriverTransientException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reference DDK driver — HTTP JSON poller (BL-144).
 * <p>
 * Point mapping format: {@code jsonPath:$.field.nested} — GETs {@code baseUrl} and extracts the path.
 */
public class JsonPollerDeviceDriver implements DeviceDriver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("jsonPollerValue")
            .field("jsonPath", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .field("value", FieldType.STRING)
            .field("connected", FieldType.BOOLEAN)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "json-poller",
            "JSON Poller Driver (DDK reference)",
            "0.2.0",
            "Polls a JSON HTTP endpoint and maps jsonPath expressions to variables",
            "ISPF",
            Map.of(
                    "baseUrl", "http://127.0.0.1:8080",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "5000"
            ),
            DriverMaturity.BETA,
            Set.of("read")
    );

    private DriverObject driverObject;
    private String baseUrl = "http://127.0.0.1:8080";
    private int timeoutMs = 5000;
    private final Map<String, JsonPollerPoint> points = new ConcurrentHashMap<>();
    private HttpClient httpClient;
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
            case "baseUrl" -> baseUrl = value.trim();
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "JSON poller connected to " + baseUrl);
    }

    @Override
    public void disconnect() {
        connected = false;
        httpClient = null;
        points.clear();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        requireConnected();
        String body = fetchBody();
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (Exception e) {
            throw new DriverTransientException("Invalid JSON from " + baseUrl, e);
        }
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            JsonPollerPoint point = JsonPollerPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            String extracted = extractPath(root, point.jsonPath());
            driverObject.updateVariable(entry.getKey(), DataRecord.single(VALUE_SCHEMA, Map.of(
                    "jsonPath", point.jsonPath(),
                    "raw", body,
                    "value", extracted == null ? "" : extracted,
                    "connected", true
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("JSON poller driver is read-only");
    }

    private String fetchBody() throws DriverException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DriverTransientException("HTTP " + response.statusCode() + " from " + baseUrl);
            }
            return response.body() == null ? "" : response.body();
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverTransientException("Failed to GET " + baseUrl, e);
        }
    }

    /**
     * Supports {@code $}, {@code $.a}, {@code $.a.b} object paths (no array indices).
     */
    static String extractPath(JsonNode root, String jsonPath) {
        if (jsonPath == null || jsonPath.isBlank() || "$".equals(jsonPath.trim())) {
            return root == null ? null : root.toString();
        }
        String path = jsonPath.trim();
        if (!path.startsWith("$.")) {
            throw new IllegalArgumentException("jsonPath must start with $. or be $, got: " + jsonPath);
        }
        JsonNode current = root;
        for (String segment : path.substring(2).split("\\.")) {
            if (segment.isBlank() || current == null || !current.isObject()) {
                return null;
            }
            current = current.get(segment);
        }
        if (current == null || current.isNull()) {
            return null;
        }
        if (current.isValueNode()) {
            return current.asText();
        }
        return current.toString();
    }

    private void requireConnected() throws DriverException {
        if (!connected || httpClient == null) {
            throw new DriverException("Not connected");
        }
    }
}
