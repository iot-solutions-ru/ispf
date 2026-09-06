package com.ispf.driver.sparkplugb;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQTT Sparkplug B host driver — subscribes to NBIRTH/DBIRTH/DDATA and maps metrics to variables.
 * <p>
 * Point mapping is the Sparkplug metric name. {@code writePoint} publishes a DCMD payload with that
 * metric. Clean-room ISPF code, Apache-2.0 — Eclipse Paho MQTT client + minimal protobuf codec.
 */
public class SparkplugBDeviceDriver implements DeviceDriver {

    private static final DataSchema METRIC_SCHEMA = DataSchema.builder("sparkplugMetric")
            .field("value", FieldType.STRING)
            .field("metric", FieldType.STRING)
            .field("datatype", FieldType.INTEGER)
            .field("topic", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "sparkplug-b",
            "MQTT Sparkplug B Driver",
            "0.1.0",
            "Sparkplug B host: MQTT subscribe NBIRTH/DBIRTH/DDATA, DCMD write, metric name mapping",
            "ISPF",
            Map.of(
                    "brokerUrl", "tcp://localhost:1883",
                    "groupId", "OpenIndustry",
                    "edgeNode", "EdgeNode1",
                    "deviceId", "Device1",
                    "clientId", ""
            ),
            null,
            Set.of("read", "write", "subscribe")
    );

    private DriverObject driverObject;
    private MqttClient client;
    private String brokerUrl = "tcp://localhost:1883";
    private String groupId = "OpenIndustry";
    private String edgeNode = "EdgeNode1";
    private String deviceId = "Device1";
    private String clientId = "";
    private String username;
    private String password;
    /** metric name → point id */
    private final Map<String, String> metricToPoint = new ConcurrentHashMap<>();
    private volatile boolean connected;
    private volatile boolean subscribed;

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
            case "brokerUrl" -> brokerUrl = value.trim();
            case "groupId" -> groupId = value.trim();
            case "edgeNode" -> edgeNode = value.trim();
            case "deviceId" -> deviceId = value.trim();
            case "clientId" -> clientId = value.trim();
            case "username" -> username = value.trim();
            case "password" -> password = value;
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        closeClient();
        try {
            String id = clientId == null || clientId.isBlank()
                    ? "ispf-sparkplug-" + UUID.randomUUID()
                    : clientId;
            client = new MqttClient(brokerUrl, id, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            if (username != null && !username.isBlank()) {
                options.setUserName(username);
                if (password != null) {
                    options.setPassword(password.toCharArray());
                }
            }
            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    connected = true;
                    if (reconnect) {
                        try {
                            subscribeSparkplugTopics();
                        } catch (Exception e) {
                            driverObject.log(DriverLogLevel.WARNING, "Sparkplug resubscribe failed: " + e.getMessage());
                        }
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    connected = false;
                    subscribed = false;
                    driverObject.log(DriverLogLevel.WARNING, "Sparkplug MQTT connection lost: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleSparkplugMessage(topic, message.getPayload());
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });
            client.connect(options);
            connected = true;
            subscribed = false;
            driverObject.log(DriverLogLevel.INFO, "Sparkplug B connected to " + brokerUrl + " group=" + groupId);
        } catch (Exception e) {
            closeClient();
            throw new DriverException("Sparkplug B connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        subscribed = false;
        metricToPoint.clear();
        closeClient();
    }

    @Override
    public boolean isConnected() {
        return connected && client != null && client.isConnected();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        metricToPoint.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String metric = entry.getValue() == null || entry.getValue().isBlank()
                    ? entry.getKey()
                    : entry.getValue().trim();
            metricToPoint.put(metric, entry.getKey());
        }
        try {
            subscribeSparkplugTopics();
        } catch (Exception e) {
            throw new DriverException("Sparkplug B subscribe failed", e);
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String metricName = metricNameForPoint(pointId);
        Object raw = extractValue(value);
        int dataType = SparkplugBCodec.inferDataType(raw);
        Object coerced = SparkplugBCodec.coerce(dataType, raw);
        SparkplugBCodec.Payload payload = new SparkplugBCodec.Payload(
                System.currentTimeMillis(),
                List.of(new SparkplugBCodec.Metric(metricName, dataType, coerced)),
                null
        );
        String topic = "spBv1.0/" + groupId + "/DCMD/" + edgeNode + "/" + deviceId;
        try {
            client.publish(topic, new MqttMessage(SparkplugBCodec.encode(payload)));
            driverObject.updateVariable(pointId, DataRecord.single(METRIC_SCHEMA, Map.of(
                    "value", String.valueOf(coerced),
                    "metric", metricName,
                    "datatype", dataType,
                    "topic", topic
            )));
        } catch (Exception e) {
            throw new DriverException("Sparkplug B DCMD publish failed", e);
        }
    }

    private void subscribeSparkplugTopics() throws Exception {
        if (client == null || !client.isConnected()) {
            return;
        }
        String base = "spBv1.0/" + groupId + "/";
        client.subscribe(base + "DDATA/#", 0);
        client.subscribe(base + "NBIRTH/#", 0);
        client.subscribe(base + "DBIRTH/#", 0);
        subscribed = true;
        driverObject.log(DriverLogLevel.INFO, "Sparkplug subscribed to " + base + "{DDATA,NBIRTH,DBIRTH}/#");
    }

    private void handleSparkplugMessage(String topic, byte[] payloadBytes) {
        if (metricToPoint.isEmpty()) {
            return;
        }
        try {
            SparkplugBCodec.Payload payload = SparkplugBCodec.decode(payloadBytes);
            for (SparkplugBCodec.Metric metric : payload.metrics()) {
                String pointId = metricToPoint.get(metric.name());
                if (pointId == null) {
                    continue;
                }
                driverObject.updateVariable(pointId, DataRecord.single(METRIC_SCHEMA, Map.of(
                        "value", String.valueOf(metric.value()),
                        "metric", metric.name(),
                        "datatype", metric.dataType(),
                        "topic", topic
                )));
            }
        } catch (Exception e) {
            driverObject.log(DriverLogLevel.WARNING, "Sparkplug payload decode failed: " + e.getMessage());
        }
    }

    private String metricNameForPoint(String pointId) {
        for (Map.Entry<String, String> entry : metricToPoint.entrySet()) {
            if (entry.getValue().equals(pointId)) {
                return entry.getKey();
            }
        }
        return pointId;
    }

    private static Object extractValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "raw", "payload", "data", "text")) {
            Object candidate = row.get(key);
            if (candidate != null) {
                return candidate;
            }
        }
        if (row.size() == 1) {
            return row.values().iterator().next();
        }
        return row.toString();
    }

    private void closeClient() {
        if (client != null) {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                }
            } catch (Exception ignored) {
            }
            try {
                client.close();
            } catch (Exception ignored) {
            }
            client = null;
        }
    }
}
