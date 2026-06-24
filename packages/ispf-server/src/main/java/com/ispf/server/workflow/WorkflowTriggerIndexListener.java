package com.ispf.server.workflow;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.bus.ObjectChangeAsyncHandler;
import org.springframework.stereotype.Component;

@Component
public class WorkflowTriggerIndexListener implements ObjectChangeAsyncHandler {

    private static final String WORKFLOWS_PREFIX = "root.platform.workflows.";

    private final WorkflowTriggerIndexRefresh triggerIndexRefresh;

    public WorkflowTriggerIndexListener(WorkflowTriggerIndexRefresh triggerIndexRefresh) {
        this.triggerIndexRefresh = triggerIndexRefresh;
    }

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public void handle(ObjectChangeEvent event) {
        if (event.type() != ObjectChangeType.VARIABLE_UPDATED || event.variableName() == null) {
            return;
        }
        if (!event.path().startsWith(WORKFLOWS_PREFIX)) {
            return;
        }
        if (!"triggerJson".equals(event.variableName()) && !"status".equals(event.variableName())) {
            return;
        }
        triggerIndexRefresh.scheduleFullRebuild();
    }
}
