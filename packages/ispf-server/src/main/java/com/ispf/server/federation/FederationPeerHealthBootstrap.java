package com.ispf.server.federation;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.server.object.ObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Idempotent platform events for federation peer health alerting (S27).
 */
@Component
public class FederationPeerHealthBootstrap {

    static final String EVENT_PEER_HEALTH_DEGRADED = "peerHealthDegraded";
    static final String EVENT_PEER_HEALTH_RECOVERED = "peerHealthRecovered";

    static final DataSchema PEER_HEALTH_PAYLOAD = DataSchema.builder("peerHealthPayload")
            .field("peerId", FieldType.STRING)
            .field("peerName", FieldType.STRING)
            .field("level", FieldType.STRING)
            .field("summary", FieldType.STRING)
            .build();

    private static final Logger log = LoggerFactory.getLogger(FederationPeerHealthBootstrap.class);

    private final ObjectManager objectManager;

    public FederationPeerHealthBootstrap(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE - 5)
    public void ensureHealthEvents() {
        if (objectManager.tree().findByPath(FederationPaths.FEDERATION_ROOT).isEmpty()) {
            return;
        }
        objectManager.upsertEvent(
                FederationPaths.FEDERATION_ROOT,
                new EventDescriptor(
                        EVENT_PEER_HEALTH_DEGRADED,
                        "Federation peer health degraded to RED",
                        PEER_HEALTH_PAYLOAD,
                        EventLevel.WARNING
                )
        );
        objectManager.upsertEvent(
                FederationPaths.FEDERATION_ROOT,
                new EventDescriptor(
                        EVENT_PEER_HEALTH_RECOVERED,
                        "Federation peer health recovered to GREEN",
                        PEER_HEALTH_PAYLOAD,
                        EventLevel.INFO
                )
        );
        log.debug("Federation peer health events ensured on {}", FederationPaths.FEDERATION_ROOT);
    }
}
