package com.ispf.server.workflow;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.object.ObjectManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class WorkflowEventTriggerIndex {

    private static final String WORKFLOWS_ROOT = "root.platform.workflows";
    private static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of());

    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;
    private volatile Snapshot snapshot = EMPTY;
    private volatile Instant lastIndexedAt;

    public WorkflowEventTriggerIndex(@Lazy ObjectManager objectManager, ObjectMapper objectMapper) {
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
    }

    public synchronized void rebuild() {
        Map<String, List<String>> eventWorkflowPathsByTarget = new HashMap<>();
        Map<String, List<String>> variableWorkflowPathsByTarget = new HashMap<>();
        for (PlatformObject node : objectManager.tree().childrenOf(WORKFLOWS_ROOT)) {
            if (node.type() != ObjectType.WORKFLOW) {
                continue;
            }
            if (readLifecycleStatus(node) != WorkflowLifecycleStatus.ACTIVE) {
                continue;
            }
            parseTrigger(node.path(), readTriggerJson(node)).ifPresent(binding -> {
                if (binding.triggerType() == TriggerType.EVENT) {
                    addPath(eventWorkflowPathsByTarget, eventKey(binding.objectPath(), binding.eventName()), node.path());
                } else if (binding.triggerType() == TriggerType.VARIABLE) {
                    addPath(variableWorkflowPathsByTarget, variableKey(binding.objectPath(), binding.variableName()), node.path());
                }
            });
        }
        snapshot = new Snapshot(freeze(eventWorkflowPathsByTarget), freeze(variableWorkflowPathsByTarget));
        touchIndexed();
    }

    public int variableTriggersIndexed() {
        Snapshot current = snapshot;
        Set<String> workflowPaths = new LinkedHashSet<>();
        for (List<String> paths : current.variableWorkflowPathsByTarget().values()) {
            workflowPaths.addAll(paths);
        }
        return workflowPaths.size();
    }

    public Instant lastIndexedAt() {
        return lastIndexedAt;
    }

    private void touchIndexed() {
        lastIndexedAt = Instant.now();
    }

    public synchronized void invalidate() {
        snapshot = EMPTY;
    }

    public List<String> findEventWorkflows(String objectPath, String eventName) {
        return snapshot.eventWorkflowPathsByTarget().getOrDefault(eventKey(objectPath, eventName), List.of());
    }

    public List<String> findVariableWorkflows(String objectPath, String variableName) {
        return snapshot.variableWorkflowPathsByTarget().getOrDefault(variableKey(objectPath, variableName), List.of());
    }

    static String eventKey(String objectPath, String eventName) {
        return objectPath + "\0" + eventName;
    }

    static String variableKey(String objectPath, String variableName) {
        return objectPath + "\0" + variableName;
    }

    static Optional<TriggerBinding> parseTrigger(String workflowPath, String triggerJson, ObjectMapper objectMapper) {
        if (triggerJson == null || triggerJson.isBlank() || "{}".equals(triggerJson.trim())) {
            return Optional.empty();
        }
        try {
            JsonNode trigger = objectMapper.readTree(triggerJson);
            String objectPath = textOrNull(trigger, "objectPath");
            if (objectPath == null) {
                return Optional.empty();
            }
            String triggerType = trigger.path("triggerType").asText("");
            String eventName = textOrNull(trigger, "eventName");
            String variableName = textOrNull(trigger, "variableName");
            if ("event".equalsIgnoreCase(triggerType) || (triggerType.isBlank() && eventName != null && variableName == null)) {
                if (eventName == null) {
                    return Optional.empty();
                }
                return Optional.of(new TriggerBinding(workflowPath, TriggerType.EVENT, objectPath, null, eventName));
            }
            if ("variable".equalsIgnoreCase(triggerType) || (triggerType.isBlank() && variableName != null)) {
                if (variableName == null) {
                    return Optional.empty();
                }
                return Optional.of(new TriggerBinding(workflowPath, TriggerType.VARIABLE, objectPath, variableName, null));
            }
            return Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<TriggerBinding> parseTrigger(String workflowPath, String triggerJson) {
        return parseTrigger(workflowPath, triggerJson, objectMapper);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private static void addPath(Map<String, List<String>> index, String key, String workflowPath) {
        index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(workflowPath);
    }

    private static WorkflowLifecycleStatus readLifecycleStatus(PlatformObject node) {
        return node.getVariable("status")
                .flatMap(v -> v.value())
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .map(WorkflowLifecycleStatus::valueOf)
                .orElse(WorkflowLifecycleStatus.DRAFT);
    }

    private static String readTriggerJson(PlatformObject node) {
        return node.getVariable("triggerJson")
                .flatMap(v -> v.value())
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .orElse("{}");
    }

    private static <T> Map<String, List<T>> freeze(Map<String, List<T>> source) {
        Map<String, List<T>> frozen = HashMap.newHashMap(source.size());
        for (Map.Entry<String, List<T>> entry : source.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    enum TriggerType {
        EVENT,
        VARIABLE
    }

    record TriggerBinding(
            String workflowPath,
            TriggerType triggerType,
            String objectPath,
            String variableName,
            String eventName
    ) {
    }

    private record Snapshot(
            Map<String, List<String>> eventWorkflowPathsByTarget,
            Map<String, List<String>> variableWorkflowPathsByTarget
    ) {
    }
}
