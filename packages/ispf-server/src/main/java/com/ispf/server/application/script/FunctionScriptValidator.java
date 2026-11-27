package com.ispf.server.application.script;

import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.datasource.DataSourceSqlSupport;
import com.ispf.server.query.oq.ObjectQuerySpecParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

@Component
public class FunctionScriptValidator {

    private static final Set<String> KNOWN_STEPS = Set.of(
            "selectOne",
            "selectMany",
            "exec",
            "setVar",
            "buildRecord",
            "map",
            "when",
            "if",
            "invoke_function",
            "invoke",
            "cancel_workflows",
            "failIfNull",
            "failIfNotEquals",
            "jsonParse",
            "readVariable",
            "writeVariable",
            "instantiateModelIfMissing",
            "setDriverTelemetry",
            "selectForUpdate",
            "advisoryLock",
            "expectRowCount",
            "queryRows",
            "scan_objects",
            "for_each_row",
            "apply_query_patch",
            "enqueueOutbox",
            "return"
    );

    private static final Pattern TRAILING_OPERATOR = Pattern.compile(".*(?:==|!=|<=|>=|[+*/<>=]|-)\\s*$");
    private static final Pattern SQL_COMMAND = Pattern.compile(
            "^(SELECT|INSERT|UPDATE|DELETE|WITH|MERGE|CALL|SHOW|EXPLAIN|VALUES)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final ObjectMapper objectMapper;
    private final ObjectQuerySpecParser objectQuerySpecParser;

    public FunctionScriptValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.objectQuerySpecParser = new ObjectQuerySpecParser(objectMapper);
    }

