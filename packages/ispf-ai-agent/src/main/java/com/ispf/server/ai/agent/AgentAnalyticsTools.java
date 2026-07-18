package com.ispf.server.ai.agent;

import com.ispf.server.platform.analytics.AnalyticsAnalysisService;
import com.ispf.server.platform.analytics.AnalyticsCatalogRegistry;
import com.ispf.server.platform.analytics.AnalyticsExpressionService;
import com.ispf.server.platform.analytics.AnalyticsQueryRequest;
import com.ispf.server.platform.analytics.AnalyticsQueryService;
import com.ispf.server.platform.analytics.catalog.AnalyticsCatalogEntry;
import com.ispf.server.platform.analytics.engine.AnalyticsTagCatalogService;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.workflow.WorkflowAiActionService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analytics AI tools (ADR-0049 Wave 2C).
 */
final class AgentAnalyticsTools {

    private AgentAnalyticsTools() {
    }

    static List<PlatformAgentTool> all(
            AnalyticsCatalogRegistry catalogRegistry,
            AnalyticsAnalysisService analysisService,
            VariableHistoryService variableHistoryService,
            WorkflowAiActionService workflowAiActionService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            AnalyticsTagCatalogService tagCatalogService,
            AnalyticsQueryService analyticsQueryService,
            AnalyticsExpressionService expressionService
    ) {
        return List.of(
                listAnalyticsCatalogTool(catalogRegistry),
                getAnalyticsTagTool(tagCatalogService, objectAccessService),
                queryAnalyticsTagsTool(analyticsQueryService, objectAccessService),
                evaluateAnalyticsExpressionTool(expressionService, objectAccessService),
                detectAnomaliesTool(analysisService, variableHistoryService, objectAccessService, tenantScopeService),
                comparePeriodsTool(analysisService, variableHistoryService, objectAccessService, tenantScopeService),
                summarizeTrendTool(
                        analysisService,
                        variableHistoryService,
                        workflowAiActionService,
                        objectAccessService,
                        tenantScopeService
                )
        );
    }

    private static PlatformAgentTool getAnalyticsTagTool(
            AnalyticsTagCatalogService tagCatalogService,
            ObjectAccessService objectAccessService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_analytics_tag";
            }

