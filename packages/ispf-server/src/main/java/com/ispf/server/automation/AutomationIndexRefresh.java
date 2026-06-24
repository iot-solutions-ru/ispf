package com.ispf.server.automation;

import com.ispf.server.alert.AlertRule;
import com.ispf.server.correlator.EventCorrelator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers automation rule index updates until the mutating transaction commits so
 * {@link AutomationRuleIndex} never publishes stale snapshots from uncommitted tree state.
 */
@Component
public class AutomationIndexRefresh {

    private final AutomationRuleIndex ruleIndex;

    public AutomationIndexRefresh(AutomationRuleIndex ruleIndex) {
        this.ruleIndex = ruleIndex;
    }

    public void afterAlertRuleCreated(AlertRule rule) {
        schedule(() -> ruleIndex.addAlertRule(rule));
    }

    public void afterAlertRuleUpdated(AlertRule previous, AlertRule current) {
        schedule(() -> ruleIndex.updateAlertRule(previous, current));
    }

    public void afterAlertRuleDeleted(AlertRule rule) {
        schedule(() -> ruleIndex.removeAlertRule(rule.id()));
    }

    public void afterCorrelatorCreated(EventCorrelator correlator) {
        schedule(() -> ruleIndex.addCorrelator(correlator));
    }

    public void afterCorrelatorUpdated(EventCorrelator previous, EventCorrelator current) {
        schedule(() -> ruleIndex.updateCorrelator(previous, current));
    }

    public void afterCorrelatorDeleted(EventCorrelator correlator) {
        schedule(() -> ruleIndex.removeCorrelator(correlator.id()));
    }

    public void scheduleFullRebuild() {
        schedule(ruleIndex::rebuild);
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
