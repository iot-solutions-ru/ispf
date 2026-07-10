package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.server.object.pubsub.VariableChangeSubscriptionRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * ADR-0029: apply live variable snapshots received from peer replicas into local RAM.
 * <p>
 * ADR-0024: no live observer → no RAM/WS update on this replica (historian stays on owner DB path).
 */
@Service
public class ClusterVariableReplicaApplier {

    private final ObjectManager objectManager;
    private final ApplicationEventPublisher eventPublisher;
    private final VariableChangeSubscriptionRegistry variableSubscriptionRegistry;

    public ClusterVariableReplicaApplier(
            ObjectManager objectManager,
            ApplicationEventPublisher eventPublisher,
            VariableChangeSubscriptionRegistry variableSubscriptionRegistry
    ) {
        this.objectManager = objectManager;
        this.eventPublisher = eventPublisher;
        this.variableSubscriptionRegistry = variableSubscriptionRegistry;
    }

    public void apply(String path, String variableName, DataRecord value, Instant observedAt) {
        if (path == null || path.isBlank() || variableName == null || variableName.isBlank() || value == null) {
            return;
        }
        if (!variableSubscriptionRegistry.interest(path, variableName).liveObserver()) {
            return;
        }
        objectManager.setDriverTelemetryValueDirect(path, variableName, value);
        eventPublisher.publishEvent(
                ObjectChangeEvent.variableUpdatedReplicaIngress(path, variableName, observedAt)
        );
    }
}
