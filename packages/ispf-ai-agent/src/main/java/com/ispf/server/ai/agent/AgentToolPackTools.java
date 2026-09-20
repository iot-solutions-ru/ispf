package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Meta-tools for progressive disclosure of the platform agent tool surface.
 */
final class AgentToolPackTools {

    private AgentToolPackTools() {
    }

    static List<PlatformAgentTool> all(Supplier<List<Map<String, Object>>> fullCatalogSupplier) {
        return List.of(
                listAgentToolsTool(fullCatalogSupplier),
                describeAgentToolTool(fullCatalogSupplier),
                enableAgentToolPackTool(fullCatalogSupplier)
        );
    }

    private static PlatformAgentTool listAgentToolsTool(Supplier<List<Map<String, Object>>> fullCatalogSupplier) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_agent_tools";
            }

            @Override
            public String description() {
                return "List platform agent tools (progressive surface). "
                        + "Args: optional pack (core|discovery|devices|dashboards|automation|bundles|scada|analytics|security|misc), "
                        + "optional query. Returns name + one-line description + pack.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String pack = stringArg(arguments, "pack");
                String query = stringArg(arguments, "query");
                List<Map<String, Object>> tools = AgentToolSurface.listTools(
                        fullCatalogSupplier.get(),
                        pack,
                        query
                );
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("status", "OK");
                result.put("count", tools.size());
                result.put("packs", AgentToolPackCatalog.packIndex());
                if (context != null && context.runState() != null) {
                    result.put("activePacks", List.copyOf(context.runState().activeToolPacks()));
                }
                result.put("tools", tools);
                result.put(
                        "hint",
                        "Enable a pack with enable_agent_tool_pack before calling tools outside the active set."
                );
                return result;
            }
        };
    }

    private static PlatformAgentTool describeAgentToolTool(Supplier<List<Map<String, Object>>> fullCatalogSupplier) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "describe_agent_tool";
            }

            @Override
            public String description() {
                return "Full description and pack for one agent tool. Args: name (exact snake_case tool name).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String name = stringArg(arguments, "name").toLowerCase(Locale.ROOT);
                if (name.isBlank()) {
                    return Map.of("status", "ERROR", "error", "name is required");
                }
                for (Map<String, Object> tool : fullCatalogSupplier.get()) {
                    if (name.equalsIgnoreCase(String.valueOf(tool.get("name")))) {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("status", "OK");
                        result.put("name", tool.get("name"));
                        result.put("pack", AgentToolPackCatalog.packFor(name));
                        result.put("description", tool.get("description"));
                        if (tool.get("inputSchema") != null) {
                            result.put("inputSchema", tool.get("inputSchema"));
                        }
                        if (context != null && context.runState() != null) {
                            result.put("active", AgentToolSurface.isToolActive(context.runState(), name));
                            result.put("activePacks", List.copyOf(context.runState().activeToolPacks()));
                        }
                        return result;
                    }
                }
                return Map.of(
                        "status", "ERROR",
                        "error", "Unknown tool: " + name,
                        "hint", "Use list_agent_tools query=... to find exact snake_case names."
                );
            }
        };
    }

    private static PlatformAgentTool enableAgentToolPackTool(Supplier<List<Map<String, Object>>> fullCatalogSupplier) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "enable_agent_tool_pack";
            }

            @Override
            public String description() {
                return "Enable a tool pack for the rest of this agent turn (progressive disclosure). "
                        + "Args: pack (required). Returns newly available tools.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String pack = stringArg(arguments, "pack").toLowerCase(Locale.ROOT);
                if (pack.isBlank()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "pack is required",
                            "packs", AgentToolPackCatalog.packIndex()
                    );
                }
                if (!AgentToolPackCatalog.ALL_PACKS.contains(pack)) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "Unknown pack: " + pack,
                            "packs", AgentToolPackCatalog.packIndex()
                    );
                }
                if (context == null || context.runState() == null) {
                    return Map.of("status", "ERROR", "error", "No agent run state");
                }
                AgentRunState runState = context.runState();
                Set<String> before = new LinkedHashSet<>(runState.activeToolPacks());
                if (before.isEmpty()) {
                    before.addAll(AgentToolPackCatalog.ALWAYS_ON);
                }
                LinkedHashSet<String> after = new LinkedHashSet<>(before);
                boolean added = after.add(pack);
                runState.setActiveToolPacks(after);

                List<Map<String, Object>> newlyAvailable = new ArrayList<>();
                for (Map<String, Object> tool : fullCatalogSupplier.get()) {
                    String name = String.valueOf(tool.get("name"));
                    if (pack.equals(AgentToolPackCatalog.packFor(name))) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("name", name);
                        row.put("description", AgentToolPackCatalog.shortDescription(
                                String.valueOf(tool.getOrDefault("description", ""))
                        ));
                        newlyAvailable.add(row);
                    }
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("status", "OK");
                result.put("pack", pack);
                result.put("enabled", added);
                result.put("activePacks", List.copyOf(runState.activeToolPacks()));
                result.put("toolsInPack", newlyAvailable);
                result.put("toolCount", newlyAvailable.size());
                result.put(
                        "hint",
                        added
                                ? "Pack enabled — you may call these tools now in this turn."
                                : "Pack was already active."
                );
                return result;
            }
        };
    }

    private static String stringArg(Map<String, Object> arguments, String key) {
        if (arguments == null || !arguments.containsKey(key) || arguments.get(key) == null) {
            return "";
        }
        return String.valueOf(arguments.get(key)).trim();
    }
}
