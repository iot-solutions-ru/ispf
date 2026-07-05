package com.ispf.server.object.pubsub;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.ObjectChangeProperties;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

/**
 * ADR-0024: publish {@link ObjectChangeEvent} only when subscribers exist.
 */
@Service
public class ObjectChangePublicationService {

    private final ApplicationEventPublisher eventPublisher;
    private final VariableChangeSubscriptionRegistry variableSubscriptionRegistry;
    private final EventFiredSubscriptionRegistry eventFiredSubscriptionRegistry;
    private final StructureChangeSubscriptionRegistry structureSubscriptionRegistry;
    private final DeviceTelemetryPolicyService telemetryPolicyService;
    private final ObjectChangeProperties objectChangeProperties;
    private final ClusterProperties clusterProperties;

    public ObjectChangePublicationService(
            ApplicationEventPublisher eventPublisher,
            VariableChangeSubscriptionRegistry variableSubscriptionRegistry,
            EventFiredSubscriptionRegistry eventFiredSubscriptionRegistry,
            StructureChangeSubscriptionRegistry structureSubscriptionRegistry,
            DeviceTelemetryPolicyService telemetryPolicyService,
            ObjectChangeProperties objectChangeProperties,
            ClusterProperties clusterProperties
    ) {
        this.eventPublisher = eventPublisher;
        this.variableSubscriptionRegistry = variableSubscriptionRegistry;
        this.eventFiredSubscriptionRegistry = eventFiredSubscriptionRegistry;
        this.structureSubscriptionRegistry = structureSubscriptionRegistry;
        this.telemetryPolicyService = telemetryPolicyService;
        this.objectChangeProperties = objectChangeProperties;
        this.clusterProperties = clusterProperties;
    }

    /**
     * @return true when an event was published
     */
    public boolean publishVariableChange(String objectPath, String variableName, Instant observedAt) {
        if (!objectChangeProperties.isDemandDrivenPublication()) {
            return publishLegacyVariableChange(objectPath, variableName, observedAt);
        }
        VariableChangeInterest interest = variableSubscriptionRegistry.interest(objectPath, variableName);
        if (!interest.hasAny()) {
            return false;
        }
        boolean telemetry = interest.historian();
        boolean automationEligible = telemetryPolicyService.automationEligible(objectPath) && interest.automation();
        if (!telemetry && !automationEligible && !interest.uiRefresh()) {
            return false;
        }
        eventPublisher.publishEvent(ObjectChangeEvent.variableUpdated(
                objectPath,
                variableName,
                telemetry,
                automationEligible,
                observedAt
        ));
        return true;
    }

    /**
     * Config or API-driven variable updates (may include revision metadata).
     */
    public boolean publishConfigVariableChange(ObjectChangeEvent template) {
        if (!objectChangeProperties.isDemandDrivenPublication()) {
            eventPublisher.publishEvent(template);
            return true;
        }
        VariableChangeInterest interest = variableSubscriptionRegistry.interest(template.path(), template.variableName());
        boolean telemetry = interest.historian();
        // Config/API writes always fan out to automation (bindings, workflows) for eligible devices.
        boolean automationEligible = telemetryPolicyService.automationEligible(template.path())
                || interest.automation();
        // Persisted config/API writes always publish (cluster NATS sync, explorer refresh).
        eventPublisher.publishEvent(new ObjectChangeEvent(
                ObjectChangeType.VARIABLE_UPDATED,
                template.path(),
                template.variableName(),
                template.timestamp() != null ? template.timestamp() : Instant.now(),
                template.revision(),
                template.changedBy(),
                telemetry,
                automationEligible,
                template.observedAt(),
                false
        ));
        return true;
    }

    public boolean publishEventFired(String objectPath, String eventName) {
        if (!objectChangeProperties.isDemandDrivenPublication()) {
            eventPublisher.publishEvent(ObjectChangeEvent.eventFired(objectPath, eventName));
            return true;
        }
        EventFiredInterest interest = eventFiredSubscriptionRegistry.interest(objectPath, eventName);
        if (!interest.hasAny()) {
            return false;
        }
        eventPublisher.publishEvent(ObjectChangeEvent.eventFired(objectPath, eventName));
        return true;
    }

    public boolean publishStructureChange(ObjectChangeEvent template) {
        if (!objectChangeProperties.isDemandDrivenPublication()) {
            eventPublisher.publishEvent(template);
            return true;
        }
        if (shouldFanOutToClusterReplicas()) {
            eventPublisher.publishEvent(template);
            return true;
        }
        StructureChangeInterest interest = structureSubscriptionRegistry.interest(template.type(), template.path());
        if (!interest.hasAny()) {
            return false;
        }
        eventPublisher.publishEvent(template);
        return true;
    }

    /** ADR-0030: cluster followers must receive structure/config writes even without local WS interest. */
    private boolean shouldFanOutToClusterReplicas() {
        return clusterProperties.enabled();
    }

    /**
     * Defers config variable fan-out until the DB transaction commits so cluster replicas can
     * reload the value from the shared database when they receive the NATS event.
     */
    public void publishConfigVariableChangeAfterCommit(ObjectChangeEvent template) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishConfigVariableChange(template);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishConfigVariableChange(template);
            }
        });
    }

    /**
     * Defers structural fan-out until the DB transaction commits so cluster replicas can
     * reload the node from the shared database when they receive the NATS event.
     */
    public void publishStructureChangeAfterCommit(ObjectChangeEvent template) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishStructureChange(template);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishStructureChange(template);
            }
        });
    }

    private boolean publishLegacyVariableChange(String objectPath, String variableName, Instant observedAt) {
        boolean automationEligible = telemetryPolicyService.automationEligible(objectPath);
        eventPublisher.publishEvent(ObjectChangeEvent.variableUpdated(
                objectPath,
                variableName,
                true,
                automationEligible,
                observedAt
        ));
        return true;
    }
}
