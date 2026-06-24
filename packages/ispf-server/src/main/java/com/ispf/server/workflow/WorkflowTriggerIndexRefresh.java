package com.ispf.server.workflow;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class WorkflowTriggerIndexRefresh {

    private final WorkflowEventTriggerIndex triggerIndex;

    public WorkflowTriggerIndexRefresh(WorkflowEventTriggerIndex triggerIndex) {
        this.triggerIndex = triggerIndex;
    }

    public void scheduleFullRebuild() {
        schedule(triggerIndex::rebuild);
    }

    private void schedule(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
