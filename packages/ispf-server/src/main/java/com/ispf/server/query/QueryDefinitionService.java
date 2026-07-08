package com.ispf.server.query;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.expression.ExpressionEngine;
import com.ispf.server.bootstrap.SystemObjectCatalogSupport;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class QueryDefinitionService {

    public static final String QUERIES_ROOT = "root.platform.queries";

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
    private final ExpressionEngine expressionEngine = new ExpressionEngine();

    public QueryDefinitionService(
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
        SystemObjectCatalogSupport.ensureFolder(objectManager, QUERIES_ROOT, ObjectType.QUERIES, null);
    }

    @Transactional(readOnly = true)
    public List<QueryDefinition> list() {
        ensureCatalog();
        List<QueryDefinition> queries = new ArrayList<>();
        if (objectManager.tree().findByPath(QUERIES_ROOT).isEmpty()) {
            return queries;
        }
        for (PlatformObject child : objectManager.tree().childrenOf(QUERIES_ROOT)) {
            if (child.type() != ObjectType.QUERY) {
                continue;
            }
            toDefinition(child.path(), child).ifPresent(queries::add);
        }
        return queries;
    }

    @Transactional(readOnly = true)
    public QueryDefinition getByPath(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.QUERY) {
            throw new IllegalArgumentException("Not a query object: " + path);
        }
        return toDefinition(path, node).orElseThrow(() -> new IllegalArgumentException("Invalid query: " + path));
    }

    @Transactional
    public QueryDefinition create(QueryDefinition definition) {
        if (definition.queryId() == null || definition.queryId().isBlank()) {
            throw new IllegalArgumentException("queryId is required");
        }
        String path = pathForQueryId(definition.queryId());
        if (objectManager.tree().findByPath(path).isPresent()) {
            throw new IllegalArgumentException("Query already exists: " + definition.queryId());
        }
        String nodeName = sanitizeNodeName(definition.queryId());
        objectManager.create(
                QUERIES_ROOT,
                nodeName,
                ObjectType.QUERY,
                definition.displayName() != null && !definition.displayName().isBlank()
                        ? definition.displayName()
                        : definition.queryId(),
                definition.description() != null ? definition.description() : "Query " + definition.queryId(),
                null
        );
        upsertVariables(path, definition);
        return getByPath(path);
    }

    @Transactional
    public QueryDefinition update(String path, QueryDefinition definition) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.QUERY) {
            throw new IllegalArgumentException("Not a query object: " + path);
        }
        if (definition.displayName() != null && !definition.displayName().isBlank()) {
            objectManager.updateInfo(path, definition.displayName(), definition.description());
        } else if (definition.description() != null) {
            objectManager.updateInfo(path, node.displayName(), definition.description());
        }
        upsertVariables(path, definition);
        return getByPath(path);
    }

    @Transactional
    public void delete(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.QUERY) {
            throw new IllegalArgumentException("Not a query object: " + path);
        }
        objectManager.delete(path);
    }

    @Transactional
    public QueryExecuteResult execute(String path) {
        return execute(getByPath(path));
    }

    @Transactional
    public QueryExecuteResult execute(QueryDefinition definition) {
        if (!definition.enabled()) {
            throw new IllegalStateException("Query is disabled: " + definition.queryId());
        }
        Instant executedAt = Instant.now();
        try {
            QuerySpec spec = parseQuerySpec(definition.fieldsJson());
            String pattern = definition.sourcePathPattern();
            if (pattern == null || pattern.isBlank()) {
                throw new IllegalArgumentException("sourcePathPattern is required");
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (PlatformObject node : objectManager.tree().all()) {
                if (!ObjectPathPattern.matches(node.path(), pattern)) {
                    continue;
                }
                if (!matchesObjectTypes(node, spec.objectTypes())) {
                    continue;
                }
                if (!passesFilter(definition.filterExpression(), node)) {
                    continue;
                }
                rows.add(buildRow(node, spec.fields()));
            }
            recordRun(definition.path(), executedAt, null);
            return new QueryExecuteResult(
                    definition.queryId(),
                    definition.path(),
                    List.copyOf(rows),
                    rows.size(),
                    executedAt.toString(),
                    null
            );
        } catch (Exception ex) {
            recordRun(definition.path(), executedAt, ex.getMessage());
            throw ex instanceof RuntimeException runtime ? runtime : new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private void recordRun(String path, Instant at, String error) {
        setString(path, "lastRunAt", at.toString());
        setString(path, "lastError", error != null ? error : "");
    }

    private boolean passesFilter(String filterExpression, PlatformObject node) {
        if (filterExpression == null || filterExpression.isBlank()) {
            return true;
        }
        Object result = expressionEngine.evaluate(filterExpression, node);
        return !(result instanceof Boolean bool) || bool;
    }

    private static boolean matchesObjectTypes(PlatformObject node, Set<ObjectType> objectTypes) {
        if (objectTypes == null || objectTypes.isEmpty()) {
            return true;
        }
        return objectTypes.contains(node.type());
    }

    private Map<String, Object> buildRow(PlatformObject node, List<FieldSpec> fields) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (FieldSpec field : fields) {
            row.put(field.name(), resolveFieldValue(node, field.source()));
        }
        return row;
    }

    private static Object resolveFieldValue(PlatformObject node, String source) {
        if (source == null || source.isBlank() || "path".equals(source)) {
            return node.path();
        }
        if ("type".equals(source)) {
            return node.type().name();
        }
        if ("displayName".equals(source)) {
            return node.displayName();
        }
        if ("description".equals(source)) {
            return node.description();
        }
        return node.getVariable(source)
                .flatMap(Variable::value)
                .map(record -> record.rowCount() > 0 ? record.firstRow() : Map.of())
                .orElse(null);
    }

    private QuerySpec parseQuerySpec(String fieldsJson) {
        if (fieldsJson == null || fieldsJson.isBlank()) {
            return new QuerySpec(List.of(new FieldSpec("path", "path")), Set.of());
        }
        try {
            JsonNode root = objectMapper.readTree(fieldsJson);
            if (root.isArray()) {
                return new QuerySpec(parseFieldArray(root), Set.of());
            }
            if (root.isObject()) {
                Set<ObjectType> objectTypes = new LinkedHashSet<>();
                JsonNode typesNode = root.get("objectTypes");
                if (typesNode != null && typesNode.isArray()) {
                    for (JsonNode typeNode : typesNode) {
                        objectTypes.add(ObjectType.valueOf(typeNode.asString()));
                    }
                }
                JsonNode fieldsNode = root.get("fields");
                if (fieldsNode != null && fieldsNode.isArray()) {
                    return new QuerySpec(parseFieldArray(fieldsNode), objectTypes);
                }
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid fieldsJson: " + ex.getMessage(), ex);
        }
        throw new IllegalArgumentException("fieldsJson must be a field array or object with fields[]");
    }

    private List<FieldSpec> parseFieldArray(JsonNode array) {
        List<FieldSpec> fields = new ArrayList<>();
        for (JsonNode fieldNode : array) {
            if (!fieldNode.isObject()) {
                continue;
            }
            String name = fieldNode.path("name").asString(null);
            String source = fieldNode.path("source").asString(name);
            if (name != null && !name.isBlank()) {
                fields.add(new FieldSpec(name, source));
            }
        }
        if (fields.isEmpty()) {
            fields.add(new FieldSpec("path", "path"));
        }
        return fields;
    }

    private record QuerySpec(List<FieldSpec> fields, Set<ObjectType> objectTypes) {
    }

    private record FieldSpec(String name, String source) {
    }

    public record QueryExecuteResult(
            String queryId,
            String path,
            List<Map<String, Object>> rows,
            int rowCount,
            String executedAt,
            String error
    ) {
    }

    public String pathForQueryId(String queryId) {
        return QUERIES_ROOT + "." + sanitizeNodeName(queryId);
    }

    public static String sanitizeNodeName(String name) {
        if (name == null || name.isBlank()) {
            return "query";
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitized.isEmpty()) {
            return "query";
        }
        if (Character.isDigit(sanitized.charAt(0))) {
            return "q_" + sanitized;
        }
        return sanitized;
    }

    private void upsertVariables(String path, QueryDefinition definition) {
        ensureStructure(path);
        setString(path, "queryId", definition.queryId());
        setString(path, "queryType", definition.queryType() != null ? definition.queryType() : "tree-scan");
        setString(path, "sourcePathPattern", definition.sourcePathPattern() != null ? definition.sourcePathPattern() : "");
        setString(path, "fieldsJson", definition.fieldsJson() != null ? definition.fieldsJson() : "[]");
        setString(path, "filterExpression", definition.filterExpression() != null ? definition.filterExpression() : "");
        setBoolean(path, "enabled", definition.enabled());
    }

    private void ensureStructure(String path) {
        structureService.ensureQueryStructure(path);
    }

    private Optional<QueryDefinition> toDefinition(String path, PlatformObject node) {
        String queryId = readString(node, "queryId").orElse(path.substring(path.lastIndexOf('.') + 1));
        return Optional.of(new QueryDefinition(
                path,
                queryId,
                node.displayName(),
                node.description(),
                readString(node, "queryType").orElse("tree-scan"),
                readString(node, "sourcePathPattern").orElse(""),
                readString(node, "fieldsJson").orElse("[]"),
                readString(node, "filterExpression").orElse(""),
                readBoolean(node, "enabled").orElse(true),
                readString(node, "lastRunAt").orElse(""),
                readString(node, "lastError").orElse("")
        ));
    }

    private void setString(String path, String variable, String value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(STRING_SCHEMA, Map.of("value", value != null ? value : "")));
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

    public record QueryDefinition(
            String path,
            String queryId,
            String displayName,
            String description,
            String queryType,
            String sourcePathPattern,
            String fieldsJson,
            String filterExpression,
            boolean enabled,
            String lastRunAt,
            String lastError
    ) {
    }
}
