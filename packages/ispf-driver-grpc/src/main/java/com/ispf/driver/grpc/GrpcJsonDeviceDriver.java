package com.ispf.driver.grpc;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lab <strong>gRPC-JSON / gRPC-Web style</strong> HTTP POST driver (driverId {@code grpc}).
 * <p>
 * This is <strong>not</strong> wire-compatible gRPC: no HTTP/2 framing, no protobuf binary codec,
 * no gRPC status trailers. Unary calls map to {@code POST /{Service}/{Method}} with JSON
 * bodies for CI loopback only. JDK-only Apache-2.0 clean-room — does not pull grpc-java.
 */
public class GrpcJsonDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("grpcJsonValue")
            .field("value", FieldType.STRING)
            .field("method", FieldType.STRING)
            .field("statusCode", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "grpc",
            "gRPC-JSON Lab Driver",
            "0.1.0",
            "Lab gRPC-JSON HTTP POST mapping (NOT wire-compatible gRPC; CI unary JSON-over-HTTP)",
            "ISPF",
            Map.of(
                    "baseUrl", "http://127.0.0.1:50051",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "10000",
                    "requestName", "name",
                    "defaultName", "world"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private HttpClient client;
    private String baseUrl = "http://127.0.0.1:50051";
    private long timeoutMs = 5000;
    private String requestName = "name";
    private String defaultName = "world";
    private final Map<String, GrpcJsonPoint> points = new ConcurrentHashMap<>();
    private final Map<String, String> lastRequestNames = new ConcurrentHashMap<>();
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
            case "baseUrl" -> baseUrl = trimTrailingSlash(value.trim());
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            case "requestName" -> requestName = value.trim();
            case "defaultName" -> defaultName = value.trim();
            default -> { }
        }
    }

    private static String trimTrailingSlash(String url) {
        if (url.endsWith("/") && url.length() > 1) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    @Override
    public void connect() throws DriverException {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        connected = true;
        driverObject.log(DriverLogLevel.INFO,
                "gRPC-JSON lab client ready (baseUrl=" + baseUrl + "; NOT wire gRPC)");
    }

    @Override
    public void disconnect() {
        connected = false;
        client = null;
        points.clear();
        lastRequestNames.clear();
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
            GrpcJsonPoint point = GrpcJsonPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            String name = lastRequestNames.getOrDefault(entry.getKey(), defaultName);
            driverObject.updateVariable(entry.getKey(), invoke(point, name));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        GrpcJsonPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown gRPC-JSON point: " + pointId);
        }
        String name = extractWriteValue(value);
        lastRequestNames.put(pointId, name);
        driverObject.updateVariable(pointId, invoke(point, name));
    }

    private DataRecord invoke(GrpcJsonPoint point, String name) throws DriverException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put(requestName, name);
        String body = GrpcJson.toJsonObject(request);
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + point.httpPath()))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DriverException("gRPC-JSON call failed: HTTP " + response.statusCode());
            }
            Object parsed = GrpcJson.parse(response.body() == null ? "" : response.body());
            String value = point.hasField()
                    ? GrpcJson.extractField(parsed, point.field())
                    : GrpcJson.stringify(parsed);
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", value == null ? "" : value,
                    "method", point.serviceMethod(),
                    "statusCode", response.statusCode()
            ));
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("gRPC-JSON call failed for " + point.serviceMethod(), e);
        }
    }

    private static String extractWriteValue(DataRecord value) throws DriverException {
        if (value == null || value.rowCount() == 0) {
            throw new DriverException("gRPC-JSON write requires a non-empty DataRecord");
        }
        Map<String, Object> row = value.firstRow();
        Object raw = row.get("value");
        if (raw == null) {
            raw = row.get("name");
        }
        if (raw == null) {
            raw = row.get("raw");
        }
        if (raw == null) {
            throw new DriverException("gRPC-JSON write requires value, name, or raw field");
        }
        return String.valueOf(raw);
    }
}
