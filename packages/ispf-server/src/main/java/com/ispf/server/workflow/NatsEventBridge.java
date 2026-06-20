package com.ispf.server.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.object.ObjectChangeEvent;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class NatsEventBridge {

    private static final Logger log = LoggerFactory.getLogger(NatsEventBridge.class);

    private final NatsProperties properties;
    private final ObjectMapper objectMapper;
    private final Connection connection;

    public NatsEventBridge(NatsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.connection = properties.enabled() ? connect() : null;
    }

    public Connection connection() {
        return connection;
    }

    @EventListener
    public void onObjectChange(ObjectChangeEvent event) {
        if (!properties.enabled() || connection == null) {
            return;
        }
        try {
            String subject = "ispf.object." + sanitize(event.path()) + "." + event.type().name().toLowerCase();
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("type", event.type().name());
            body.put("path", event.path());
            if (event.variableName() != null) {
                body.put("variableName", event.variableName());
            }
            body.put("timestamp", event.timestamp().toString());
            body.put("source", properties.replicaId());
            String payload = objectMapper.writeValueAsString(body);
            connection.publish(subject, payload.getBytes(StandardCharsets.UTF_8));
            if (properties.replicaEventsEnabled()) {
                connection.publish(
                        "ispf.events." + event.type().name().toLowerCase(),
                        payload.getBytes(StandardCharsets.UTF_8)
                );
            }
        } catch (Exception e) {
            log.warn("Failed to publish NATS event for {}: {}", event.path(), e.getMessage());
        }
    }

    public void publish(String subject, String message) {
        if (!properties.enabled() || connection == null) {
            log.info("[nats:disabled] {} -> {}", subject, message);
            return;
        }
        try {
            connection.publish(subject, message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to publish NATS message to {}: {}", subject, e.getMessage());
        }
    }

    public void publishWorkflowEvent(String workflowPath, String event, Map<String, Object> payload) {
        try {
            Map<String, Object> body = new java.util.HashMap<>(payload);
            body.put("workflowPath", workflowPath);
            body.put("event", event);
            body.put("source", properties.replicaId());
            publish("ispf.workflow." + sanitize(workflowPath) + "." + event, objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            log.warn("Failed to publish workflow NATS event: {}", e.getMessage());
        }
    }

    public boolean isEnabled() {
        return properties.enabled() && connection != null;
    }

    @PreDestroy
    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Connection connect() {
        try {
            Options options = new Options.Builder()
                    .server(properties.url())
                    .connectionName("ispf-server-" + properties.replicaId().substring(0, 8))
                    .connectionTimeout(Duration.ofSeconds(2))
                    .maxReconnects(3)
                    .build();
            Connection conn = Nats.connect(options);
            log.info("Connected to NATS at {}", properties.url());
            return conn;
        } catch (Exception e) {
            log.warn("NATS connection failed ({}). Event bridge will run in no-op mode.", e.getMessage());
            return null;
        }
    }

    private static String sanitize(String path) {
        return path.replace('.', '_');
    }
}
