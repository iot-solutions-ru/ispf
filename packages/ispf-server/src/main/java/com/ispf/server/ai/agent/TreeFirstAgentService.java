package com.ispf.server.ai.agent;

import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.server.ai.audit.AiToolAuditService;
import com.ispf.server.ai.context.ContextPackService;
import com.ispf.server.ai.context.PlatformBriefingService;
import com.ispf.server.ai.llm.LlmProviderRegistry;
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

    private static final int HISTORY_SUMMARY_MAX_LEN = 800;

    private final LlmProviderRegistry llmProviderRegistry;
    private final PlatformAgentToolRegistry toolRegistry;
    private final ContextPackService contextPackService;
    private final PlatformBriefingService platformBriefingService;
    private final AiToolAuditService auditService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final AgentSessionStore sessionStore;
    private final AgentRunCancellationRegistry cancellationRegistry;

    public TreeFirstAgentService(
            LlmProviderRegistry llmProviderRegistry,
            PlatformAgentToolRegistry toolRegistry,
            ContextPackService contextPackService,
            PlatformBriefingService platformBriefingService,
            AiToolAuditService auditService,
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            AgentSessionStore sessionStore,
            AgentRunCancellationRegistry cancellationRegistry
    ) {
        this.llmProviderRegistry = llmProviderRegistry;
        this.toolRegistry = toolRegistry;
        this.contextPackService = contextPackService;
        this.platformBriefingService = platformBriefingService;
        this.auditService = auditService;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.sessionStore = sessionStore;
        this.cancellationRegistry = cancellationRegistry;
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
        session.runState().clearPending();

        String userMessage = message.trim();
        AgentContext context = new AgentContext(actor, authentication, session.runState());
        List<Map<String, Object>> steps = new ArrayList<>();
        List<LlmMessage> messages = buildMessagesWithHistory(session, userMessage);

        int maxStepsTotal = Math.max(1, aiProperties.getAgentMaxSteps());

        String finishSummary = null;
        Map<String, Object> finishResult = Map.of();
        String finalStatus = AgentTurnStatus.ERROR;

        try (AgentRunCancellationRegistry.RunHandle run = cancellationRegistry.start(
                session.sessionId(),
                userMessage
        )) {
            while (steps.size() < maxStepsTotal) {
                if (cancellationRegistry.isCancelled(session.sessionId())) {
                    finalStatus = AgentTurnStatus.CANCELLED;
                    finishSummary = "Выполнение остановлено пользователем после "
                            + steps.size() + " шаг(ов).";
                    break;
                }

                int stepNumber = steps.size() + 1;

                AgentLlmActionResolver.ParseAttempt parsed = AgentLlmActionResolver.resolve(
                        objectMapper,
                        llmProviderRegistry,
                        messages,
                        this::buildAgentLlmRequest,
                        aiProperties.getAgentParseRetries()
                );
                LlmResponse response = parsed.response();
                if (parsed.failed() || parsed.action() == null) {
                    finishSummary = """
                            Не удалось разобрать ответ модели после нескольких попыток. \
                            Попробуйте переформулировать запрос короче или начните новый чат.""";
                    finalStatus = AgentTurnStatus.ERROR;
                    Map<String, Object> errorStep = Map.of(
                            "step", stepNumber,
                            "type", "error",
                            "label", "Ошибка разбора ответа модели",
                            "error", parsed.error() != null ? parsed.error() : "parse failed",
                            "rawPreview", preview(response != null ? response.content() : null)
                    );
                    steps.add(errorStep);
                    publishStep(session.sessionId(), errorStep);
                    auditService.record(
                            "agent_parse_error",
                            session.sessionId(),
                            actor,
                            userMessage,
                            finalStatus,
                            llmProviderRegistry.activeProvider().providerId(),
                            response != null ? response.model() : null,
                            contextPackService.contextPackVersion(),
                            List.of(parsed.error() != null ? parsed.error() : "parse failed")
                    );
                    break;
                }

                AgentJsonProtocol.AgentAction agentAction = parsed.action();
                if ("finish".equals(agentAction.type())) {
                    finishSummary = agentAction.summary();
                    finishResult = agentAction.result() != null ? agentAction.result() : Map.of();
                    finalStatus = AgentTurnStatus.OK;
                    Map<String, Object> finishStep = Map.of(
                            "step", stepNumber,
                            "type", "finish",
                            "summary", finishSummary != null ? finishSummary : "",
                            "label", AgentStepHumanizer.label("finish", null, null, null, finishSummary),
                            "result", finishResult
                    );
                    steps.add(finishStep);
                    publishStep(session.sessionId(), finishStep);
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

                String toolName = agentAction.toolName();
                Map<String, Object> toolArgs = agentAction.arguments() != null ? agentAction.arguments() : Map.of();
                Map<String, Object> toolResult;
                try {
                    toolResult = toolRegistry.execute(toolName, toolArgs, context);
                } catch (Exception ex) {
                    toolResult = Map.of("status", "ERROR", "error", ex.getMessage());
                }

                Map<String, Object> toolStep = Map.of(
                        "step", stepNumber,
                        "type", "tool",
                        "tool", toolName,
                        "label", AgentStepHumanizer.label("tool", toolName, toolArgs, toolResult, null),
                        "arguments", toolArgs,
                        "result", toolResult
                );
                steps.add(toolStep);
                publishStep(session.sessionId(), toolStep);

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
                                + "\n\n" + AgentLoopGuard.continuationHint(toolName, steps, maxStepsTotal)
                ));
            }
        }

        if (finishSummary == null && !AgentTurnStatus.CANCELLED.equals(finalStatus)) {
            finalStatus = AgentTurnStatus.ERROR;
            finishSummary = "Достигнут лимит " + maxStepsTotal
                    + " шагов без завершения задачи.";
        }

        return persistCompletedTurn(session, userMessage, finishSummary, finalStatus, steps, finishResult, actor);
    }

    public Map<String, Object> cancelRun(String sessionId) {
        cancellationRegistry.cancel(sessionId);
        return Map.of("status", "OK", "sessionId", sessionId, "cancelRequested", true);
    }

    public Map<String, Object> runProgress(String sessionId) {
        return cancellationRegistry.progress(sessionId);
    }

    private void publishStep(String sessionId, Map<String, Object> step) {
        cancellationRegistry.recordStep(sessionId, step);
    }

    private Map<String, Object> persistCompletedTurn(
            AgentSession session,
            String userMessage,
            String finishSummary,
            String finalStatus,
            List<Map<String, Object>> steps,
            Map<String, Object> finishResult,
            String actor
    ) {
        AgentTurn turn = AgentTurn.create(userMessage, finishSummary, finalStatus, steps, finishResult);
        session.addTurn(turn);
        sessionStore.persistAfterTurn(session, turn);
        return buildResponse(
                session,
                userMessage,
                finalStatus,
                finishSummary,
                steps,
                finishResult,
                turn.turnId(),
                steps.size(),
                aiProperties.getAgentMaxSteps()
        );
    }

    private Map<String, Object> buildResponse(
            AgentSession session,
            String userMessage,
            String finalStatus,
            String finishSummary,
            List<Map<String, Object>> steps,
            Map<String, Object> finishResult,
            String turnId,
            int stepsCompleted,
            int maxStepsTotal
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", finalStatus);
        result.put("sessionId", session.sessionId());
        result.put("turnId", turnId);
        result.put("title", session.title());
        result.put("message", userMessage);
        result.put("rootPath", session.rootPath());
        result.put("steps", steps);
        result.put("summary", finishSummary);
        result.put("result", finishResult);
        result.put("tools", toolRegistry.toolCatalog());
        result.put("provider", llmProviderRegistry.status());
        result.put("contextPackVersion", contextPackService.contextPackVersion());
        result.put("stepsCompleted", stepsCompleted);
        result.put("maxSteps", maxStepsTotal);
        result.put("running", cancellationRegistry.isRunning(session.sessionId()));
        return result;
    }

    private LlmRequest buildAgentLlmRequest(List<LlmMessage> messages) {
        Map<String, Object> providerOptions = aiProperties.isAgentDisableThinking()
                ? Map.of("chat_template_kwargs", Map.of("enable_thinking", false))
                : Map.of();
        return new LlmRequest(
                aiProperties.getModel(),
                messages,
                aiProperties.getMaxTokens(),
                aiProperties.getTemperature(),
                providerOptions
        );
    }

    private List<LlmMessage> buildMessagesWithHistory(AgentSession session, String userMessage) {
        List<LlmMessage> messages = new ArrayList<>();
        boolean includeStatic = aiProperties.isBriefingEveryTurn() || session.turns().isEmpty();
        String briefing = platformBriefingService.buildBriefing(session.rootPath(), includeStatic);
        messages.add(new LlmMessage("system", AgentPromptBuilder.build(
                session.rootPath(),
                toolRegistry.toolCatalog(),
                briefing
        )));

        List<AgentTurn> history = session.turns();
        int maxTurns = Math.max(1, aiProperties.getAgentMaxHistoryTurns());
        int start = Math.max(0, history.size() - maxTurns);
        for (int i = start; i < history.size(); i++) {
            AgentTurn turn = history.get(i);
            messages.add(new LlmMessage("user", turn.userMessage()));
            messages.add(new LlmMessage("assistant", truncateForHistory(turn.assistantSummary())));
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

    private static String truncateForHistory(String summary) {
        if (summary == null || summary.isBlank()) {
            return "";
        }
        String trimmed = summary.trim();
        if (trimmed.length() <= HISTORY_SUMMARY_MAX_LEN) {
            return trimmed;
        }
        return trimmed.substring(0, HISTORY_SUMMARY_MAX_LEN - 1) + "…";
    }

    private static String preview(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        return trimmed.length() <= 240 ? trimmed : trimmed.substring(0, 237) + "...";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
