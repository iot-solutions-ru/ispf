package com.ispf.server.workflow;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.config.ReplicaCapability;
import com.ispf.server.object.ClusterVariableReplicaApplier;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStreamSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Subscribes to NATS object-change fanout and republishes locally (cross-replica WebSocket sync).
 * ADR-0029: applies live value snapshots when present in payload.
 */
@Component
public class NatsObjectChangeSubscriber {

    private static final Logger log = LoggerFactory.getLogger(NatsObjectChangeSubscriber.class);

    private final NatsProperties properties;
    private final ClusterProperties clusterProperties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Connection connection;
    private final NatsJetStreamSupport jetStreamSupport;
    private final ClusterVariableReplicaApplier replicaApplier;

    public NatsObjectChangeSubscriber(
            NatsProperties properties,
            ClusterProperties clusterProperties,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            NatsEventBridge eventBridge,
            NatsJetStreamSupport jetStreamSupport,
            ClusterVariableReplicaApplier replicaApplier
    ) {
        this.properties = properties;
        this.clusterProperties = clusterProperties;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.connection = eventBridge.connection();
        this.jetStreamSupport = jetStreamSupport;
        this.replicaApplier = replicaApplier;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(130)
    public void subscribe() {
        if (!properties.enabled() || !properties.replicaEventsEnabled() || connection == null) {
            return;
        }
        if (clusterProperties.enabled() && !clusterProperties.hasCapability(ReplicaCapability.REPLICA_SYNC)) {
            log.info("NATS replica subscribe skipped (replica-sync capability disabled, replicaId={})",
                    properties.replicaId());
            return;
        }
        Dispatcher dispatcher = connection.createDispatcher(this::handleMessage);
        if (properties.jetStreamEnabled() && jetStreamSupport.isActive()) {
            try {
                JetStreamSubscription subscription = jetStreamSupport.subscribeReplicaEvents(dispatcher, this::handleMessage);
                if (subscription != null) {
                    log.info(
                            "Subscribed to JetStream replica events (stream={}, replicaId={})",
                            properties.jetStreamStreamName(),
                            properties.replicaId()
                    );
                    return;
                }
            } catch (Exception ex) {
                log.warn("JetStream replica subscribe failed, falling back to core NATS: {}", ex.getMessage());
            }
        }
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
            if (type == ObjectChangeType.VARIABLE_UPDATED && variableName != null && payload.containsKey("value")) {
                DataRecord value = objectMapper.convertValue(payload.get("value"), DataRecord.class);
                Instant observedAt = parseInstant(payload.get("observedAt"));
                replicaApplier.apply(path, variableName, value, observedAt);
                return;
            }
            ObjectChangeEvent event = type == ObjectChangeType.VARIABLE_UPDATED && variableName != null
                    ? ObjectChangeEvent.variableUpdated(path, variableName)
                    : ObjectChangeEvent.of(type, path);
            eventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("Failed to handle NATS replica event: {}", ex.getMessage());
        }
    }

    private static Instant parseInstant(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(String.valueOf(raw));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
