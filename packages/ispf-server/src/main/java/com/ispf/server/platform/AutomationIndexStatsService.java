package com.ispf.server.platform;

import com.ispf.server.automation.AutomationRuleIndex;
import com.ispf.server.workflow.WorkflowEventTriggerIndex;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AutomationIndexStatsService {

    private final AutomationRuleIndex ruleIndex;
    private final WorkflowEventTriggerIndex workflowTriggerIndex;

    public AutomationIndexStatsService(
            AutomationRuleIndex ruleIndex,
            WorkflowEventTriggerIndex workflowTriggerIndex
    ) {
        this.ruleIndex = ruleIndex;
        this.workflowTriggerIndex = workflowTriggerIndex;
    }

    public AutomationIndexStats stats() {
        Instant ruleIndexedAt = ruleIndex.lastIndexedAt();
        Instant workflowIndexedAt = workflowTriggerIndex.lastIndexedAt();
        Instant lastRebuildAt = latest(ruleIndexedAt, workflowIndexedAt);
        return new AutomationIndexStats(
                ruleIndex.alertRulesIndexed(),
                ruleIndex.correlatorsIndexed(),
                workflowTriggerIndex.variableTriggersIndexed(),
                lastRebuildAt
        );
    }

    private static Instant latest(Instant first, Instant second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    public record AutomationIndexStats(
            int alertRulesIndexed,
            int correlatorsIndexed,
            int workflowTriggersIndexed,
            Instant lastRebuildAt
    ) {
    }
}
