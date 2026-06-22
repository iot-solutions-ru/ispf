package com.ispf.server.ai.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

public final class AgentJsonProtocol {

    public record AgentAction(
            String type,
            String toolName,
            Map<String, Object> arguments,
            String summary,
            Map<String, Object> result
    ) {
    }

    private AgentJsonProtocol() {
    }

    public static AgentAction parse(ObjectMapper objectMapper, String content) throws Exception {
        String json = extractJsonObject(objectMapper, content);
        JsonNode node = objectMapper.readTree(json);
        String type = node.path("type").asString("").trim();
        if ("finish".equalsIgnoreCase(type)) {
            return new AgentAction(
                    "finish",
                    null,
                    null,
                    node.path("summary").asString(null),
                    node.isObject() && node.has("result")
                            ? objectMapper.convertValue(node.get("result"), Map.class)
                            : Map.of()
            );
        }
        if ("tool".equalsIgnoreCase(type)) {
            String name = node.has("name") ? node.path("name").asString(null) : node.path("tool").asString(null);
            Map<String, Object> args = node.has("arguments")
                    ? objectMapper.convertValue(node.get("arguments"), Map.class)
                    : Map.of();
            return new AgentAction("tool", name, args, null, null);
        }
        throw new IllegalArgumentException("Agent action must be {\"type\":\"tool\",...} or {\"type\":\"finish\",...}");
    }

    private static String extractJsonObject(ObjectMapper objectMapper, String content) throws Exception {
        String trimmed = content != null ? content.trim() : "";
        String best = null;
        int bestScore = -1;
        for (int start = trimmed.indexOf('{'); start >= 0; start = trimmed.indexOf('{', start + 1)) {
            int end = findJsonObjectEnd(trimmed, start);
            if (end < 0) {
                continue;
            }
            String candidate = trimmed.substring(start, end + 1);
            try {
                JsonNode node = objectMapper.readTree(candidate);
                if (!node.isObject() || node.isEmpty()) {
                    continue;
                }
                int score = node.size();
                if (node.has("type")) {
                    score += 20;
                }
                if (node.has("name") || node.has("tool")) {
                    score += 10;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            } catch (Exception ignored) {
                // scan next
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("Agent response does not contain a JSON action object");
        }
        return best;
    }

    private static int findJsonObjectEnd(String content, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
