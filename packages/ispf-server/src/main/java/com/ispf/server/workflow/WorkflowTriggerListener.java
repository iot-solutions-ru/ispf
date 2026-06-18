package com.ispf.server.workflow;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WorkflowTriggerListener {

    private final WorkflowService workflowService;

    public WorkflowTriggerListener(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @EventListener
    public void onObjectChange(ObjectChangeEvent event) {
        if (event.type() != ObjectChangeType.VARIABLE_UPDATED || event.variableName() == null) {
            return;
        }
        workflowService.handleVariableTrigger(event.path(), event.variableName());
    }
}
