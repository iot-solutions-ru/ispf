package com.ispf.server.cluster;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.config.ReplicaCapability;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStreamSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Subscribes to NATS object-change fanout and republishes locally (cross-replica WebSocket sync).
 * ADR-0029: applies live value snapshots when present in payload.
 */
@Component
@ConditionalOnProperty(prefix = "ispf.nats", name = "enabled", havingValue = "true")
public class NatsObjectChangeSubscriber {

    private static final Logger log = LoggerFactory.getLogger(NatsObjectChangeSubscriber.class);

    private final NatsProperties properties;
    private final ClusterProperties clusterProperties;
    private final Connection connection;
    private final NatsJetStreamSupport jetStreamSupport;
    private final NatsReplicaEventProcessor replicaEventProcessor;

    public NatsObjectChangeSubscriber(
            NatsProperties properties,
            ClusterProperties clusterProperties,
            NatsEventBridge eventBridge,
            NatsJetStreamSupport jetStreamSupport,
            NatsReplicaEventProcessor replicaEventProcessor
    ) {
        this.properties = properties;
        this.clusterProperties = clusterProperties;
        this.connection = eventBridge.connection();
        this.jetStreamSupport = jetStreamSupport;
        this.replicaEventProcessor = replicaEventProcessor;
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
        Dispatcher dispatcher = connection.createDispatcher(this::enqueueMessage);
        if (properties.jetStreamEnabled() && jetStreamSupport.isActive()) {
            try {
                JetStreamSubscription subscription = jetStreamSupport.subscribeReplicaEvents(
                        dispatcher,
                        this::enqueueMessage
                );
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

    private void enqueueMessage(io.nats.client.Message message) {
        if (message == null) {
            return;
        }
        replicaEventProcessor.offer(message.getData());
    }
}
