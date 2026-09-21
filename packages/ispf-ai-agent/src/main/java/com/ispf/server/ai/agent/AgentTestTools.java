package com.ispf.server.ai.agent;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.alert.AlertRuleService;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.script.PlatformScriptBridge;
import com.ispf.server.application.test.FunctionTestRunner;
import com.ispf.server.event.EventJournalRecord;
import com.ispf.server.event.EventJournalStore;
import com.ispf.server.object.ObjectTreePort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * W6 solution test tools — function smoke, telemetry inject, variable assert, bundle tests[].
 */
final class AgentTestTools {

    private static final int EVENT_RETRY_ATTEMPTS = 8;
    private static final long EVENT_RETRY_SLEEP_MS = 25L;

    private AgentTestTools() {
    }

    static List<PlatformAgentTool> all(
            FunctionTestRunner functionTestRunner,
            ApplicationBundleDeployService bundleDeployService,
            PlatformScriptBridge platformScriptBridge,
            AlertRuleService alertRuleService,
            EventJournalStore eventJournalStore,
            ObjectTreePort objectTreePort
    ) {
        return List.of(
                testFunctionTool(functionTestRunner),
                simulateTelemetryTool(platformScriptBridge, alertRuleService, eventJournalStore),
                assertVariableTool(objectTreePort),
                runBundleTestsTool(bundleDeployService)
        );
    }

