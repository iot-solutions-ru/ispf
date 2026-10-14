package com.ispf.server.ai.agent;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.server.object.ObjectTreePort;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Agent tools for object-tree functions (script and Java sourceType).
 */
final class AgentFunctionTools {

    private AgentFunctionTools() {
    }

    static List<PlatformAgentTool> all(
            ObjectTreePort objectTreePort,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectMapper objectMapper
    ) {
        return List.of(
                deployTreeFunctionTool(objectTreePort, objectAccessService, tenantScopeService, objectMapper),
                getFunctionTemplateTool()
        );
    }

    private static PlatformAgentTool deployTreeFunctionTool(
            ObjectTreePort objectTreePort,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "deploy_tree_function";
            }

            @Override
            public String description() {
                return "Deploy or update a function on an object tree node. Args: path, functionName, "
                        + "hostKind (required enum SINGLETON|INSTANCE — DEVICE is rejected), "
                        + "sourceType (script|java), sourceBody (JSON steps string OR Java class source), "
                        + "inputSchema, outputSchema ({name?, fields:[{name,type,nullable?}]}), "
                        + "optional description, dataSourcePath (script SQL), version. "
                        + "Host path must be a SINGLETON hub (root.platform.singleton-blueprints.*) or INSTANCE twin — "
                        + "never DEVICE (solution logic canon / ADR-0060). There is no DEVICE default. "
                        + "Prefer sourceType=script on demostand (JSON steps: setVar, invoke_function, return). "
                        + "Java needs a JDK compiler on the server — often unavailable; use script + invoke_function "
                        + "to call device calculate(). Use get_function_template topic=java|script for skeleton. "
                        + "Test with invoke_tree_function. Application SQL functions: deploy_app_function sourceType=script.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                String functionName = stringArg(arguments, "functionName");
                String sourceType = normalizeSourceType(stringArg(arguments, "sourceType"));
                String sourceBody = stringArg(arguments, "sourceBody");
                if (path.isBlank() || functionName.isBlank() || sourceType.isBlank() || sourceBody.isBlank()) {
                    return Map.of(
                            "status", "ERROR",
                            "error",
                            "path, functionName, sourceType (script|java), sourceBody are required"
                    );
                }
                if (!"script".equals(sourceType) && !"java".equals(sourceType)) {
                    return Map.of(
                            "status", "ERROR",
                            "error",
                            "sourceType must be script or java (use deploy_app_function for application script deploy)"
                    );
                }
                Map<String, Object> hostError = LogicHostGuard.rejectDeviceHost(
                        stringArg(arguments, "hostKind"),
                        path,
                        LogicHostGuard.liveType(objectTreePort, path)
                );
                if (hostError != null) {
                    return hostError;
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireWrite(path, auth);
                try {
                    DataSchema inputSchema = schemaFromArg(arguments.get("inputSchema"), functionName + "Input", objectMapper);
                    DataSchema outputSchema = schemaFromArg(arguments.get("outputSchema"), functionName + "Output", objectMapper);
                    String description = stringArg(arguments, "description");
                    if (description.isBlank()) {
                        description = functionName;
                    }
                    String dataSourcePath = optionalString(arguments, "dataSourcePath");
                    String version = stringArg(arguments, "version");
                    if (version.isBlank()) {
                        version = "1";
                    }
                    FunctionDescriptor descriptor = new FunctionDescriptor(
                            functionName,
                            description,
                            inputSchema,
                            outputSchema,
                            sourceType,
                            sourceBody,
                            dataSourcePath,
                            version
                    );
                    FunctionDescriptor saved = objectTreePort.upsertFunction(path, descriptor);
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", "OK");
                    response.put("path", path);
                    response.put("functionName", saved.name());
                    response.put("sourceType", saved.sourceType());
                    response.put("version", saved.version());
                    response.put("hint", "Test: invoke_tree_function path=" + path + " functionName=" + functionName);
                    return response;
                } catch (IllegalArgumentException ex) {
                    return deployError(ex.getMessage(), sourceType);
                } catch (Exception ex) {
                    return deployError(ex.getMessage(), sourceType);
                }
            }
        };
    }

    private static Map<String, Object> deployError(String message, String sourceType) {
        String error = message == null ? "deploy_tree_function failed" : message;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ERROR");
        result.put("error", error);
        String lower = error.toLowerCase(Locale.ROOT);
        if (lower.contains("compiler") || lower.contains("jdk") || "java".equals(sourceType)) {
            result.put(
                    "hint",
                    "Demostand often has no JDK compiler. Retry deploy_tree_function sourceType=script "
                            + "sourceBody={\"steps\":[{\"type\":\"invoke_function\",\"objectPath\":\"self\","
                            + "\"function\":\"calculate\",\"input\":{...},\"var\":\"out\"},"
                            + "{\"type\":\"return\",\"fields\":{...}}]}. "
                            + "Step type is invoke_function (not invoke)."
            );
        } else if (lower.contains("unknown script step type")) {
            result.put(
                    "hint",
                    "Use script step types: setVar, readVariable, invoke_function, return "
                            + "(invoke is accepted as alias of invoke_function)."
            );
        }
        return result;
    }

    private static PlatformAgentTool getFunctionTemplateTool() {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_function_template";
            }

            @Override
            public String description() {
                return "Reference skeleton for object-tree functions. Arg: topic (java|script|comparison). "
                        + "Java functions compile on save; script functions use JSON steps (selectOne, return, …).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String topic = stringArg(arguments, "topic").toLowerCase(Locale.ROOT);
                if (topic.isBlank()) {
                    topic = "comparison";
                }
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("status", "OK");
                response.put("topic", topic);
                response.put("deployTool", "deploy_tree_function");
                response.put("invokeTool", "invoke_tree_function");
                switch (topic) {
                    case "java" -> {
                        response.put("sourceType", "java");
                        response.put("rules", List.of(
                                "One public class implementing com.ispf.core.function.ObjectJavaFunction",
                                "Method: DataRecord invoke(DataRecord input, JavaFunctionContext context)",
                                "Build output with DataSchema.builder(...).field(name, FieldType.*) and DataRecord.single",
                                "Compiled on save — compilation error returns ERROR from deploy_tree_function",
                                "No Runtime/ProcessBuilder/reflection/java.net (except InetAddress)/java.io.File",
                                "For SQL/workflow/readVariable use sourceType=script instead"
                        ));
                        response.put("exampleSourceBody", JAVA_ECHO_TEMPLATE);
                        response.put("exampleInputSchema", Map.of(
                                "name", "echoInput",
                                "fields", List.of(Map.of("name", "value", "type", "STRING"))
                        ));
                        response.put("exampleOutputSchema", Map.of(
                                "name", "echoOutput",
                                "fields", List.of(Map.of("name", "value", "type", "STRING"))
                        ));
                    }
                    case "script" -> {
                        response.put("sourceType", "script");
                        response.put("rules", List.of(
                                "sourceBody is JSON: {\"steps\":[...]}",
                                "Must end with return step; vars: input, setVar, selectOne, readVariable, invoke_function",
                                "Call device calculate via step type invoke_function (alias: invoke)",
                                "Optional dataSourcePath for SQL steps",
                                "Same engine as deploy_app_function / bundle functions[]"
                        ));
                        response.put("exampleSourceBody", SCRIPT_ECHO_TEMPLATE);
                    }
                    default -> {
                        response.put("whenJava", "Only when JDK compiler is available — prefer script on demostand");
                        response.put("whenScript", "Default: readVariable / invoke_function / return — sourceType=script");
                        response.put("whenAppDeploy", "Application BFF with app schema — deploy_app_function sourceType=script");
                        response.put("whenBuiltin", "Platform handlers (acknowledgeAlarm, calculate) — call via invoke_function, do not redeploy");
                    }
                }
                return response;
            }
        };
    }

    static String normalizeSourceType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "javascript", "js", "json" -> "script";
            default -> raw.trim().toLowerCase(Locale.ROOT);
        };
    }

    @SuppressWarnings("unchecked")
    static DataSchema schemaFromArg(Object raw, String defaultName, ObjectMapper objectMapper) throws Exception {
        if (raw == null) {
            return DataSchema.builder(defaultName).build();
        }
        if (raw instanceof String text && !text.isBlank()) {
            return objectMapper.readValue(text.trim(), DataSchema.class);
        }
        if (raw instanceof Map<?, ?> map) {
            String name = map.containsKey("name") ? String.valueOf(map.get("name")) : defaultName;
            DataSchema.Builder builder = DataSchema.builder(name);
            Object fields = map.get("fields");
            if (fields instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> fieldMap) {
                        String fieldName = String.valueOf(fieldMap.get("name"));
                        FieldType type = parseFieldType(String.valueOf(fieldMap.get("type")));
                        builder.field(fieldName, type);
                    }
                }
            } else if (fields instanceof Map<?, ?> fieldMap) {
                fieldMap.forEach((key, type) -> builder.field(String.valueOf(key), parseFieldType(String.valueOf(type))));
            }
            return builder.build();
        }
        return DataSchema.builder(defaultName).build();
    }

    private static FieldType parseFieldType(String raw) {
        if (raw == null || raw.isBlank()) {
            return FieldType.STRING;
        }
        try {
            return FieldType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return FieldType.STRING;
        }
    }

    private static String optionalString(Map<String, Object> args, String key) {
        String value = stringArg(args, key);
        return value.isBlank() ? null : value;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        if (args == null) {
            return "";
        }
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static final String JAVA_ECHO_TEMPLATE = """
            import com.ispf.core.function.ObjectJavaFunction;
            import com.ispf.core.function.JavaFunctionContext;
            import com.ispf.core.model.DataRecord;
            import com.ispf.core.model.DataSchema;
            import com.ispf.core.model.FieldType;
            import java.util.Map;

            public class EchoFn implements ObjectJavaFunction {
                @Override
                public DataRecord invoke(DataRecord input, JavaFunctionContext context) {
                    Object value = input != null && input.rowCount() > 0
                            ? input.firstRow().get("value")
                            : null;
                    DataSchema schema = DataSchema.builder("echoOutput")
                            .field("value", FieldType.STRING)
                            .build();
                    return DataRecord.single(schema, Map.of(
                            "value", value == null ? "" : String.valueOf(value)
                    ));
                }
            }
            """.trim();

    private static final String SCRIPT_ECHO_TEMPLATE = """
            {"steps":[{"type":"return","fields":{"message":"${input.text}","ok":true}}]}
            """.trim();
}
