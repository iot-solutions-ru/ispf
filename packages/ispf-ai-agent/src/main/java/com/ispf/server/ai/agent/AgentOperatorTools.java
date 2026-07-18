package com.ispf.server.ai.agent;

import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.workflow.WorkQueueService;
import com.ispf.server.workflow.WorkQueueService.WorkQueueItem;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentOperatorTools {

    private AgentOperatorTools() {
    }

    public static List<PlatformAgentTool> all(
            VariableHistoryService variableHistoryService,
            WorkQueueService workQueueService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            com.ispf.server.operator.OperatorAgentMemoryService memoryService,
            com.ispf.server.operator.OperatorAppDocumentService documentService
    ) {
        return List.of(
                getOperatorScopeTool(),
                listAppMemoryTool(memoryService),
                rememberAppMemoryTool(memoryService),
                listAppDocumentsTool(documentService),
                readAppDocumentTool(documentService),
                searchAppDocumentsTool(documentService),
                getOperatorLinkTool(),
                getVariableHistoryTool(variableHistoryService, objectAccessService, tenantScopeService),
                getVariableHistoryTrendTool(variableHistoryService, objectAccessService, tenantScopeService),
                listWorkQueueTool(workQueueService)
        );
    }

    private static PlatformAgentTool listAppMemoryTool(com.ispf.server.operator.OperatorAgentMemoryService memoryService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_app_memory";
            }

            @Override
            public String description() {
                return "Search long-term memory for this operator app. Args: optional query, limit (default 20).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                OperatorAgentScope scope = context.operatorScope();
                if (scope == null) {
                    return Map.of("status", "ERROR", "error", "Operator scope not configured");
                }
                String query = stringArg(arguments, "query");
                int limit = intArg(arguments, "limit", 20);
                List<Map<String, Object>> rows = new ArrayList<>();
                for (var item : memoryService.list(scope.appId(), query, limit)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("kind", item.kind());
                    row.put("topic", item.topic());
                    row.put("content", item.content());
                    row.put("updatedAt", item.updatedAt().toString());
                    row.put("useCount", item.useCount());
                    rows.add(row);
                }
                return Map.of(
                        "status", "OK",
                        "appId", scope.appId(),
                        "count", rows.size(),
                        "memories", rows
                );
            }
        };
    }

    private static PlatformAgentTool rememberAppMemoryTool(com.ispf.server.operator.OperatorAgentMemoryService memoryService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "remember_app_memory";
            }

            @Override
            public String description() {
                return "Store durable knowledge for this app (not platform config). "
                        + "Args: content (required), optional kind (fact|glossary|preference|playbook|correction), topic.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                OperatorAgentScope scope = context.operatorScope();
                if (scope == null) {
                    return Map.of("status", "ERROR", "error", "Operator scope not configured");
                }
                String content = stringArg(arguments, "content");
                if (content.isBlank()) {
                    return Map.of("status", "ERROR", "error", "content is required");
                }
                String kind = stringArg(arguments, "kind");
                String topic = stringArg(arguments, "topic");
                var saved = memoryService.remember(
                        scope.appId(),
                        kind.isBlank() ? "fact" : kind,
                        topic.isBlank() ? content : topic,
                        content,
                        context.actor(),
                        null
                );
                return Map.of(
                        "status", "OK",
                        "appId", scope.appId(),
                        "kind", saved.kind(),
                        "topic", saved.topic(),
                        "content", saved.content()
                );
            }
        };
    }

    private static PlatformAgentTool listAppDocumentsTool(com.ispf.server.operator.OperatorAppDocumentService documentService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_app_documents";
            }

            @Override
            public String description() {
                return "List uploaded knowledge-base documents for this operator app. Optional limit (default 30).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                OperatorAgentScope scope = context.operatorScope();
                if (scope == null) {
                    return Map.of("status", "ERROR", "error", "Operator scope not configured");
                }
                int limit = intArg(arguments, "limit", 30);
                return Map.of(
                        "status", "OK",
                        "appId", scope.appId(),
                        "count", documentService.count(scope.appId()),
                        "documents", documentService.listMetadata(scope.appId(), limit)
                );
            }
        };
    }

    private static PlatformAgentTool readAppDocumentTool(com.ispf.server.operator.OperatorAppDocumentService documentService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "read_app_document";
            }

            @Override
            public String description() {
                return "Read full text of an uploaded document. Args: docId (required).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                OperatorAgentScope scope = context.operatorScope();
                if (scope == null) {
                    return Map.of("status", "ERROR", "error", "Operator scope not configured");
                }
                String docId = stringArg(arguments, "docId");
                if (docId.isBlank()) {
                    return Map.of("status", "ERROR", "error", "docId is required");
                }
                var doc = documentService.get(scope.appId(), docId);
                if (doc.isEmpty()) {
                    return Map.of("status", "ERROR", "error", "Document not found: " + docId);
                }
                var record = doc.get();
                return Map.of(
                        "status", "OK",
                        "docId", record.docId(),
                        "filename", record.filename(),
                        "description", record.description() != null ? record.description() : "",
                        "content", record.contentText()
                );
            }
        };
    }

    private static PlatformAgentTool searchAppDocumentsTool(com.ispf.server.operator.OperatorAppDocumentService documentService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "search_app_documents";
            }

            @Override
            public String description() {
                return "Search uploaded documents by filename, description, or content. Args: query (required), limit (default 10).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                OperatorAgentScope scope = context.operatorScope();
                if (scope == null) {
                    return Map.of("status", "ERROR", "error", "Operator scope not configured");
                }
                String query = stringArg(arguments, "query");
                if (query.isBlank()) {
                    return Map.of("status", "ERROR", "error", "query is required");
                }
                int limit = intArg(arguments, "limit", 10);
                List<Map<String, Object>> rows = new ArrayList<>();
                for (var doc : documentService.search(scope.appId(), query, limit)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("docId", doc.docId());
                    row.put("filename", doc.filename());
                    row.put("description", doc.description());
                    String text = doc.contentText();
                    if (text != null && text.length() > 1200) {
                        text = text.substring(0, 1199) + "…";
                    }
                    row.put("excerpt", text);
                    rows.add(row);
                }
                return Map.of(
                        "status", "OK",
                        "appId", scope.appId(),
                        "query", query,
                        "count", rows.size(),
                        "documents", rows
                );
            }
        };
    }

    private static PlatformAgentTool getOperatorLinkTool() {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_operator_link";
            }

            @Override
            public String description() {
                return "Build operator UI URL for a dashboard or report in this app. "
                        + "Args: path (required), optional kind (dashboard|report), optional title.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                OperatorAgentScope scope = context.operatorScope();
                if (scope == null) {
                    return Map.of("status", "ERROR", "error", "Operator scope not configured");
                }
                String path = stringArg(arguments, "path");
                if (path.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path is required");
                }
                if (!scope.isPathAllowed(path)) {
                    return Map.of("status", "ERROR", "error", "Path outside operator app scope: " + path);
                }
                String kind = stringArg(arguments, "kind");
                if (kind.isBlank()) {
                    kind = path.contains(".reports.") ? "report" : "dashboard";
                }
                String title = stringArg(arguments, "title");
                if (title.isBlank()) {
                    title = path.substring(path.lastIndexOf('.') + 1);
                }
                String url = com.ispf.server.operator.OperatorAgentResultEnricher.operatorUrl(scope.appId(), kind, path);
                return Map.of(
                        "status", "OK",
                        "kind", kind,
                        "path", path,
                        "title", title,
                        "url", url,
                        "hint", "Include this link in finish.result.links when answering"
                );
            }
        };
    }

    private static PlatformAgentTool getOperatorScopeTool() {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_operator_scope";
            }

            @Override
            public String description() {
                return "Return allowed object path prefixes for the current operator app.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                OperatorAgentScope scope = context.operatorScope();
                if (scope == null) {
                    return Map.of("status", "ERROR", "error", "Operator scope not configured");
                }
                return Map.of(
                        "status", "OK",
                        "appId", scope.appId(),
                        "title", scope.title(),
                        "pathPrefixes", scope.pathPrefixes(),
                        "briefingRoot", scope.briefingRoot()
                );
            }
        };
    }

    private static PlatformAgentTool getVariableHistoryTool(
            VariableHistoryService variableHistoryService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_variable_history";
            }

            @Override
            public String description() {
                return "Read historian samples for a variable. Args: path, name, optional field (default value), "
                        + "minutes (default 60), limit (default 120).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                String name = stringArg(arguments, "name");
                if (path.isBlank() || name.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path and name are required");
                }
                var auth = context.authentication();
                objectAccessService.requireRead(path, auth);
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Path not visible: " + path);
                }
                int minutes = intArg(arguments, "minutes", 60);
                minutes = Math.min(Math.max(minutes, 1), 10_080);
                int limit = intArg(arguments, "limit", 120);
                Instant to = Instant.now();
                Instant from = to.minus(minutes, ChronoUnit.MINUTES);
                String field = stringArg(arguments, "field");
                if (field.isBlank()) {
                    field = "value";
                }
                try {
                    VariableHistoryService.VariableHistoryResponse response = variableHistoryService.query(
                            path,
                            name,
                            field,
                            from,
                            to,
                            limit
                    );
                    List<Map<String, Object>> samples = new ArrayList<>();
                    for (VariableHistoryService.VariableHistorySample sample : response.samples()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("ts", sample.ts().toString());
                        row.put("value", sample.value());
                        if (sample.text() != null) {
                            row.put("text", sample.text());
                        }
                        samples.add(row);
                    }
                    return Map.of(
                            "status", "OK",
                            "objectPath", response.objectPath(),
                            "variableName", response.variableName(),
                            "field", response.field(),
                            "from", from.toString(),
                            "to", to.toString(),
                            "sampleCount", samples.size(),
                            "samples", samples
                    );
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool getVariableHistoryTrendTool(
            VariableHistoryService variableHistoryService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_variable_trend";
            }

            @Override
            public String description() {
                return "Aggregated historian trend (avg/min/max buckets). Args: path, name, optional field, "
                        + "minutes (default 1440), bucket (1h|15m|1d, default 1h), maxBuckets (default 48).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                String name = stringArg(arguments, "name");
                if (path.isBlank() || name.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path and name are required");
                }
                var auth = context.authentication();
                objectAccessService.requireRead(path, auth);
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Path not visible: " + path);
                }
                int minutes = intArg(arguments, "minutes", 1440);
                minutes = Math.min(Math.max(minutes, 1), 10_080);
                String bucket = stringArg(arguments, "bucket");
                if (bucket.isBlank()) {
                    bucket = "1h";
                }
                int maxBuckets = intArg(arguments, "maxBuckets", 48);
                String field = stringArg(arguments, "field");
                if (field.isBlank()) {
                    field = "value";
                }
                Instant to = Instant.now();
                Instant from = to.minus(minutes, ChronoUnit.MINUTES);
                try {
                    VariableHistoryService.VariableHistoryAggregateResponse response = variableHistoryService.aggregate(
                            path,
                            name,
                            field,
                            from,
                            to,
                            bucket,
                            maxBuckets
                    );
                    List<Map<String, Object>> buckets = new ArrayList<>();
                    for (VariableHistoryService.VariableHistoryBucket item : response.buckets()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("ts", item.ts().toString());
                        row.put("avg", item.avg());
                        row.put("min", item.min());
                        row.put("max", item.max());
                        buckets.add(row);
                    }
                    return Map.of(
                            "status", "OK",
                            "objectPath", response.objectPath(),
                            "variableName", response.variableName(),
                            "field", response.field(),
                            "bucket", response.bucket(),
                            "from", from.toString(),
                            "to", to.toString(),
                            "bucketCount", buckets.size(),
                            "buckets", buckets
                    );
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool listWorkQueueTool(WorkQueueService workQueueService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_work_queue";
            }

            @Override
            public String description() {
                return "Open operator work-queue tasks for the current app. Optional limit (default 20).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                OperatorAgentScope scope = context.operatorScope();
                if (scope == null) {
                    return Map.of("status", "ERROR", "error", "Operator scope not configured");
                }
                int limit = intArg(arguments, "limit", 20);
                limit = Math.min(Math.max(limit, 1), 100);
                List<WorkQueueItem> tasks = workQueueService.listOpenTasks(limit, scope.appId());
                List<Map<String, Object>> rows = new ArrayList<>();
                for (WorkQueueItem task : tasks) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("taskId", task.id());
                    row.put("workflowPath", task.workflowPath());
                    row.put("title", task.title());
                    row.put("status", task.status());
                    row.put("createdAt", task.createdAt() != null ? task.createdAt().toString() : null);
                    row.put("operatorAppId", task.operatorAppId());
                    rows.add(row);
                }
                return Map.of(
                        "status", "OK",
                        "appId", scope.appId(),
                        "count", rows.size(),
                        "tasks", rows
                );
            }
        };
    }

    private static String stringArg(Map<String, Object> arguments, String key) {
        if (arguments == null) {
            return "";
        }
        Object raw = arguments.get(key);
        return raw != null ? String.valueOf(raw).trim() : "";
    }

    private static int intArg(Map<String, Object> arguments, String key, int defaultValue) {
        if (arguments == null) {
            return defaultValue;
        }
        Object raw = arguments.get(key);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(String.valueOf(raw).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
