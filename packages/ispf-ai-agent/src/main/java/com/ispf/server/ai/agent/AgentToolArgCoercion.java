package com.ispf.server.ai.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Best-effort argument normalization before schema validation (Qwen/native tool-call quirks).
 */
final class AgentToolArgCoercion {

    private AgentToolArgCoercion() {
    }

    static Map<String, Object> coerce(String toolName, Map<String, Object> arguments) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        if (toolName == null || toolName.isBlank()) {
            return args;
        }
        String normalized = toolName.trim().toLowerCase(Locale.ROOT);
        if ("enable_agent_tool_pack".equals(normalized)) {
            return coerceEnableAgentToolPack(args);
        }
        return args;
    }

    private static Map<String, Object> coerceEnableAgentToolPack(Map<String, Object> args) {
        Map<String, Object> copy = new LinkedHashMap<>(args);
        String pack = stringValue(copy.get("pack"));
        if (!pack.isBlank()) {
            copy.put("pack", pack.toLowerCase(Locale.ROOT));
            return copy;
        }
        for (String alt : List.of("name", "toolPack", "packId", "packName", "id", "value")) {
            String candidate = stringValue(copy.get(alt));
            if (candidate.isBlank()) {
                continue;
            }
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (AgentToolPackCatalog.ALL_PACKS.contains(lower)) {
                copy.put("pack", lower);
                return copy;
            }
            // Model sometimes passes a tool name; map to its pack.
            String fromTool = AgentToolPackCatalog.packFor(lower);
            if (fromTool != null
                    && !fromTool.isBlank()
                    && !AgentToolPackCatalog.CORE.equals(fromTool)
                    && AgentToolPackCatalog.ALL_PACKS.contains(fromTool)) {
                copy.put("pack", fromTool);
                return copy;
            }
        }
        return copy;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
