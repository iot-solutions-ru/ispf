package com.ispf.server.eventfilter;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.bootstrap.SystemObjectCatalogSupport;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EventFilterObjectService {

    public static final String EVENT_FILTERS_ROOT = "root.platform.event-filters";

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

    public EventFilterObjectService(ObjectManager objectManager, SystemObjectStructureService structureService) {
        this.objectManager = objectManager;
        this.structureService = structureService;
    }

    @Transactional
    public void ensureCatalog() {
        SystemObjectCatalogSupport.ensureFolder(objectManager, EVENT_FILTERS_ROOT, ObjectType.EVENT_FILTERS, null);
    }

    /** Read-only listing; catalog ensured by {@link #ensureCatalog()} at bootstrap. */
    @Transactional(readOnly = true)
    public List<EventFilterDefinition> list() {
        List<EventFilterDefinition> filters = new ArrayList<>();
        if (objectManager.tree().findByPath(EVENT_FILTERS_ROOT).isEmpty()) {
            return filters;
        }
        for (PlatformObject child : objectManager.tree().childrenOf(EVENT_FILTERS_ROOT)) {
            if (child.type() != ObjectType.EVENT_FILTER) {
                continue;
            }
            toDefinition(child.path(), child).ifPresent(filters::add);
        }
        return filters;
    }

    @Transactional(readOnly = true)
    public EventFilterDefinition getByPath(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.EVENT_FILTER) {
            throw new IllegalArgumentException("Not an event filter object: " + path);
        }
        return toDefinition(path, node).orElseThrow(() -> new IllegalArgumentException("Invalid event filter: " + path));
    }

    @Transactional
    public EventFilterDefinition upsert(EventFilterDefinition definition) {
        if (definition.filterId() == null || definition.filterId().isBlank()) {
            throw new IllegalArgumentException("filterId is required");
        }
        ensureCatalog();
        String path = pathForFilterId(definition.filterId());
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    EVENT_FILTERS_ROOT,
                    sanitizeNodeName(definition.filterId()),
                    ObjectType.EVENT_FILTER,
                    definition.displayName() != null && !definition.displayName().isBlank()
                            ? definition.displayName()
                            : definition.filterId(),
                    definition.description() != null ? definition.description() : "Event filter " + definition.filterId(),
                    null
            );
        } else {
            objectManager.reconcileType(path, ObjectType.EVENT_FILTER);
        }
        ensureStructure(path);
        setString(path, "filterId", definition.filterId());
        setString(path, "eventNamePattern", definition.eventNamePattern() != null ? definition.eventNamePattern() : "*");
        setString(path, "sourceObjectPathPattern",
                definition.sourceObjectPathPattern() != null ? definition.sourceObjectPathPattern() : "root.platform.**");
        setInteger(path, "minSeverity", definition.minSeverity());
        setInteger(path, "maxSeverity", definition.maxSeverity());
        setInteger(path, "timeWindowMs", definition.timeWindowMs());
        setString(path, "filterExpression", definition.filterExpression() != null ? definition.filterExpression() : "");
        setBoolean(path, "enabled", definition.enabled());
        return getByPath(path);
    }

    @Transactional
    public void delete(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.EVENT_FILTER) {
            throw new IllegalArgumentException("Not an event filter object: " + path);
        }
        objectManager.delete(path);
    }

    public String pathForFilterId(String filterId) {
        return EVENT_FILTERS_ROOT + "." + sanitizeNodeName(filterId);
    }

    public static String sanitizeNodeName(String name) {
        if (name == null || name.isBlank()) {
            return "filter";
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitized.isEmpty()) {
            return "filter";
        }
        if (Character.isDigit(sanitized.charAt(0))) {
            return "f_" + sanitized;
        }
        return sanitized;
    }

    private void ensureStructure(String path) {
        structureService.ensureEventFilterStructure(path);
    }

    private Optional<EventFilterDefinition> toDefinition(String path, PlatformObject node) {
        String filterId = readString(node, "filterId").orElse(path.substring(path.lastIndexOf('.') + 1));
        return Optional.of(new EventFilterDefinition(
                path,
                filterId,
                node.displayName(),
                node.description(),
                readString(node, "eventNamePattern").orElse("*"),
                readString(node, "sourceObjectPathPattern").orElse("root.platform.**"),
                readLong(node, "minSeverity").orElse(0L),
                readLong(node, "maxSeverity").orElse(100L),
                readLong(node, "timeWindowMs").orElse(0L),
                readString(node, "filterExpression").orElse(""),
                readBoolean(node, "enabled").orElse(true)
        ));
    }

    private void setString(String path, String variable, String value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(STRING_SCHEMA, Map.of("value", value != null ? value : "")));
    }

    private void setInteger(String path, String variable, long value) {
        objectManager.setVariableValue(
                path,
                variable,
                DataRecord.single(INTEGER_SCHEMA, Map.of("value", (int) Math.min(value, Integer.MAX_VALUE)))
        );
    }

    private void setBoolean(String path, String variable, boolean value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(BOOLEAN_SCHEMA, Map.of("value", value)));
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

    private static Optional<Long> readLong(PlatformObject node, String name) {
        return node.getVariable(name).flatMap(Variable::value).map(r -> {
            Object v = r.firstRow().get("value");
            if (v instanceof Number n) {
                return n.longValue();
            }
            return Long.parseLong(String.valueOf(v));
        });
    }

    public record EventFilterDefinition(
            String path,
            String filterId,
            String displayName,
            String description,
            String eventNamePattern,
            String sourceObjectPathPattern,
            long minSeverity,
            long maxSeverity,
            long timeWindowMs,
            String filterExpression,
            boolean enabled
    ) {
    }
}
