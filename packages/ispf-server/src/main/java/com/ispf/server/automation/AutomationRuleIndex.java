package com.ispf.server.automation;

import com.ispf.server.alert.AlertRule;
import com.ispf.server.correlator.CorrelatorPatternType;
import com.ispf.server.correlator.EventCorrelator;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AutomationRuleIndex {

    private static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of());

    private final AutomationTreeService treeService;
    private volatile Snapshot snapshot = EMPTY;

    public AutomationRuleIndex(@Lazy AutomationTreeService treeService) {
        this.treeService = treeService;
    }

    public synchronized void rebuild() {
        Map<String, List<String>> alertRulePathsByTarget = new HashMap<>();
        for (AlertRule rule : treeService.listAlertRules()) {
            String key = alertKey(rule.objectPath(), rule.watchVariable());
            alertRulePathsByTarget.computeIfAbsent(key, ignored -> new ArrayList<>()).add(rule.id());
        }
        Map<String, List<String>> correlatorPathsByEventName = new HashMap<>();
        for (EventCorrelator correlator : treeService.listCorrelators()) {
            indexCorrelatorPaths(correlator, correlatorPathsByEventName);
        }
        snapshot = new Snapshot(freeze(alertRulePathsByTarget), freeze(correlatorPathsByEventName));
    }

    public synchronized void invalidate() {
        snapshot = EMPTY;
    }

    public List<AlertRule> findAlertRules(String objectPath, String watchVariable) {
        List<String> paths = snapshot.alertRulePathsByTarget().getOrDefault(
                alertKey(objectPath, watchVariable), List.of());
        return paths.stream()
                .map(treeService::getAlertRule)
                .filter(AlertRule::enabled)
                .toList();
    }

    public List<EventCorrelator> findCorrelatorsForEvent(String eventName) {
        List<String> paths = snapshot.correlatorPathsByEventName().getOrDefault(eventName, List.of());
        Set<String> seen = new LinkedHashSet<>();
        List<EventCorrelator> correlators = new ArrayList<>();
        for (String path : paths) {
            if (!seen.add(path)) {
                continue;
            }
            EventCorrelator correlator = treeService.getCorrelator(path);
            if (correlator.enabled()) {
                correlators.add(correlator);
            }
        }
        return correlators;
    }

    static String alertKey(String objectPath, String watchVariable) {
        return objectPath + "\0" + watchVariable;
    }

    static void indexCorrelatorPaths(
            EventCorrelator correlator,
            Map<String, List<String>> correlatorPathsByEventName
    ) {
        addCorrelatorPath(correlatorPathsByEventName, correlator.eventName(), correlator.id());
        if (correlator.secondEventName() == null || correlator.secondEventName().isBlank()) {
            return;
        }
        if (correlator.patternType() == CorrelatorPatternType.EVENT_CHAIN) {
            for (String part : correlator.secondEventName().split(",")) {
                addCorrelatorPath(correlatorPathsByEventName, part.trim(), correlator.id());
            }
        }
        addCorrelatorPath(correlatorPathsByEventName, correlator.secondEventName(), correlator.id());
    }

    private static void addCorrelatorPath(
            Map<String, List<String>> correlatorPathsByEventName,
            String eventName,
            String correlatorPath
    ) {
        if (eventName == null || eventName.isBlank()) {
            return;
        }
        correlatorPathsByEventName.computeIfAbsent(eventName, ignored -> new ArrayList<>()).add(correlatorPath);
    }

    private static <T> Map<String, List<T>> freeze(Map<String, List<T>> source) {
        Map<String, List<T>> frozen = HashMap.newHashMap(source.size());
        for (Map.Entry<String, List<T>> entry : source.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private record Snapshot(
            Map<String, List<String>> alertRulePathsByTarget,
            Map<String, List<String>> correlatorPathsByEventName
    ) {
    }
}
