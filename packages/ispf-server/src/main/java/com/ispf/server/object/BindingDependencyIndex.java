package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.core.ref.PlatformRef;
import com.ispf.expression.BindingDependencyParser;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps (sourceObjectPath, variableName) → consumer object paths with binding rules.
 */
@Component
public class BindingDependencyIndex {

    private final BindingRulesService bindingRulesService;
    private final Map<String, Set<String>> consumersByKey = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> eventConsumersByKey = new ConcurrentHashMap<>();

    public BindingDependencyIndex(BindingRulesService bindingRulesService) {
        this.bindingRulesService = bindingRulesService;
    }

    public void rebuild(String objectPath) {
        var rules = bindingRulesService.listRules(objectPath);
        synchronized (this) {
            removeObject(objectPath);
            for (BindingRule rule : rules) {
                if (!rule.enabled()) {
                    continue;
                }
                registerRule(objectPath, rule);
            }
        }
    }

    public void rebuildAll(Iterable<String> objectPaths) {
        Map<String, java.util.List<BindingRule>> rulesByPath = new java.util.LinkedHashMap<>();
        for (String objectPath : objectPaths) {
            rulesByPath.put(objectPath, bindingRulesService.listRules(objectPath));
        }
        synchronized (this) {
            consumersByKey.clear();
            eventConsumersByKey.clear();
            for (Map.Entry<String, java.util.List<BindingRule>> entry : rulesByPath.entrySet()) {
                for (BindingRule rule : entry.getValue()) {
                    if (!rule.enabled()) {
                        continue;
                    }
                    registerRule(entry.getKey(), rule);
                }
            }
        }
    }

    public synchronized void removeObject(String objectPath) {
        // Drop this path as a consumer only; keep the map entry when other consumers remain.
        // (removeIf(set.remove(path)) would delete the whole entry whenever remove returned true.)
        removeConsumerFromIndex(consumersByKey, objectPath);
        removeConsumerFromIndex(eventConsumersByKey, objectPath);
    }

    /** Drops {@code objectPath} and all descendant paths ({@code objectPath.*}) as consumers. */
    public synchronized void removeSubtree(String objectPath) {
        if (objectPath == null || objectPath.isBlank()) {
            return;
        }
        removeSubtreeFromIndex(consumersByKey, objectPath);
        removeSubtreeFromIndex(eventConsumersByKey, objectPath);
    }

    private static void removeConsumerFromIndex(Map<String, Set<String>> index, String objectPath) {
        index.entrySet().removeIf(entry -> {
            Set<String> consumers = entry.getValue();
            consumers.remove(objectPath);
            return consumers.isEmpty();
        });
    }

    private static void removeSubtreeFromIndex(Map<String, Set<String>> index, String objectPath) {
        String descendantPrefix = objectPath + ".";
        index.entrySet().removeIf(entry -> {
            Set<String> consumers = entry.getValue();
            consumers.removeIf(path -> path.equals(objectPath) || path.startsWith(descendantPrefix));
            return consumers.isEmpty();
        });
    }

    public Set<String> eventConsumers(String objectPath, String eventName) {
        return eventConsumersByKey.getOrDefault(eventKey(objectPath, eventName), Set.of());
    }

    public Set<String> consumers(String changedObjectPath, String changedVariable) {
        Set<String> result = new LinkedHashSet<>();
        String key = key(changedObjectPath, changedVariable);
        result.addAll(consumersByKey.getOrDefault(key, Set.of()));
        result.addAll(consumersByKey.getOrDefault(key(changedObjectPath, BindingVariableRef.ANY), Set.of()));
        result.addAll(consumersByKey.getOrDefault(key(BindingVariableRef.ANY, changedVariable), Set.of()));
        result.addAll(consumersByKey.getOrDefault(key(BindingVariableRef.ANY, BindingVariableRef.ANY), Set.of()));
        return result;
    }

    /** True when at least one enabled binding rule activates on this variable change. */
    public boolean hasConsumers(String changedObjectPath, String changedVariable) {
        return !consumers(changedObjectPath, changedVariable).isEmpty();
    }

    private void registerRule(String ruleObjectPath, BindingRule rule) {
        BindingActivators activators = rule.activators();
        for (BindingVariableRef ref : activators.onVariableChange()) {
            String sourcePath = resolveActivatorPath(ruleObjectPath, ref.objectPath());
            String variableName = ref.variableName();
            consumersByKey.computeIfAbsent(key(sourcePath, variableName), ignored -> ConcurrentHashMap.newKeySet())
                    .add(ruleObjectPath);
        }
        for (BindingVariableRef ref : BindingDependencyParser.parseRefAtDependencies(rule.expression())) {
            consumersByKey.computeIfAbsent(key(ref.objectPath(), ref.variableName()), ignored -> ConcurrentHashMap.newKeySet())
                    .add(ruleObjectPath);
        }
        String onEvent = activators.onEvent();
        if (onEvent != null) {
            onEvent = onEvent.trim();
            if (onEvent.isBlank()) {
                onEvent = null;
            }
        }
        PlatformRef eventRef = activators.eventRef(ruleObjectPath);
        if (eventRef != null && eventRef.isEvent()) {
            eventConsumersByKey.computeIfAbsent(eventKey(eventRef.object(), eventRef.name()),
                            ignored -> ConcurrentHashMap.newKeySet())
                    .add(ruleObjectPath);
        } else if (onEvent != null) {
            eventConsumersByKey.computeIfAbsent(eventKey(ruleObjectPath, onEvent.trim()), ignored -> ConcurrentHashMap.newKeySet())
                    .add(ruleObjectPath);
        }
    }

    private static String resolveActivatorPath(String ruleObjectPath, String activatorPath) {
        if (activatorPath == null
                || activatorPath.isBlank()
                || BindingVariableRef.SELF.equals(activatorPath)) {
            return ruleObjectPath;
        }
        return activatorPath;
    }

    private static String key(String objectPath, String variableName) {
        return objectPath + "|" + variableName;
    }

    private static String eventKey(String objectPath, String eventName) {
        return objectPath + "|" + eventName;
    }
}
