package com.ispf.server.automation;

import com.ispf.server.alert.AlertRule;
import com.ispf.server.correlator.CorrelatorPatternType;
import com.ispf.server.correlator.EventCorrelator;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AutomationRuleIndex {

    private static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of(), Map.of());

    private final AutomationTreeService treeService;
    private volatile Snapshot snapshot = EMPTY;
    private volatile Instant lastIndexedAt;

    public AutomationRuleIndex(@Lazy AutomationTreeService treeService) {
        this.treeService = treeService;
    }

    public synchronized void rebuild() {
        Map<String, List<String>> alertRulePathsByTarget = new HashMap<>();
        Map<String, String> alertRuleKeyByPath = new HashMap<>();
        for (AlertRule rule : treeService.listAlertRules()) {
            indexAlertRule(alertRulePathsByTarget, alertRuleKeyByPath, rule);
        }
        Map<String, List<String>> correlatorPathsByEventName = new HashMap<>();
        Map<String, String> correlatorEventKeysByPath = new HashMap<>();
        for (EventCorrelator correlator : treeService.listCorrelators()) {
            indexCorrelator(correlatorPathsByEventName, correlatorEventKeysByPath, correlator);
        }
        snapshot = new Snapshot(
                freeze(alertRulePathsByTarget),
                freeze(correlatorPathsByEventName),
                Map.copyOf(alertRuleKeyByPath),
                Map.copyOf(correlatorEventKeysByPath)
        );
        touchIndexed();
    }

    public synchronized void invalidate() {
        snapshot = EMPTY;
    }

    public synchronized void addAlertRule(AlertRule rule) {
        Map<String, List<String>> alerts = mutableLists(snapshot.alertRulePathsByTarget());
        Map<String, String> alertKeys = new HashMap<>(snapshot.alertRuleKeyByPath());
        removeAlertRuleFromMaps(alerts, alertKeys, rule.id());
        indexAlertRule(alerts, alertKeys, rule);
        snapshot = new Snapshot(freeze(alerts), snapshot.correlatorPathsByEventName(),
                Map.copyOf(alertKeys), snapshot.correlatorEventKeysByPath());
        touchIndexed();
    }

    public synchronized void updateAlertRule(AlertRule previous, AlertRule current) {
        Map<String, List<String>> alerts = mutableLists(snapshot.alertRulePathsByTarget());
        Map<String, String> alertKeys = new HashMap<>(snapshot.alertRuleKeyByPath());
        removeAlertRuleFromMaps(alerts, alertKeys, previous.id());
        indexAlertRule(alerts, alertKeys, current);
        snapshot = new Snapshot(freeze(alerts), snapshot.correlatorPathsByEventName(),
                Map.copyOf(alertKeys), snapshot.correlatorEventKeysByPath());
        touchIndexed();
    }

    public synchronized void removeAlertRule(String rulePath) {
        Map<String, List<String>> alerts = mutableLists(snapshot.alertRulePathsByTarget());
        Map<String, String> alertKeys = new HashMap<>(snapshot.alertRuleKeyByPath());
        removeAlertRuleFromMaps(alerts, alertKeys, rulePath);
        snapshot = new Snapshot(freeze(alerts), snapshot.correlatorPathsByEventName(),
                Map.copyOf(alertKeys), snapshot.correlatorEventKeysByPath());
        touchIndexed();
    }

    public synchronized void addCorrelator(EventCorrelator correlator) {
        Map<String, List<String>> correlators = mutableLists(snapshot.correlatorPathsByEventName());
        Map<String, String> correlatorKeys = new HashMap<>(snapshot.correlatorEventKeysByPath());
        removeCorrelatorFromMaps(correlators, correlatorKeys, correlator.id());
        indexCorrelator(correlators, correlatorKeys, correlator);
        snapshot = new Snapshot(snapshot.alertRulePathsByTarget(), freeze(correlators),
                snapshot.alertRuleKeyByPath(), Map.copyOf(correlatorKeys));
        touchIndexed();
    }

    public synchronized void updateCorrelator(EventCorrelator previous, EventCorrelator current) {
        Map<String, List<String>> correlators = mutableLists(snapshot.correlatorPathsByEventName());
        Map<String, String> correlatorKeys = new HashMap<>(snapshot.correlatorEventKeysByPath());
        removeCorrelatorFromMaps(correlators, correlatorKeys, previous.id());
        indexCorrelator(correlators, correlatorKeys, current);
        snapshot = new Snapshot(snapshot.alertRulePathsByTarget(), freeze(correlators),
                snapshot.alertRuleKeyByPath(), Map.copyOf(correlatorKeys));
        touchIndexed();
    }

    public synchronized void removeCorrelator(String correlatorPath) {
        Map<String, List<String>> correlators = mutableLists(snapshot.correlatorPathsByEventName());
        Map<String, String> correlatorKeys = new HashMap<>(snapshot.correlatorEventKeysByPath());
        removeCorrelatorFromMaps(correlators, correlatorKeys, correlatorPath);
        snapshot = new Snapshot(snapshot.alertRulePathsByTarget(), freeze(correlators),
                snapshot.alertRuleKeyByPath(), Map.copyOf(correlatorKeys));
        touchIndexed();
    }

    public int alertRulesIndexed() {
        return snapshot.alertRuleKeyByPath().size();
    }

    public int correlatorsIndexed() {
        return snapshot.correlatorEventKeysByPath().size();
    }

    public Instant lastIndexedAt() {
        return lastIndexedAt;
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

    private static void indexAlertRule(
            Map<String, List<String>> alertRulePathsByTarget,
            Map<String, String> alertRuleKeyByPath,
            AlertRule rule
    ) {
        String key = alertKey(rule.objectPath(), rule.watchVariable());
        addPath(alertRulePathsByTarget, key, rule.id());
        alertRuleKeyByPath.put(rule.id(), key);
    }

    private static void indexCorrelator(
            Map<String, List<String>> correlatorPathsByEventName,
            Map<String, String> correlatorEventKeysByPath,
            EventCorrelator correlator
    ) {
        indexCorrelatorPaths(correlator, correlatorPathsByEventName);
        correlatorEventKeysByPath.put(correlator.id(), correlator.eventName());
    }

    private static void removeAlertRuleFromMaps(
            Map<String, List<String>> alertRulePathsByTarget,
            Map<String, String> alertRuleKeyByPath,
            String rulePath
    ) {
        String previousKey = alertRuleKeyByPath.remove(rulePath);
        if (previousKey == null) {
            return;
        }
        List<String> paths = alertRulePathsByTarget.get(previousKey);
        if (paths == null) {
            return;
        }
        paths.remove(rulePath);
        if (paths.isEmpty()) {
            alertRulePathsByTarget.remove(previousKey);
        }
    }

    private static void removeCorrelatorFromMaps(
            Map<String, List<String>> correlatorPathsByEventName,
            Map<String, String> correlatorEventKeysByPath,
            String correlatorPath
    ) {
        correlatorEventKeysByPath.remove(correlatorPath);
        for (Map.Entry<String, List<String>> entry : correlatorPathsByEventName.entrySet()) {
            entry.getValue().remove(correlatorPath);
        }
        correlatorPathsByEventName.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private static void addPath(Map<String, List<String>> index, String key, String path) {
        index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(path);
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

    private static Map<String, List<String>> mutableLists(Map<String, List<String>> source) {
        Map<String, List<String>> copy = HashMap.newHashMap(source.size());
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    private static <T> Map<String, List<T>> freeze(Map<String, List<T>> source) {
        Map<String, List<T>> frozen = HashMap.newHashMap(source.size());
        for (Map.Entry<String, List<T>> entry : source.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private void touchIndexed() {
        lastIndexedAt = Instant.now();
    }

    private record Snapshot(
            Map<String, List<String>> alertRulePathsByTarget,
            Map<String, List<String>> correlatorPathsByEventName,
            Map<String, String> alertRuleKeyByPath,
            Map<String, String> correlatorEventKeysByPath
    ) {
    }
}