    private static PlatformAgentTool testFunctionTool(FunctionTestRunner functionTestRunner) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "test_function";
            }

            @Override
            public String description() {
                return "Run an application function smoke test in a rollback transaction. "
                        + "Args: objectPath, functionName, optional input, fixtureSql[], expect{errorCode,rowCount,fields}, "
                        + "rollback (default true). Returns status PASS|FAIL.";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String objectPath = stringArg(arguments, "objectPath");
                if (objectPath.isBlank()) {
                    objectPath = stringArg(arguments, "path");
                }
                String functionName = stringArg(arguments, "functionName");
                if (objectPath.isBlank() || functionName.isBlank()) {
                    return Map.of("status", "ERROR", "error", "objectPath and functionName are required");
                }
                boolean rollback = !Boolean.FALSE.equals(arguments.get("rollback"));
                Map<String, Object> input = arguments.get("input") instanceof Map<?, ?> map
                        ? (Map<String, Object>) map
                        : Map.of();
                List<String> fixtureSql = stringList(arguments.get("fixtureSql"));
                Map<String, Object> expect = arguments.get("expect") instanceof Map<?, ?> map
                        ? (Map<String, Object>) map
                        : Map.of();
                String id = optionalString(arguments, "id");
                if (id == null) {
                    id = functionName;
                }
                FunctionTestRunner.TestResult result = functionTestRunner.run(new FunctionTestRunner.TestSpec(
                        id,
                        "function",
                        objectPath,
                        functionName,
                        null,
                        input,
                        fixtureSql,
                        Map.of(),
                        expect,
                        rollback
                ));
                return resultToMap(result);
            }
        };
    }

    private static PlatformAgentTool simulateTelemetryTool(
            PlatformScriptBridge platformScriptBridge,
            AlertRuleService alertRuleService,
            EventJournalStore eventJournalStore
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "simulate_telemetry";
            }

            @Override
            public String description() {
                return "Inject driver telemetry via setDriverTelemetry (virtual driver is read-only). "
                        + "Args: objectPath, variable, fields map and/or series[] of field maps. "
                        + "Runs alert evaluation and returns firedEvents (journal findLatest with retry).";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String objectPath = stringArg(arguments, "objectPath");
                if (objectPath.isBlank()) {
                    objectPath = stringArg(arguments, "path");
                }
                String variable = stringArg(arguments, "variable");
                if (variable.isBlank()) {
                    variable = stringArg(arguments, "name");
                }
                if (objectPath.isBlank() || variable.isBlank()) {
                    return Map.of("status", "ERROR", "error", "objectPath and variable are required");
                }
                List<Map<String, Object>> series = new ArrayList<>();
                if (arguments.get("series") instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> row) {
                            series.add((Map<String, Object>) row);
                        }
                    }
                }
                if (series.isEmpty() && arguments.get("fields") instanceof Map<?, ?> fields) {
                    series.add((Map<String, Object>) fields);
                }
                if (series.isEmpty()) {
                    return Map.of("status", "ERROR", "error", "fields or series[] is required");
                }
                try {
                    List<Map<String, Object>> written = new ArrayList<>();
                    for (Map<String, Object> fields : series) {
                        platformScriptBridge.setDriverTelemetry(objectPath, variable, fields);
                        alertRuleService.processVariableChange(objectPath, variable);
                        written.add(Map.copyOf(fields));
                    }
                    List<Map<String, Object>> firedEvents = collectFiredEvents(
                            eventJournalStore,
                            objectPath,
                            optionalString(arguments, "eventName")
                    );
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", "OK");
                    result.put("objectPath", objectPath);
                    result.put("variable", variable);
                    result.put("written", written);
                    result.put("firedEvents", firedEvents);
                    return result;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage() != null ? ex.getMessage() : "");
                }
            }
        };
    }

    private static PlatformAgentTool assertVariableTool(ObjectTreePort objectTreePort) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "assert_variable";
            }

            @Override
            public String description() {
                return "Assert a variable field value. Args: objectPath, variable, field, op (eq|neq|gt|gte|lt|lte|contains), expected. "
                        + "Returns status PASS|FAIL.";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String objectPath = stringArg(arguments, "objectPath");
                if (objectPath.isBlank()) {
                    objectPath = stringArg(arguments, "path");
                }
                String variable = stringArg(arguments, "variable");
                if (variable.isBlank()) {
                    variable = stringArg(arguments, "name");
                }
                String field = stringArg(arguments, "field");
                String op = stringArg(arguments, "op");
                if (op.isBlank()) {
                    op = "eq";
                }
                if (objectPath.isBlank() || variable.isBlank() || field.isBlank()) {
                    return Map.of("status", "ERROR", "error", "objectPath, variable, and field are required");
                }
                if (!arguments.containsKey("expected")) {
                    return Map.of("status", "ERROR", "error", "expected is required");
                }
                Object expected = arguments.get("expected");
                try {
                    PlatformObject node = objectTreePort.require(objectPath);
                    Map<String, Object> row = node.getVariable(variable)
                            .flatMap(v -> v.value().map(DataRecord::firstRow))
                            .map(LinkedHashMap::new)
                            .map(r -> (Map<String, Object>) r)
                            .orElse(Map.of());
                    Object actual = row.get(field);
                    boolean pass = compare(op, actual, expected);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", pass ? "PASS" : "FAIL");
                    result.put("objectPath", objectPath);
                    result.put("variable", variable);
                    result.put("field", field);
                    result.put("op", op);
                    result.put("expected", expected);
                    result.put("actual", actual);
                    if (!pass) {
                        result.put("errors", List.of(
                                "field " + field + " " + op + " " + expected + " but was " + actual
                        ));
                    }
                    return result;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage() != null ? ex.getMessage() : "");
                }
            }
        };
    }

    private static PlatformAgentTool runBundleTestsTool(ApplicationBundleDeployService bundleDeployService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "run_bundle_tests";
            }

            @Override
            public String description() {
                return "Run manifest tests[] for a deployed application. Arg: appId. "
                        + "Returns status PASS|FAIL and results[]. On FAIL consider rollback_application_deploy.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String appId = stringArg(arguments, "appId");
                if (appId.isBlank()) {
                    appId = stringArg(arguments, "packageId");
                }
                if (appId.isBlank()) {
                    return Map.of("status", "ERROR", "error", "appId is required");
                }
                try {
                    List<FunctionTestRunner.TestResult> results = bundleDeployService.runTests(appId);
                    List<Map<String, Object>> rows = results.stream().map(AgentTestTools::resultToMap).toList();
                    boolean allPass = results.stream().allMatch(r -> "PASS".equals(r.status()));
                    boolean anyFail = results.stream().anyMatch(r -> "FAIL".equals(r.status()));
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", results.isEmpty() || allPass ? "PASS" : "FAIL");
                    response.put("appId", appId);
                    response.put("count", results.size());
                    response.put("results", rows);
                    if (anyFail) {
                        response.put("hint", "Tests failed — consider rollback_application_deploy for " + appId);
                    }
                    return response;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage() != null ? ex.getMessage() : "");
                }
            }
        };
    }

    private static List<Map<String, Object>> collectFiredEvents(
            EventJournalStore eventJournalStore,
            String objectPath,
            String eventName
    ) {
        List<Map<String, Object>> fired = new ArrayList<>();
        for (int attempt = 0; attempt < EVENT_RETRY_ATTEMPTS; attempt++) {
            if (eventName != null && !eventName.isBlank()) {
                eventJournalStore.findLatest(objectPath, eventName).ifPresent(record ->
                        fired.add(eventToMap(record))
                );
            } else {
                for (EventJournalRecord record : eventJournalStore.queryRecent(objectPath, 10)) {
                    fired.add(eventToMap(record));
                }
            }
            if (!fired.isEmpty()) {
                break;
            }
            try {
                Thread.sleep(EVENT_RETRY_SLEEP_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return fired;
    }

    private static Map<String, Object> eventToMap(EventJournalRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", record.id());
        row.put("objectPath", record.objectPath());
        row.put("eventName", record.eventName());
        row.put("level", record.level());
        row.put("payloadJson", record.payloadJson());
        row.put("occurredAt", record.occurredAt() != null ? record.occurredAt().toString() : null);
        return row;
    }

    private static Map<String, Object> resultToMap(FunctionTestRunner.TestResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", result.id());
        map.put("kind", result.kind());
        map.put("status", result.status());
        map.put("errors", result.errors());
        map.put("details", result.details());
        return map;
    }

    private static boolean compare(String op, Object actual, Object expected) {
        String normalized = op.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "neq", "ne", "!=" -> !valuesEqual(expected, actual);
            case "gt", ">" -> compareNumbers(actual, expected) > 0;
            case "gte", ">=" -> compareNumbers(actual, expected) >= 0;
            case "lt", "<" -> compareNumbers(actual, expected) < 0;
            case "lte", "<=" -> compareNumbers(actual, expected) <= 0;
            case "contains" -> String.valueOf(actual).contains(String.valueOf(expected));
            default -> valuesEqual(expected, actual);
        };
    }

    private static int compareNumbers(Object actual, Object expected) {
        return Double.compare(toDouble(actual), toDouble(expected));
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static boolean valuesEqual(Object expected, Object actual) {
        if (expected instanceof Number expectedNumber && actual instanceof Number actualNumber) {
            return Double.compare(expectedNumber.doubleValue(), actualNumber.doubleValue()) == 0;
        }
        return Objects.equals(String.valueOf(expected), String.valueOf(actual));
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !String.valueOf(item).isBlank()) {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String optionalString(Map<String, Object> args, String key) {
        String value = stringArg(args, key);
        return value.isBlank() ? null : value;
    }
}
