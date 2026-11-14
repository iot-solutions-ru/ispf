package com.ispf.server.application.data;

import com.ispf.server.cluster.NatsJetStreamSupport;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Delivers an outbox row on NATS JetStream. The message id is the JetStream
 * publish id so a replay of the same row is deduplicated by the stream.
 */
public final class NatsOutboxDelivery implements ApplicationMessageRelay.Delivery {

    private final NatsJetStreamSupport jetStream;
    private final Set<String> connectors;

    public NatsOutboxDelivery(NatsJetStreamSupport jetStream, Set<String> connectors) {
        this.jetStream = jetStream;
        this.connectors = Set.copyOf(connectors);
    }

    public static Set<String> parseConnectors(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        java.util.LinkedHashSet<String> parsed = new java.util.LinkedHashSet<>();
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                parsed.add(trimmed);
            }
        }
        return Set.copyOf(parsed);
    }

    public Set<String> connectors() {
        return connectors;
    }

    @Override
    public void deliver(String appId, String connectorId, String messageId, String body) {
        if (!connectors.contains(connectorId)) {
            throw new IllegalStateException("no NATS outbox connector " + connectorId);
        }
        try {
            jetStream.publishOutbox(
                    connectorId,
                    messageId,
                    (body == null ? "" : body).getBytes(StandardCharsets.UTF_8)
            );
        } catch (NatsJetStreamSupport.OutboxPublishException ex) {
            throw new RetryableOutboxDelivery("outbox NATS failed for " + messageId + ": " + ex.getMessage());
        }
    }
}
