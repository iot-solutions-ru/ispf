package com.ispf.server.ai.mcp;

import com.ispf.server.ai.agent.AgentContext;
import com.ispf.server.ai.agent.AgentRunState;
import com.ispf.server.ai.agent.AgentSession;
import com.ispf.server.ai.agent.AgentSessionStore;
import com.ispf.server.ai.agent.PlatformAgentToolRegistry;
import com.ispf.server.ai.audit.AiToolAuditService;
import com.ispf.server.ai.context.ContextPackService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Delegates MCP tool calls to {@link PlatformAgentToolRegistry} (0006).
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
    private final ObjectMapper objectMapper;

    public McpToolAdapter(
            PlatformAgentToolRegistry toolRegistry,
            AgentSessionStore sessionStore,
            AiToolAuditService auditService,
            ContextPackService contextPackService,
            ObjectMapper objectMapper
    ) {
        this.toolRegistry = toolRegistry;
        this.sessionStore = sessionStore;
        this.auditService = auditService;
        this.contextPackService = contextPackService;
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
            result = toolRegistry.execute(toolName, args, context);
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
