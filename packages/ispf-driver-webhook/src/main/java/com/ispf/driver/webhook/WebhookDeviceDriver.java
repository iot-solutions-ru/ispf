package com.ispf.driver.webhook;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Webhook notification gateway — POSTs JSON payload to a configured URL.
 * Separate from generic {@code http} poll client.
 */
public class WebhookDeviceDriver implements DeviceDriver {

    private static final DataSchema RESULT_SCHEMA = DataSchema.builder("webhookResult")
            .field("value", FieldType.STRING)
            .field("statusCode", FieldType.INTEGER)
            .field("detail", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "webhook",
            "Webhook Notification Driver",
            "0.1.0",
            "POSTs JSON to a configured webhook URL. Configure targetUrl (or relayUrl) per device.",
            "ISPF",
            Map.of(
                    "targetUrl", "http://127.0.0.1:8090/hook",
                    "timeoutMs", "15000"
            ),
            DriverMaturity.PRODUCTION,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private HttpClient client;
    private String targetUrl = "";
    private long timeoutMs = 15_000;
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
        if (value == null) {
            return;
        }
        switch (key) {
            case "targetUrl", "relayUrl", "url" -> targetUrl = value.trim();
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new DriverException("webhook driver requires targetUrl (or relayUrl)");
        }
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            throw new DriverException("webhook targetUrl must be http(s): " + targetUrl);
        }
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Webhook ready (" + targetUrl + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
        client = null;
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
        lastMappings.clear();
        lastMappings.putAll(pointMappings);
        for (String pointId : pointMappings.keySet()) {
            requireOutboundMapping(pointMappings.get(pointId));
            driverObject.updateVariable(pointId, idleRecord());
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        if (value == null || value.rowCount() == 0) {
            throw new DriverException("webhook write requires a non-empty DataRecord");
        }
        String mapping = lastMappings.get(pointId);
        if (mapping == null) {
            throw new DriverException("Unknown webhook point: " + pointId + " (poll once or map outbound)");
        }
        requireOutboundMapping(mapping);
        Map<String, Object> row = value.firstRow();
        String body;
        Object raw = firstNonBlank(row, "value", "payload", "body");
        if (raw != null && looksLikeJson(String.valueOf(raw))) {
            body = String.valueOf(raw).trim();
        } else {
            Map<String, Object> payload = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                if ("statusCode".equals(key) || "detail".equals(key)) {
                    continue;
                }
                if (entry.getValue() != null) {
                    payload.put(key, entry.getValue());
                }
            }
            body = toJsonObject(payload);
        }
        DataRecord result = postJson(body);
        driverObject.updateVariable(pointId, result);
    }

    private DataRecord postJson(String json) throws DriverException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(targetUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String detail = response.body() == null ? "" : response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DriverException("webhook HTTP " + response.statusCode() + ": " + detail);
            }
            return DataRecord.single(RESULT_SCHEMA, Map.of(
                    "value", "sent",
                    "statusCode", response.statusCode(),
                    "detail", detail
            ));
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("webhook failed", e);
        }
    }

    private static DataRecord idleRecord() {
        return DataRecord.single(RESULT_SCHEMA, Map.of(
                "value", "ready",
                "statusCode", 0,
                "detail", ""
        ));
    }

    private static void requireOutboundMapping(String raw) throws DriverException {
        if (raw == null || raw.isBlank()) {
            throw new DriverException("webhook point mapping is blank");
        }
        String token = raw.trim().toLowerCase(Locale.ROOT);
        if (!"outbound".equals(token) && !"send".equals(token) && !"webhook".equals(token)) {
            throw new DriverException("webhook point mapping must be outbound|send|webhook, got: " + raw);
        }
    }

    private static boolean looksLikeJson(String text) {
        String trimmed = text.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
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
            sb.append('"').append(escape(entry.getKey())).append('"').append(':');
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                String text = String.valueOf(value);
                if (looksLikeJson(text)) {
                    sb.append(text.trim());
                } else {
                    sb.append('"').append(escape(text)).append('"');
                }
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
