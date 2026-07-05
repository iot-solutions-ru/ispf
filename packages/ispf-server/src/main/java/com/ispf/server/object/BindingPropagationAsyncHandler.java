package com.ispf.server.object;

import com.ispf.core.binding.BindingRulesConstants;
import com.ispf.core.dashboard.DashboardContextConstants;
import com.ispf.server.config.ObjectChangeProperties;
import com.ispf.server.object.bus.ObjectChangeAsyncHandler;
import com.ispf.server.object.bus.ObjectChangeLane;
import org.springframework.stereotype.Component;

/**
 * ADR-0024: async binding propagation on the automation lane (replaces sync path for telemetry).
 */
@Component
public class BindingPropagationAsyncHandler implements ObjectChangeAsyncHandler {

    private final BindingRuleEngine bindingRuleEngine;
    private final BindingDependencyIndex dependencyIndex;
    private final ObjectChangeProperties objectChangeProperties;

    public BindingPropagationAsyncHandler(
            BindingRuleEngine bindingRuleEngine,
            BindingDependencyIndex dependencyIndex,
            ObjectChangeProperties objectChangeProperties
    ) {
        this.bindingRuleEngine = bindingRuleEngine;
        this.dependencyIndex = dependencyIndex;
        this.objectChangeProperties = objectChangeProperties;
    }

    @Override
    public ObjectChangeLane lane() {
        return ObjectChangeLane.AUTOMATION;
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public void handle(ObjectChangeEvent event) {
        if (event.replicaIngress()) {
            return;
        }
        if (!objectChangeProperties.isDemandDrivenPublication()) {
            return;
        }
        if (event.type() == ObjectChangeType.EVENT_FIRED && event.variableName() != null) {
            bindingRuleEngine.onEvent(event.path(), event.variableName());
            return;
        }
        if (!event.automationEligible()) {
            return;
        }
        if (event.type() != ObjectChangeType.VARIABLE_UPDATED || event.variableName() == null) {
            return;
        }
        propagateVariableChange(event.path(), event.variableName());
    }

    static void propagateVariableChange(
            String changedPath,
            String changedVariable,
            BindingRuleEngine bindingRuleEngine,
            BindingDependencyIndex dependencyIndex
    ) {
        if (BindingRulesConstants.isReservedVariable(changedVariable)
                || DashboardContextConstants.isReservedVariable(changedVariable)
                || BindingStateVariables.BINDING_STATE.equals(changedVariable)) {
            return;
        }
        bindingRuleEngine.onVariableChanged(changedPath, changedPath, changedVariable);
        for (String consumerPath : dependencyIndex.consumers(changedPath, changedVariable)) {
            if (!consumerPath.equals(changedPath)) {
                bindingRuleEngine.onVariableChanged(consumerPath, changedPath, changedVariable);
            }
        }
    }

    private void propagateVariableChange(String changedPath, String changedVariable) {
        propagateVariableChange(changedPath, changedVariable, bindingRuleEngine, dependencyIndex);
    }
}
