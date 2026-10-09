package com.ispf.server.application.binding;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.alert.AlertRuleService;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.data.ApplicationSchemaSupport;
import com.ispf.expression.BindingExpressionEvaluator;
import com.ispf.server.binding.BindingInvokeAuditService;
import com.ispf.server.binding.SqlBindingValues;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.platform.AutomationMetricsRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import java.util.Locale;
@Service
@Transactional
public class ApplicationSqlBindingService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationSqlBindingService.class);

    private static final DataSchema SINGLE_VALUE_SCHEMA = DataSchema.builder("sqlBindingValue")
            .field("value", FieldType.DOUBLE)
            .build();

    /** Binding ids already soft-disabled after a missing target; skip refresh for JVM lifetime. */
    private final Set<String> disabledOrphanBindingIds = ConcurrentHashMap.newKeySet();

    private final ApplicationSqlBindingStore store;
    private final ApplicationSchemaSession schemaSession;
    private final ApplicationDataStore dataStore;
    private final ObjectManager objectManager;
    private final AlertRuleService alertRuleService;
    private final BindingInvokeAuditService bindingAuditService;
    private final ObjectEntityMapper entityMapper;
    private final ApplicationSqlBindingEventIndex sqlBindingEventIndex;
    private final AutomationMetricsRecorder metricsRecorder;

    public ApplicationSqlBindingService(
            ApplicationSqlBindingStore store,
            ApplicationSchemaSession schemaSession,
            ApplicationDataStore dataStore,
            ObjectManager objectManager,
            @Lazy AlertRuleService alertRuleService,
            BindingInvokeAuditService bindingAuditService,
            ObjectEntityMapper entityMapper,
            ApplicationSqlBindingEventIndex sqlBindingEventIndex,
            AutomationMetricsRecorder metricsRecorder
    ) {
        this.store = store;
        this.schemaSession = schemaSession;
        this.dataStore = dataStore;
        this.objectManager = objectManager;
        this.alertRuleService = alertRuleService;
        this.bindingAuditService = bindingAuditService;
        this.entityMapper = entityMapper;
        this.sqlBindingEventIndex = sqlBindingEventIndex;
        this.metricsRecorder = metricsRecorder;
    }

    public void deploy(String appId, DeploySqlBindingRequest request) {
        ApplicationSchemaSupport.validateSelectQuery(request.query(), "Binding query");
        String valueField = request.valueField() != null && !request.valueField().isBlank()
                ? request.valueField()
                : "value";
        String refreshMode = normalizeRefreshMode(request.refresh());
        Long intervalMs = request.refreshIntervalMs();
        if ("on_schedule".equals(refreshMode) && (intervalMs == null || intervalMs <= 0)) {
            intervalMs = 30_000L;
        }
        store.upsert(new ApplicationSqlBindingStore.SqlBinding(
                UUID.randomUUID(),
                appId,
                request.objectPath(),
                request.variable(),
                request.query(),
                refreshMode,
                intervalMs,
                valueField,
                request.triggerObjectPath(),
                request.triggerFunctionName(),
                request.enabled() == null || request.enabled(),
                null
        ));
        sqlBindingEventIndex.onBindingChanged();
        ensureVariable(request.objectPath(), request.variable());
        store.listByApp(appId).stream()
                .filter(binding -> binding.objectPath().equals(request.objectPath())
                        && binding.variableName().equals(request.variable()))
                .findFirst()
                .ifPresent(binding -> executeRefresh(binding, "MANUAL"));
    }

    public List<Map<String, Object>> list(String appId) {
        return store.listByApp(appId).stream().map(this::toMap).toList();
    }

    public Map<String, Object> refresh(String appId, String objectPath, String variableName) {
        store.listByApp(appId).stream()
                .filter(binding -> binding.objectPath().equals(objectPath)
                        && binding.variableName().equals(variableName))
                .findFirst()
                .ifPresent(binding -> executeRefresh(binding, "MANUAL"));
        return Map.of(
                "appId", appId,
                "objectPath", objectPath,
                "variable", variableName,
                "status", "refreshed"
        );
    }

    /** One binding of an event fan-out; the event may be handled on the publisher's thread, inside its transaction. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refreshBinding(ApplicationSqlBindingStore.SqlBinding binding) {
        refreshIsolated(binding, triggerForRefreshMode(binding.refreshMode()));
    }

    /**
     * Joins the caller's transaction (a workflow step), so the queries see the function's uncommitted writes; a
     * failing query fails the caller.
     */
    public void refreshAfterFunction(String appId, String objectPath, String functionName) {
        for (ApplicationSqlBindingStore.SqlBinding binding : store.listForFunctionSuccess(appId, objectPath, functionName)) {
            executeRefresh(binding, "FUNCTION_SUCCESS");
        }
    }

    /** The bindings of {@link #refreshAfterFunction} once the function's transaction has committed. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refreshAfterFunctionCommit(String appId, String objectPath, String functionName) {
        for (ApplicationSqlBindingStore.SqlBinding binding : store.listForFunctionSuccess(appId, objectPath, functionName)) {
            refreshIsolated(binding, "FUNCTION_SUCCESS");
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refreshScheduledBindings() {
        for (ApplicationSqlBindingStore.SqlBinding binding : store.listEnabledForSchedule()) {
            long intervalMs = binding.refreshIntervalMs() != null ? binding.refreshIntervalMs() : 30_000L;
            if (binding.lastRefreshedAt() != null
                    && binding.lastRefreshedAt().plusMillis(intervalMs).isAfter(java.time.Instant.now())) {
                continue;
            }
            refreshIsolated(binding, "SCHEDULE");
        }
    }

    public void refreshBinding(String appId, String objectPath, String variableName) {
        store.listByApp(appId).stream()
                .filter(binding -> binding.objectPath().equals(objectPath)
                        && binding.variableName().equals(variableName))
                .findFirst()
                .ifPresent(binding -> executeRefresh(binding, "MANUAL"));
    }

    /**
     * One binding of a fan-out. Called outside a transaction, so its query and writes commit on their own: a failed
     * statement, which aborts the whole transaction on PostgreSQL, cannot undo or block another binding.
     */
    private void refreshIsolated(ApplicationSqlBindingStore.SqlBinding binding, String triggerKind) {
        try {
            executeRefresh(binding, triggerKind, SqlBindingValues.OnQueryFailure.MARK_BAD);
        } catch (RuntimeException ex) {
            log.error("Application SQL binding {} refresh failed", binding.id(), ex);
        }
    }

    private void executeRefresh(ApplicationSqlBindingStore.SqlBinding binding, String triggerKind) {
        executeRefresh(binding, triggerKind, SqlBindingValues.OnQueryFailure.PROPAGATE);
    }

    private void executeRefresh(
            ApplicationSqlBindingStore.SqlBinding binding,
            String triggerKind,
            SqlBindingValues.OnQueryFailure onQueryFailure
    ) {
        if (disabledOrphanBindingIds.contains(binding.id().toString())) {
            return;
        }
        long start = System.nanoTime();
        boolean success = true;
        boolean changed = false;
        String error = null;
        DataRecord previous = null;
        DataRecord next = null;
        try {
            FieldType fieldType = resolveValueFieldType(binding);
            SqlBindingValues.Extracted extracted = queryValue(binding, fieldType, onQueryFailure);
            previous = schemaSession.callWithPlatformCatalog(() ->
                    objectManager.tree().findByPath(binding.objectPath())
                            .flatMap(node -> node.getVariable(binding.variableName()))
                            .flatMap(Variable::value)
                            .orElse(null));
            DataRecord record = extracted.ok()
                    ? toValueRecord(extracted.value(), fieldType)
                    : SqlBindingValues.badQuality(previous, fieldType);
            next = record;
            changed = !BindingExpressionEvaluator.recordsEqual(previous, record);
            if (!extracted.ok()) {
                success = false;
                error = extracted.detail();
                metricsRecorder.recordSqlBindingFailure(extracted.failure());
                if (changed) {
                    log.warn(
                            "Application SQL binding {} returned no usable value ({}); {} / {} keeps its last value"
                                    + " with quality=BAD",
                            binding.id(),
                            extracted.detail(),
                            binding.objectPath(),
                            binding.variableName()
                    );
                }
            }
            boolean write = extracted.ok() || changed;
            if (write) {
                schemaSession.runWithPlatformCatalog(() ->
                        objectManager.setSystemVariableValue(binding.objectPath(), binding.variableName(), record)
                );
            }
            store.markRefreshed(binding.id());
            if (write) {
                schemaSession.runWithPlatformCatalog(() ->
                        alertRuleService.processVariableChange(binding.objectPath(), binding.variableName())
                );
            }
        } catch (ObjectNotFoundException ex) {
            success = false;
            changed = false;
            error = ex.getMessage();
            // Orphan target must not abort the refresh fan-out for remaining bindings (H4 parity).
            if (disabledOrphanBindingIds.add(binding.id().toString())) {
                log.warn(
                        "Disabling application SQL binding {} (missing target {} / {}): {}",
                        binding.id(),
                        binding.objectPath(),
                        binding.variableName(),
                        ex.getMessage()
                );
                try {
                    store.setEnabled(binding.id(), false);
                } catch (RuntimeException disableEx) {
                    log.warn("Could not disable application SQL binding {}: {}", binding.id(), disableEx.getMessage());
                }
            }
        } catch (RuntimeException ex) {
            success = false;
            changed = false;
            error = ex.getMessage();
            throw ex;
        } finally {
            bindingAuditService.recordSql(
                    binding.objectPath(),
                    binding.id().toString(),
                    binding.variableName(),
                    triggerKind,
                    success,
                    changed,
                    error,
                    System.nanoTime() - start,
                    entityMapper.auditDiff(previous, next)
            );
        }
    }

    private static String triggerForRefreshMode(String refreshMode) {
        return switch (normalizeRefreshMode(refreshMode)) {
            case "on_function_success" -> "FUNCTION_SUCCESS";
            case "on_event" -> "EVENT";
            default -> "SCHEDULE";
        };
    }

    private void ensureVariable(String objectPath, String variableName) {
        schemaSession.runWithPlatformCatalog(() -> {
            PlatformObject node = objectManager.require(objectPath);
            if (node.getVariable(variableName).isEmpty()) {
                node.addVariable(new Variable(
                        variableName,
                        SINGLE_VALUE_SCHEMA,
                        true,
                        false, DataRecord.single(SINGLE_VALUE_SCHEMA, Map.of("value", 0.0))
                ));
                objectManager.persistNodeTree(objectPath);
            }
        });
    }

    private static Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            normalized.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return normalized;
    }

    private SqlBindingValues.Extracted queryValue(
            ApplicationSqlBindingStore.SqlBinding binding,
            FieldType fieldType,
            SqlBindingValues.OnQueryFailure onQueryFailure
    ) {
        String schemaName = resolveSchemaName(binding.appId());
        String field = binding.valueField() != null ? binding.valueField() : "value";
        SqlBindingValues.Extracted[] extracted = new SqlBindingValues.Extracted[1];
        try {
            schemaSession.runInSchema(schemaName, () -> {
                ApplicationSchemaSupport.validateSelectQuery(binding.querySql(), "Binding query");
                List<Map<String, Object>> rows = dataStore.queryForList(binding.querySql());
                if (rows.isEmpty()) {
                    extracted[0] = SqlBindingValues.Extracted.noData("query returned no rows");
                    return;
                }
                Map<String, Object> row = normalizeRow(rows.getFirst());
                String column = field.toLowerCase(Locale.ROOT);
                if (!row.containsKey(column) && row.size() == 1) {
                    column = row.keySet().iterator().next();
                }
                extracted[0] = row.containsKey(column)
                        ? convert(field, row.get(column), fieldType)
                        : SqlBindingValues.Extracted.noData("column '" + field + "' not in result");
            });
        } catch (RuntimeException ex) {
            if (onQueryFailure == SqlBindingValues.OnQueryFailure.MARK_BAD) {
                return SqlBindingValues.Extracted.queryFailed(ex);
            }
            metricsRecorder.recordSqlBindingFailure(SqlBindingValues.Failure.QUERY_FAILED);
            throw ex;
        }
        return extracted[0];
    }

    private static SqlBindingValues.Extracted convert(String field, Object value, FieldType fieldType) {
        return switch (fieldType) {
            case STRING -> value != null
                    ? SqlBindingValues.Extracted.of(String.valueOf(value))
                    : SqlBindingValues.Extracted.noData("column '" + field + "' is NULL");
            case INTEGER, LONG -> SqlBindingValues.toLong(field, value);
            default -> SqlBindingValues.toDouble(field, value);
        };
    }

    private static DataRecord toValueRecord(Object value, FieldType fieldType) {
        return switch (fieldType) {
            case STRING, INTEGER, LONG -> DataRecord.single(
                    DataSchema.builder("sqlBindingValue").field("value", fieldType).build(),
                    Map.of("value", value)
            );
            default -> DataRecord.single(SINGLE_VALUE_SCHEMA, Map.of("value", value));
        };
    }

    private static com.ispf.core.model.FieldType resolveValueFieldType(ApplicationSqlBindingStore.SqlBinding binding) {
        if (binding == null) {
            return FieldType.DOUBLE;
        }
        String field = binding.valueField() != null ? binding.valueField() : "value";
        if ("value".equalsIgnoreCase(field) || field.isBlank()) {
            return FieldType.DOUBLE;
        }
        if (field.toLowerCase(Locale.ROOT).contains("code") || field.toLowerCase(Locale.ROOT).contains("name") || field.toLowerCase(Locale.ROOT).contains("status")) {
            return FieldType.STRING;
        }
        return FieldType.DOUBLE;
    }

    private String resolveSchemaName(String appId) {
        return dataStore.findApp(appId)
                .map(app -> String.valueOf(app.get("schema_name")))
                .filter(name -> name != null && !name.isBlank() && !"null".equals(name))
                .orElse(ApplicationSchemaSupport.defaultSchemaName(appId));
    }

    private static String normalizeRefreshMode(String refresh) {
        if (refresh == null || refresh.isBlank()) {
            return "on_schedule";
        }
        return switch (refresh) {
            case "on_function_success" -> "on_function_success";
            case "on_event" -> "on_event";
            default -> "on_schedule";
        };
    }

    private Map<String, Object> toMap(ApplicationSqlBindingStore.SqlBinding binding) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("objectPath", binding.objectPath());
        map.put("variable", binding.variableName());
        map.put("refresh", binding.refreshMode());
        map.put("refreshIntervalMs", binding.refreshIntervalMs());
        map.put("valueField", binding.valueField());
        map.put("enabled", binding.enabled());
        map.put("lastRefreshedAt", binding.lastRefreshedAt());
        return map;
    }

    public record DeploySqlBindingRequest(
            String objectPath,
            String variable,
            String query,
            String refresh,
            Long refreshIntervalMs,
            String valueField,
            String triggerObjectPath,
            String triggerFunctionName,
            Boolean enabled
    ) {
    }
}
