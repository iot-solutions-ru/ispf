package com.ispf.server.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.ObjectChangeType;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Subscribes to NATS object-change fanout and republishes locally (cross-replica WebSocket sync).
 */
@Component
public class NatsObjectChangeSubscriber {

    private static final Logger log = LoggerFactory.getLogger(NatsObjectChangeSubscriber.class);

    private final NatsProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Connection connection;

    public NatsObjectChangeSubscriber(
            NatsProperties properties,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            NatsEventBridge eventBridge
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.connection = eventBridge.connection();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(130)
    public void subscribe() {
        if (!properties.enabled() || !properties.replicaEventsEnabled() || connection == null) {
            return;
        }
        Dispatcher dispatcher = connection.createDispatcher(this::handleMessage);
        dispatcher.subscribe("ispf.events.>");
        log.info("Subscribed to NATS replica events (replicaId={})", properties.replicaId());
    }

    private void handleMessage(io.nats.client.Message message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    message.getData(),
                    new TypeReference<>() {
                    }
            );
            Object source = payload.get("source");
            if (source != null && properties.replicaId().equals(String.valueOf(source))) {
                return;
            }
            ObjectChangeType type = ObjectChangeType.valueOf(String.valueOf(payload.get("type")));
            String path = String.valueOf(payload.get("path"));
            String variableName = payload.get("variableName") != null
                    ? String.valueOf(payload.get("variableName"))
                    : null;
            ObjectChangeEvent event = type == ObjectChangeType.VARIABLE_UPDATED && variableName != null
                    ? ObjectChangeEvent.variableUpdated(path, variableName)
                    : ObjectChangeEvent.of(type, path);
            eventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("Failed to handle NATS replica event: {}", ex.getMessage());
        }
    }
}
