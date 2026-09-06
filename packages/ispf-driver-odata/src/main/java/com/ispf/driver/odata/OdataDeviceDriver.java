package com.ispf.driver.odata;

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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OData JSON v4 subset client — HTTP GET of entity sets / properties via {@code java.net.http}.
 * Clean-room JDK-only (Apache-2.0 ISPF), no third-party OData stack.
 */
public class OdataDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("odataValue")
            .field("value", FieldType.STRING)
            .field("path", FieldType.STRING)
            .field("statusCode", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "odata",
            "OData Driver",
            "0.1.0",
            "Polls OData JSON v4 subset endpoints (value array / property) over HTTP GET",
            "ISPF",
            Map.of(
                    "baseUrl", "http://127.0.0.1:8080/odata",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "10000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private HttpClient client;
    private String baseUrl = "http://127.0.0.1:8080/odata";
    private long timeoutMs = 5000;
    private final Map<String, OdataPoint> points = new ConcurrentHashMap<>();
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
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "OData client ready (baseUrl=" + baseUrl + ")");
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
            OdataPoint point = OdataPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), fetch(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        OdataPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown OData point: " + pointId);
        }
        String payload = extractWriteValue(value);
        String property = point.hasProperty() ? point.property() : "value";
        String body = OdataJson.toJsonObject(Map.of(property, payload));
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolveUrl(point.path())))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DriverException("OData write failed: HTTP " + response.statusCode());
            }
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", payload,
                    "path", point.path(),
                    "statusCode", response.statusCode()
            )));
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("OData write failed for " + point.path(), e);
        }
    }

    private DataRecord fetch(OdataPoint point) throws DriverException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolveUrl(point.path())))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DriverException("OData GET failed: HTTP " + response.statusCode());
            }
            Object parsed = OdataJson.parse(response.body() == null ? "" : response.body());
            String value = OdataJson.extract(parsed, point.property());
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", value == null ? "" : value,
                    "path", point.path(),
                    "statusCode", response.statusCode()
            ));
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("OData GET failed for " + point.path(), e);
        }
    }

    private String resolveUrl(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return baseUrl + path;
    }

    private static String extractWriteValue(DataRecord value) throws DriverException {
        if (value == null || value.rowCount() == 0) {
            throw new DriverException("OData write requires a non-empty DataRecord");
        }
        Map<String, Object> row = value.firstRow();
        Object raw = row.get("value");
        if (raw == null) {
            raw = row.get("raw");
        }
        if (raw == null) {
            throw new DriverException("OData write requires value or raw field");
        }
        return String.valueOf(raw);
    }
}
