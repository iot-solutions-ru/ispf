package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AgentSessionKnowledgeTools {

    private AgentSessionKnowledgeTools() {
    }

    static PlatformAgentTool searchSessionContextTool(AgentSessionDocumentService documentService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "search_session_context";
            }

            @Override
            public String description() {
                return "Search documents uploaded to this agent chat session (read-only). "
                        + "Args: query (string), optional limit (int, default 5).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                if (context.sessionId() == null || context.sessionId().isBlank()) {
                    return Map.of("status", "ERROR", "error", "No agent session context");
                }
                String query = stringArg(arguments, "query");
                if (query.isBlank()) {
                    return Map.of("status", "ERROR", "error", "query is required");
                }
                int limit = intArg(arguments, "limit", 5);
                List<AgentSessionDocumentRecord> docs = documentService.search(context.sessionId(), query, limit);
                List<Map<String, Object>> rows = new ArrayList<>();
                for (AgentSessionDocumentRecord doc : docs) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("docId", doc.docId());
                    row.put("filename", doc.filename());
                    row.put("description", doc.description() != null ? doc.description() : "");
                    row.put("excerpt", excerpt(doc.contentText(), 1200));
                    rows.add(row);
                }
                return Map.of("status", "OK", "documents", rows, "count", rows.size());
            }
        };
    }

    private static String excerpt(String text, int max) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 1) + "…";
    }

    private static String stringArg(Map<String, Object> arguments, String key) {
        Object value = arguments != null ? arguments.get(key) : null;
        return value != null ? String.valueOf(value).trim() : "";
    }

    private static int intArg(Map<String, Object> arguments, String key, int defaultValue) {
        Object value = arguments != null ? arguments.get(key) : null;
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
