package com.ispf.driver.mqtt;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.ingress.DriverIngress;
import com.ispf.driver.ingress.DriverIngressBuffer;
import com.ispf.driver.ingress.DriverIngressFifoExecutor;
import com.ispf.driver.ingress.IngressElasticSettings;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * MQTT device driver — subscribes to topics and maps payloads to object variables.
 * <p>
 * Callbacks enqueue into a last-value-wins ingress buffer so Paho I/O threads never block on
 * variable updates under flood load.
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

    private static final DataSchema INGRESS_SCHEMA = DataSchema.builder("mqttIngress")
            .field("topic", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final IngressElasticSettings DEFAULT_ELASTIC = new IngressElasticSettings(
            true, 4, 32, 100, 6, 200
    );

    private DriverObject driverObject;
    private MqttClient client;
    private String brokerUrl = "tcp://localhost:1883";
    private String topicPrefix = "";
    private String ingressVariable;
    private String username;
    private String password;
    private int callbackThreads = 4;
    private int callbackQueueCapacity = 10_000;
    private boolean ingressCoalesceEnabled = true;
    private IngressElasticSettings ingressElastic = DEFAULT_ELASTIC;
    private final Map<String, String> subscriptions = new ConcurrentHashMap<>();
    private volatile boolean disconnected;
    private DriverIngressBuffer<String, byte[]> ingressBuffer;
    private DriverIngressFifoExecutor ingressFifo;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
        ingressElastic = IngressElasticSettings.resolve(driverObject.configuration(), DEFAULT_ELASTIC);
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
            case "ingressVariable" -> ingressVariable = value.trim();
            case "username" -> username = value.trim();
            case "password" -> password = value;
            case "callbackThreads" -> callbackThreads = DriverIngress.resolveThreads(Map.of(key, value), callbackThreads);
            case "callbackQueueCapacity" -> callbackQueueCapacity = DriverIngress.resolveCapacity(Map.of(key, value), callbackQueueCapacity);
            case "ingressCoalesceEnabled" -> ingressCoalesceEnabled = DriverIngress.resolveCoalesceEnabled(Map.of(key, value), ingressCoalesceEnabled);
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        try {
            if (ingressCoalesceEnabled) {
                ingressBuffer = new DriverIngressBuffer<>(
                        ingressElastic,
                        callbackQueueCapacity,
                        this::handleMessage,
                        "mqtt-ingress-worker",
                        true
                );
            } else {
                ingressFifo = new DriverIngressFifoExecutor(
                        ingressElastic,
                        callbackQueueCapacity,
                        "mqtt-ingress-fifo",
                        new ThreadPoolExecutor.CallerRunsPolicy()
                );
            }
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
            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    disconnected = false;
                    if (reconnect) {
                        driverObject.log(DriverLogLevel.INFO, "MQTT reconnected: " + serverURI);
                        resubscribeAll();
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    driverObject.log(DriverLogLevel.WARNING, "MQTT connection lost: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    byte[] payload = message.getPayload();
                    byte[] copy = payload == null ? new byte[0] : payload.clone();
                    DriverIngressBuffer<String, byte[]> buffer = ingressBuffer;
                    DriverIngressFifoExecutor fifo = ingressFifo;
                    if (fifo != null) {
                        fifo.execute(() -> handleMessage(topic, copy));
                    } else if (buffer != null) {
                        buffer.submit(topic, copy);
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // no-op
                }
            });
            client.connect(options);
            disconnected = false;
            driverObject.log(DriverLogLevel.INFO, "Connected to MQTT broker: " + brokerUrl);
        } catch (Exception e) {
            shutdownIngress();
            throw new DriverException("MQTT connect failed", e);
        }
    }

    private void handleMessage(String topic, byte[] payloadBytes) {
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        Instant observed = MqttPayloadTimestamps.resolve(payloadBytes);
        String variableName = resolveVariableForTopic(topic);
        if (usesIngressSchema(variableName)) {
            driverObject.updateVariable(variableName, DataRecord.single(
                    INGRESS_SCHEMA,
                    Map.of("topic", topic, "raw", payload)
            ), observed);
        } else {
            driverObject.updateVariable(variableName, DataRecord.single(
                    PAYLOAD_SCHEMA,
                    Map.of("raw", payload)
            ), observed);
        }
    }

    @Override
    public void disconnect() {
        disconnected = true;
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
                client.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
        shutdownIngress();
    }

    private void shutdownIngress() {
        shutdownIngressBuffer();
        shutdownIngressFifo();
    }

    private void shutdownIngressFifo() {
        DriverIngressFifoExecutor fifo = ingressFifo;
        ingressFifo = null;
        if (fifo != null) {
            fifo.close();
        }
    }

    private void shutdownIngressBuffer() {
        DriverIngressBuffer<String, byte[]> buffer = ingressBuffer;
        ingressBuffer = null;
        if (buffer != null) {
            buffer.shutdown();
        }
    }

    @Override
    public boolean isConnected() {
        return !disconnected && client != null && client.isConnected();
    }

    private void resubscribeAll() {
        if (!isConnected() || subscriptions.isEmpty()) {
            return;
        }
        for (String topic : subscriptions.keySet()) {
            try {
                client.subscribe(topic);
            } catch (Exception e) {
                driverObject.log(DriverLogLevel.WARNING, "Resubscribe failed: " + topic + " — " + e.getMessage());
            }
        }
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String topic = topicPrefix + entry.getValue();
            String variableName = resolveMappedVariable(entry.getKey());
            try {
                client.subscribe(topic);
                subscriptions.put(topic, variableName);
            } catch (Exception e) {
                throw new DriverException("Subscribe failed: " + topic, e);
            }
        }
    }

    private String resolveMappedVariable(String mappingKey) {
        if (ingressVariable != null && !ingressVariable.isBlank()) {
            return ingressVariable;
        }
        return mappingKey;
    }

    private String resolveVariableForTopic(String arrivedTopic) {
        if (ingressVariable != null && !ingressVariable.isBlank()) {
            return ingressVariable;
        }
        String direct = subscriptions.get(arrivedTopic);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> entry : subscriptions.entrySet()) {
            if (mqttTopicMatches(entry.getKey(), arrivedTopic)) {
                return entry.getValue();
            }
        }
        return "lastMessage";
    }

    private static boolean mqttTopicMatches(String pattern, String topic) {
        if (pattern.equals(topic)) {
            return true;
        }
        String[] patternParts = pattern.split("/", -1);
        String[] topicParts = topic.split("/", -1);
        int patternIndex = 0;
        int topicIndex = 0;
        while (patternIndex < patternParts.length && topicIndex < topicParts.length) {
            String patternPart = patternParts[patternIndex];
            if ("#".equals(patternPart)) {
                return true;
            }
            if ("+".equals(patternPart)) {
                patternIndex++;
                topicIndex++;
                continue;
            }
            if (!patternPart.equals(topicParts[topicIndex])) {
                return false;
            }
            patternIndex++;
            topicIndex++;
        }
        return patternIndex == patternParts.length && topicIndex == topicParts.length;
    }

    private boolean usesIngressSchema(String variableName) {
        if (ingressVariable != null && !ingressVariable.isBlank()) {
            return ingressVariable.equals(variableName);
        }
        return "lastIngress".equals(variableName);
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
