package com.ispf.server.object;

import com.ispf.core.binding.BindingRule;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BindingRulesStartupRunner {

    private final ObjectManager objectManager;
    private final BindingDependencyIndex dependencyIndex;
    private final BindingRuleEngine bindingRuleEngine;
    private final BindingRulesService bindingRulesService;
    private final BindingPeriodicScheduleRegistry periodicScheduleRegistry;
    private final BindingPeriodicScheduler periodicScheduler;

    public BindingRulesStartupRunner(
            ObjectManager objectManager,
            BindingDependencyIndex dependencyIndex,
            BindingRuleEngine bindingRuleEngine,
            BindingRulesService bindingRulesService,
            BindingPeriodicScheduleRegistry periodicScheduleRegistry,
            BindingPeriodicScheduler periodicScheduler
    ) {
        this.objectManager = objectManager;
        this.dependencyIndex = dependencyIndex;
        this.bindingRuleEngine = bindingRuleEngine;
        this.bindingRulesService = bindingRulesService;
        this.periodicScheduleRegistry = periodicScheduleRegistry;
        this.periodicScheduler = periodicScheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    public void initializeBindingRules() {
        var paths = objectManager.tree().all().stream().map(node -> node.path()).toList();
        dependencyIndex.rebuildAll(paths);
        periodicScheduleRegistry.clearAll();
        for (String path : periodicScheduleRegistry.objectPathsWithBindingRules()) {
            // Historian periodicMs is owned by AnalyticsEngineScheduler — never index it here.
            List<BindingRule> reactive = bindingRulesService.listRules(path).stream()
                    .filter(BindingRule::isReactive)
                    .toList();
            periodicScheduleRegistry.syncObject(path, reactive);
        }
        periodicScheduler.reschedule();
        for (String path : paths) {
            bindingRuleEngine.onStartup(path);
        }
    }
}
