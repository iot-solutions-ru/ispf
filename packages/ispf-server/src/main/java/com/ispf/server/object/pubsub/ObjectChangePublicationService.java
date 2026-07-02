package com.ispf.server.object.pubsub;

import com.ispf.server.config.ObjectChangeProperties;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

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

    public ObjectChangePublicationService(
            ApplicationEventPublisher eventPublisher,
            VariableChangeSubscriptionRegistry variableSubscriptionRegistry,
            EventFiredSubscriptionRegistry eventFiredSubscriptionRegistry,
            StructureChangeSubscriptionRegistry structureSubscriptionRegistry,
            DeviceTelemetryPolicyService telemetryPolicyService,
            ObjectChangeProperties objectChangeProperties
    ) {
        this.eventPublisher = eventPublisher;
        this.variableSubscriptionRegistry = variableSubscriptionRegistry;
        this.eventFiredSubscriptionRegistry = eventFiredSubscriptionRegistry;
        this.structureSubscriptionRegistry = structureSubscriptionRegistry;
        this.telemetryPolicyService = telemetryPolicyService;
        this.objectChangeProperties = objectChangeProperties;
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
        if (!interest.hasAny()) {
            return false;
        }
        boolean telemetry = interest.historian();
        boolean automationEligible = interest.automation();
        if (!telemetry && !automationEligible && !interest.uiRefresh()) {
            return false;
        }
        eventPublisher.publishEvent(new ObjectChangeEvent(
                ObjectChangeType.VARIABLE_UPDATED,
                template.path(),
                template.variableName(),
                template.timestamp() != null ? template.timestamp() : Instant.now(),
                template.revision(),
                template.changedBy(),
                telemetry,
                automationEligible,
                template.observedAt()
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
        StructureChangeInterest interest = structureSubscriptionRegistry.interest(template.type(), template.path());
        if (!interest.hasAny()) {
            return false;
        }
        eventPublisher.publishEvent(template);
        return true;
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
