package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.server.object.pubsub.ObjectWebSocketPathInterestRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * ADR-0029: apply live variable snapshots received from peer replicas into local RAM.
 */
@Service
public class ClusterVariableReplicaApplier {

    private final ObjectManager objectManager;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectWebSocketPathInterestRegistry webSocketPathInterest;

    public ClusterVariableReplicaApplier(
            ObjectManager objectManager,
            ApplicationEventPublisher eventPublisher,
            ObjectWebSocketPathInterestRegistry webSocketPathInterest
    ) {
        this.objectManager = objectManager;
        this.eventPublisher = eventPublisher;
        this.webSocketPathInterest = webSocketPathInterest;
    }

    public void apply(String path, String variableName, DataRecord value, Instant observedAt) {
        if (path == null || path.isBlank() || variableName == null || variableName.isBlank() || value == null) {
            return;
        }
        objectManager.setDriverTelemetryValueDirect(path, variableName, value);
        if (webSocketPathInterest.hasPathInterest(path)) {
            eventPublisher.publishEvent(
                    ObjectChangeEvent.variableUpdatedReplicaIngress(path, variableName, observedAt)
            );
        }
    }
}