            @Override
            public String description() {
                return "Get analytics tag catalog entry with lineage. Arg: path (tag path).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                if (path.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path is required");
                }
                try {
                    objectAccessService.requireRead(path, context.authentication());
                    var entry = tagCatalogService.getCatalogEntry(path);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("status", "OK");
                    row.put("tag", entry);
                    return row;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool queryAnalyticsTagsTool(
            AnalyticsQueryService analyticsQueryService,
            ObjectAccessService objectAccessService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "query_analytics_tags";
            }

            @Override
            public String description() {
                return "Multi-tag historian query. Args: objectPath, variable, optional hours (default 4), "
                        + "bucket (default 15m), agg (default avg).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String objectPath = stringArg(arguments, "objectPath");
                String variable = stringArg(arguments, "variable");
                if (objectPath.isBlank() || variable.isBlank()) {
                    return Map.of("status", "ERROR", "error", "objectPath and variable are required");
                }
                try {
                    objectAccessService.requireRead(objectPath, context.authentication());
                    int hours = intArg(arguments, "hours", 4);
                    Instant to = Instant.now();
                    Instant from = to.minus(Math.max(1, hours), ChronoUnit.HOURS);
                    String bucket = stringArg(arguments, "bucket");
                    if (bucket.isBlank()) {
                        bucket = "15m";
                    }
                    String agg = stringArg(arguments, "agg");
                    if (agg.isBlank()) {
                        agg = "avg";
                    }
                    var response = analyticsQueryService.query(new AnalyticsQueryRequest(
                            List.of(new AnalyticsQueryRequest.AnalyticsQueryTag(objectPath, variable, "value", null)),
                            from,
                            to,
                            bucket,
                            agg,
                            48,
                            null
                    ));
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", "OK");
                    result.put("query", response);
                    return result;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool evaluateAnalyticsExpressionTool(
            AnalyticsExpressionService expressionService,
            ObjectAccessService objectAccessService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "evaluate_analytics_expression";
            }

            @Override
            public String description() {
                return "Validate/evaluate CEL-over-historian expression. Args: expression, objectPath, "
                        + "optional mode=validate|evaluate (default evaluate).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String expression = stringArg(arguments, "expression");
                String objectPath = stringArg(arguments, "objectPath");
                if (expression.isBlank() || objectPath.isBlank()) {
                    return Map.of("status", "ERROR", "error", "expression and objectPath are required");
                }
                try {
                    objectAccessService.requireRead(objectPath, context.authentication());
                    String mode = stringArg(arguments, "mode");
                    if ("validate".equalsIgnoreCase(mode)) {
                        var validated = expressionService.validate(expression, objectPath);
                        return Map.of("status", "OK", "mode", "validate", "result", validated);
                    }
                    var evaluated = expressionService.evaluate(expression, objectPath, null);
                    return Map.of("status", "OK", "mode", "evaluate", "result", evaluated);
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool listAnalyticsCatalogTool(AnalyticsCatalogRegistry catalogRegistry) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_analytics_catalog";
            }

            @Override
            public String description() {
                return "List analytics function catalog entries (historian + analysis). Optional kind filter.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String kind = stringArg(arguments, "kind");
                List<Map<String, Object>> rows = new ArrayList<>();
                for (AnalyticsCatalogEntry entry : catalogRegistry.list()) {
                    if (!kind.isBlank() && entry.kinds().stream().noneMatch(k -> k.equalsIgnoreCase(kind))) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", entry.id());
                    row.put("displayName", entry.displayName());
                    row.put("tier", entry.tier());
                    row.put("kinds", entry.kinds());
                    row.put("signature", entry.syntax());
                    row.put("description", entry.description());
                    rows.add(row);
                }
                return Map.of("status", "OK", "count", rows.size(), "functions", rows);
            }
        };
    }

    private static PlatformAgentTool detectAnomaliesTool(
            AnalyticsAnalysisService analysisService,
            VariableHistoryService variableHistoryService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "detect_anomalies";
            }

            @Override
            public String description() {
                return "Rule-based anomalyScore/zScore over variable history. Args: objectPath, variable, "
                        + "optional hours (default 4), zThreshold (default 3).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String objectPath = stringArg(arguments, "objectPath");
                String variable = stringArg(arguments, "variable");
                if (objectPath.isBlank() || variable.isBlank()) {
                    return Map.of("status", "ERROR", "error", "objectPath and variable are required");
                }
                try {
                    objectAccessService.requireRead(objectPath, context.authentication());
                    List<Double> series = loadSeries(variableHistoryService, objectPath, variable, intArg(arguments, "hours", 4));
                    double threshold = doubleArg(arguments, "zThreshold", 3.0);
                    Map<String, Object> analysis = analysisService.analyzeSeries(series, threshold);
                    return Map.of("status", "OK", "objectPath", objectPath, "variable", variable, "analysis", analysis);
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool comparePeriodsTool(
            AnalyticsAnalysisService analysisService,
            VariableHistoryService variableHistoryService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "compare_periods";
            }

            @Override
            public String description() {
                return "periodOverPeriod compare for a variable. Args: objectPath, variable, optional hoursPerPeriod (default 4).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String objectPath = stringArg(arguments, "objectPath");
                String variable = stringArg(arguments, "variable");
                if (objectPath.isBlank() || variable.isBlank()) {
                    return Map.of("status", "ERROR", "error", "objectPath and variable are required");
                }
                try {
                    objectAccessService.requireRead(objectPath, context.authentication());
                    int hours = intArg(arguments, "hoursPerPeriod", 4);
                    Instant end = Instant.now();
                    Instant mid = end.minus(hours, ChronoUnit.HOURS);
                    Instant start = mid.minus(hours, ChronoUnit.HOURS);
                    List<Double> previous = loadSeriesRange(variableHistoryService, objectPath, variable, start, mid);
                    List<Double> current = loadSeriesRange(variableHistoryService, objectPath, variable, mid, end);
                    return Map.of(
                            "status", "OK",
                            "comparison", analysisService.periodOverPeriod(current, previous)
                    );
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool summarizeTrendTool(
            AnalyticsAnalysisService analysisService,
            VariableHistoryService variableHistoryService,
            WorkflowAiActionService workflowAiActionService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "summarize_trend";
            }

            @Override
            public String description() {
                return "Build deterministic trend summary then LLM narrate (2-4 sentences). "
                        + "Args: objectPath, variable, optional hours (default 4).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String objectPath = stringArg(arguments, "objectPath");
                String variable = stringArg(arguments, "variable");
                if (objectPath.isBlank() || variable.isBlank()) {
                    return Map.of("status", "ERROR", "error", "objectPath and variable are required");
                }
                try {
                    objectAccessService.requireRead(objectPath, context.authentication());
                    List<Double> series = loadSeries(variableHistoryService, objectPath, variable, intArg(arguments, "hours", 4));
                    Map<String, Object> summary = analysisService.analyzeSeries(series, 3.0);
                    String prompt = "Summarize this OT tag trend in 2-4 short sentences for an operator. "
                            + "Data JSON: " + summary;
                    String narrative = workflowAiActionService.llmComplete(prompt, "platform-default", 30_000);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", "OK");
                    result.put("summary", summary);
                    result.put("narrative", narrative);
                    return result;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static List<Double> loadSeries(
            VariableHistoryService historyService,
            String objectPath,
            String variable,
            int hours
    ) {
        Instant end = Instant.now();
        Instant start = end.minus(Math.max(1, hours), ChronoUnit.HOURS);
        return loadSeriesRange(historyService, objectPath, variable, start, end);
    }

    private static List<Double> loadSeriesRange(
            VariableHistoryService historyService,
            String objectPath,
            String variable,
            Instant start,
            Instant end
    ) {
        List<Double> series = new ArrayList<>();
        VariableHistoryService.VariableHistoryResponse response = historyService.query(
                objectPath,
                variable,
                "value",
                start,
                end,
                500
        );
        for (VariableHistoryService.VariableHistorySample sample : response.samples()) {
            if (sample.value() != null) {
                series.add(sample.value());
            } else {
                series.add(Double.NaN);
            }
        }
        return series;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        if (args == null) {
            return "";
        }
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intArg(Map<String, Object> args, String key, int fallback) {
        Object raw = args != null ? args.get(key) : null;
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static double doubleArg(Map<String, Object> args, String key, double fallback) {
        Object raw = args != null ? args.get(key) : null;
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
