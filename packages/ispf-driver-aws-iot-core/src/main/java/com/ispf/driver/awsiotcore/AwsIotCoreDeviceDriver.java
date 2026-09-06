package com.ispf.driver.awsiotcore;

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
 * AWS IoT Core–shaped MQTT lab driver ({@code aws-iot-core}).
 * <p>
 * <strong>Honesty:</strong> this is a lab MQTT 3.1.1 device client that uses AWS IoT–style
 * topic mappings over plain TCP. It is <strong>not</strong> a full AWS IoT Core SDK —
 * no SigV4, no TLS requirement in lab, no shadow/jobs HTTP APIs.
 * <p>
 * Configuration:
 * <ul>
 *   <li>{@code host} — broker / IoT endpoint hostname</li>
 *   <li>{@code clientId} — MQTT client id</li>
 *   <li>{@code port} — MQTT port (default {@code 1883} plain lab)</li>
 *   <li>{@code timeoutMs} — connect / ack / read wait</li>
 * </ul>
 * Topic conventions:
 * <ul>
 *   <li>Write: publish QoS1 to the point mapping topic (e.g. {@code dt/{clientId}/sensor})</li>
 *   <li>Read: subscribe to the mapping topic; record {@code value} is the last payload string</li>
 * </ul>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only (no AWS SDK).
 */
public class AwsIotCoreDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("awsIotCoreValue")
            .field("value", FieldType.STRING)
            .field("topic", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "aws-iot-core",
            "AWS IoT Core Driver",
            "0.1.0",
            "Lab MQTT client with AWS IoT–shaped topic mappings (not full IoT Core SDK)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "clientId", "ispf-aws-iot",
                    "port", "1883",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private String clientId = "ispf-aws-iot";
    private int port = 1883;
    private int timeoutMs = 3000;

    private Mqtt311Lab mqtt;
    private final Map<String, String> pointTopics = new ConcurrentHashMap<>();
    private final Set<String> subscribed = ConcurrentHashMap.newKeySet();

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
            case "clientId" -> clientId = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        try {
            mqtt = new Mqtt311Lab(host, port, timeoutMs, clientId);
            mqtt.addListener(this::onPublish);
            mqtt.connect();
            driverObject.log(DriverLogLevel.INFO,
                    "AWS IoT Core lab MQTT connected to " + host + ":" + port
                            + " as clientId=" + clientId);
        } catch (IOException e) {
            disconnect();
            throw new DriverException("AWS IoT Core lab MQTT connect failed for "
                    + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        pointTopics.clear();
        subscribed.clear();
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
        pointTopics.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String topic = entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId
                    : entry.getValue().trim();
            pointTopics.put(pointId, topic);
            ensureSubscribed(topic);
            String payload;
            try {
                payload = mqtt.awaitPayload(topic, timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DriverException("Interrupted waiting for MQTT on " + topic, e);
            }
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
        String topic = pointTopics.getOrDefault(pointId, pointId);
        String payload = extractValue(value);
        try {
            mqtt.publish(topic, payload, 1);
        } catch (IOException e) {
            throw new DriverException("AWS IoT Core lab MQTT publish failed for " + topic, e);
        }
        pointTopics.put(pointId, topic);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", payload,
                "topic", topic
        )));
    }

    private void ensureSubscribed(String topic) throws DriverException {
        if (subscribed.contains(topic)) {
            return;
        }
        try {
            mqtt.subscribe(topic, 1);
            subscribed.add(topic);
        } catch (IOException e) {
            throw new DriverException("AWS IoT Core lab MQTT subscribe failed for " + topic, e);
        }
    }

    private void onPublish(String topic, String payload) {
        for (Map.Entry<String, String> entry : pointTopics.entrySet()) {
            if (topic.equals(entry.getValue())) {
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
