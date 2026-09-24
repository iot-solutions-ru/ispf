package com.ispf.server.automation;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.correlator.CorrelatorActionType;
import com.ispf.server.correlator.CorrelatorPatternType;
import com.ispf.server.correlator.EventCorrelator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Correlator nodes under {@code root.platform.correlators}. Public entry points stay on
 * {@link AutomationTreeService} so transaction boundaries do not move.
 */
final class AutomationCorrelatorCatalog {

    private final AutomationTreeService tree;

    AutomationCorrelatorCatalog(AutomationTreeService tree) {
        this.tree = tree;
    }

    List<EventCorrelator> listCorrelators() {
        List<EventCorrelator> correlators = new ArrayList<>();
        for (PlatformObject node : tree.objectManager.tree().all()) {
            if (node.type() == ObjectType.CORRELATOR
                    && node.path().startsWith(AutomationTreeService.CORRELATORS_ROOT + ".")) {
                correlators.add(toCorrelator(node));
            }
        }
        return correlators;
    }

    EventCorrelator getCorrelator(String path) {
        return toCorrelator(requireCorrelator(path));
    }

    List<EventCorrelator> findEnabledCorrelatorsForEvent(String eventName) {
        return tree.ruleIndex.findCorrelatorsForEvent(eventName);
    }

    EventCorrelator createCorrelator(
            String name,
            String targetObjectPath,
            CorrelatorPatternType patternType,
            String eventName,
            String secondEventName,
            int windowSeconds,
            int minOccurrences,
            int cooldownSeconds,
            int sequenceGapSeconds,
            CorrelatorActionType actionType,
            String actionTarget,
            String payloadFilterExpr,
            boolean enabled
    ) {
        String path = uniqueCorrelatorPath(name);
        createCorrelatorNode(path, name, targetObjectPath, patternType, eventName, secondEventName,
                windowSeconds, minOccurrences, cooldownSeconds, sequenceGapSeconds, actionType, actionTarget,
                payloadFilterExpr, enabled, null);
        EventCorrelator correlator = getCorrelator(path);
        tree.indexRefresh.afterCorrelatorCreated(correlator);
        return correlator;
    }

    EventCorrelator updateCorrelator(
            String path,
            String name,
            String targetObjectPath,
            CorrelatorPatternType patternType,
            String eventName,
            String secondEventName,
            Integer windowSeconds,
            Integer minOccurrences,
            Integer cooldownSeconds,
            Integer sequenceGapSeconds,
            CorrelatorActionType actionType,
            String actionTarget,
            String payloadFilterExpr,
            Boolean enabled
    ) {
        EventCorrelator previous = getCorrelator(path);
        PlatformObject node = requireCorrelator(path);
        if (name != null && !name.isBlank()) {
            tree.objectManager.updateInfo(path, name, node.description());
        }
        if (targetObjectPath != null) {
            tree.setString(path, "targetObjectPath", targetObjectPath);
        }
        if (patternType != null) {
            tree.setString(path, "patternType", patternType.name());
        }
        if (eventName != null) {
            tree.setString(path, "eventName", eventName);
        }
        if (secondEventName != null) {
            tree.setString(path, "secondEventName", secondEventName);
        }
        if (windowSeconds != null) {
            tree.setInteger(path, "windowSeconds", windowSeconds);
        }
        if (minOccurrences != null) {
            tree.setInteger(path, "minOccurrences", minOccurrences);
        }
        if (cooldownSeconds != null) {
            tree.setInteger(path, "cooldownSeconds", cooldownSeconds);
        }
        if (sequenceGapSeconds != null) {
            tree.setInteger(path, "sequenceGapSeconds", sequenceGapSeconds);
        }
        if (actionType != null) {
            tree.setString(path, "actionType", actionType.name());
        }
        if (actionTarget != null) {
            tree.setString(path, "actionTarget", actionTarget);
        }
        if (payloadFilterExpr != null) {
            tree.setString(path, "payloadFilterExpr", payloadFilterExpr);
        }
        if (enabled != null) {
            tree.setBoolean(path, "enabled", enabled);
        }
        tree.objectManager.persistNodeTree(path);
        EventCorrelator correlator = getCorrelator(path);
        tree.indexRefresh.afterCorrelatorUpdated(previous, correlator);
        return correlator;
    }

    void setCorrelatorLastTriggeredAt(String path, Instant triggeredAt) {
        tree.setRuntimeString(path, "lastTriggeredAt", triggeredAt != null ? triggeredAt.toString() : "");
        tree.objectManager.persistNodeTree(path);
    }

    void deleteCorrelator(String path) {
        EventCorrelator correlator = getCorrelator(path);
        tree.correlatorWindowStore.clearCorrelator(path);
        tree.objectManager.delete(path);
        tree.indexRefresh.afterCorrelatorDeleted(correlator);
    }

    void ensureDemoCorrelators() {
        ensureEscalationCorrelator();
        if (tree.objectManager.tree().findByPath(
                AutomationTreeService.correlatorPathForName("Alarm handler on threshold event")).isPresent()) {
            return;
        }
        createCorrelator(
                "Alarm handler on threshold event",
                "root.platform.devices.demo-sensor-01",
                CorrelatorPatternType.COUNT,
                "thresholdExceeded",
                null,
                0,
                1,
                120,
                0,
                CorrelatorActionType.RUN_WORKFLOW,
                "root.platform.workflows.demo-alarm-handler",
                "",
                true
        );
        createCorrelator(
                "Threshold then alarm active (sequence demo)",
                "root.platform.devices.demo-sensor-01",
                CorrelatorPatternType.SEQUENCE,
                "thresholdExceeded",
                "alarmActive",
                300,
                1,
                120,
                0,
                CorrelatorActionType.RUN_WORKFLOW,
                "root.platform.workflows.demo-alarm-handler",
                "",
                false
        );
    }

