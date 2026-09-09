package com.ispf.server.object;

import com.ispf.core.binding.BindingRule;
import com.ispf.core.object.ObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BindingRulesStartupRunner {

    private static final Logger log = LoggerFactory.getLogger(BindingRulesStartupRunner.class);

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
    @Order(PlatformObjectReadinessGate.AFTER_OBJECT_TREE_READY_ORDER)
    public void initializeBindingRules() {
        if (!objectManager.isInitialized()) {
            log.warn("Skipping binding rules startup: object tree not ready");
            return;
        }
        var paths = objectManager.tree().all().stream().map(node -> node.path()).toList();
        dependencyIndex.rebuildAll(paths);
        periodicScheduleRegistry.clearAll();
        for (String path : periodicScheduleRegistry.objectPathsWithBindingRules()) {
            // Historian periodicMs is owned by AnalyticsEngineScheduler — never index it here.
            try {
                List<BindingRule> reactive = bindingRulesService.listRules(path).stream()
                        .filter(BindingRule::isReactive)
                        .toList();
                periodicScheduleRegistry.syncObject(path, reactive);
            } catch (ObjectNotFoundException ex) {
                log.debug("Skip binding schedule for missing object {}: {}", path, ex.getMessage());
            }
        }
        periodicScheduler.reschedule();
        for (String path : paths) {
            try {
                bindingRuleEngine.onStartup(path);
            } catch (ObjectNotFoundException ex) {
                log.debug("Skip binding startup for missing object {}: {}", path, ex.getMessage());
            }
        }
    }
}
