package com.ispf.server.application.script;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import com.ispf.core.model.DataSchema;
import com.ispf.server.application.function.ApplicationFunctionRuntime;
import com.ispf.server.workflow.WorkflowInstanceCancelService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Executes JSON step scripts for application-deployed functions (REQ-PF-01).
 */
@Component
public class FunctionScriptEngine {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final WorkflowInstanceCancelService workflowCancelService;

    public FunctionScriptEngine(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            WorkflowInstanceCancelService workflowCancelService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.workflowCancelService = workflowCancelService;
    }

    public DataRecord execute(
            String sourceBody,
            DataRecord input,
            DataSchema outputSchema,
            ScriptExecutionContext context
    ) {
        try {
            JsonNode root = objectMapper.readTree(sourceBody);
            JsonNode steps = root.get("steps");
            if (steps == null || !steps.isArray()) {
                throw new IllegalArgumentException("Script must contain steps array");
            }

            Map<String, Object> inputMap = input != null && input.rowCount() > 0
                    ? new LinkedHashMap<>(input.firstRow())
                    : new LinkedHashMap<>();
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("input", inputMap);

            for (JsonNode step : steps) {
                String type = step.path("type").asText();
                switch (type) {
                    case "selectOne" -> {
                        String var = step.path("var").asText();
                        String sql = step.path("sql").asText();
                        List<Object> params = resolveParams(step.get("params"), vars);
                        List<Map<String, Object>> rows = normalizeRows(jdbcTemplate.queryForList(sql, params.toArray()));
                        vars.put(var, rows.isEmpty() ? null : rows.get(0));
                    }
                    case "selectMany" -> {
                        String var = step.path("var").asText();
                        String sql = step.path("sql").asText();
                        List<Object> params = resolveParams(step.get("params"), vars);
                        vars.put(var, normalizeRows(jdbcTemplate.queryForList(sql, params.toArray())));
                    }
                    case "exec" -> {
                        String sql = step.path("sql").asText();
                        List<Object> params = resolveParams(step.get("params"), vars);
                        jdbcTemplate.update(sql, params.toArray());
                    }
                    case "setVar" -> vars.put(step.path("var").asText(), resolveStepValue(step.get("value"), vars));
                    case "buildRecord" -> vars.put(
                            step.path("var").asText(),
                            resolveFields(step.get("fields"), vars)
                    );
                    case "map" -> vars.put(step.path("var").asText(), mapCollection(step, vars));
                    case "invoke_function" -> {
                        Map<String, Object> nestedInput = resolveInputObject(step.get("input"), vars);
                        DataRecord nestedOutput = context.invokeFunction(
                                step.path("objectPath").asText(),
                                step.path("functionName").asText(),
                                nestedInput
                        );
                        Map<String, Object> nestedRow = ApplicationFunctionRuntime.rowAsMap(nestedOutput);
                        if (!"OK".equals(ApplicationFunctionRuntime.errorCode(nestedRow))) {
                            return toOutputRecord(outputSchema, nestedRow);
                        }
                        String var = step.path("var").asText("");
                        if (!var.isBlank()) {
                            vars.put(var, nestedRow);
                        }
                    }
                    case "cancel_workflows" -> {
                        List<String> statusIn = readStringList(step.get("statusIn"));
                        Map<String, Object> detail = resolveInputObject(step.get("detail"), vars);
                        int cancelled = workflowCancelService.cancelByWorkflowPath(
                                step.path("workflowPath").asText(),
                                statusIn,
                                step.path("reason").asText("cancelled"),
                                objectMapper.writeValueAsString(detail),
                                "application-script"
                        );
                        Map<String, Object> cancelResult = Map.of("cancelledCount", cancelled);
                        String var = step.path("var").asText("");
                        if (!var.isBlank()) {
                            vars.put(var, cancelResult);
                        }
                    }
                    case "failIfNull" -> {
                        String var = step.path("var").asText();
                        if (resolvePath(vars, var) == null) {
                            return wireError(outputSchema, step);
                        }
                    }
                    case "failIfNotEquals" -> {
                        Object actual = resolvePath(vars, step.path("var").asText());
                        String expected = step.path("equals").asText();
                        if (!Objects.equals(String.valueOf(actual), expected)) {
                            return wireError(outputSchema, step);
                        }
                    }
                    case "return" -> {
                        Map<String, Object> row = resolveFields(step.get("fields"), vars);
                        return toOutputRecord(outputSchema, row);
                    }
                    default -> throw new IllegalArgumentException("Unknown script step type: " + type);
                }
            }
            throw new IllegalStateException("Script must end with return step");
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Script execution failed: " + ex.getMessage(), ex);
        }
    }

