package com.ispf.server.application.data;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Delivers an outbox row to Kafka. The message id is the record key so a
 * consumer can dedupe. The producer is idempotent with acks=all.
 */
public final class KafkaOutboxDelivery implements ApplicationMessageRelay.Delivery, AutoCloseable {

    private final Producer<String, String> producer;
    private final Map<String, String> topics;
    private final Duration sendTimeout;

    public KafkaOutboxDelivery(String bootstrapServers, Map<String, String> topics) {
        this(newProducer(bootstrapServers), topics, Duration.ofSeconds(5));
    }

    KafkaOutboxDelivery(Producer<String, String> producer, Map<String, String> topics, Duration sendTimeout) {
        this.producer = producer;
        this.topics = Map.copyOf(topics);
        this.sendTimeout = sendTimeout;
    }

    public static Map<String, String> parseTopics(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        LinkedHashMap<String, String> parsed = new LinkedHashMap<>();
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int split = trimmed.indexOf('=');
            if (split <= 0 || split == trimmed.length() - 1) {
                throw new IllegalArgumentException("outbox kafka topic must be connector=topic: " + trimmed);
            }
            parsed.put(trimmed.substring(0, split).trim(), trimmed.substring(split + 1).trim());
        }
        return Map.copyOf(parsed);
    }

    public Map<String, String> topics() {
        return topics;
    }

    private static Producer<String, String> newProducer(String bootstrapServers) {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalArgumentException("kafka bootstrap servers are required");
        }
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, "ispf-application-outbox");
        return new KafkaProducer<>(properties);
    }

    @Override
    public void deliver(String appId, String connectorId, String messageId, String body) {
        String topic = topics.get(connectorId);
        if (topic == null) {
            throw new IllegalStateException("no Kafka topic for connector " + connectorId);
        }
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topic,
                null,
                messageId,
                body == null ? "" : body
        );
        record.headers().add("X-Ispf-App", appId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Future<RecordMetadata> future = producer.send(record);
        try {
            future.get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new RetryableOutboxDelivery("outbox Kafka timed out for " + messageId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RetryableOutboxDelivery("outbox Kafka interrupted for " + messageId);
        } catch (ExecutionException ex) {
            throw new RetryableOutboxDelivery(
                    "outbox Kafka failed for " + messageId + ": " + ex.getCause().getMessage());
        }
    }

    @Override
    public void close() {
        producer.close(Duration.ofSeconds(2));
    }
}