    /**
     * Whether {@code sourceBody} contains {@code selectOne}, {@code selectMany}, or {@code exec},
     * including steps nested under {@code then}, {@code else}, or {@code steps}.
     * Invalid JSON is treated as no SQL step.
     */
    public boolean containsSqlStep(String sourceBody) {
        if (sourceBody == null || sourceBody.isBlank()) {
            return false;
        }
        try {
            return containsSqlStep(objectMapper.readTree(sourceBody));
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean containsSqlStep(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return false;
        }
        if (node.isArray() || node.isObject()) {
            String type = node.path("type").asText("");
            if ("selectOne".equals(type) || "selectMany".equals(type) || "exec".equals(type)
                    || "selectForUpdate".equals(type) || "advisoryLock".equals(type)) {
                return true;
            }
            for (JsonNode child : node) {
                if (containsSqlStep(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Rewrite common LLM aliases in script steps before validate/persist:
     * {@code invoke}→{@code invoke_function}, {@code path}→{@code objectPath},
     * {@code function}→{@code functionName}, default {@code objectPath=self}.
     */
    public String normalizeAliases(String sourceBody) {
        if (sourceBody == null || sourceBody.isBlank()) {
            return sourceBody;
        }
        try {
            JsonNode root = objectMapper.readTree(sourceBody);
            if (!(root instanceof tools.jackson.databind.node.ObjectNode objectRoot)) {
                return sourceBody;
            }
            JsonNode steps = objectRoot.get("steps");
            if (steps == null || !steps.isArray()) {
                return sourceBody;
            }
            normalizeInvokeSteps(steps);
            return objectMapper.writeValueAsString(objectRoot);
        } catch (Exception ex) {
            return sourceBody;
        }
    }

    private void normalizeInvokeSteps(JsonNode steps) {
        if (steps == null || !steps.isArray()) {
            return;
        }
        for (JsonNode step : steps) {
            if (!(step instanceof tools.jackson.databind.node.ObjectNode objectStep)) {
                continue;
            }
            String type = objectStep.path("type").asText("");
            if ("invoke".equals(type)) {
                objectStep.put("type", "invoke_function");
                type = "invoke_function";
            }
            if ("setVar".equals(type) && !hasNonBlank(objectStep, "var")) {
                if (hasNonBlank(objectStep, "name")) {
                    objectStep.put("var", objectStep.path("name").asText());
                } else if (hasNonBlank(objectStep, "variable")) {
                    objectStep.put("var", objectStep.path("variable").asText());
                }
            }
            if ("invoke_function".equals(type)) {
                if (!hasNonBlank(objectStep, "functionName") && hasNonBlank(objectStep, "function")) {
                    objectStep.put("functionName", objectStep.path("function").asText());
                }
                if (!hasNonBlank(objectStep, "objectPath")) {
                    if (hasNonBlank(objectStep, "path")) {
                        objectStep.put("objectPath", objectStep.path("path").asText());
                    } else if (!hasNonBlank(objectStep, "ref")) {
                        // calculate() lives on the virtual DEVICE — never invent self for it.
                        String fn = objectStep.path("functionName").asText("").toLowerCase(java.util.Locale.ROOT);
                        if (!"calculate".equals(fn)) {
                            objectStep.put("objectPath", "self");
                        }
                    }
                }
            }
            JsonNode thenSteps = objectStep.get("then");
            if (thenSteps != null) {
                normalizeInvokeSteps(thenSteps);
            }
            JsonNode elseSteps = objectStep.get("else");
            if (elseSteps != null) {
                normalizeInvokeSteps(elseSteps);
            }
            JsonNode nested = objectStep.get("steps");
            if (nested != null) {
                normalizeInvokeSteps(nested);
            }
        }
    }

    public void validate(String sourceBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(sourceBody);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Script body must be valid JSON: " + ex.getMessage());
        }

        JsonNode steps = root.get("steps");
        if (steps == null || !steps.isArray() || steps.isEmpty()) {
            throw new IllegalArgumentException("Script must contain a non-empty steps array");
        }
        validateSteps(steps, true);
    }

    public void validateResolvedTargets(String sourceBody, String hostPath, ObjectTree tree) {
        JsonNode root;
        try {
            root = objectMapper.readTree(sourceBody);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Script body must be valid JSON: " + ex.getMessage());
        }
        JsonNode steps = root.get("steps");
        if (steps != null && steps.isArray()) {
            validateResolvedSteps(steps, hostPath, tree);
        }
    }

    private void validateResolvedSteps(JsonNode steps, String hostPath, ObjectTree tree) {
        for (JsonNode step : steps) {
            validateResolvedStep(step, hostPath, tree);
        }
    }

    private void validateResolvedStep(JsonNode step, String hostPath, ObjectTree tree) {
        if (step == null || !step.isObject()) {
            return;
        }
        switch (step.path("type").asText("")) {
            case "selectOne", "selectMany", "selectForUpdate", "exec" -> requireSingleSql(step.path("sql").asText(""));
            case "setVar" -> {
                if (step.has("expression")) {
                    requireSetVarExpression(step.path("expression").asText(""));
                }
            }
            case "readVariable", "writeVariable" -> requireVariable(step, hostPath, tree);
            case "invoke_function", "invoke" -> requireFunction(step, hostPath, tree);
            case "cancel_workflows" -> requireWorkflow(step, tree);
            case "queryRows" -> objectQuerySpecParser.parse(step.path("spec").asText(""));
            default -> {
            }
        }
        validateNested(step.get("then"), hostPath, tree);
        validateNested(step.get("else"), hostPath, tree);
        validateNested(step.get("steps"), hostPath, tree);
    }

    private void validateNested(JsonNode node, String hostPath, ObjectTree tree) {
        if (node != null && node.isArray()) {
            validateResolvedSteps(node, hostPath, tree);
        }
    }

    private static void requireSingleSql(String sql) {
        String normalized = DataSourceSqlSupport.normalizeSql(sql);
        if (!SQL_COMMAND.matcher(normalized).find()) {
            throw new IllegalArgumentException("Invalid SQL statement");
        }
    }

    private static void requireSetVarExpression(String expression) {
        String trimmed = expression == null ? "" : expression.trim();
        if (trimmed.isBlank() || TRAILING_OPERATOR.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("setVar expression is incomplete");
        }
    }

    private static void requireVariable(JsonNode step, String hostPath, ObjectTree tree) {
        String name = step.path("variable").asText("");
        if (name.isBlank() || isTemplate(name)) {
            return;
        }
        String objectPath = literalObjectPath(step, hostPath);
        if (objectPath == null) {
            return;
        }
        PlatformObject node = tree.findByPath(objectPath)
                .orElseThrow(() -> new IllegalArgumentException("Unknown object: " + objectPath));
        if (node.getVariable(name).isEmpty()) {
            throw new IllegalArgumentException("Unknown variable: " + name);
        }
    }

    private static void requireFunction(JsonNode step, String hostPath, ObjectTree tree) {
        String name = step.path("functionName").asText("");
        if (name.isBlank()) {
            name = step.path("function").asText("");
        }
        if (name.isBlank() || isTemplate(name)) {
            return;
        }
        String objectPath = literalObjectPath(step, hostPath);
        if (objectPath == null) {
            return;
        }
        PlatformObject node = tree.findByPath(objectPath)
                .orElseThrow(() -> new IllegalArgumentException("Unknown object: " + objectPath));
        if (!node.functions().containsKey(name)) {
            throw new IllegalArgumentException("Unknown function: " + name);
        }
    }

    private static void requireWorkflow(JsonNode step, ObjectTree tree) {
        String workflowPath = step.path("workflowPath").asText("").trim();
        if (workflowPath.isBlank() || isTemplate(workflowPath)) {
            return;
        }
        PlatformObject node = tree.findByPath(workflowPath).orElse(null);
        if (node == null || node.type() != ObjectType.WORKFLOW) {
            throw new IllegalArgumentException("Unknown workflow: " + workflowPath);
        }
    }

    private static String literalObjectPath(JsonNode step, String hostPath) {
        String raw = step.path("objectPath").asText("");
        if (raw.isBlank()) {
            raw = step.path("path").asText("");
        }
        if (raw.isBlank() || "self".equalsIgnoreCase(raw)) {
            return hostPath;
        }
        if (isTemplate(raw)) {
            return null;
        }
        return raw.trim();
    }

    private static boolean isTemplate(String value) {
        return value.contains("${") || value.contains("{{");
    }

    private void validateSteps(JsonNode steps, boolean requireReturn) {
        boolean hasReturn = false;
        for (JsonNode step : steps) {
            String type = step.path("type").asText("");
            if (type.isBlank()) {
                throw new IllegalArgumentException("Each script step must have a type");
            }
            if (!KNOWN_STEPS.contains(type)) {
                throw new IllegalArgumentException("Unknown script step type: " + type);
            }
            if ("return".equals(type)) {
                hasReturn = true;
            }
            validateStep(type, step);
        }
        if (requireReturn && !hasReturn) {
            throw new IllegalArgumentException("Script must include at least one return step");
        }
    }

    private void validateStep(String type, JsonNode step) {
        switch (type) {
            case "selectOne", "selectMany" -> require(step, "var", "sql");
            case "exec" -> require(step, "sql");
            case "setVar" -> {
                require(step, "var");
                if (!step.has("value") && !step.has("expression")) {
                    throw new IllegalArgumentException("setVar step requires value or expression");
                }
            }
            case "buildRecord" -> {
                require(step, "var");
                if (!step.has("fields") || !step.get("fields").isObject()) {
                    throw new IllegalArgumentException("buildRecord step requires fields object");
                }
            }
            case "map" -> {
                require(step, "var", "source");
                if (!step.has("fields") || !step.get("fields").isObject()) {
                    throw new IllegalArgumentException("map step requires fields object");
                }
            }
            case "when", "if" -> {
                if (!step.has("var") && !step.has("notNull")) {
                    throw new IllegalArgumentException(type + " step requires var or notNull");
                }
                JsonNode thenSteps = step.get("then");
                if (thenSteps == null || !thenSteps.isArray() || thenSteps.isEmpty()) {
                    throw new IllegalArgumentException(type + " step requires non-empty then array");
                }
                validateSteps(thenSteps, false);
                JsonNode elseSteps = step.get("else");
                if (elseSteps != null && elseSteps.isArray() && !elseSteps.isEmpty()) {
                    validateSteps(elseSteps, false);
                }
            }
            case "invoke_function", "invoke" -> {
                if (!hasNonBlank(step, "functionName") && !hasNonBlank(step, "function") && !hasNonBlank(step, "ref")) {
                    throw new IllegalArgumentException(
                            "Step invoke_function requires functionName (or function) — "
                                    + "example: {\"type\":\"invoke_function\",\"objectPath\":\"root.platform.devices.virt-device-1\","
                                    + "\"functionName\":\"calculate\",\"input\":{...},\"var\":\"out\"}"
                    );
                }
                if (!hasNonBlank(step, "objectPath") && !hasNonBlank(step, "path") && !hasNonBlank(step, "ref")) {
                    throw new IllegalArgumentException(
                            "Step invoke_function requires objectPath (or path / ref); use \"self\" or the device path"
                    );
                }
            }
            case "cancel_workflows" -> require(step, "workflowPath");
            case "failIfNull" -> require(step, "var");
            case "failIfNotEquals" -> require(step, "var", "equals");
            case "jsonParse" -> {
                require(step, "var", "source");
                if (!step.has("fields") || !step.get("fields").isArray() || step.get("fields").isEmpty()) {
                    throw new IllegalArgumentException("jsonParse step requires non-empty fields array");
                }
            }
            case "readVariable" -> require(step, "objectPath", "variable", "var");
            case "writeVariable" -> {
                require(step, "objectPath", "variable");
                if (!step.has("fields") || !step.get("fields").isObject()) {
                    throw new IllegalArgumentException("writeVariable step requires fields object");
                }
            }
            case "instantiateModelIfMissing" -> {
                if ((!step.has("blueprintName") || step.get("blueprintName").asText("").isBlank())
                        && (!step.has("modelName") || step.get("modelName").asText("").isBlank())) {
                    throw new IllegalArgumentException("instantiateModelIfMissing step requires blueprintName or modelName");
                }
                require(step, "parentPath", "instanceName", "var");
            }
            case "setDriverTelemetry" -> {
                require(step, "objectPath");
                if (!step.has("fields") || !step.get("fields").isObject()) {
                    throw new IllegalArgumentException("setDriverTelemetry step requires fields object");
                }
            }
            case "queryRows", "scan_objects" -> require(step, "spec");
            case "for_each_row" -> {
                if (!step.has("source")) {
                    throw new IllegalArgumentException("for_each_row step requires source");
                }
                JsonNode body = step.get("steps");
                if (body == null || !body.isArray() || body.isEmpty()) {
                    throw new IllegalArgumentException("for_each_row step requires non-empty steps array");
                }
                validateSteps(body, false);
            }
            case "apply_query_patch" -> {
                if (!step.has("patches")) {
                    throw new IllegalArgumentException("apply_query_patch step requires patches");
                }
            }
            case "enqueueOutbox" -> {
                require(step, "connectorId");
                boolean hasMessage = step.has("messageId") && !step.get("messageId").asText("").isBlank();
                boolean hasParts = step.has("messageParts") && step.get("messageParts").isArray()
                        && !step.get("messageParts").isEmpty();
                if (!hasMessage && !hasParts) {
                    throw new IllegalArgumentException("enqueueOutbox step requires messageId or messageParts");
                }
            }
            case "return" -> {
                if (!step.has("fields") || !step.get("fields").isObject()) {
                    throw new IllegalArgumentException("return step requires fields object");
                }
            }
            default -> {
            }
        }
    }

    private static void require(JsonNode step, String... fields) {
        for (String field : fields) {
            if (!hasNonBlank(step, field)) {
                throw new IllegalArgumentException("Step " + step.path("type").asText() + " requires " + field);
            }
        }
    }

    private static boolean hasNonBlank(JsonNode step, String field) {
        return step != null && step.has(field) && !step.get(field).asText("").isBlank();
    }
}
