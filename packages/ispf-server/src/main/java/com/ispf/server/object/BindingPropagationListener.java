package com.ispf.server.object;

import com.ispf.core.dashboard.DashboardContextConstants;
import com.ispf.server.config.ObjectChangeProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Synchronous propagation for {@code EVENT_FIRED}, dashboard context, and legacy mode.
 * Variable telemetry updates use {@link BindingPropagationAsyncHandler} when demand-driven (ADR-0024).
 */
@Component
public class BindingPropagationListener {

    private final BindingRuleEngine bindingRuleEngine;
    private final BindingDependencyIndex dependencyIndex;
    private final ObjectChangeProperties objectChangeProperties;

    public BindingPropagationListener(
            BindingRuleEngine bindingRuleEngine,
            BindingDependencyIndex dependencyIndex,
            ObjectChangeProperties objectChangeProperties
    ) {
        this.bindingRuleEngine = bindingRuleEngine;
        this.dependencyIndex = dependencyIndex;
        this.objectChangeProperties = objectChangeProperties;
    }

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onObjectChange(ObjectChangeEvent event) {
        if (event.type() == ObjectChangeType.EVENT_FIRED && event.variableName() != null) {
            if (objectChangeProperties.isDemandDrivenPublication()) {
                return;
            }
            bindingRuleEngine.onEvent(event.path(), event.variableName());
            for (String consumerPath : dependencyIndex.eventConsumers(event.path(), event.variableName())) {
                if (!consumerPath.equals(event.path())) {
                    bindingRuleEngine.onRemoteEvent(consumerPath, event.path(), event.variableName());
                }
            }
            return;
        }
        if (event.type() == ObjectChangeType.VARIABLE_UPDATED && event.variableName() != null) {
            if (DashboardContextConstants.isReservedVariable(event.variableName())) {
                bindingRuleEngine.onContextChange(event.path());
                return;
            }
            if (objectChangeProperties.isDemandDrivenPublication()) {
                return;
            }
            BindingPropagationAsyncHandler.propagateVariableChange(
                    event.path(),
                    event.variableName(),
                    bindingRuleEngine,
                    dependencyIndex
            );
        }
    }
}
