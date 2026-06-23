package com.ispf.driver.mqtt;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQTT device driver — subscribes to topics and maps payloads to object variables.
 */
public class MqttDeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "mqtt",
            "MQTT Driver",
            "0.1.0",
            "Connects to MQTT brokers and maps topic payloads to ISPF variables",
            "ISPF",
            Map.of(
                    "brokerUrl", "tcp://localhost:1883",
                    "topicPrefix", "ispf/devices/"
            )
    );

    private static final DataSchema PAYLOAD_SCHEMA = DataSchema.builder("mqttPayload")
            .field("raw", FieldType.STRING)
            .build();

    private DriverObject driverObject;
    private MqttClient client;
    private String brokerUrl = "tcp://localhost:1883";
    private String topicPrefix = "";
    private String username;
    private String password;
    private final Map<String, String> subscriptions = new ConcurrentHashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
        driverObject.getVariable("topicPrefix").ifPresent(record -> {
            Object raw = record.firstRow().get("raw");
            if (raw != null && !raw.toString().isBlank()) {
                topicPrefix = raw.toString();
            }
        });
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "brokerUrl" -> brokerUrl = value.trim();
            case "topicPrefix" -> topicPrefix = value.trim();
            case "username" -> username = value.trim();
            case "password" -> password = value;
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            client = new MqttClient(brokerUrl, "ispf-driver-" + UUID.randomUUID(), new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            if (username != null && !username.isBlank()) {
                options.setUserName(username);
                if (password != null) {
                    options.setPassword(password.toCharArray());
                }
            }
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    connected = false;
                    driverObject.log(DriverLogLevel.WARNING, "MQTT connection lost: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    String variableName = subscriptions.getOrDefault(topic, "lastMessage");
                    driverObject.updateVariable(variableName, DataRecord.single(
                            PAYLOAD_SCHEMA,
                            Map.of("raw", payload)
                    ));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // no-op
                }
            });
            client.connect(options);
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "Connected to MQTT broker: " + brokerUrl);
        } catch (Exception e) {
            throw new DriverException("MQTT connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
                client.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
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
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String topic = topicPrefix + entry.getValue();
            try {
                client.subscribe(topic);
                subscriptions.put(topic, entry.getKey());
            } catch (Exception e) {
                throw new DriverException("Subscribe failed: " + topic, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String topic = topicPrefix + pointId;
        Object raw = value.firstRow().get("raw");
        try {
            client.publish(topic, new MqttMessage(String.valueOf(raw).getBytes()));
        } catch (Exception e) {
            throw new DriverException("Publish failed: " + topic, e);
        }
    }
}
