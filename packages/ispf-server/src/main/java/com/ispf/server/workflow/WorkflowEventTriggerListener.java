package com.ispf.server.workflow;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.bus.ObjectChangeAsyncHandler;
import org.springframework.stereotype.Component;

@Component
public class WorkflowEventTriggerListener implements ObjectChangeAsyncHandler {

    private final WorkflowService workflowService;

    public WorkflowEventTriggerListener(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public int order() {
        return 55;
    }

    @Override
    public void handle(ObjectChangeEvent event) {
        if (event.type() != ObjectChangeType.EVENT_FIRED || event.variableName() == null) {
            return;
        }
        workflowService.handleEventTrigger(event.path(), event.variableName());
    }
}
