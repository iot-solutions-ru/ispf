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
        String normalized = normalizeModelContent(content);
        String json = extractJsonObject(objectMapper, normalized);
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

    private static String normalizeModelContent(String content) {
        String trimmed = content != null ? content.trim() : "";
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        int redactedEnd = trimmed.lastIndexOf("</think>");
        if (redactedEnd >= 0) {
            trimmed = trimmed.substring(redactedEnd + "</think>".length()).trim();
        }
        int fenceStart = trimmed.indexOf("```json");
        if (fenceStart >= 0) {
            int bodyStart = fenceStart + "```json".length();
            int fenceEnd = trimmed.indexOf("```", bodyStart);
            if (fenceEnd > bodyStart) {
                return trimmed.substring(bodyStart, fenceEnd).trim();
            }
        }
        return trimmed;
    }

    private static String extractJsonObject(ObjectMapper objectMapper, String content) throws Exception {
        String trimmed = content != null ? content.trim() : "";
        String best = null;
        int bestScore = -1;
        int bestStart = -1;
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
                String type = node.path("type").asString("").trim();
                if (!type.isEmpty() && !"tool".equalsIgnoreCase(type) && !"finish".equalsIgnoreCase(type)) {
                    continue;
                }
                int score = node.size();
                if ("tool".equalsIgnoreCase(type) || "finish".equalsIgnoreCase(type)) {
                    score += 100;
                }
                if (node.has("name") || node.has("tool")) {
                    score += 10;
                }
                if (score > bestScore || (score == bestScore && start > bestStart)) {
                    bestScore = score;
                    bestStart = start;
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
