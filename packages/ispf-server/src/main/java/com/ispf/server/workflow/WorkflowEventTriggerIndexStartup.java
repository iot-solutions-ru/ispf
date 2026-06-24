package com.ispf.server.workflow;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class WorkflowEventTriggerIndexStartup {

    private final WorkflowEventTriggerIndex triggerIndex;

    public WorkflowEventTriggerIndexStartup(WorkflowEventTriggerIndex triggerIndex) {
        this.triggerIndex = triggerIndex;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
    public void rebuildAfterTreeLoaded() {
        triggerIndex.rebuild();
    }
}
