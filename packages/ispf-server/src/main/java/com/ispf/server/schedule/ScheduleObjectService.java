package com.ispf.server.schedule;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.bootstrap.SystemObjectCatalogSupport;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.model.SystemObjectStructureService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final SystemObjectStructureService structureService;
    private final ObjectMapper objectMapper;

    public ScheduleObjectService(
            ObjectManager objectManager,
            SystemObjectStructureService structureService,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.structureService = structureService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void ensureCatalog() {
        SystemObjectCatalogSupport.ensureFolder(objectManager, SCHEDULES_ROOT, ObjectType.SCHEDULES, null);
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
                    null
            );
        } else {
            objectManager.reconcileType(path, ObjectType.SCHEDULE);
        }
        ensureStructure(path);
        setString(path, "scheduleId", definition.scheduleId());
        setBoolean(path, "enabled", definition.enabled());
        setInteger(path, "intervalMs", definition.intervalMs());
        setString(path, "actionType", definition.actionType());
        setString(path, "actionJson", definition.actionJson());
    }

    @Transactional(readOnly = true)
    public ScheduleView getByPath(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.SCHEDULE) {
            throw new IllegalArgumentException("Not a schedule object: " + path);
        }
        ensureStructure(path);
        ScheduleDefinition definition = toDefinition(path, node).orElseThrow();
        ActionTarget target = parseActionTarget(definition.actionType(), definition.actionJson());
        return new ScheduleView(
                path,
                definition.scheduleId(),
                node.displayName(),
                node.description(),
                definition.enabled(),
                definition.intervalMs(),
                definition.actionType(),
                target.objectPath(),
                target.functionName(),
                definition.actionJson(),
                definition.lastTickAt(),
                definition.lastError()
        );
    }

    @Transactional
    public ScheduleView create(
            String scheduleId,
            String displayName,
            String description,
            Boolean enabled,
            Long intervalMs,
            String objectPath,
            String functionName
    ) {
        if (scheduleId == null || scheduleId.isBlank()) {
            throw new IllegalArgumentException("scheduleId is required");
        }
        String path = pathForScheduleId(scheduleId);
        if (objectManager.tree().findByPath(path).isPresent()) {
            throw new IllegalArgumentException("Schedule already exists: " + scheduleId);
        }
        String nodeName = sanitizeNodeName(scheduleId);
        objectManager.create(
                SCHEDULES_ROOT,
                nodeName,
                ObjectType.SCHEDULE,
                displayName != null && !displayName.isBlank() ? displayName : scheduleId,
                description != null ? description : "",
                null
        );
        applyScheduleFields(path, scheduleId, enabled, intervalMs, objectPath, functionName);
        if (displayName != null && !displayName.isBlank()) {
            objectManager.updateInfo(path, displayName, description != null ? description : "");
        }
        return getByPath(path);
    }

    @Transactional
    public ScheduleView update(
            String path,
            String displayName,
            String description,
            Boolean enabled,
            Long intervalMs,
            String objectPath,
            String functionName
    ) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.SCHEDULE) {
            throw new IllegalArgumentException("Not a schedule object: " + path);
        }
        String scheduleId = readString(node, "scheduleId").orElse(path.substring(path.lastIndexOf('.') + 1));
        String nextDisplayName = displayName != null && !displayName.isBlank() ? displayName : node.displayName();
        String nextDescription = description != null ? description : node.description();
        objectManager.updateInfo(path, nextDisplayName, nextDescription);
        applyScheduleFields(path, scheduleId, enabled, intervalMs, objectPath, functionName);
        return getByPath(path);
    }

    public String pathForScheduleId(String scheduleId) {
        return SCHEDULES_ROOT + "." + sanitizeNodeName(scheduleId);
    }

    private void applyScheduleFields(
            String path,
            String scheduleId,
            Boolean enabled,
            Long intervalMs,
            String objectPath,
            String functionName
    ) {
        ensureStructure(path);
        setString(path, "scheduleId", scheduleId);
        if (enabled != null) {
            setBoolean(path, "enabled", enabled);
        }
        if (intervalMs != null) {
            setInteger(path, "intervalMs", intervalMs);
        }
        if (objectPath != null || functionName != null) {
            ScheduleDefinition current = toDefinition(path, objectManager.require(path)).orElseThrow();
            ActionTarget target = parseActionTarget(current.actionType(), current.actionJson());
            String nextObjectPath = objectPath != null ? objectPath : target.objectPath();
            String nextFunctionName = functionName != null ? functionName : target.functionName();
            setString(path, "actionType", "invoke_function");
            setString(path, "actionJson", buildActionJson(nextObjectPath, nextFunctionName));
        }
    }

    private String buildActionJson(String objectPath, String functionName) {
        try {
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("objectPath", objectPath != null ? objectPath : "");
            action.put("functionName", functionName != null ? functionName : "");
            return objectMapper.writeValueAsString(action);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to serialize schedule action", ex);
        }
    }

    private ActionTarget parseActionTarget(String actionType, String actionJson) {
        if (!"invoke_function".equals(actionType) || actionJson == null || actionJson.isBlank()) {
            return new ActionTarget("", "");
        }
        try {
            Map<?, ?> action = objectMapper.readValue(actionJson, Map.class);
            return new ActionTarget(
                    stringValue(action.get("objectPath")),
                    stringValue(action.get("functionName"))
            );
        } catch (Exception ex) {
            return new ActionTarget("", "");
        }
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private record ActionTarget(String objectPath, String functionName) {
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
        structureService.ensureScheduleStructure(path);
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

    public record ScheduleView(
            String path,
            String scheduleId,
            String displayName,
            String description,
            boolean enabled,
            long intervalMs,
            String actionType,
            String objectPath,
            String functionName,
            String actionJson,
            Instant lastTickAt,
            String lastError
    ) {
    }
}
