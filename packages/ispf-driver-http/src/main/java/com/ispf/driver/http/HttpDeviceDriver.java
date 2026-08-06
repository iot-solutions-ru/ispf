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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP/HTTPS client driver — polls REST endpoints and posts write payloads to mapped URLs.
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
            "0.2.0",
            "Polls HTTP/HTTPS endpoints and writes POST/PUT/PATCH bodies to mapped URLs (relay/webhook)",
            "ISPF",
            Map.of(
                    "baseUrl", "http://127.0.0.1:8080",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "10000",
                    "insecureTls", "false",
                    "writePath", ""
            ),
            com.ispf.driver.DriverMaturity.PRODUCTION,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private HttpClient client;
    private String baseUrl = "http://127.0.0.1:8080";
    private String writePath = "";
    private long timeoutMs = 5000;
    private final Map<String, HttpPoint> points = new ConcurrentHashMap<>();
    private final Map<String, String> lastMappings = new ConcurrentHashMap<>();
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
            case "writePath" -> writePath = value.trim();
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
        lastMappings.clear();
        lastMappings.putAll(pointMappings);
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            HttpPoint point = HttpPoint.parse(entry.getValue(), baseUrl);
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), fetch(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        if (value == null || value.rowCount() == 0) {
            throw new DriverException("HTTP write requires a non-empty DataRecord");
        }
        HttpPoint point = resolvePoint(pointId);
        String method = writeMethod(point.method());
        String url = resolveWriteUrl(point, value);
        String body = extractWriteBody(value);
        String contentType = contentTypeFor(body, value);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", contentType)
                    .method(method, HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body() == null ? "" : response.body();
            String responseType = response.headers().firstValue("Content-Type").orElse("");
            DataRecord result = DataRecord.single(RESPONSE_SCHEMA, Map.of(
                    "statusCode", response.statusCode(),
                    "value", responseBody,
                    "contentType", responseType
            ));
            driverObject.updateVariable(pointId, result);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DriverException("HTTP write failed for " + url + ": HTTP " + response.statusCode());
            }
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("HTTP write failed for " + url, e);
        }
    }

    private HttpPoint resolvePoint(String pointId) throws DriverException {
        HttpPoint point = points.get(pointId);
        if (point != null) {
            return point;
        }
        String mapping = lastMappings.get(pointId);
        if (mapping != null) {
            point = HttpPoint.parse(mapping, baseUrl);
            points.put(pointId, point);
            return point;
        }
        throw new DriverException("Unknown HTTP point: " + pointId);
    }

    private String resolveWriteUrl(HttpPoint point, DataRecord value) {
        Map<String, Object> row = value.firstRow();
        Object override = firstNonBlank(row, "url", "path");
        if (override != null) {
            return HttpPoint.resolveUrl(String.valueOf(override).trim(), baseUrl);
        }
        if (writePath != null && !writePath.isBlank()) {
            return HttpPoint.resolveUrl(writePath, baseUrl);
        }
        return point.url();
    }

    private static String writeMethod(String mappedMethod) {
        String method = mappedMethod == null ? "POST" : mappedMethod.toUpperCase(Locale.ROOT);
        return switch (method) {
            case "POST", "PUT", "PATCH", "DELETE" -> method;
            default -> "POST";
        };
    }

    private static String extractWriteBody(DataRecord value) {
        Map<String, Object> row = value.firstRow();
        // Structured notification / relay payloads: always JSON-encode the row fields.
        if (firstNonBlank(row, "to", "subject", "destination", "text") != null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                if ("statusCode".equals(key) || "contentType".equals(key) || "url".equals(key) || "path".equals(key)) {
                    continue;
                }
                if (entry.getValue() != null) {
                    payload.put(key, entry.getValue());
                }
            }
            return toJsonObject(payload);
        }
        Object direct = firstNonBlank(row, "value", "payload", "body");
        if (direct != null) {
            return String.valueOf(direct);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            if ("statusCode".equals(key) || "contentType".equals(key) || "url".equals(key) || "path".equals(key)) {
                continue;
            }
            if (entry.getValue() != null) {
                payload.put(key, entry.getValue());
            }
        }
        if (payload.isEmpty()) {
            return "";
        }
        return toJsonObject(payload);
    }

    private static String contentTypeFor(String body, DataRecord value) {
        Map<String, Object> row = value.firstRow();
        Object explicit = firstNonBlank(row, "contentType");
        if (explicit != null) {
            return String.valueOf(explicit);
        }
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return "application/json";
        }
        if (!trimmed.isEmpty() && row.size() > 1) {
            return "application/json";
        }
        return "application/json";
    }

    private static Object firstNonBlank(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String toJsonObject(Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escapeJson(entry.getKey())).append('"').append(':');
            appendJsonValue(sb, entry.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    private static void appendJsonValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
            return;
        }
        String text = String.valueOf(value).trim();
        if (("true".equals(text) || "false".equals(text) || "null".equals(text))
                || text.matches("-?\\d+(\\.\\d+)?")) {
            sb.append(text);
            return;
        }
        if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))) {
            sb.append(text);
            return;
        }
        sb.append('"').append(escapeJson(text)).append('"');
    }

    private static String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
