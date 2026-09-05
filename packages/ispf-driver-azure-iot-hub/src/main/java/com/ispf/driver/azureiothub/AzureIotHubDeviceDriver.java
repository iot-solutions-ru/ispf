package com.ispf.driver.azureiothub;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Azure IoT Hub–shaped MQTT lab driver ({@code azure-iot-hub}).
 * <p>
 * <strong>Honesty:</strong> this is a lab MQTT 3.1.1 device client that uses Azure IoT Hub
 * <em>topic conventions</em> over plain TCP. It is <strong>not</strong> a full Azure IoT Hub
 * service/device SDK — no SAS tokens, no TLS requirement in lab, no AMQP/HTTPS twin APIs.
 * <p>
 * Configuration:
 * <ul>
 *   <li>{@code host} — hub hostname (lab broker host)</li>
 *   <li>{@code deviceId} — device id used in topic paths</li>
 *   <li>{@code port} — MQTT port (default {@code 1883} plain lab)</li>
 *   <li>{@code timeoutMs} — connect / ack / read wait</li>
 * </ul>
 * Topic conventions:
 * <ul>
 *   <li>Telemetry write publish: {@code devices/{deviceId}/messages/events/}
 *       plus optional point-mapping suffix (or a full topic if mapping starts with
 *       {@code devices/})</li>
 *   <li>C2D read subscribe: {@code devices/{deviceId}/messages/devicebound/#};
 *       point mapping matches topic suffix or full topic</li>
 * </ul>
 * Record field {@code value} is the MQTT payload string.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only (no Azure SDK).
 */
public class AzureIotHubDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("azureIotHubValue")
            .field("value", FieldType.STRING)
            .field("topic", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "azure-iot-hub",
            "Azure IoT Hub Driver",
            "0.1.0",
            "Lab MQTT client with Azure IoT Hub topic conventions (not full IoT Hub SDK)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "deviceId", "lab-device",
                    "port", "1883",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private String deviceId = "lab-device";
    private int port = 1883;
    private int timeoutMs = 3000;

    private Mqtt311Lab mqtt;
    private final Map<String, String> pointTopics = new ConcurrentHashMap<>();
    private volatile boolean subscribedC2d;

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
            case "deviceId" -> deviceId = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        try {
            mqtt = new Mqtt311Lab(host, port, timeoutMs, deviceId);
            mqtt.addListener(this::onPublish);
            mqtt.connect();
            subscribedC2d = false;
            driverObject.log(DriverLogLevel.INFO,
                    "Azure IoT Hub lab MQTT connected to " + host + ":" + port
                            + " as deviceId=" + deviceId);
        } catch (IOException e) {
            disconnect();
            throw new DriverException("Azure IoT Hub lab MQTT connect failed for "
                    + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        subscribedC2d = false;
        pointTopics.clear();
        if (mqtt != null) {
            mqtt.close();
            mqtt = null;
        }
    }

    @Override
    public boolean isConnected() {
        return mqtt != null && mqtt.isConnected();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        ensureC2dSubscription();
        pointTopics.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String mapping = entry.getValue() == null ? "" : entry.getValue().trim();
            pointTopics.put(pointId, mapping);
            String payload;
            try {
                payload = mqtt.awaitMatching(topic -> matchesC2d(topic, mapping), timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DriverException("Interrupted waiting for C2D on " + mapping, e);
            }
            String topic = resolveMatchedTopic(mapping, payload);
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", payload == null ? "" : payload,
                    "topic", topic
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String mapping = pointTopics.getOrDefault(pointId, pointId);
        String topic = resolveTelemetryTopic(mapping);
        String payload = extractValue(value);
        try {
            mqtt.publish(topic, payload, 1);
        } catch (IOException e) {
            throw new DriverException("Azure IoT Hub lab MQTT publish failed for " + topic, e);
        }
        pointTopics.put(pointId, mapping);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", payload,
                "topic", topic
        )));
    }

    private void ensureC2dSubscription() throws DriverException {
        if (subscribedC2d) {
            return;
        }
        String filter = "devices/" + deviceId + "/messages/devicebound/#";
        try {
            mqtt.subscribe(filter, 1);
            subscribedC2d = true;
        } catch (IOException e) {
            throw new DriverException("Azure IoT Hub lab MQTT subscribe failed for " + filter, e);
        }
    }

    String resolveTelemetryTopic(String mapping) {
        if (mapping != null && mapping.startsWith("devices/")) {
            return mapping;
        }
        String base = "devices/" + deviceId + "/messages/events/";
        if (mapping == null || mapping.isBlank()) {
            return base;
        }
        if (mapping.startsWith("/")) {
            return base + mapping.substring(1);
        }
        return base + mapping;
    }

    boolean matchesC2d(String topic, String mapping) {
        if (topic == null) {
            return false;
        }
        String prefix = "devices/" + deviceId + "/messages/devicebound/";
        if (!topic.startsWith(prefix) && !topic.equals(prefix.substring(0, prefix.length() - 1))) {
            // still allow exact full-topic mappings
            if (mapping != null && !mapping.isBlank() && topic.equals(mapping)) {
                return true;
            }
            return false;
        }
        if (mapping == null || mapping.isBlank()) {
            return true;
        }
        if (topic.equals(mapping) || mapping.equals(topic)) {
            return true;
        }
        if (mapping.startsWith("devices/")) {
            return topic.equals(mapping);
        }
        String suffix = mapping.startsWith("/") ? mapping.substring(1) : mapping;
        return topic.equals(prefix + suffix) || topic.endsWith("/" + suffix) || topic.endsWith(suffix);
    }

    private String resolveMatchedTopic(String mapping, String payload) {
        if (mapping != null && mapping.startsWith("devices/")) {
            return mapping;
        }
        for (String topic : mqtt.lastPayloads().keySet()) {
            if (matchesC2d(topic, mapping)) {
                String value = mqtt.lastPayload(topic);
                if (payload == null || payload.equals(value)) {
                    return topic;
                }
            }
        }
        String prefix = "devices/" + deviceId + "/messages/devicebound/";
        if (mapping == null || mapping.isBlank()) {
            return prefix;
        }
        return mapping.startsWith("/") ? prefix + mapping.substring(1) : prefix + mapping;
    }

    private void onPublish(String topic, String payload) {
        for (Map.Entry<String, String> entry : pointTopics.entrySet()) {
            if (matchesC2d(topic, entry.getValue())) {
                driverObject.updateVariable(entry.getKey(), DataRecord.single(VALUE_SCHEMA, Map.of(
                        "value", payload,
                        "topic", topic
                )));
            }
        }
    }

    private static String extractValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "payload", "data", "text", "raw")) {
            Object candidate = row.get(key);
            if (candidate != null) {
                return String.valueOf(candidate);
            }
        }
        if (row.size() == 1) {
            return String.valueOf(row.values().iterator().next());
        }
        return row.toString();
    }
}
