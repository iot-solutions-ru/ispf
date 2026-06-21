package com.ispf.server.schedule;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ScheduleObjectService {

    public static final String SCHEDULES_ROOT = "root.platform.schedules";

    private static final DataSchema STRING_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema INTEGER_SCHEMA = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();

    private static final DataSchema BOOLEAN_SCHEMA = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private final ObjectManager objectManager;
    private final ModelRegistry modelRegistry;
    private final ModelEngine modelEngine;

    public ScheduleObjectService(
            ObjectManager objectManager,
            ModelRegistry modelRegistry,
            ModelEngine modelEngine
    ) {
        this.objectManager = objectManager;
        this.modelRegistry = modelRegistry;
        this.modelEngine = modelEngine;
    }

    @Transactional
    public void ensureCatalog() {
        if (objectManager.tree().findByPath(SCHEDULES_ROOT).isEmpty()) {
            objectManager.create(
                    "root.platform",
                    "schedules",
                    ObjectType.SCHEDULES,
                    "Schedules",
                    "Platform scheduler jobs (tree-first)",
                    null
            );
        } else {
            objectManager.reconcileType(SCHEDULES_ROOT, ObjectType.SCHEDULES);
        }
    }

    @Transactional(readOnly = true)
    public List<ScheduleDefinition> listEnabled() {
        ensureCatalogPresent();
        List<ScheduleDefinition> schedules = new ArrayList<>();
        if (objectManager.tree().findByPath(SCHEDULES_ROOT).isEmpty()) {
            return schedules;
        }
        for (PlatformObject child : objectManager.tree().childrenOf(SCHEDULES_ROOT)) {
            if (child.type() != ObjectType.SCHEDULE) {
                continue;
            }
            toDefinition(child.path(), child).filter(ScheduleDefinition::enabled).ifPresent(schedules::add);
        }
        return schedules;
    }

    @Transactional
    public void upsert(ScheduleDefinition definition) {
        ensureCatalog();
        String nodeName = sanitizeNodeName(definition.scheduleId());
        String path = SCHEDULES_ROOT + "." + nodeName;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    SCHEDULES_ROOT,
                    nodeName,
                    ObjectType.SCHEDULE,
                    definition.scheduleId(),
                    "Schedule " + definition.scheduleId(),
                    "schedule-v1"
            );
        }
        ensureStructure(path);
        setString(path, "scheduleId", definition.scheduleId());
        setBoolean(path, "enabled", definition.enabled());
        setInteger(path, "intervalMs", definition.intervalMs());
        setString(path, "actionType", definition.actionType());
        setString(path, "actionJson", definition.actionJson());
    }

    @Transactional
    public void recordTick(String path, Instant at, String error) {
        setString(path, "lastTickAt", at != null ? at.toString() : "");
        setString(path, "lastError", error != null ? error : "");
    }

    private void ensureCatalogPresent() {
        if (objectManager.tree().findByPath(SCHEDULES_ROOT).isEmpty()) {
            ensureCatalog();
        }
    }

    private void ensureStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.getVariable("scheduleId").isPresent()) {
            return;
        }
        modelRegistry.findByName("schedule-v1").ifPresent(model -> {
            modelEngine.applyModel(model.id(), path);
            objectManager.persistNodeTree(path);
        });
    }

    private Optional<ScheduleDefinition> toDefinition(String path, PlatformObject node) {
        String scheduleId = readString(node, "scheduleId").orElse(path.substring(path.lastIndexOf('.') + 1));
        return Optional.of(new ScheduleDefinition(
                path,
                scheduleId,
                readBoolean(node, "enabled").orElse(true),
                readInteger(node, "intervalMs").orElse(60_000L),
                readString(node, "actionType").orElse("invoke_function"),
                readString(node, "actionJson").orElse("{}"),
                readInstant(node, "lastTickAt"),
                readString(node, "lastError").orElse(null)
        ));
    }

    public static String sanitizeNodeName(String name) {
        if (name == null || name.isBlank()) {
            return "schedule";
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        return sanitized.isEmpty() ? "schedule" : sanitized;
    }

    private void setString(String path, String variable, String value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(STRING_SCHEMA, java.util.Map.of("value", value != null ? value : "")));
    }

    private void setInteger(String path, String variable, long value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(INTEGER_SCHEMA, java.util.Map.of("value", (int) Math.min(value, Integer.MAX_VALUE))));
    }

    private void setBoolean(String path, String variable, boolean value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(BOOLEAN_SCHEMA, java.util.Map.of("value", value)));
    }

    private static Optional<String> readString(PlatformObject node, String name) {
        return node.getVariable(name).flatMap(Variable::value).map(r -> String.valueOf(r.firstRow().get("value")));
    }

    private static Optional<Boolean> readBoolean(PlatformObject node, String name) {
        return node.getVariable(name).flatMap(Variable::value).map(r -> {
            Object v = r.firstRow().get("value");
            return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
        });
    }

    private static Optional<Long> readInteger(PlatformObject node, String name) {
        return node.getVariable(name).flatMap(Variable::value).map(r -> {
            Object v = r.firstRow().get("value");
            if (v instanceof Number n) {
                return n.longValue();
            }
            return Long.parseLong(String.valueOf(v));
        });
    }

    private static Instant readInstant(PlatformObject node, String name) {
        return readString(node, name)
                .filter(s -> !s.isBlank())
                .map(Instant::parse)
                .orElse(null);
    }

    public record ScheduleDefinition(
            String path,
            String scheduleId,
            boolean enabled,
            long intervalMs,
            String actionType,
            String actionJson,
            Instant lastTickAt,
            String lastError
    ) {
    }
}
