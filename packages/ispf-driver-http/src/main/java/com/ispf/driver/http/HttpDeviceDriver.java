package com.ispf.driver.http;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP/HTTPS client driver — polls REST endpoints and maps responses to ISPF variables.
 */
public class HttpDeviceDriver implements DeviceDriver {

    private static final DataSchema RESPONSE_SCHEMA = DataSchema.builder("httpResponse")
            .field("statusCode", FieldType.INTEGER)
            .field("value", FieldType.STRING)
            .field("contentType", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "http",
            "HTTP Client Driver",
            "0.1.0",
            "Polls HTTP/HTTPS endpoints (GET/HEAD) and maps status and body to ISPF variables",
            "ISPF",
            Map.of(
                    "baseUrl", "http://127.0.0.1:8080",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "10000",
                    "insecureTls", "false"
            )
    );

    private DriverObject driverObject;
    private HttpClient client;
    private String baseUrl = "http://127.0.0.1:8080";
    private long timeoutMs = 5000;
    private final Map<String, HttpPoint> points = new ConcurrentHashMap<>();
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
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "HTTP client ready (baseUrl=" + baseUrl + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
        client = null;
        driverObject.log(DriverLogLevel.INFO, "HTTP client disconnected");
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
            HttpPoint point = HttpPoint.parse(entry.getValue(), baseUrl);
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), fetch(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("HTTP driver is read-only in v0.1");
    }

    private DataRecord fetch(HttpPoint point) throws DriverException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(point.url()))
                    .timeout(Duration.ofMillis(timeoutMs));
            String method = point.method();
            if ("HEAD".equals(method)) {
                builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
            } else if ("GET".equals(method)) {
                builder.GET();
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = client.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            String body = response.body() == null ? "" : response.body();
            if (point.parseJsonBody()) {
                body = extractJsonScalar(body);
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            return DataRecord.single(RESPONSE_SCHEMA, Map.of(
                    "statusCode", response.statusCode(),
                    "value", body,
                    "contentType", contentType
            ));
        } catch (Exception e) {
            throw new DriverException("HTTP request failed for " + point.url(), e);
        }
    }

    private static String extractJsonScalar(String body) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
