package com.ispf.server.ai.agent;

import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.server.ai.audit.AiToolAuditService;
import com.ispf.server.ai.context.ContextPackService;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import com.ispf.server.ai.validation.BundleValidationResult;
import com.ispf.server.config.AiProperties;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TreeFirstAgentService {

    private final LlmProviderRegistry llmProviderRegistry;
    private final PlatformAgentToolRegistry toolRegistry;
    private final ContextPackService contextPackService;
    private final AiToolAuditService auditService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public TreeFirstAgentService(
            LlmProviderRegistry llmProviderRegistry,
            PlatformAgentToolRegistry toolRegistry,
            ContextPackService contextPackService,
            AiToolAuditService auditService,
            AiProperties aiProperties,
            ObjectMapper objectMapper
    ) {
        this.llmProviderRegistry = llmProviderRegistry;
        this.toolRegistry = toolRegistry;
        this.contextPackService = contextPackService;
        this.auditService = auditService;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> run(String goal, String rootPath, Authentication authentication, String actor)
            throws Exception {
        AgentSession session = AgentSession.create(actor, rootPath);
        return runTurn(session, goal, authentication, actor);
    }

    public Map<String, Object> runTurn(
            AgentSession session,
            String message,
            Authentication authentication,
            String actor
    ) throws Exception {
        ensureLlmAvailable();

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }

        String userMessage = message.trim();
        AgentContext context = new AgentContext(actor, authentication, session.runState());
        List<Map<String, Object>> steps = new ArrayList<>();
        List<LlmMessage> messages = buildMessagesWithHistory(session, userMessage);

        String finishSummary = null;
        Map<String, Object> finishResult = Map.of();
        String finalStatus = BundleValidationResult.ERROR;
        int maxSteps = Math.max(1, aiProperties.getAgentMaxSteps());

        for (int step = 1; step <= maxSteps; step++) {
            LlmResponse response = llmProviderRegistry.complete(new LlmRequest(
                    aiProperties.getModel(),
                    messages,
                    aiProperties.getMaxTokens(),
                    aiProperties.getTemperature()
            ));

            AgentJsonProtocol.AgentAction action = AgentJsonProtocol.parse(objectMapper, response.content());
            if ("finish".equals(action.type())) {
                finishSummary = action.summary();
                finishResult = action.result() != null ? action.result() : Map.of();
                finalStatus = BundleValidationResult.OK;
                steps.add(Map.of(
                        "step", step,
                        "type", "finish",
                        "summary", finishSummary != null ? finishSummary : "",
                        "label", AgentStepHumanizer.label("finish", null, null, null, finishSummary),
                        "result", finishResult
                ));
                auditService.record(
                        "agent_finish",
                        session.sessionId(),
                        actor,
                        userMessage,
                        finalStatus,
                        llmProviderRegistry.activeProvider().providerId(),
                        response.model(),
                        contextPackService.contextPackVersion(),
                        List.of()
                );
                break;
            }

            String toolName = action.toolName();
            Map<String, Object> toolArgs = action.arguments() != null ? action.arguments() : Map.of();
            Map<String, Object> toolResult;
            try {
                toolResult = toolRegistry.execute(toolName, toolArgs, context);
            } catch (Exception ex) {
                toolResult = Map.of("status", "ERROR", "error", ex.getMessage());
            }

            steps.add(Map.of(
                    "step", step,
                    "type", "tool",
                    "tool", toolName,
                    "label", AgentStepHumanizer.label("tool", toolName, toolArgs, toolResult, null),
                    "arguments", toolArgs,
                    "result", toolResult
            ));

            auditService.record(
                    "agent_tool_" + toolName,
                    session.sessionId(),
                    actor,
                    writeJson(toolArgs),
                    String.valueOf(toolResult.getOrDefault("status", "UNKNOWN")),
                    llmProviderRegistry.activeProvider().providerId(),
                    response.model(),
                    contextPackService.contextPackVersion(),
                    toolResult.containsKey("errors") ? (List<String>) toolResult.get("errors") : List.of()
            );

            messages.add(new LlmMessage("assistant", response.content()));
            messages.add(new LlmMessage(
                    "user",
                    "Tool result for " + toolName + ":\n" + writeJson(toolResult)
                            + "\n\nContinue with another tool action or finish when the goal is complete."
            ));
        }

        if (finishSummary == null) {
            finalStatus = BundleValidationResult.ERROR;
            finishSummary = "Agent stopped after " + maxSteps + " steps without finish action";
        }

        AgentTurn turn = AgentTurn.create(userMessage, finishSummary, finalStatus, steps, finishResult);
        session.addTurn(turn);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", finalStatus);
        result.put("sessionId", session.sessionId());
        result.put("turnId", turn.turnId());
        result.put("title", session.title());
        result.put("message", userMessage);
        result.put("rootPath", session.rootPath());
        result.put("steps", steps);
        result.put("summary", finishSummary);
        result.put("result", finishResult);
        result.put("tools", toolRegistry.toolCatalog());
        result.put("provider", llmProviderRegistry.status());
        result.put("contextPackVersion", contextPackService.contextPackVersion());
        return result;
    }

    private List<LlmMessage> buildMessagesWithHistory(AgentSession session, String userMessage) {
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(new LlmMessage("system", buildSystemPrompt(session.rootPath())));

        List<AgentTurn> history = session.turns();
        int maxTurns = Math.max(1, aiProperties.getAgentMaxHistoryTurns());
        int start = Math.max(0, history.size() - maxTurns);
        for (int i = start; i < history.size(); i++) {
            AgentTurn turn = history.get(i);
            messages.add(new LlmMessage("user", turn.userMessage()));
            messages.add(new LlmMessage("assistant", turn.assistantSummary()));
        }
        messages.add(new LlmMessage("user", userMessage));
        return messages;
    }

    private void ensureLlmAvailable() {
        if (!llmProviderRegistry.isGenerationAvailable()) {
            throw new IllegalStateException(
                    "LLM provider is not configured. Set ispf.ai.provider and base-url/model."
            );
        }
    }

    private String buildSystemPrompt(String rootPath) {
        String effectiveRoot = rootPath == null || rootPath.isBlank() ? "root" : rootPath.trim();
        StringBuilder tools = new StringBuilder();
        for (Map<String, Object> tool : toolRegistry.toolCatalog()) {
            tools.append("- ")
                    .append(tool.get("name"))
                    .append(": ")
                    .append(tool.get("description"))
                    .append("\n");
        }
        return """
                You are the ISPF platform agent — a helpful admin copilot for the object tree.
                The user speaks in plain language (often Russian). Your finish summary MUST be in the same language,
                friendly and non-technical: explain what was created/found and where to open it in the UI.
                You may receive prior turns in this chat — use them for follow-up requests (e.g. "add dashboard for that device").
                
                Work step-by-step using platform tools. For devices, drivers, dashboards — use tree tools first.
                Always search_context when unsure about SNMP OIDs, dashboard layout, or bundle fields.
                Default tree root for this run: %s
                
                Reply with ONLY one JSON object per turn:
                {"type":"tool","name":"<tool>","arguments":{...}}
                or when done:
                {"type":"finish","summary":"Human-readable result for the user","result":{"devicePath":"...","dashboardPath":"..."}}
                
                Available tools:
                %s
                
                Playbooks:
                %s
                
                Rules:
                - create_object types: DEVICE, DASHBOARD, CUSTOM, WORKFLOW, REPORT, ALERT, CORRELATOR, ...
                - SNMP device: templateId snmp-agent-v1, driverId snmp, host 127.0.0.1:161 community public
                - set_variable for driverConfigJson, driverPointMappingsJson, dashboard layout/title
                - configure_driver or driver_control start after SNMP mappings are set
                - list_variables to show metrics to the user in finish summary
                - Reuse existing demo paths when present: %s and %s
                - bundle import only after validate_bundle/dry_run_deploy OK
                - Never invent REST paths; use tools only
                """.formatted(
                effectiveRoot,
                tools,
                AgentPlaybooks.snmpLocalhostMonitoring(),
                AgentPlaybooks.SNMP_DEVICE_PATH,
                AgentPlaybooks.SNMP_DASHBOARD_PATH
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
