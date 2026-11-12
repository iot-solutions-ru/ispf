package com.ispf.server.ai.mcp;

import com.ispf.server.ai.agent.AgentContext;
import com.ispf.server.ai.agent.AgentInteractionMode;
import com.ispf.server.ai.agent.AgentMutateApprovalGuard;
import com.ispf.server.ai.agent.AgentPlanPhase;
import com.ispf.server.ai.agent.AgentProfile;
import com.ispf.server.ai.agent.AgentRunState;
import com.ispf.server.ai.agent.AgentSession;
import com.ispf.server.ai.agent.AgentSessionStore;
import com.ispf.server.ai.agent.AgentToolInputSchemas;
import com.ispf.server.ai.agent.AgentToolSchemas;
import com.ispf.server.ai.agent.PlatformAgentToolRegistry;
import com.ispf.server.ai.audit.AiToolAuditService;
import com.ispf.server.ai.context.ContextPackService;
import com.ispf.server.config.AiProperties;
import com.ispf.server.config.McpProperties;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.workflow.WorkflowService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Delegates MCP tool calls to {@link PlatformAgentToolRegistry} and publishes ACTIVE workflows
 * with {@code toolDescription} as dynamic tools (ADR-0049).
 */
@Service
@ConditionalOnProperty(prefix = "ispf.mcp", name = "enabled", havingValue = "true")
public class McpToolAdapter {

    static final String MCP_CREATE_AGENT_SESSION = "mcp_create_agent_session";
    static final String MCP_APPROVE_MUTATE_PLAN = "mcp_approve_mutate_plan";

    private static final Set<String> MCP_META_TOOLS = Set.of(MCP_CREATE_AGENT_SESSION, MCP_APPROVE_MUTATE_PLAN);

    private final PlatformAgentToolRegistry toolRegistry;
    private final AgentSessionStore sessionStore;
    private final McpConnectionStateStore connectionStateStore;
    private final AiToolAuditService auditService;
    private final ContextPackService contextPackService;
    private final WorkflowService workflowService;
    private final AiProperties aiProperties;
    private final McpProperties mcpProperties;
    private final ObjectAccessService objectAccessService;
    private final ObjectMapper objectMapper;

