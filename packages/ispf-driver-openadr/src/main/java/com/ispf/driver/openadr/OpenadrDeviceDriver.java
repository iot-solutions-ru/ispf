package com.ispf.driver.openadr;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenADR 2.0b simplified VEN — polls a VTN for distribute-event payloads (XML or JSON subset).
 * Clean-room JDK HTTP client; no OpenLEADR / proprietary VTN SDK.
 */
public class OpenadrDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("openadrValue")
            .field("value", FieldType.STRING)
            .field("kind", FieldType.STRING)
            .field("statusCode", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "openadr",
            "OpenADR Driver",
            "0.1.0",
            "OpenADR 2.0b simplified VEN event poll (XML/JSON subset) against a VTN URL",
            "ISPF",
            Map.of(
                    "vtnUrl", "http://127.0.0.1:8080/OpenADR2/Simple/2.0b",
                    "venId", "ven-ispf-1",
                    "pollMethod", "POST",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "30000",
                    "accept", "application/xml"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private HttpClient client;
    private String vtnUrl = "http://127.0.0.1:8080/OpenADR2/Simple/2.0b";
    private String venId = "ven-ispf-1";
    private String pollMethod = "POST";
    private String accept = "application/xml";
    private long timeoutMs = 5000;
    private final Map<String, OpenadrPoint> points = new ConcurrentHashMap<>();
    private volatile OpenadrEventPayload lastPayload = OpenadrEventPayload.parse("");
    private volatile int lastStatusCode = -1;
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
            case "vtnUrl" -> vtnUrl = value.trim();
            case "venId" -> venId = value.trim();
            case "pollMethod" -> pollMethod = value.trim().toUpperCase(Locale.ROOT);
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            case "accept" -> accept = value.trim();
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        connected = true;
        driverObject.log(DriverLogLevel.INFO,
                "OpenADR VEN ready (vtnUrl=" + vtnUrl + ", venId=" + venId + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
        client = null;
        points.clear();
        lastPayload = OpenadrEventPayload.parse("");
        lastStatusCode = -1;
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
        pollVtn();
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            OpenadrPoint point = OpenadrPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), recordFor(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        OpenadrPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown OpenADR point: " + pointId);
        }
        String opt = extractWriteValue(value);
        String eventId = lastPayload.eventId;
        if (eventId == null || eventId.isBlank()) {
            throw new DriverException("No active OpenADR event to acknowledge");
        }
        postCreatedEvent(eventId, opt);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", opt,
                "kind", point.kind(),
                "statusCode", lastStatusCode
        )));
    }

    private void pollVtn() throws DriverException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(vtnUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Accept", accept);
            HttpRequest request;
            if ("GET".equals(pollMethod)) {
                request = builder.GET().build();
            } else {
                String contentType = accept.contains("json") ? "application/json" : "application/xml";
                request = builder
                        .header("Content-Type", contentType)
                        .POST(HttpRequest.BodyPublishers.ofString(buildPollEnvelope()))
                        .build();
            }
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            lastStatusCode = response.statusCode();
            if (lastStatusCode < 200 || lastStatusCode >= 300) {
                throw new DriverException("OpenADR poll failed: HTTP " + lastStatusCode);
            }
            lastPayload = OpenadrEventPayload.parse(response.body() == null ? "" : response.body());
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("OpenADR poll failed for " + vtnUrl, e);
        }
    }

    private void postCreatedEvent(String eventId, String optType) throws DriverException {
        try {
            String contentType = accept.contains("json") ? "application/json" : "application/xml";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vtnUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", contentType)
                    .header("Accept", accept)
                    .POST(HttpRequest.BodyPublishers.ofString(buildCreatedEventEnvelope(eventId, optType)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            lastStatusCode = response.statusCode();
            if (lastStatusCode < 200 || lastStatusCode >= 300) {
                throw new DriverException("OpenADR createdEvent failed: HTTP " + lastStatusCode);
            }
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("OpenADR createdEvent failed", e);
        }
    }

    private DataRecord recordFor(OpenadrPoint point) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", lastPayload.valueFor(point),
                "kind", point.kind(),
                "statusCode", lastStatusCode
        ));
    }

    private String buildPollEnvelope() {
        if (accept.contains("json")) {
            return "{\"request\":\"oadrPoll\",\"venID\":\"" + escapeJson(venId) + "\"}";
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<oadr:oadrPayload xmlns:oadr=\"http://openadr.org/oadr-2.0b/2012/07\">"
                + "<oadr:oadrSignedObject><oadr:oadrPoll>"
                + "<ei:venID xmlns:ei=\"http://docs.oasis-open.org/ns/energyinterop/201110\">"
                + escapeXml(venId) + "</ei:venID>"
                + "</oadr:oadrPoll></oadr:oadrSignedObject></oadr:oadrPayload>";
    }

    private String buildCreatedEventEnvelope(String eventId, String optType) {
        String opt = optType == null || optType.isBlank() ? "optIn" : optType;
        if (accept.contains("json")) {
            return "{\"request\":\"oadrCreatedEvent\",\"venID\":\"" + escapeJson(venId)
                    + "\",\"eventID\":\"" + escapeJson(eventId)
                    + "\",\"optType\":\"" + escapeJson(opt) + "\"}";
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<oadr:oadrPayload xmlns:oadr=\"http://openadr.org/oadr-2.0b/2012/07\""
                + " xmlns:ei=\"http://docs.oasis-open.org/ns/energyinterop/201110\">"
                + "<oadr:oadrSignedObject><oadr:oadrCreatedEvent>"
                + "<ei:eiResponse><ei:responseCode>200</ei:responseCode></ei:eiResponse>"
                + "<ei:eventResponses><ei:eventResponse>"
                + "<ei:responseCode>200</ei:responseCode>"
                + "<ei:qualifiedEventID><ei:eventID>" + escapeXml(eventId) + "</ei:eventID></ei:qualifiedEventID>"
                + "<ei:optType>" + escapeXml(opt) + "</ei:optType>"
                + "</ei:eventResponse></ei:eventResponses>"
                + "<ei:venID>" + escapeXml(venId) + "</ei:venID>"
                + "</oadr:oadrCreatedEvent></oadr:oadrSignedObject></oadr:oadrPayload>";
    }

    private static String extractWriteValue(DataRecord value) throws DriverException {
        if (value == null || value.rowCount() == 0) {
            throw new DriverException("OpenADR write requires a non-empty DataRecord");
        }
        Map<String, Object> row = value.firstRow();
        Object raw = row.get("value");
        if (raw == null) {
            raw = row.get("optType");
        }
        if (raw == null) {
            raw = row.get("raw");
        }
        return raw == null ? "optIn" : String.valueOf(raw);
    }

    private static String escapeXml(String raw) {
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String escapeJson(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