    private static DataRecord toOutputRecord(DataSchema outputSchema, Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (FieldDefinition field : outputSchema.fields()) {
            Object value = row.get(field.name());
            if (value == null && !field.nullable()) {
                value = defaultValue(field.type());
            } else {
                value = coerceFieldValue(field, value);
            }
            normalized.put(field.name(), value);
        }
        return DataRecord.single(outputSchema, normalized);
    }

    private static Object coerceFieldValue(FieldDefinition field, Object value) {
        if (value == null || field.type() != FieldType.BOOLEAN) {
            return value;
        }
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text) || "1".equals(text);
        }
        return value;
    }

    private static Object defaultValue(com.ispf.core.model.FieldType type) {
        return switch (type) {
            case BOOLEAN -> false;
            case INTEGER, LONG -> 0L;
            case DOUBLE -> 0.0;
            case RECORD_LIST -> List.of();
            default -> "";
        };
    }

    private static DataRecord wireError(DataSchema outputSchema, JsonNode step) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("error_code", step.path("error_code").asText(step.path("code").asText("ERROR")));
        row.put("error_message", step.path("error_message").asText(step.path("message").asText("Script validation failed")));
        return toOutputRecord(outputSchema, row);
    }

    private List<Map<String, Object>> mapCollection(JsonNode step, Map<String, Object> vars) {
        Object source = resolveStepValue(step.get("source"), vars);
        if (!(source instanceof List<?> list)) {
            throw new IllegalArgumentException("map source must be a list");
        }
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Object element : list) {
            Map<String, Object> scoped = new LinkedHashMap<>(vars);
            if (element instanceof Map<?, ?> row) {
                scoped.put("item", normalizeRow(toStringKeyMap(row)));
            } else {
                scoped.put("item", element);
            }
            mapped.add(resolveFields(step.get("fields"), scoped));
        }
        return mapped;
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        row.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        return normalized;
    }

    private Map<String, Object> resolveFields(JsonNode fieldsNode, Map<String, Object> vars) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (fieldsNode == null || !fieldsNode.isObject()) {
            return row;
        }
        fieldsNode.forEachEntry((key, value) ->
                row.put(key, resolveStepValue(value, vars))
        );
        return row;
    }

    private Map<String, Object> resolveInputObject(JsonNode inputNode, Map<String, Object> vars) {
        Map<String, Object> input = new LinkedHashMap<>();
        if (inputNode == null || !inputNode.isObject()) {
            return input;
        }
        inputNode.forEachEntry((key, value) ->
                input.put(key, resolveStepValue(value, vars))
        );
        return input;
    }

    private Object resolveStepValue(JsonNode valueNode, Map<String, Object> vars) {
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        if (valueNode.isObject() || valueNode.isArray()) {
            return objectMapper.convertValue(valueNode, Object.class);
        }
        return resolveValue(valueNode.asText(), vars);
    }

    private List<Object> resolveParams(JsonNode paramsNode, Map<String, Object> vars) {
        List<Object> params = new ArrayList<>();
        if (paramsNode == null || !paramsNode.isArray()) {
            return params;
        }
        for (JsonNode node : paramsNode) {
            params.add(resolveStepValue(node, vars));
        }
        return params;
    }

    private Object resolveValue(String token, Map<String, Object> vars) {
        if (token == null) {
            return null;
        }
        if (token.startsWith("${") && token.endsWith("}")) {
            return resolvePath(vars, token.substring(2, token.length() - 1));
        }
        if (token.startsWith("$")) {
            return resolvePath(vars, token.substring(1));
        }
        return token;
    }

    @SuppressWarnings("unchecked")
    private Object resolvePath(Map<String, Object> vars, String path) {
        String[] parts = path.split("\\.");
        Object current = vars;
        for (String part : parts) {
            if (current == null) {
                return null;
            }
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private static List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows) {
        return rows.stream().map(FunctionScriptEngine::normalizeRow).toList();
    }

    private static List<String> readStringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            values.add(item.asText());
        }
        return values;
    }

    private static Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        row.forEach((key, value) -> normalized.put(key.toLowerCase(), value));
        return normalized;
    }
}
