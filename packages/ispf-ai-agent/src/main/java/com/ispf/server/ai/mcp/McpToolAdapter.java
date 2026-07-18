package com.ispf.server.ai.mcp;

import com.ispf.server.ai.agent.AgentContext;
import com.ispf.server.ai.agent.AgentRunState;
import com.ispf.server.ai.agent.AgentSession;
import com.ispf.server.ai.agent.AgentSessionStore;
import com.ispf.server.ai.agent.PlatformAgentToolRegistry;
import com.ispf.server.ai.audit.AiToolAuditService;
import com.ispf.server.ai.context.ContextPackService;
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

/**
 * Delegates MCP tool calls to {@link PlatformAgentToolRegistry} and publishes ACTIVE workflows
 * with {@code toolDescription} as dynamic tools (ADR-0049).
 */
@Service
@ConditionalOnProperty(prefix = "ispf.mcp", name = "enabled", havingValue = "true")
public class McpToolAdapter {

    private static final Map<String, Object> GENERIC_INPUT_SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", true
    );

    private final PlatformAgentToolRegistry toolRegistry;
    private final AgentSessionStore sessionStore;
    private final AiToolAuditService auditService;
    private final ContextPackService contextPackService;
    private final WorkflowService workflowService;
    private final ObjectMapper objectMapper;

    public McpToolAdapter(
            PlatformAgentToolRegistry toolRegistry,
            AgentSessionStore sessionStore,
            AiToolAuditService auditService,
            ContextPackService contextPackService,
            WorkflowService workflowService,
            ObjectMapper objectMapper
    ) {
        this.toolRegistry = toolRegistry;
        this.sessionStore = sessionStore;
        this.auditService = auditService;
        this.contextPackService = contextPackService;
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (Map<String, Object> entry : toolRegistry.toolCatalog()) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", entry.get("name"));
            tool.put("description", entry.get("description"));
            tool.put("inputSchema", GENERIC_INPUT_SCHEMA);
            tools.add(tool);
        }
        for (WorkflowService.PublishedWorkflowTool published : workflowService.listPublishedWorkflowTools()) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", published.toolName());
            tool.put("description", published.description() + " [workflow:" + published.path() + "]");
            tool.put("inputSchema", parseInputSchema(published.inputSchemaJson()));
            tools.add(tool);
        }
        return tools;
    }

    public Map<String, Object> callTool(
            String toolName,
            Map<String, Object> arguments,
            Authentication authentication,
            String actor
    ) throws Exception {
        Map<String, Object> args = arguments != null ? new LinkedHashMap<>(arguments) : new LinkedHashMap<>();
        String sessionId = extractSessionId(args);
        AgentRunState runState = new AgentRunState();
        Optional<AgentSession> session = Optional.empty();
        if (sessionId != null && !sessionId.isBlank()) {
            session = sessionStore.get(sessionId.trim(), actor);
            if (session.isPresent()) {
                runState = session.get().runState();
            }
        }

        AgentContext context = new AgentContext(actor, authentication, runState);
        Map<String, Object> result;
        String status;
        try {
            Optional<WorkflowService.PublishedWorkflowTool> published =
                    workflowService.findPublishedWorkflowTool(toolName);
            if (published.isPresent()) {
                result = invokePublishedWorkflow(published.get(), args);
            } else {
                result = toolRegistry.execute(toolName, args, context);
            }
            status = String.valueOf(result.getOrDefault("status", "OK"));
        } catch (Exception ex) {
            result = Map.of("status", "ERROR", "error", ex.getMessage());
            status = "ERROR";
        }

        session.ifPresent(sessionStore::persistState);

        auditService.record(
                "mcp_" + toolName,
                sessionId != null ? sessionId : "mcp",
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

    private Map<String, Object> invokePublishedWorkflow(
            WorkflowService.PublishedWorkflowTool published,
            Map<String, Object> args
    ) throws Exception {
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
            return GENERIC_INPUT_SCHEMA;
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
        return GENERIC_INPUT_SCHEMA;
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
