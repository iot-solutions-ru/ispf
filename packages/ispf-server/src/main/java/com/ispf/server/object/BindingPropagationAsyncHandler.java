package com.ispf.server.object;

import com.ispf.core.binding.BindingRulesConstants;
import com.ispf.core.dashboard.DashboardContextConstants;
import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.server.config.ObjectChangeProperties;
import com.ispf.server.object.bus.ObjectChangeAsyncHandler;
import com.ispf.server.object.bus.ObjectChangeLane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ADR-0024: async binding propagation on the automation lane (replaces sync path for telemetry).
 */
@Component
public class BindingPropagationAsyncHandler implements ObjectChangeAsyncHandler {

    private static final Logger log = LoggerFactory.getLogger(BindingPropagationAsyncHandler.class);

    private final BindingRuleEngine bindingRuleEngine;
    private final BindingDependencyIndex dependencyIndex;
    private final ObjectChangeProperties objectChangeProperties;
    private final ObjectManager objectManager;

    public BindingPropagationAsyncHandler(
            BindingRuleEngine bindingRuleEngine,
            BindingDependencyIndex dependencyIndex,
            ObjectChangeProperties objectChangeProperties,
            ObjectManager objectManager
    ) {
        this.bindingRuleEngine = bindingRuleEngine;
        this.dependencyIndex = dependencyIndex;
        this.objectChangeProperties = objectChangeProperties;
        this.objectManager = objectManager;
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
        if (!objectManager.isInitialized()) {
            return;
        }
        if (event.replicaIngress()) {
            return;
        }
        if (!objectChangeProperties.isDemandDrivenPublication()) {
            return;
        }
        if (event.type() == ObjectChangeType.EVENT_FIRED && event.variableName() != null) {
            try {
                bindingRuleEngine.onEvent(event.path(), event.variableName());
            } catch (ObjectNotFoundException ex) {
                forgetMissingObject(dependencyIndex, ex, event.path());
            } catch (RuntimeException ex) {
                log.warn("Async event binding failed on {} ({}): {}", event.path(), event.variableName(), ex.getMessage(), ex);
            }
            for (String consumerPath : dependencyIndex.eventConsumers(event.path(), event.variableName())) {
                if (!consumerPath.equals(event.path())) {
                    try {
                        bindingRuleEngine.onRemoteEvent(consumerPath, event.path(), event.variableName());
                    } catch (ObjectNotFoundException ex) {
                        forgetMissingObject(dependencyIndex, ex, consumerPath);
                    } catch (RuntimeException ex) {
                        log.warn(
                                "Async remote event binding failed on {} for {}/{}: {}",
                                consumerPath,
                                event.path(),
                                event.variableName(),
                                ex.getMessage(),
                                ex
                        );
                    }
                }
            }
            return;
        }
        if (!event.automationEligible()) {
            return;
        }
        if (event.type() != ObjectChangeType.VARIABLE_UPDATED || event.variableName() == null) {
            return;
        }
        propagateVariableChangeAsync(event.path(), event.variableName());
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
        try {
            bindingRuleEngine.onVariableChanged(changedPath, changedPath, changedVariable);
        } catch (ObjectNotFoundException ex) {
            forgetMissingObject(dependencyIndex, ex, changedPath);
        }
        for (String consumerPath : dependencyIndex.consumers(changedPath, changedVariable)) {
            if (!consumerPath.equals(changedPath)) {
                try {
                    bindingRuleEngine.onVariableChanged(consumerPath, changedPath, changedVariable);
                } catch (ObjectNotFoundException ex) {
                    forgetMissingObject(dependencyIndex, ex, consumerPath);
                }
            }
        }
    }

    private void propagateVariableChangeAsync(String changedPath, String changedVariable) {
        if (BindingRulesConstants.isReservedVariable(changedVariable)
                || DashboardContextConstants.isReservedVariable(changedVariable)
                || BindingStateVariables.BINDING_STATE.equals(changedVariable)) {
            return;
        }
        try {
            bindingRuleEngine.onVariableChanged(changedPath, changedPath, changedVariable);
        } catch (ObjectNotFoundException ex) {
            forgetMissingObject(dependencyIndex, ex, changedPath);
        } catch (RuntimeException ex) {
            log.warn("Async binding propagation failed on {} ({}): {}", changedPath, changedVariable, ex.getMessage(), ex);
        }
        for (String consumerPath : dependencyIndex.consumers(changedPath, changedVariable)) {
            if (!consumerPath.equals(changedPath)) {
                try {
                    bindingRuleEngine.onVariableChanged(consumerPath, changedPath, changedVariable);
                } catch (ObjectNotFoundException ex) {
                    forgetMissingObject(dependencyIndex, ex, consumerPath);
                } catch (RuntimeException ex) {
                    log.warn(
                            "Async cross-object binding propagation failed on {} for {}/{}: {}",
                            consumerPath,
                            changedPath,
                            changedVariable,
                            ex.getMessage(),
                            ex
                    );
                }
            }
        }
    }

    /**
     * Evicts the missing path from the dependency index. Prefer {@link ObjectNotFoundException#path()} —
     * a live consumer may throw because a remote write target is gone, and that must not remove the consumer.
     */
    static void forgetMissingObject(
            BindingDependencyIndex dependencyIndex,
            ObjectNotFoundException ex,
            String fallbackPath
    ) {
        String missing = ex.path();
        if (missing == null || missing.isBlank()) {
            missing = fallbackPath;
        }
        if (missing != null && !missing.isBlank()) {
            dependencyIndex.removeSubtree(missing);
        }
    }
}