    void ensureEscalationCorrelator() {
        String path = AutomationTreeService.correlatorPathForName("Recurring threshold escalation");
        if (tree.objectManager.tree().findByPath(path).isPresent()) {
            return;
        }
        createCorrelator(
                "Recurring threshold escalation",
                "root.platform.devices.demo-sensor-01",
                CorrelatorPatternType.COUNT,
                "thresholdExceeded",
                null,
                300,
                3,
                120,
                0,
                CorrelatorActionType.RUN_WORKFLOW,
                "root.platform.workflows.demo-alarm-handler",
                "",
                true
        );
    }

    void createCorrelatorNode(
            String path,
            String displayName,
            String targetObjectPath,
            CorrelatorPatternType patternType,
            String eventName,
            String secondEventName,
            int windowSeconds,
            int minOccurrences,
            int cooldownSeconds,
            int sequenceGapSeconds,
            CorrelatorActionType actionType,
            String actionTarget,
            String payloadFilterExpr,
            boolean enabled,
            Instant lastTriggeredAt
    ) {
        tree.ensureParent(path);
        String name = AutomationTreeService.leafName(path);
        tree.objectManager.create(AutomationTreeService.parentPath(path), name, ObjectType.CORRELATOR, displayName,
                "Event correlator", "correlator-v1");
        tree.ensureCorrelatorStructureInternal(path);
        tree.setString(path, "targetObjectPath", targetObjectPath != null ? targetObjectPath : "");
        tree.setString(path, "patternType", patternType.name());
        tree.setString(path, "eventName", eventName);
        tree.setString(path, "secondEventName", secondEventName != null ? secondEventName : "");
        tree.setInteger(path, "windowSeconds", windowSeconds);
        tree.setInteger(path, "minOccurrences", minOccurrences);
        tree.setInteger(path, "cooldownSeconds", cooldownSeconds);
        tree.setInteger(path, "sequenceGapSeconds", sequenceGapSeconds);
        tree.setString(path, "actionType", actionType.name());
        tree.setString(path, "actionTarget", actionTarget);
        tree.setString(path, "payloadFilterExpr", payloadFilterExpr != null ? payloadFilterExpr : "");
        tree.setBoolean(path, "enabled", enabled);
        if (lastTriggeredAt != null) {
            tree.setRuntimeString(path, "lastTriggeredAt", lastTriggeredAt.toString());
        }
        tree.objectManager.persistNodeTree(path);
    }

    private EventCorrelator toCorrelator(PlatformObject node) {
        Instant createdAt = node.createdAt() != null ? node.createdAt() : Instant.now();
        String patternRaw = AutomationTreeService.readString(node, "patternType").orElse("COUNT");
        String actionRaw = AutomationTreeService.readString(node, "actionType").orElse("RUN_WORKFLOW");
        String lastTriggeredRaw = AutomationTreeService.readString(node, "lastTriggeredAt").orElse("");
        Instant lastTriggered = lastTriggeredRaw.isBlank() ? null : Instant.parse(lastTriggeredRaw);
        return new EventCorrelator(
                node.path(),
                node.displayName(),
                AutomationTreeService.blankToNull(AutomationTreeService.readString(node, "targetObjectPath").orElse(null)),
                CorrelatorPatternType.valueOf(patternRaw),
                AutomationTreeService.readString(node, "eventName").orElse(""),
                AutomationTreeService.blankToNull(AutomationTreeService.readString(node, "secondEventName").orElse(null)),
                AutomationTreeService.readInteger(node, "windowSeconds").orElse(0),
                AutomationTreeService.readInteger(node, "minOccurrences").orElse(1),
                AutomationTreeService.readInteger(node, "cooldownSeconds").orElse(120),
                AutomationTreeService.readInteger(node, "sequenceGapSeconds").orElse(0),
                CorrelatorActionType.valueOf(actionRaw),
                AutomationTreeService.readString(node, "actionTarget").orElse(""),
                AutomationTreeService.blankToNull(AutomationTreeService.readString(node, "payloadFilterExpr").orElse(null)),
                AutomationTreeService.readBoolean(node, "enabled").orElse(true),
                lastTriggered,
                createdAt,
                createdAt
        );
    }

    private PlatformObject requireCorrelator(String path) {
        PlatformObject node = tree.objectManager.require(path);
        if (node.type() != ObjectType.CORRELATOR) {
            throw new IllegalArgumentException("Not a correlator object: " + path);
        }
        return node;
    }

    private String uniqueCorrelatorPath(String name) {
        String base = AutomationTreeService.correlatorPathForName(name);
        if (tree.objectManager.tree().findByPath(base).isEmpty()) {
            return base;
        }
        int suffix = 2;
        while (tree.objectManager.tree().findByPath(base + "-" + suffix).isPresent()) {
            suffix++;
        }
        return base + "-" + suffix;
    }
}
