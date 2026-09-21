package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Session/run helpers for the progressive tool surface (active packs + filtered catalogs).
 */
public final class AgentToolSurface {

    private AgentToolSurface() {
    }

    /**
     * Reset active packs for a new user turn: always-on + optional keyword hints (Plan/Execute/Auto).
     */
    public static void beginTurn(AgentRunState runState, AgentInteractionMode mode, String userMessage) {
        if (runState == null) {
            return;
        }
        LinkedHashSet<String> packs = new LinkedHashSet<>(AgentToolPackCatalog.defaultPacks(mode));
        if (mode != null && mode != AgentInteractionMode.ASK) {
            packs.addAll(AgentToolPackCatalog.hintPacks(userMessage));
        }
        runState.setActiveToolPacks(packs);
    }

    public static boolean isToolActive(AgentRunState runState, String toolName) {
        if (AgentToolPackCatalog.isAlwaysAllowed(toolName)) {
            return true;
        }
        if (runState == null) {
            return true;
        }
        Set<String> active = runState.activeToolPacks();
        if (active.isEmpty()) {
            // Not initialized (operator/copilot paths) — allow full surface.
            return true;
        }
        return active.contains(AgentToolPackCatalog.packFor(toolName));
    }

    public static Map<String, Object> inactiveToolResult(String toolName, AgentRunState runState) {
        String pack = AgentToolPackCatalog.packFor(toolName);
        Map<String, Object> result = new LinkedHashMap<>(AgentToolErrors.error(
                "TOOL_PACK_INACTIVE",
                "Tool '" + toolName + "' is not in the active tool surface for this turn",
                "",
                "Call enable_agent_tool_pack with pack=\"" + pack + "\" (or list_agent_tools), "
                        + "then retry. Capability is deferred, not removed.",
                AgentToolErrors.DOC_REF_0060
        ));
        result.put("pack", pack);
        result.put("activePacks", runState == null ? List.of() : List.copyOf(runState.activeToolPacks()));
        return result;
    }

    public static List<Map<String, Object>> filterCatalog(
            List<Map<String, Object>> fullCatalog,
            AgentRunState runState,
            boolean shortDescriptions
    ) {
        if (fullCatalog == null || fullCatalog.isEmpty()) {
            return List.of();
        }
        if (runState == null || runState.activeToolPacks().isEmpty()) {
            return shortDescriptions ? shorten(fullCatalog) : fullCatalog;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : fullCatalog) {
            Object name = row.get("name");
            if (name == null) {
                continue;
            }
            if (isToolActive(runState, String.valueOf(name))) {
                filtered.add(shortDescriptions ? shortenRow(row) : row);
            }
        }
        return List.copyOf(filtered);
    }

    public static List<Map<String, Object>> listTools(
            List<Map<String, Object>> fullCatalog,
            String packFilter,
            String query
    ) {
        if (fullCatalog == null || fullCatalog.isEmpty()) {
            return List.of();
        }
        String pack = packFilter == null ? "" : packFilter.trim().toLowerCase(Locale.ROOT);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> tool : fullCatalog) {
            String name = String.valueOf(tool.get("name"));
            String toolPack = AgentToolPackCatalog.packFor(name);
            if (!pack.isBlank() && !pack.equals(toolPack)) {
                continue;
            }
            String description = String.valueOf(tool.getOrDefault("description", ""));
            if (!q.isBlank()
                    && !name.toLowerCase(Locale.ROOT).contains(q)
                    && !description.toLowerCase(Locale.ROOT).contains(q)
                    && !toolPack.contains(q)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("pack", toolPack);
            row.put("description", AgentToolPackCatalog.shortDescription(description));
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    private static List<Map<String, Object>> shorten(List<Map<String, Object>> catalog) {
        List<Map<String, Object>> out = new ArrayList<>(catalog.size());
        for (Map<String, Object> row : catalog) {
            out.add(shortenRow(row));
        }
        return List.copyOf(out);
    }

    private static Map<String, Object> shortenRow(Map<String, Object> row) {
        Map<String, Object> copy = new LinkedHashMap<>();
        copy.put("name", row.get("name"));
        copy.put("description", AgentToolPackCatalog.shortDescription(String.valueOf(row.getOrDefault("description", ""))));
        return copy;
    }
}
