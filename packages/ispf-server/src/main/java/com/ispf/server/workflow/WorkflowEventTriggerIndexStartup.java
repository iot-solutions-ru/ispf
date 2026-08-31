package com.ispf.server.workflow;

import com.ispf.server.object.PlatformObjectReadinessGate;
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
    @Order(PlatformObjectReadinessGate.AFTER_OBJECT_TREE_READY_ORDER)
    public void rebuildAfterTreeLoaded() {
        triggerIndex.rebuild();
    }
}
