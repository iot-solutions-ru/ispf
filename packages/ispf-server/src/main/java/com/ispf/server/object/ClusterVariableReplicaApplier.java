package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
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

    public ClusterVariableReplicaApplier(ObjectManager objectManager, ApplicationEventPublisher eventPublisher) {
        this.objectManager = objectManager;
        this.eventPublisher = eventPublisher;
    }

    public void apply(String path, String variableName, DataRecord value, Instant observedAt) {
        if (path == null || path.isBlank() || variableName == null || variableName.isBlank() || value == null) {
            return;
        }
        objectManager.setDriverTelemetryValueDirect(path, variableName, value);
        eventPublisher.publishEvent(
                ObjectChangeEvent.variableUpdatedReplicaIngress(path, variableName, observedAt)
        );
    }
}
