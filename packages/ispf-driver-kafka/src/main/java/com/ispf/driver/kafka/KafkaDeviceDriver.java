package com.ispf.driver.kafka;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka driver — consume latest message or produce to a topic.
 * <p>
 * With {@code eventToVariable=true}, consumed records update per-message-key variables (MQTT-style).
 */
public class KafkaDeviceDriver implements DeviceDriver {

    private static final DataSchema MESSAGE_SCHEMA = DataSchema.builder("kafkaMessage")
            .field("value", FieldType.STRING)
            .field("offset", FieldType.LONG)
            .field("partition", FieldType.INTEGER)
            .build();

    private static final DataSchema INGRESS_SCHEMA = DataSchema.builder("kafkaIngress")
            .field("topic", FieldType.STRING)
            .field("key", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "kafka",
            "Kafka Driver",
            "0.1.0",
            "Polls Kafka topics for latest messages or produces payloads",
            "ISPF",
            Map.of(
                    "bootstrapServers", "localhost:9092",
                    "topic", "ispf/events",
                    "groupId", "ispf-driver",
                    "timeoutMs", "5000",
                    "eventToVariable", "false"
            )
    );

    private DriverObject driverObject;
    private String bootstrapServers = "localhost:9092";
    private String topic = "ispf/events";
    private String groupId = "ispf-driver";
    private long timeoutMs = 5000;
    private String ingressVariable;
    private boolean eventToVariable;
    private KafkaConsumer<String, String> consumer;
    private KafkaProducer<String, String> producer;
    private final Map<String, KafkaPoint> points = new ConcurrentHashMap<>();
    private final Map<String, String> keyVariableNames = new ConcurrentHashMap<>();
    private volatile boolean connected;
    private ExecutorService consumerExecutor;
    private final AtomicBoolean consumerRunning = new AtomicBoolean();

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
            case "bootstrapServers" -> bootstrapServers = value.trim();
            case "topic" -> topic = value.trim();
            case "groupId" -> groupId = value.trim();
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            case "ingressVariable" -> ingressVariable = value.trim();
            case "eventToVariable" -> eventToVariable = Boolean.parseBoolean(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        releaseResources();
        try {
            Properties consumerProps = new Properties();
            consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG,
                    groupId == null || groupId.isBlank() ? "ispf-" + UUID.randomUUID() : groupId);
            consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
            consumer = new KafkaConsumer<>(consumerProps);
            consumer.subscribe(Collections.singletonList(topic));

            Properties producerProps = new Properties();
            producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producer = new KafkaProducer<>(producerProps);

            connected = true;
            if (eventToVariable) {
                startConsumerLoop();
            }
            driverObject.log(DriverLogLevel.INFO,
                    "Kafka ready (bootstrap=" + bootstrapServers + ", topic=" + topic + ")");
        } catch (Exception e) {
            releaseResources();
            throw new DriverException("Kafka connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        releaseResources();
    }

    private void releaseResources() {
        stopConsumerLoop();
        connected = false;
        if (consumer != null) {
            consumer.close();
            consumer = null;
        }
        if (producer != null) {
            producer.close();
            producer = null;
        }
    }

    private void startConsumerLoop() {
        if (!consumerRunning.compareAndSet(false, true)) {
            return;
        }
        consumerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "kafka-driver-consumer");
            thread.setDaemon(true);
            return thread;
        });
        consumerExecutor.submit(this::consumeLoop);
    }

    private void stopConsumerLoop() {
        consumerRunning.set(false);
        if (consumerExecutor != null) {
            consumerExecutor.shutdownNow();
            consumerExecutor = null;
        }
    }

    private void consumeLoop() {
        while (consumerRunning.get() && connected && consumer != null) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    handleRecord(record);
                }
            } catch (Exception e) {
                if (consumerRunning.get()) {
                    driverObject.log(DriverLogLevel.WARNING, "Kafka consume loop: " + e.getMessage());
                }
            }
        }
    }

    private void handleRecord(ConsumerRecord<String, String> record) {
        String variableName = resolveVariableForRecord(record);
        String payload = record.value() == null ? "" : record.value();
        if (usesIngressSchema(variableName)) {
            driverObject.updateVariable(variableName, DataRecord.single(
                    INGRESS_SCHEMA,
                    Map.of(
                            "topic", topic,
                            "key", record.key() == null ? "" : record.key(),
                            "raw", payload
                    )
            ));
            return;
        }
        driverObject.updateVariable(variableName, DataRecord.single(
                MESSAGE_SCHEMA,
                Map.of(
                        "value", payload,
                        "offset", record.offset(),
                        "partition", record.partition()
                )
        ));
    }

    private String resolveVariableForRecord(ConsumerRecord<String, String> record) {
        if (ingressVariable != null && !ingressVariable.isBlank()) {
            return ingressVariable;
        }
        if (eventToVariable) {
            String key = record.key();
            if (key != null && !key.isBlank()) {
                return keyToVariableName(key);
            }
            return "partition_" + record.partition();
        }
        for (Map.Entry<String, KafkaPoint> entry : points.entrySet()) {
            if (entry.getValue().mode() == KafkaPoint.KafkaMode.CONSUME) {
                return entry.getKey();
            }
        }
        return "lastMessage";
    }

    private boolean usesIngressSchema(String variableName) {
        if (ingressVariable != null && !ingressVariable.isBlank()) {
            return ingressVariable.equals(variableName);
        }
        return eventToVariable;
    }

    private String keyToVariableName(String key) {
        return keyVariableNames.computeIfAbsent(key, this::sanitizeKeyToVariableName);
    }

    private String sanitizeKeyToVariableName(String key) {
        String sanitized = key.replaceAll("[^a-zA-Z0-9]+", "_").replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("^_|_$", "");
        if (sanitized.isBlank()) {
            sanitized = "key";
        }
        if (Character.isDigit(sanitized.charAt(0))) {
            sanitized = "v_" + sanitized;
        }
        if (sanitized.length() > 64) {
            sanitized = sanitized.substring(0, 64);
        }
        return sanitized;
    }

    @Override
    public boolean isConnected() {
        return connected && consumer != null && producer != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            KafkaPoint point = KafkaPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
        }
        if (eventToVariable) {
            return;
        }
        for (Map.Entry<String, KafkaPoint> entry : points.entrySet()) {
            driverObject.updateVariable(entry.getKey(), execute(entry.getValue()));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Kafka driver is read-only in v0.1");
    }

    private DataRecord execute(KafkaPoint point) throws DriverException {
        try {
            if (point.mode() == KafkaPoint.KafkaMode.PRODUCE) {
                producer.send(new ProducerRecord<>(topic, point.payload())).get();
                return DataRecord.single(MESSAGE_SCHEMA, Map.of(
                        "value", point.payload(),
                        "offset", -1L,
                        "partition", -1
                ));
            }
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(timeoutMs));
            String value = "";
            long offset = -1L;
            int partition = -1;
            for (ConsumerRecord<String, String> record : records) {
                value = record.value() == null ? "" : record.value();
                offset = record.offset();
                partition = record.partition();
            }
            return DataRecord.single(MESSAGE_SCHEMA, Map.of(
                    "value", value,
                    "offset", offset,
                    "partition", partition
            ));
        } catch (Exception e) {
            throw new DriverException("Kafka operation failed", e);
        }
    }
}
