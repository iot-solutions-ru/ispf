package com.ispf.server.object;

import com.ispf.core.binding.BindingRule;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BindingPeriodicScheduler {

    private static final String LOCK_NAME = "binding_periodic_rules";
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    private final ObjectManager objectManager;
    private final BindingRulesService bindingRulesService;
    private final BindingRuleEngine bindingRuleEngine;
    private final PlatformLeaderLockService leaderLockService;
    private final ClusterProperties clusterProperties;
    private final Map<String, Long> lastRunByRuleKey = new ConcurrentHashMap<>();

    public BindingPeriodicScheduler(
            ObjectManager objectManager,
            BindingRulesService bindingRulesService,
            BindingRuleEngine bindingRuleEngine,
            PlatformLeaderLockService leaderLockService,
            ClusterProperties clusterProperties
    ) {
        this.objectManager = objectManager;
        this.bindingRulesService = bindingRulesService;
        this.bindingRuleEngine = bindingRuleEngine;
        this.leaderLockService = leaderLockService;
        this.clusterProperties = clusterProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        lastRunByRuleKey.clear();
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        if (!clusterProperties.isSchedulerActive()) {
            return;
        }
        if (!leaderLockService.tryAcquire(LOCK_NAME, LOCK_TTL)) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            for (var node : objectManager.tree().all()) {
                String objectPath = node.path();
                for (BindingRule rule : bindingRulesService.listRules(objectPath)) {
                    if (!rule.enabled() || !rule.activators().hasPeriodicSchedule()) {
                        continue;
                    }
                    String ruleKey = objectPath + "#" + rule.id();
                    long periodMs = rule.activators().periodicMs();
                    Long lastRun = lastRunByRuleKey.get(ruleKey);
                    if (lastRun != null && now - lastRun < periodMs) {
                        continue;
                    }
                    lastRunByRuleKey.put(ruleKey, now);
                    bindingRuleEngine.onPeriodic(objectPath, rule.id());
                }
            }
        } finally {
            leaderLockService.release(LOCK_NAME);
        }
    }
}