    public McpToolAdapter(
            PlatformAgentToolRegistry toolRegistry,
            AgentSessionStore sessionStore,
            McpConnectionStateStore connectionStateStore,
            AiToolAuditService auditService,
            ContextPackService contextPackService,
            WorkflowService workflowService,
            AiProperties aiProperties,
            McpProperties mcpProperties,
            ObjectAccessService objectAccessService,
            ObjectMapper objectMapper
    ) {
        this.toolRegistry = toolRegistry;
        this.sessionStore = sessionStore;
        this.connectionStateStore = connectionStateStore;
        this.auditService = auditService;
        this.contextPackService = contextPackService;
        this.workflowService = workflowService;
        this.aiProperties = aiProperties;
        this.mcpProperties = mcpProperties;
        this.objectAccessService = objectAccessService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(mcpMetaTool(
                MCP_CREATE_AGENT_SESSION,
                "Create a persisted AI agent session for this MCP connection and return sessionId. "
                        + "Optional rootPath (default root). Run state is carried on the connection until this is called.",
                AgentToolSchemas.objectSchema(
                        Map.of(
                                "rootPath",
                                AgentToolSchemas.stringProp(
                                        "Object-tree root for the session (default root)"
                                ),
                                "interactionMode",
                                AgentToolSchemas.stringProp(
                                        "Optional PLAN, EXECUTE, or CHAT"
                                )
                        ),
                        List.of(),
                        false
                )
        ));
        tools.add(mcpMetaTool(
                MCP_APPROVE_MUTATE_PLAN,
                "Approve mutate tools for this MCP connection when "
                        + "ispf.ai.agent-require-approval-for-mutate is enabled. "
                        + "Requires an approved plan in the UI unless ispf.mcp.allow-inband-mutate-approval=true.",
                AgentToolSchemas.objectSchema(
                        Map.of(
                                "note",
                                AgentToolSchemas.stringProp(
                                        "Optional human-readable approval note"
                                )
                        ),
                        List.of(),
                        false
                )
        ));
        for (Map<String, Object> entry : toolRegistry.toolCatalog()) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", entry.get("name"));
            tool.put("description", entry.get("description"));
            Object schema = entry.get("inputSchema");
            tool.put("inputSchema", schema != null ? schema : AgentToolInputSchemas.forTool(String.valueOf(entry.get("name"))));
            maybePutDestructiveHint(tool, String.valueOf(entry.get("name")));
            tools.add(tool);
        }
        for (WorkflowService.PublishedWorkflowTool published : workflowService.listPublishedWorkflowTools()) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", published.toolName());
            tool.put("description", published.description() + " [workflow:" + published.path() + "]");
            tool.put("inputSchema", parseInputSchema(published.inputSchemaJson()));
            tool.put("annotations", Map.of("destructiveHint", true, "openWorldHint", true));
            tools.add(tool);
        }
        return tools;
    }

    public Map<String, Object> callTool(
            String toolName,
            Map<String, Object> arguments,
            Authentication authentication,
            String actor,
            String connectionKey
    ) throws Exception {
        if (MCP_META_TOOLS.contains(toolName)) {
            Map<String, Object> metaResult = executeMetaTool(toolName, arguments, actor, connectionKey);
            String metaStatus = String.valueOf(metaResult.getOrDefault("status", "OK"));
            auditService.record(
                    "mcp_" + toolName,
                    connectionStateStore.agentSessionId(connectionKey).orElse("mcp"),
                    actor,
                    writeJson(arguments != null ? arguments : Map.of()),
                    metaStatus,
                    "mcp",
                    "n/a",
                    contextPackService.contextPackVersion(),
                    "ERROR".equals(metaStatus)
                            ? List.of(String.valueOf(metaResult.get("error")))
                            : List.of()
            );
            return wrapMetaResult(metaResult, objectMapper);
        }

        Map<String, Object> args = arguments != null ? new LinkedHashMap<>(arguments) : new LinkedHashMap<>();
        String sessionId = extractSessionId(args);
        ResolvedRunState resolved = resolveRunState(connectionKey, sessionId, actor);
        AgentRunState runState = resolved.runState();
        Optional<AgentSession> session = resolved.session();

        AgentContext context = new AgentContext(actor, authentication, runState);
        Map<String, Object> result;
        String status;
        Optional<AgentMutateApprovalGuard.BlockDecision> approvalBlock =
                AgentMutateApprovalGuard.checkBeforeTool(
                        aiProperties.isAgentRequireApprovalForMutate(),
                        runState,
                        toolName,
                        AgentProfile.ADMIN,
                        true
                );
        if (approvalBlock.isPresent() && approvalBlock.get().blocked()) {
            result = new LinkedHashMap<>();
            result.put("status", "ERROR");
            result.put("error", approvalBlock.get().error());
            result.put("hint", approvalBlock.get().hint());
            status = "ERROR";
        } else try {
            Optional<WorkflowService.PublishedWorkflowTool> published =
                    workflowService.findPublishedWorkflowTool(toolName);
            if (published.isPresent()) {
                result = invokePublishedWorkflow(published.get(), args, authentication);
            } else {
                result = toolRegistry.execute(toolName, args, context);
            }
            status = String.valueOf(result.getOrDefault("status", "OK"));
            if ("import_package".equals(toolName) && "OK".equals(status)) {
                String packageId = packageIdFromArgs(args);
                if (packageId != null) {
                    runState.clearBundleValidated(packageId);
                }
            }
        } catch (Exception ex) {
            result = Map.of("status", "ERROR", "error", ex.getMessage());
            status = "ERROR";
        }

        session.ifPresent(sessionStore::persistState);

        String auditSessionId = session.map(AgentSession::sessionId)
                .orElseGet(() -> connectionStateStore.agentSessionId(connectionKey).orElse(
                        sessionId != null ? sessionId : "mcp"
                ));

        auditService.record(
                "mcp_" + toolName,
                auditSessionId,
                actor,
                writeJson(args),
                status,
                "mcp",
                "n/a",
                contextPackService.contextPackVersion(),
                "ERROR".equals(status) ? List.of(String.valueOf(result.get("error"))) : List.of()
        );

        boolean isError = "ERROR".equals(status);
        return Map.of(
                "content", List.of(Map.of(
                        "type", "text",
                        "text", writeJson(result)
                )),
                "isError", isError
        );
    }

    private Map<String, Object> executeMetaTool(
            String toolName,
            Map<String, Object> arguments,
            String actor,
            String connectionKey
    ) {
        Map<String, Object> args = arguments != null ? arguments : Map.of();
        if (MCP_CREATE_AGENT_SESSION.equals(toolName)) {
            String rootPath = args.get("rootPath") != null ? String.valueOf(args.get("rootPath")) : null;
            AgentRunState connectionState = connectionStateStore.runStateFor(connectionKey);
            AgentSession session = sessionStore.create(actor, rootPath);
            session.runState().restore(objectMapper, connectionState.snapshot(objectMapper));
            Object modeRaw = args.get("interactionMode");
            if (modeRaw != null && !String.valueOf(modeRaw).isBlank()) {
                AgentInteractionMode mode = AgentInteractionMode.fromString(String.valueOf(modeRaw));
                if (aiProperties.isAgentRequireApprovalForMutate()
                        && mode == AgentInteractionMode.EXECUTE) {
                    return Map.of(
                            "status", "ERROR",
                            "error",
                            "interactionMode EXECUTE is not allowed over MCP when "
                                    + "ispf.ai.agent-require-approval-for-mutate is enabled",
                            "hint",
                            "Use PLAN mode and obtain human plan approval before calling mutate tools."
                    );
                }
                session.runState().setInteractionMode(mode);
            }
            connectionStateStore.bindAgentSession(connectionKey, session.sessionId());
            sessionStore.persistState(session);
            return Map.of(
                    "status", "OK",
                    "sessionId", session.sessionId(),
                    "rootPath", session.rootPath(),
                    "planState", session.runState().planStateSummary()
            );
        }
        if (MCP_APPROVE_MUTATE_PLAN.equals(toolName)) {
            if (aiProperties.isAgentRequireApprovalForMutate()
                    && !mcpProperties.isAllowInbandMutateApproval()) {
                ResolvedRunState resolved = resolveRunState(connectionKey, null, actor);
                AgentRunState runState = resolved.runState();
                if (!hasApprovablePlan(runState)) {
                    return Map.of(
                            "status", "ERROR",
                            "error",
                            "In-band MCP mutate approval is disabled (ispf.mcp.allow-inband-mutate-approval=false)",
                            "hint",
                            "Approve the plan in the AI Studio UI, or enable ispf.mcp.allow-inband-mutate-approval "
                                    + "only in non-production environments."
                    );
                }
                resolved.runState().approvePlan(actor);
                resolved.session().ifPresent(sessionStore::persistState);
                Map<String, Object> ok = new LinkedHashMap<>();
                ok.put("status", "OK");
                ok.put("planApproved", resolved.runState().isPlanApproved());
                ok.put("planState", resolved.runState().planStateSummary());
                Object note = args.get("note");
                if (note != null && !String.valueOf(note).isBlank()) {
                    ok.put("note", String.valueOf(note));
                }
                return ok;
            }
            ResolvedRunState resolved = resolveRunState(connectionKey, null, actor);
            resolved.runState().approvePlan(actor);
            resolved.session().ifPresent(sessionStore::persistState);
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("status", "OK");
            ok.put("planApproved", resolved.runState().isPlanApproved());
            ok.put("planState", resolved.runState().planStateSummary());
            Object note = args.get("note");
            if (note != null && !String.valueOf(note).isBlank()) {
                ok.put("note", String.valueOf(note));
            }
            return ok;
        }
        return Map.of("status", "ERROR", "error", "Unknown MCP meta tool: " + toolName);
    }

    private ResolvedRunState resolveRunState(String connectionKey, String sessionIdArg, String actor) {
        Optional<String> boundSessionId = connectionStateStore.agentSessionId(connectionKey);
        String effectiveSessionId = sessionIdArg != null && !sessionIdArg.isBlank()
                ? sessionIdArg.trim()
                : boundSessionId.orElse(null);
        if (effectiveSessionId != null) {
            Optional<AgentSession> session = sessionStore.get(effectiveSessionId, actor);
            if (session.isPresent()) {
                return new ResolvedRunState(session.get().runState(), session);
            }
        }
        return new ResolvedRunState(connectionStateStore.runStateFor(connectionKey), Optional.empty());
    }

    private record ResolvedRunState(AgentRunState runState, Optional<AgentSession> session) {
    }

    private static Map<String, Object> mcpMetaTool(String name, String description, Map<String, Object> inputSchema) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("inputSchema", inputSchema);
        if (MCP_APPROVE_MUTATE_PLAN.equals(name)) {
            tool.put("annotations", Map.of("destructiveHint", true));
        }
        return tool;
    }

    private static boolean hasApprovablePlan(AgentRunState runState) {
        if (runState == null) {
            return false;
        }
        if (runState.planPhase() == AgentPlanPhase.AWAITING_APPROVAL
                || runState.planPhase() == AgentPlanPhase.APPROVED
                || runState.planPhase() == AgentPlanPhase.PLANNING) {
            return !runState.storedPlan().isEmpty();
        }
        return runState.isPlanningActive() && !runState.storedPlan().isEmpty();
    }

    private static void maybePutDestructiveHint(Map<String, Object> tool, String toolName) {
        Optional<AgentMutateApprovalGuard.BlockDecision> wouldBlock =
                AgentMutateApprovalGuard.checkBeforeTool(true, new AgentRunState(), toolName, AgentProfile.ADMIN);
        if (wouldBlock.isPresent() && wouldBlock.get().blocked()) {
            tool.put("annotations", Map.of("destructiveHint", true));
        }
    }

    private static String packageIdFromArgs(Map<String, Object> args) {
        if (args == null) {
            return null;
        }
        Object packageId = args.get("packageId");
        if (packageId == null || String.valueOf(packageId).isBlank()) {
            packageId = args.get("appId");
        }
        if (packageId == null || String.valueOf(packageId).isBlank()) {
            return null;
        }
        return String.valueOf(packageId).trim();
    }

    private static Map<String, Object> wrapMetaResult(Map<String, Object> result, ObjectMapper objectMapper) {
        boolean isError = "ERROR".equals(String.valueOf(result.get("status")));
        String text;
        try {
            text = objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            text = String.valueOf(result);
        }
        return Map.of(
                "content", List.of(Map.of("type", "text", "text", text)),
                "isError", isError
        );
    }

    private Map<String, Object> invokePublishedWorkflow(
            WorkflowService.PublishedWorkflowTool published,
            Map<String, Object> args,
            Authentication authentication
    ) throws Exception {
        try {
            objectAccessService.requireMemberInvoke(
                    published.path(),
                    "workflow",
                    published.path(),
                    List.of(),
                    authentication
            );
        } catch (Exception ex) {
            return Map.of("status", "ERROR", "error", ex.getMessage());
        }
        Map<String, String> input = new LinkedHashMap<>();
        Object nested = args.get("input");
        if (nested instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k != null) {
                    input.put(k.toString(), v == null ? "" : v.toString());
                }
            });
        } else {
            args.forEach((k, v) -> {
                if (!"path".equals(k) && !"sessionId".equals(k)) {
                    input.put(k, v == null ? "" : v.toString());
                }
            });
        }
        return workflowService.invokeWorkflowTool(published.path(), input);
    }

    private Object parseInputSchema(String inputSchemaJson) {
        if (inputSchemaJson == null || inputSchemaJson.isBlank() || "{}".equals(inputSchemaJson.trim())) {
            return AgentToolSchemas.openObject("Workflow tool input (no inputSchemaJson on WORKFLOW)");
        }
        try {
            JsonNode node = objectMapper.readTree(inputSchemaJson);
            if (node != null && node.isObject()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> schema = objectMapper.convertValue(node, Map.class);
                if (schema != null && !schema.isEmpty()) {
                    return schema;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return AgentToolSchemas.openObject("Workflow tool input (invalid inputSchemaJson)");
    }

    private String extractSessionId(Map<String, Object> args) {
        Object raw = args.remove("sessionId");
        return raw != null ? raw.toString() : null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

}
