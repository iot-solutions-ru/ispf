package com.ispf.server.object.pubsub;

import com.ispf.server.automation.AutomationRuleIndex;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.workflow.WorkflowEventTriggerIndex;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * ADR-0024: demand-driven publication — who subscribes to {@code (path, variable)} updates.
 */
@Service
public class VariableChangeSubscriptionRegistry {

    private static final String WORKFLOWS_PREFIX = "root.platform.workflows.";

    private final ObjectManager objectManager;
    private final BindingDependencyIndex bindingDependencyIndex;
    private final AutomationRuleIndex automationRuleIndex;
    private final WorkflowEventTriggerIndex workflowTriggerIndex;
    private final ObjectWebSocketPathInterestRegistry webSocketPathInterest;
    private final FederationExportInterestRegistry federationExportInterest;

    public VariableChangeSubscriptionRegistry(
            @Lazy ObjectManager objectManager,
            BindingDependencyIndex bindingDependencyIndex,
            AutomationRuleIndex automationRuleIndex,
            WorkflowEventTriggerIndex workflowTriggerIndex,
            ObjectWebSocketPathInterestRegistry webSocketPathInterest,
            FederationExportInterestRegistry federationExportInterest
    ) {
        this.objectManager = objectManager;
        this.bindingDependencyIndex = bindingDependencyIndex;
        this.automationRuleIndex = automationRuleIndex;
        this.workflowTriggerIndex = workflowTriggerIndex;
        this.webSocketPathInterest = webSocketPathInterest;
        this.federationExportInterest = federationExportInterest;
    }

    public VariableChangeInterest interest(String objectPath, String variableName) {
        if (objectPath == null || objectPath.isBlank() || variableName == null || variableName.isBlank()) {
            return VariableChangeInterest.NONE;
        }
        boolean historian = isHistorized(objectPath, variableName);
        boolean bindings = hasBindingSubscribers(objectPath, variableName);
        boolean alerts = !automationRuleIndex.findAlertRules(objectPath, variableName).isEmpty();
        boolean workflows = !workflowTriggerIndex.findVariableWorkflows(objectPath, variableName).isEmpty();
        boolean workflowIndex = isWorkflowIndexVariable(objectPath, variableName);
        boolean uiRefresh = webSocketPathInterest.hasPathInterest(objectPath)
                || federationExportInterest.hasPathInterest(objectPath);
        return new VariableChangeInterest(historian, bindings, alerts, workflows, workflowIndex, uiRefresh);
    }

    private boolean isWorkflowIndexVariable(String objectPath, String variableName) {
        return objectPath.startsWith(WORKFLOWS_PREFIX)
                && ("triggerJson".equals(variableName) || "status".equals(variableName));
    }

    private boolean isHistorized(String objectPath, String variableName) {
        return objectManager.tree().findByPath(objectPath)
                .flatMap(node -> node.getVariable(variableName))
                .map(com.ispf.core.object.Variable::historyEnabled)
                .orElse(false);
    }

    private boolean hasBindingSubscribers(String objectPath, String variableName) {
        return !bindingDependencyIndex.consumers(objectPath, variableName).isEmpty();
    }
}
