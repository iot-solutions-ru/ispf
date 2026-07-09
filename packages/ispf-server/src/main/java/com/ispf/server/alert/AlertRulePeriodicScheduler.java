package com.ispf.server.alert;

import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Re-evaluates alert rules with {@code pollIntervalMs > 0} on a fixed tick.
 */
@Component
public class AlertRulePeriodicScheduler {

    private static final String LOCK_NAME = "alert_rule_periodic";
    private static final Duration LOCK_TTL = Duration.ofSeconds(15);

    private final AlertRuleService alertRuleService;
    private final AutomationTreeService automationTreeService;
    private final PlatformLeaderLockService leaderLockService;
    private final ClusterProperties clusterProperties;
    private final Map<String, Instant> lastPolledAt = new ConcurrentHashMap<>();

    public AlertRulePeriodicScheduler(
            AlertRuleService alertRuleService,
            AutomationTreeService automationTreeService,
            PlatformLeaderLockService leaderLockService,
            ClusterProperties clusterProperties
    ) {
        this.alertRuleService = alertRuleService;
        this.automationTreeService = automationTreeService;
        this.leaderLockService = leaderLockService;
        this.clusterProperties = clusterProperties;
    }

    @Scheduled(fixedDelayString = "${ispf.alert-rule.poll-tick-ms:1000}")
    public void tick() {
        if (!clusterProperties.isSchedulerActive()) {
            return;
        }
        if (!leaderLockService.tryAcquire(LOCK_NAME, LOCK_TTL)) {
            return;
        }
        try {
            runDue();
        } finally {
            leaderLockService.release(LOCK_NAME);
        }
    }

    void runDue() {
        Instant now = Instant.now();
        for (AlertRule rule : automationTreeService.listEnabledPeriodicAlertRules()) {
            Instant last = lastPolledAt.get(rule.id());
            if (last != null && last.plusMillis(rule.pollIntervalMs()).isAfter(now)) {
                continue;
            }
            lastPolledAt.put(rule.id(), now);
            alertRuleService.evaluateRule(rule);
        }
    }

    void resetSchedule(String rulePath) {
        lastPolledAt.remove(rulePath);
    }
}
