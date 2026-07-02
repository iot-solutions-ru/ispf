package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingVariableRef;
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

    public synchronized void rebuild(String objectPath) {
        removeObject(objectPath);
        for (BindingRule rule : bindingRulesService.listRules(objectPath)) {
            if (!rule.enabled()) {
                continue;
            }
            registerRule(objectPath, rule);
        }
    }

    public synchronized void rebuildAll(Iterable<String> objectPaths) {
        consumersByKey.clear();
        eventConsumersByKey.clear();
        for (String objectPath : objectPaths) {
            rebuild(objectPath);
        }
    }

    public synchronized void removeObject(String objectPath) {
        consumersByKey.entrySet().removeIf(entry -> entry.getValue().remove(objectPath));
        eventConsumersByKey.entrySet().removeIf(entry -> entry.getValue().remove(objectPath));
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
        if (onEvent != null && !onEvent.isBlank()) {
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
