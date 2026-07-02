package com.ispf.server.object.pubsub;

import com.ispf.server.application.binding.ApplicationSqlBindingEventIndex;
import com.ispf.server.automation.AutomationRuleIndex;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.server.workflow.WorkflowEventTriggerIndex;
import org.springframework.stereotype.Service;

/**
 * ADR-0024: demand-driven publication for {@code EVENT_FIRED}.
 */
@Service
public class EventFiredSubscriptionRegistry {

    private final BindingDependencyIndex bindingDependencyIndex;
    private final AutomationRuleIndex automationRuleIndex;
    private final WorkflowEventTriggerIndex workflowTriggerIndex;
    private final ApplicationSqlBindingEventIndex sqlBindingEventIndex;

    public EventFiredSubscriptionRegistry(
            BindingDependencyIndex bindingDependencyIndex,
            AutomationRuleIndex automationRuleIndex,
            WorkflowEventTriggerIndex workflowTriggerIndex,
            ApplicationSqlBindingEventIndex sqlBindingEventIndex
    ) {
        this.bindingDependencyIndex = bindingDependencyIndex;
        this.automationRuleIndex = automationRuleIndex;
        this.workflowTriggerIndex = workflowTriggerIndex;
        this.sqlBindingEventIndex = sqlBindingEventIndex;
    }

    public EventFiredInterest interest(String objectPath, String eventName) {
        if (objectPath == null || objectPath.isBlank() || eventName == null || eventName.isBlank()) {
            return EventFiredInterest.NONE;
        }
        boolean bindings = !bindingDependencyIndex.eventConsumers(objectPath, eventName).isEmpty();
        boolean correlators = !automationRuleIndex.findCorrelatorsForEvent(eventName).isEmpty();
        boolean workflows = !workflowTriggerIndex.findEventWorkflows(objectPath, eventName).isEmpty();
        boolean sqlBindings = sqlBindingEventIndex.hasBindings(objectPath, eventName);
        return new EventFiredInterest(bindings, correlators, workflows, sqlBindings);
    }
}
