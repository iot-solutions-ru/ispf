package com.ispf.server.binding;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.bootstrap.SystemObjectCatalogSupport;
import com.ispf.server.datasource.DataSourcePathResolver;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.object.ObjectManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SqlBindingObjectService {

    public static final String BINDINGS_ROOT = "root.platform.bindings";

    private static final DataSchema STRING_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema INTEGER_SCHEMA = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();

    private static final DataSchema BOOLEAN_SCHEMA = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private static final DataSchema DOUBLE_SCHEMA = DataSchema.builder("doubleValue")
            .field("value", FieldType.DOUBLE)
            .build();

    private final ObjectManager objectManager;
    private final ModelRegistry modelRegistry;
    private final ModelEngine modelEngine;
    private final ApplicationSchemaSession schemaSession;
    private final DataSourcePathResolver dataSourcePathResolver;
    private final JdbcTemplate jdbcTemplate;

    public SqlBindingObjectService(
            ObjectManager objectManager,
            ModelRegistry modelRegistry,
            ModelEngine modelEngine,
            ApplicationSchemaSession schemaSession,
            DataSourcePathResolver dataSourcePathResolver,
            JdbcTemplate jdbcTemplate
    ) {
        this.objectManager = objectManager;
        this.modelRegistry = modelRegistry;
        this.modelEngine = modelEngine;
        this.schemaSession = schemaSession;
        this.dataSourcePathResolver = dataSourcePathResolver;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void ensureCatalog() {
        SystemObjectCatalogSupport.ensureFolder(objectManager, BINDINGS_ROOT, ObjectType.BINDINGS, null);
    }

    @Transactional
    public void upsert(BindingDefinition definition) {
        ensureCatalog();
        String nodeName = sanitizeNodeName(definition.bindingId());
        String path = BINDINGS_ROOT + "." + nodeName;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    BINDINGS_ROOT,
                    nodeName,
                    ObjectType.BINDING,
                    definition.bindingId(),
                    "SQL binding " + definition.bindingId(),
                    "sql-binding-v1"
            );
        }
        ensureStructure(path);
        setString(path, "targetObjectPath", definition.targetObjectPath());
        setString(path, "variable", definition.variable());
        setString(path, "dataSourcePath", definition.dataSourcePath());
        setString(path, "query", definition.query());
        setString(path, "valueField", definition.valueField());
        setString(path, "refresh", definition.refresh());
        setInteger(path, "refreshIntervalMs", definition.refreshIntervalMs());
        setString(path, "triggerObjectPath", definition.triggerObjectPath());
        setString(path, "triggerFunctionName", definition.triggerFunctionName());
        setBoolean(path, "enabled", definition.enabled());
        ensureVariable(definition.targetObjectPath(), definition.variable());
    }

    @Transactional(readOnly = true)
    public List<BindingDefinition> listEnabledForSchedule() {
        return listAll().stream()
                .filter(BindingDefinition::enabled)
                .filter(b -> "on_schedule".equals(b.refresh()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BindingDefinition> listForFunctionSuccess(String objectPath, String functionName) {
        return listAll().stream()
                .filter(BindingDefinition::enabled)
                .filter(b -> "on_function_success".equals(b.refresh()))
                .filter(b -> objectPath.equals(b.triggerObjectPath()))
                .filter(b -> matchesTriggerFunction(b.triggerFunctionName(), functionName))
                .toList();
    }

    private static boolean matchesTriggerFunction(String configured, String functionName) {
        if (configured == null || configured.isBlank() || functionName == null || functionName.isBlank()) {
            return false;
        }
        for (String part : configured.split(",")) {
            if (functionName.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public BindingDefinition getByPath(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.BINDING) {
            throw new IllegalArgumentException("Not a SQL binding object: " + path);
        }
        return toDefinition(path, node)
                .orElseThrow(() -> new IllegalArgumentException("Invalid binding: " + path));
    }

    @Transactional
    public BindingDefinition create(BindingDefinition definition) {
        if (definition.bindingId() == null || definition.bindingId().isBlank()) {
            throw new IllegalArgumentException("bindingId is required");
        }
        upsert(definition);
        return getByPath(pathForBindingId(definition.bindingId()));
    }

    @Transactional
    public BindingDefinition update(String path, BindingDefinition definition) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.BINDING) {
            throw new IllegalArgumentException("Not a SQL binding object: " + path);
        }
        String bindingId = path.substring(path.lastIndexOf('.') + 1);
        upsert(new BindingDefinition(
                path,
                bindingId,
                definition.targetObjectPath(),
                definition.variable(),
                definition.dataSourcePath(),
                definition.query(),
                definition.valueField(),
                definition.refresh(),
                definition.refreshIntervalMs(),
                definition.triggerObjectPath(),
                definition.triggerFunctionName(),
                definition.enabled(),
                definition.lastRefreshedAt()
        ));
        return getByPath(path);
    }

    @Transactional
    public void refresh(String path) {
        BindingDefinition binding = getByPath(path);
        executeRefresh(binding);
    }

    public String pathForBindingId(String bindingId) {
        return BINDINGS_ROOT + "." + sanitizeNodeName(bindingId);
    }

    @Transactional
    public void refreshAfterFunction(String objectPath, String functionName) {
        for (BindingDefinition binding : listForFunctionSuccess(objectPath, functionName)) {
            executeRefresh(binding);
        }
    }

    @Transactional
    public void refreshScheduledBindings() {
        Instant now = Instant.now();
        for (BindingDefinition binding : listEnabledForSchedule()) {
            long intervalMs = binding.refreshIntervalMs() > 0 ? binding.refreshIntervalMs() : 30_000L;
            if (binding.lastRefreshedAt() != null
                    && binding.lastRefreshedAt().plusMillis(intervalMs).isAfter(now)) {
                continue;
            }
            executeRefresh(binding);
        }
    }

    private List<BindingDefinition> listAll() {
        ensureCatalog();
        List<BindingDefinition> bindings = new ArrayList<>();
        if (objectManager.tree().findByPath(BINDINGS_ROOT).isEmpty()) {
            return bindings;
        }
        for (PlatformObject child : objectManager.tree().childrenOf(BINDINGS_ROOT)) {
            if (child.type() != ObjectType.BINDING) {
                continue;
            }
            toDefinition(child.path(), child).ifPresent(bindings::add);
        }
        return bindings;
    }

    private void executeRefresh(BindingDefinition binding) {
        String schemaName = dataSourcePathResolver.resolveSchemaName(binding.dataSourcePath());
        Object[] extracted = new Object[1];
        schemaSession.runInSchema(schemaName, () -> {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(binding.query());
            if (rows.isEmpty()) {
                extracted[0] = null;
                return;
            }
            Map<String, Object> row = rows.get(0);
            String field = binding.valueField() != null && !binding.valueField().isBlank()
                    ? binding.valueField()
                    : "value";
            Object value = row.get(field);
            if (value == null) {
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(field)) {
                        value = entry.getValue();
                        break;
                    }
                }
            }
            extracted[0] = value;
        });
        Object value = extracted[0];
        double numeric = value instanceof Number number ? number.doubleValue() : 0.0;
        objectManager.setSystemVariableValue(
                binding.targetObjectPath(),
                binding.variable(),
                DataRecord.single(DOUBLE_SCHEMA, Map.of("value", numeric))
        );
        setString(binding.path(), "lastRefreshedAt", Instant.now().toString());
    }

    private void ensureStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.getVariable("query").isPresent()) {
            return;
        }
        modelRegistry.findByName("sql-binding-v1").ifPresent(model -> {
            modelEngine.applyModel(model.id(), path);
            objectManager.persistNodeTree(path);
        });
    }

    private void ensureVariable(String objectPath, String variableName) {
        PlatformObject target = objectManager.require(objectPath);
        if (target.getVariable(variableName).isPresent()) {
            return;
        }
        objectManager.createVariable(
                objectPath,
                variableName,
                DOUBLE_SCHEMA,
                true,
                false,
                DataRecord.single(DOUBLE_SCHEMA, Map.of("value", 0.0)),
                false,
                null
        );
    }

    private Optional<BindingDefinition> toDefinition(String path, PlatformObject node) {
        String bindingId = path.substring(path.lastIndexOf('.') + 1);
        return Optional.of(new BindingDefinition(
                path,
                bindingId,
                readString(node, "targetObjectPath").orElse(""),
                readString(node, "variable").orElse("value"),
                readString(node, "dataSourcePath").orElse(""),
                readString(node, "query").orElse(""),
                readString(node, "valueField").orElse("value"),
                readString(node, "refresh").orElse("manual"),
                readLong(node, "refreshIntervalMs").orElse(30_000L),
                readString(node, "triggerObjectPath").orElse(""),
                readString(node, "triggerFunctionName").orElse(""),
                readBoolean(node, "enabled").orElse(true),
                readInstant(node, "lastRefreshedAt")
        ));
    }

    public static String sanitizeNodeName(String name) {
        if (name == null || name.isBlank()) {
            return "binding";
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        return sanitized.isEmpty() ? "binding" : sanitized;
    }

    private void setString(String path, String variable, String value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(STRING_SCHEMA, Map.of("value", value != null ? value : "")));
    }

    private void setInteger(String path, String variable, long value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(INTEGER_SCHEMA, Map.of("value", (int) Math.min(value, Integer.MAX_VALUE))));
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

    private static Instant readInstant(PlatformObject node, String name) {
        return readString(node, name).filter(s -> !s.isBlank()).map(Instant::parse).orElse(null);
    }

    public record BindingDefinition(
            String path,
            String bindingId,
            String targetObjectPath,
            String variable,
            String dataSourcePath,
            String query,
            String valueField,
            String refresh,
            long refreshIntervalMs,
            String triggerObjectPath,
            String triggerFunctionName,
            boolean enabled,
            Instant lastRefreshedAt
    ) {
    }
}
