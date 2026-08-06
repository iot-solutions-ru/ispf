package com.ispf.driver.sms;

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
 * SMS notification gateway — POSTs JSON {@code {to,body}} to a configured HTTP SMS relay.
 * Distinct from protocol driver {@code smpp} (SMSC bind/submit).
 */
public class SmsDeviceDriver implements DeviceDriver {

    private static final DataSchema RESULT_SCHEMA = DataSchema.builder("smsResult")
            .field("value", FieldType.STRING)
            .field("statusCode", FieldType.INTEGER)
            .field("detail", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "sms",
            "SMS Notification Driver",
            "0.1.0",
            "Sends SMS via HTTP relay (JSON to/body). Configure relayUrl per device. Not SMPP.",
            "ISPF",
            Map.of(
                    "relayUrl", "http://127.0.0.1:8090/sms",
                    "timeoutMs", "15000",
                    "defaultTo", ""
            ),
            DriverMaturity.PRODUCTION,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private HttpClient client;
    private String relayUrl = "";
    private String defaultTo = "";
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
            case "relayUrl" -> relayUrl = value.trim();
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            case "defaultTo" -> defaultTo = value.trim();
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        if (relayUrl == null || relayUrl.isBlank()) {
            throw new DriverException("sms driver requires relayUrl");
        }
        if (!relayUrl.startsWith("http://") && !relayUrl.startsWith("https://")) {
            throw new DriverException("sms relayUrl must be http(s): " + relayUrl);
        }
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "SMS relay ready (" + relayUrl + ")");
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
            throw new DriverException("sms write requires a non-empty DataRecord");
        }
        String mapping = lastMappings.get(pointId);
        if (mapping == null) {
            throw new DriverException("Unknown sms point: " + pointId + " (poll once or map outbound)");
        }
        requireOutboundMapping(mapping);
        Map<String, Object> row = value.firstRow();
        String to = firstNonBlank(row, "to", "destination", "msisdn");
        if (to == null || to.isBlank()) {
            to = defaultTo;
        }
        String body = firstNonBlank(row, "body", "text", "message", "value");
        if (to == null || to.isBlank()) {
            throw new DriverException("sms write requires to (or defaultTo)");
        }
        if (body == null || body.isBlank()) {
            throw new DriverException("sms write requires body/text/message");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("to", to.trim());
        payload.put("body", body);
        DataRecord result = postJson(payload);
        driverObject.updateVariable(pointId, result);
    }

    private DataRecord postJson(Map<String, Object> payload) throws DriverException {
        try {
            String json = toJsonObject(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(relayUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String detail = response.body() == null ? "" : response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DriverException("sms relay HTTP " + response.statusCode() + ": " + detail);
            }
            return DataRecord.single(RESULT_SCHEMA, Map.of(
                    "value", "sent",
                    "statusCode", response.statusCode(),
                    "detail", detail
            ));
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("sms relay failed", e);
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
            throw new DriverException("sms point mapping is blank");
        }
        String token = raw.trim().toLowerCase(Locale.ROOT);
        if (!"outbound".equals(token) && !"send".equals(token) && !"sms".equals(token)) {
            throw new DriverException("sms point mapping must be outbound|send|sms, got: " + raw);
        }
    }

    private static String firstNonBlank(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
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
            sb.append('"').append(escape(String.valueOf(entry.getValue()))).append('"');
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
