package com.ispf.server.object;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class BindingRulesStartupRunner {

    private final ObjectManager objectManager;
    private final BindingDependencyIndex dependencyIndex;
    private final BindingRuleEngine bindingRuleEngine;

    public BindingRulesStartupRunner(
            ObjectManager objectManager,
            BindingDependencyIndex dependencyIndex,
            BindingRuleEngine bindingRuleEngine
    ) {
        this.objectManager = objectManager;
        this.dependencyIndex = dependencyIndex;
        this.bindingRuleEngine = bindingRuleEngine;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    public void initializeBindingRules() {
        var paths = objectManager.tree().all().stream().map(node -> node.path()).toList();
        dependencyIndex.rebuildAll(paths);
        for (String path : paths) {
            bindingRuleEngine.onStartup(path);
        }
    }
}
