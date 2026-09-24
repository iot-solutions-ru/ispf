package com.ispf.server.workflow;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.bus.ObjectChangeAsyncHandler;
import org.springframework.stereotype.Component;

@Component
public class WorkflowTriggerListener implements ObjectChangeAsyncHandler {

    private final WorkflowService workflowService;

    public WorkflowTriggerListener(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public void handle(ObjectChangeEvent event) {
        if (!event.automationEligible()) {
            return;
        }
        if (event.type() != ObjectChangeType.VARIABLE_UPDATED || event.variableName() == null) {
            return;
        }
        workflowService.handleVariableTrigger(event.path(), event.variableName());
    }
}
