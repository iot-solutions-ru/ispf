package com.ispf.server.ai.agent;

import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.ai.LlmToolSpec;
import com.ispf.server.ai.audit.AgentAuditMetrics;
import com.ispf.server.ai.audit.AiToolAuditService;
import com.ispf.server.ai.context.ContextPackService;
import com.ispf.server.ai.context.PlatformBriefingService;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import com.ispf.server.config.AiProperties;
import com.ispf.server.object.ObjectTreePort;
import com.ispf.server.operator.OperatorAgentMemoryLearner;
import com.ispf.server.operator.OperatorAgentMemoryService;
import com.ispf.server.operator.OperatorAgentResultEnricher;
import com.ispf.server.operator.OperatorAppDocumentService;
import com.ispf.server.operator.OperatorAppUiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TreeFirstAgentService {

    private static final Logger log = LoggerFactory.getLogger(TreeFirstAgentService.class);

    private final ExecutorService agentRunExecutor;

    private final LlmProviderRegistry llmProviderRegistry;
    private final PlatformAgentToolRegistry toolRegistry;
    private final ContextPackService contextPackService;
    private final PlatformBriefingService platformBriefingService;
    private final AiToolAuditService auditService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final AgentSessionStore sessionStore;
    private final AgentRunCancellationRegistry cancellationRegistry;
    private final OperatorAgentScopeService operatorScopeService;
    private final OperatorAgentMemoryService operatorMemoryService;
    private final OperatorAgentMemoryLearner operatorMemoryLearner;
    private final OperatorAppDocumentService operatorDocumentService;
    private final OperatorAppUiService operatorAppUiService;
    private final OperatorAgentResultEnricher operatorResultEnricher;
    private final AgentAttachmentValidator attachmentValidator;
    private final AgentTurnRateLimiter turnRateLimiter;
    private final AgentMetricsRecorder agentMetrics;
    private final AgentSessionDocumentService sessionDocumentService;
    private final ObjectTreePort ObjectTreePort;
    private final AgentPromptAssembler promptAssembler;

    public TreeFirstAgentService(
            LlmProviderRegistry llmProviderRegistry,
            PlatformAgentToolRegistry toolRegistry,
            ContextPackService contextPackService,
            PlatformBriefingService platformBriefingService,
            AiToolAuditService auditService,
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            AgentSessionStore sessionStore,
            AgentRunCancellationRegistry cancellationRegistry,
            OperatorAgentScopeService operatorScopeService,
            OperatorAgentMemoryService operatorMemoryService,
            OperatorAgentMemoryLearner operatorMemoryLearner,
            OperatorAppDocumentService operatorDocumentService,
            OperatorAppUiService operatorAppUiService,
            OperatorAgentResultEnricher operatorResultEnricher,
            AgentAttachmentValidator attachmentValidator,
            AgentTurnRateLimiter turnRateLimiter,
            AgentMetricsRecorder agentMetrics,
            AgentSessionDocumentService sessionDocumentService,
            ObjectTreePort ObjectTreePort
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
        this.operatorScopeService = operatorScopeService;
        this.operatorMemoryService = operatorMemoryService;
        this.operatorMemoryLearner = operatorMemoryLearner;
        this.operatorDocumentService = operatorDocumentService;
        this.operatorAppUiService = operatorAppUiService;
        this.operatorResultEnricher = operatorResultEnricher;
        this.attachmentValidator = attachmentValidator;
        this.turnRateLimiter = turnRateLimiter;
        this.agentMetrics = agentMetrics;
        this.sessionDocumentService = sessionDocumentService;
        this.ObjectTreePort = ObjectTreePort;
        this.promptAssembler = new AgentPromptAssembler(
                aiProperties,
                platformBriefingService,
                toolRegistry,
                operatorMemoryService,
                operatorDocumentService,
                operatorAppUiService,
                sessionDocumentService
        );
        this.agentRunExecutor = new DelegatingSecurityContextExecutorService(
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    public void submitRunTurn(
            AgentSession session,
            String message,
            List<AgentAttachmentValidator.AttachmentInput> attachments,
            Authentication authentication,
            String actor,
            String interactionMode
    ) {
        String sessionId = session.sessionId();
        cancellationRegistry.clearStaleRun(sessionId);
        if (cancellationRegistry.isRunning(sessionId)) {
            throw new IllegalStateException("Agent run already in progress for session: " + sessionId);
        }
        AgentRunCancellationRegistry.RunHandle reserved = cancellationRegistry.start(
                sessionId,
                message != null && !message.isBlank() ? message : "(attachment)",
                session.runState().planStateSummary()
        );
        try {
            turnRateLimiter.acquire(actor);
            agentMetrics.recordTurnStarted();
        } catch (AgentTurnRateLimiter.AgentRateLimitException ex) {
            agentMetrics.recordRateLimited();
            reserved.close();
            throw ex;
        }
        agentRunExecutor.execute(() -> {
            try {
                AgentSession live = sessionStore.require(sessionId, actor)
                        .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
                runTurn(live, message, attachments, authentication, actor, interactionMode);
            } catch (Exception ex) {
                log.warn("Async agent turn failed for session {}: {}", sessionId, ex.getMessage(), ex);
                persistAsyncTurnFailure(sessionId, actor, message, attachments, ex);
            } finally {
                turnRateLimiter.release(actor);
                reserved.close();
            }
        });
    }

    public void submitOperatorTurn(
            AgentSession session,
            String appId,
            String message,
            Authentication authentication,
            String actor
    ) {
        String sessionId = session.sessionId();
        cancellationRegistry.clearStaleRun(sessionId);
        if (cancellationRegistry.isRunning(sessionId)) {
            throw new IllegalStateException("Agent run already in progress for session: " + sessionId);
        }
        try {
            turnRateLimiter.acquire(actor);
            agentMetrics.recordTurnStarted();
        } catch (AgentTurnRateLimiter.AgentRateLimitException ex) {
            agentMetrics.recordRateLimited();
            throw ex;
        }
        agentRunExecutor.execute(() -> {
            try {
                AgentSession live = sessionStore.require(sessionId, actor)
                        .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
                runOperatorTurn(live, appId, message, authentication, actor);
            } catch (Exception ex) {
                log.warn("Async operator agent turn failed for session {}: {}", sessionId, ex.getMessage(), ex);
                persistAsyncTurnFailure(sessionId, actor, message, List.of(), ex);
            } finally {
                turnRateLimiter.release(actor);
            }
        });
    }

    public Map<String, Object> run(String goal, String rootPath, Authentication authentication, String actor)
            throws Exception {
        AgentSession session = sessionStore.create(actor, rootPath);
        return runTurn(session, goal, authentication, actor);
    }

    public Map<String, Object> runTurn(
            AgentSession session,
            String message,
            Authentication authentication,
            String actor
    ) throws Exception {
        return runTurn(session, message, authentication, actor, null);
    }

    public Map<String, Object> runTurn(
            AgentSession session,
            String message,
            Authentication authentication,
            String actor,
            String interactionMode
    ) throws Exception {
        return runTurn(session, message, List.of(), authentication, actor, interactionMode);
    }

    public Map<String, Object> runTurn(
            AgentSession session,
            String message,
            List<AgentAttachmentValidator.AttachmentInput> attachments,
            Authentication authentication,
            String actor,
            String interactionMode
    ) throws Exception {
        ensureLlmAvailable();
        String sessionId = session.sessionId();
        boolean runPreReserved = cancellationRegistry.isRunning(sessionId);
        boolean acquiredRateLimit = false;
        if (!runPreReserved) {
            try {
                turnRateLimiter.acquire(actor);
                acquiredRateLimit = true;
                agentMetrics.recordTurnStarted();
            } catch (AgentTurnRateLimiter.AgentRateLimitException ex) {
                agentMetrics.recordRateLimited();
                throw ex;
            }
        }
        AgentRunCancellationRegistry.RunHandle localHandle = null;
        try {
            if (!runPreReserved) {
                localHandle = cancellationRegistry.start(
                        sessionId,
                        message != null && !message.isBlank() ? message : "(attachment)",
                        session.runState().planStateSummary()
                );
            } else {
                cancellationRegistry.touch(sessionId);
            }

            publishStep(sessionId, Map.of(
                    "step", 0,
                    "type", "status",
                    "label", "Подготовка запроса…",
                    "result", Map.of("status", "RUNNING")
            ));

            AgentAttachmentValidator.PreparedUserMessage prepared =
                    attachmentValidator.prepare(message, attachments);
            if (prepared.llmText().isBlank() && prepared.imageParts().isEmpty()) {
                throw new IllegalArgumentException("message is required");
            }
            session.runState().clearPending();

            String userMessage = prepared.displayMessage();
            String llmUserText = prepared.llmText();
            cancellationRegistry.updateUserMessage(sessionId, userMessage);
            AgentProfile profile = session.runState().agentProfile();
            if (interactionMode != null && !interactionMode.isBlank()) {
                session.runState().setInteractionMode(AgentInteractionMode.fromString(interactionMode));
            }
            // Admin Copilot is its own helper — never inherit Studio Ask/Plan mode machinery or hints.
            if ("copilot".equalsIgnoreCase(session.runState().clientChannel())) {
                session.runState().setInteractionMode(AgentInteractionMode.EXECUTE);
                session.runState().resetPlan();
                session.runState().unlockMutationsForTurn();
            }
            final boolean askMode = profile != AgentProfile.OPERATOR
                    && session.runState().interactionMode() == AgentInteractionMode.ASK
                    && !"copilot".equalsIgnoreCase(session.runState().clientChannel());
            final boolean copilotChannel = "copilot".equalsIgnoreCase(session.runState().clientChannel());
            final String turnId = UUID.randomUUID().toString();
            session.runState().setPlanDepth(AgentPlanDepth.resolve(
                    llmUserText,
                    AgentPromptAssembler.hasTextAttachment(prepared.attachmentMetadata()),
                    sessionDocumentService.count(sessionId) > 0
            ));
            boolean planApprovedThisTurn = AgentPlanGuard.beginTurn(
                    session.runState(),
                    llmUserText,
                    profile,
                    aiProperties.isAgentRequireApprovalForMutate(),
                    actor
            );
            if (profile != AgentProfile.OPERATOR && !copilotChannel) {
                AgentToolSurface.beginTurn(
                        session.runState(),
                        session.runState().interactionMode(),
                        llmUserText
                );
            } else {
                session.runState().clearActiveToolPacks();
            }
            if (planApprovedThisTurn) {
                recordTurnAudit(
                        session,
                        turnId,
                        0,
                        "agent_plan_approved",
                        actor,
                        userMessage,
                        "OK",
                        null,
                        List.of(),
                        null,
                        null,
                        profile
                );
            }
            OperatorAgentScope operatorScope = resolveOperatorScope(session, profile);
            AgentContext context = new AgentContext(
                    actor,
                    authentication,
                    session.runState(),
                    profile,
                    operatorScope,
                    sessionId
            );
            List<Map<String, Object>> steps = new ArrayList<>();
            List<LlmMessage> messages = promptAssembler.buildMessagesWithHistory(
                    session,
                    prepared,
                    profile,
                    operatorScope
            );

            List<Map<String, Object>> attachmentMetadata = prepared.attachmentMetadata();

            int maxStepsTotal = Math.max(1, aiProperties.getAgentMaxSteps());

            String finishSummary = null;
            Map<String, Object> finishResult = Map.of();
            String finalStatus = AgentTurnStatus.ERROR;

            while (steps.size() < maxStepsTotal) {
                if (cancellationRegistry.isCancelled(session.sessionId())) {
                    finalStatus = AgentTurnStatus.CANCELLED;
                    finishSummary = "Выполнение остановлено пользователем после "
                            + steps.size() + " шаг(ов).";
                    break;
                }

                int stepNumber = steps.size() + 1;

                boolean hasTextAttachment = AgentPromptAssembler.hasTextAttachment(attachmentMetadata);
                cancellationRegistry.touch(session.sessionId());
                AgentLlmActionResolver.ParseAttempt parsed = AgentLlmActionResolver.resolve(
                        objectMapper,
                        llmProviderRegistry,
                        messages,
                        requestMessages -> buildAgentLlmRequest(requestMessages, session.runState(), profile),
                        aiProperties.getAgentParseRetries(),
                        profile == AgentProfile.OPERATOR,
                        new AgentLlmActionResolver.ResolveContext(
                                hasTextAttachment,
                                !askMode && session.runState().isPlanningActive(),
                                session.runState().isPlanApproved(),
                                askMode,
                                AgentPhasedPlanIntake.resolveStage(session.runState())
                        )
                );
                LlmResponse response = parsed.response();
                recordTurnAudit(
                        session,
                        turnId,
                        stepNumber,
                        "agent_llm_round",
                        actor,
                        userMessage,
                        parsed.failed() ? "ERROR" : "OK",
                        response != null ? response.model() : null,
                        parsed.failed() && parsed.error() != null ? List.of(parsed.error()) : List.of(),
                        parsed.llmLatencyMs(),
                        response,
                        profile
                );
                if (parsed.failed() || parsed.action() == null) {
                    boolean truncated = response != null
                            && (response.truncatedByLength()
                            || AgentJsonProtocol.looksLikeTruncatedContent(response.content()));
                    finishSummary = truncated
                            ? """
                            Ответ модели обрезан — план слишком большой для одного сообщения. \
                            Частичный план сохранён, если удалось извлечь данные. \
                            Напишите «продолжи план» или «добавь следующие разделы» — план достраивается поэтапно."""
                            : """
                            Не удалось разобрать ответ модели после нескольких попыток. \
                            Попробуйте переформулировать запрос короче или начните новый чат.""";
                    finalStatus = AgentTurnStatus.ERROR;
                    Map<String, Object> errorStep = new LinkedHashMap<>();
                    errorStep.put("step", stepNumber);
                    errorStep.put("type", "error");
                    errorStep.put("label", truncated ? "Ответ обрезан" : "Ошибка разбора ответа модели");
                    errorStep.put("error", parsed.error() != null ? parsed.error() : "parse failed");
                    errorStep.put("rawPreview", preview(response != null ? response.content() : null));
                    if (truncated) {
                        errorStep.put("truncated", true);
                    }
                    steps.add(errorStep);
                    publishStep(session.sessionId(), errorStep);
                    recordTurnAudit(
                            session,
                            turnId,
                            stepNumber,
                            "agent_parse_error",
                            actor,
                            userMessage,
                            finalStatus,
                            response != null ? response.model() : null,
                            List.of(parsed.error() != null ? parsed.error() : "parse failed"),
                            parsed.llmLatencyMs(),
                            response,
                            profile
                    );
                    break;
                }

                AgentJsonProtocol.AgentAction agentAction = parsed.action();
                if ("finish".equals(agentAction.type())) {
                    Map<String, Object> candidateResult = agentAction.result() != null
                            ? new LinkedHashMap<>(agentAction.result())
                            : new LinkedHashMap<>();
                    if (copilotChannel) {
                        Optional<String> focusReject = AgentCopilotFocusGuard.rejectClarifyFinish(
                                session.runState().clientChannel(),
                                session.runState().clientFocus(),
                                agentAction.summary()
                        );
                        if (focusReject.isPresent()
                                && !AgentCopilotFocusGuard.alreadyNudged(steps)) {
                            Map<String, Object> guardStep = Map.of(
                                    "step", stepNumber,
                                    "type", "guard",
                                    "label", "Copilot UI focus",
                                    "error", "Finish ignored live UI focus",
                                    "hint", focusReject.get(),
                                    "copilotFocusNudge", true
                            );
                            steps.add(guardStep);
                            publishStep(session.sessionId(), guardStep);
                            messages.add(new LlmMessage("assistant", response.content()));
                            messages.add(new LlmMessage("user", focusReject.get()));
                            continue;
                        }
                    }
                    if (askMode) {
                        AgentPlanGuard.normalizeFinishForAskMode(candidateResult);
                        finishSummary = agentAction.summary();
                        finishResult = candidateResult;
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
                        recordTurnAudit(
                                session,
                                turnId,
                                stepNumber,
                                "agent_finish",
                                actor,
                                userMessage,
                                finalStatus,
                                response.model(),
                                List.of(),
                                parsed.llmLatencyMs(),
                                response,
                                profile
                        );
                        break;
                    }
                    if (copilotChannel) {
                        // Screen helper finish — no Studio plan/approval UX.
                        AgentPlanGuard.normalizeFinishForAskMode(candidateResult);
                        finishSummary = agentAction.summary();
                        finishResult = candidateResult;
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
                        recordTurnAudit(
                                session,
                                turnId,
                                stepNumber,
                                "agent_finish",
                                actor,
                                userMessage,
                                finalStatus,
                                response.model(),
                                List.of(),
                                parsed.llmLatencyMs(),
                                response,
                                profile
                        );
                        break;
                    }
                    AcceptanceVerdict.Result executionAcceptance = null;
                    if (profile != AgentProfile.OPERATOR) {
                        AgentPlanGuard.FinishOutcome planOutcome = AgentPlanGuard.evaluateFinish(
                                session.runState(),
                                candidateResult,
                                steps,
                                llmUserText,
                                profile
                        );
                        if (planOutcome == AgentPlanGuard.FinishOutcome.BLOCK_NEEDS_PLAN
                                || planOutcome == AgentPlanGuard.FinishOutcome.BLOCK_NEEDS_APPROVAL) {
                            boolean executeMode = session.runState().interactionMode() == AgentInteractionMode.EXECUTE;
                            String guardLabel = planOutcome == AgentPlanGuard.FinishOutcome.BLOCK_NEEDS_APPROVAL
                                    ? "Требуется утверждение плана"
                                    : askMode
                                            ? "Режим «Спросить»"
                                            : executeMode
                                                    ? "Режим «Выполнить»"
                                                    : runStateAntiReplanHint(session.runState(), candidateResult)
                                                            ? "Выполнение плана"
                                                            : "Требуется план";
                            String guardError = planOutcome == AgentPlanGuard.FinishOutcome.BLOCK_NEEDS_APPROVAL
                                    ? "Execution finish blocked: plan not approved yet."
                                    : askMode
                                            ? "Ask mode: finish with a plain answer only — no phase=plan, plan, questions, or approval."
                                            : executeMode
                                                    ? "Execute mode: act with tools — no phase=plan, plan, or clarifying questions."
                                                    : runStateAntiReplanHint(session.runState(), candidateResult)
                                                            ? "Execution phase: do not emit phase=plan — execute the next approved step."
                                                            : AgentLitePlanBootstrap.PLANNING_GUARD_ERROR;
                            if (planOutcome == AgentPlanGuard.FinishOutcome.BLOCK_NEEDS_PLAN
                                    && AgentLitePlanBootstrap.shouldRecoverFromPlanningGuardLoop(steps, guardError)) {
                                var recovered = AgentLitePlanBootstrap.resolveFinishPlan(llmUserText, session.runState());
                                if (recovered.isPresent()) {
                                    finishResult = new LinkedHashMap<>(recovered.get());
                                    AgentPlanGuard.capturePlan(session.runState(), finishResult);
                                    syncRunPlanState(session);
                                    finishSummary = "Подготовлен эталонный LITE-план — утвердите и начнём выполнение.";
                                    finalStatus = AgentTurnStatus.OK;
                                    Map<String, Object> finishStep = Map.of(
                                            "step", stepNumber,
                                            "type", "finish",
                                            "summary", finishSummary,
                                            "label", AgentStepHumanizer.label("finish", null, null, null, finishSummary),
                                            "result", finishResult,
                                            "litePlanRecovered", true
                                    );
                                    steps.add(finishStep);
                                    publishStep(session.sessionId(), finishStep);
                                    recordTurnAudit(
                                            session,
                                            turnId,
                                            stepNumber,
                                            "agent_finish",
                                            actor,
                                            userMessage,
                                            finalStatus,
                                            response.model(),
                                            List.of(),
                                            parsed.llmLatencyMs(),
                                            response,
                                            profile
                                    );
                                    break;
                                }
                            }
                            String guardHint = planOutcome == AgentPlanGuard.FinishOutcome.BLOCK_NEEDS_APPROVAL
                                    ? "Wait for user approval (e.g. «Да, начинаем») before finishing with devicePath/dashboardPath."
                                    : askMode
                                            ? "Use read-only discovery tools, then finish with summary only "
                                                    + "(optional read-only suggestions — no plan panel)."
                                            : executeMode
                                                    ? "Run discovery if needed, mutate with tools, then finish with summary "
                                                            + "(optional open-device/open-dashboard suggestions)."
                                                    : runStateAntiReplanHint(session.runState(), candidateResult)
                                                            ? "Follow the approved plan checklist step-by-step with tools."
                                                            : "Run discovery tools, then finish with result.phase=plan, "
                                                                    + "result.plan, result.questions, result.suggestions "
                                                                    + "(include primary: «Утвердить полный план»).";
                            Map<String, Object> guardStep = Map.of(
                                    "step", stepNumber,
                                    "type", "guard",
                                    "label", guardLabel,
                                    "error", guardError,
                                    "hint", guardHint
                            );
                            steps.add(guardStep);
                            publishStep(session.sessionId(), guardStep);
                            messages.add(new LlmMessage("assistant", response.content()));
                            messages.add(new LlmMessage(
                                    "user",
                                    askMode
                                            ? "Ask mode (read-only): do NOT emit phase=plan, result.plan, "
                                                    + "result.questions, or approval suggestions. "
                                                    + "Run list_*/get_*/search_* if needed, then finish with a plain summary."
                                            : executeMode
                                                    ? "Execute mode: do NOT emit phase=plan, result.plan, or result.questions. "
                                                            + "Use tools to complete the task, then finish with a plain summary."
                                            : "Finish blocked during planning phase. "
                                                    + "Do NOT claim objects were created. "
                                                    + "Use read-only tools if needed, then finish with phase=plan, "
                                                    + "result.plan (goal + steps[]), result.questions, result.suggestions. "
                                                    + "Keep summary to 1–3 sentences — the UI plan panel reads result.plan, "
                                                    + "not summary prose."
                            ));
                            continue;
                        }
                        if (planOutcome == AgentPlanGuard.FinishOutcome.ALLOW_PLAN
                                && AgentPlanGuard.shouldCapturePlan(session.runState(), candidateResult)) {
                            AgentPlanFinishNormalizer.applyPhasedPolicy(candidateResult, session.runState());
                            AgentSpecPlanValidator.ValidationResult specValidation =
                                    AgentSpecPlanValidator.validatePlanFinish(
                                            candidateResult, userMessage, steps, session.runState());
                            if (!specValidation.ok()) {
                                Map<String, Object> guardStep = Map.of(
                                        "step", stepNumber,
                                        "type", "guard",
                                        "label", "Проверка SIF-плана",
                                        "error", "Plan validation failed",
                                        "hint", AgentSpecPlanValidator.formatValidationHint(specValidation)
                                );
                                steps.add(guardStep);
                                publishStep(session.sessionId(), guardStep);
                                messages.add(new LlmMessage("assistant", response.content()));
                                messages.add(new LlmMessage(
                                        "user",
                                        "Plan validation failed:\n"
                                                + AgentSpecPlanValidator.formatValidationHint(specValidation)
                                                + "\n\nFix the plan finish payload and retry."
                                ));
                                continue;
                            }
                            captureHandoffMetadata(session.runState(), candidateResult);
                            AgentPlanGuard.capturePlan(session.runState(), candidateResult);
                            syncRunPlanState(session);
                        }
                        if (planOutcome == AgentPlanGuard.FinishOutcome.ALLOW_EXECUTION) {
                            executionAcceptance = AcceptanceVerdict.evaluate(
                                    steps,
                                    userMessage,
                                    session.runState().assignmentType()
                            );
                            var block = AgentPlatformTurnGuard.blockIfNeeded(executionAcceptance);
                            if (block.isPresent()) {
                                AgentPlatformTurnGuard.BlockDecision decision = block.get();
                                if (AgentPlatformTurnGuard.isStuckGuardLoop(steps, decision.error())) {
                                    finishSummary = "Проверка завершения повторялась — объекты, вероятно, уже созданы. "
                                            + "Проверьте платформу вручную и при необходимости продолжите в новом сообщении.";
                                    finishResult = new LinkedHashMap<>();
                                    finishResult.put("interactive", true);
                                    finishResult.put("platformGuardStuck", true);
                                    finishResult.put("guardError", decision.error());
                                    finalStatus = AgentTurnStatus.OK;
                                    Map<String, Object> finishStep = Map.of(
                                            "step", stepNumber,
                                            "type", "finish",
                                            "summary", finishSummary,
                                            "label", AgentStepHumanizer.label("finish", null, null, null, finishSummary),
                                            "result", finishResult,
                                            "platformGuardStuck", true
                                    );
                                    steps.add(finishStep);
                                    publishStep(session.sessionId(), finishStep);
                                    break;
                                }
                                Map<String, Object> guardStep = new LinkedHashMap<>();
                                guardStep.put("step", stepNumber);
                                guardStep.put("type", "guard");
                                guardStep.put("label", "Проверка перед завершением");
                                guardStep.put("error", decision.error());
                                guardStep.put("hint", decision.hint() != null ? decision.hint() : "");
                                guardStep.put("checkId", decision.checkId());
                                guardStep.put("acceptance", executionAcceptance.toMap());
                                steps.add(guardStep);
                                publishStep(session.sessionId(), guardStep);
                                messages.add(new LlmMessage("assistant", response.content()));
                                messages.add(new LlmMessage(
                                        "user",
                                        "Finish blocked:\n" + decision.error()
                                                + (decision.hint() != null ? "\n\n" + decision.hint() : "")
                                                + "\n\nContinue with tools until devices have telemetry, then finish."
                                ));
                                continue;
                            }
                            AgentPlanGuard.completeExecution(session.runState());
                        }
                    }
                    finishSummary = agentAction.summary();
                    finishResult = candidateResult;
                    finalStatus = AgentTurnStatus.OK;
                    Map<String, Object> finishStep = new LinkedHashMap<>();
                    finishStep.put("step", stepNumber);
                    finishStep.put("type", "finish");
                    finishStep.put("summary", finishSummary != null ? finishSummary : "");
                    finishStep.put("label", AgentStepHumanizer.label("finish", null, null, null, finishSummary));
                    finishStep.put("result", finishResult);
                    if (executionAcceptance != null) {
                        finishStep.put("acceptance", executionAcceptance.toMap());
                    }
                    steps.add(finishStep);
                    publishStep(session.sessionId(), finishStep);
                    recordTurnAudit(
                            session,
                            turnId,
                            stepNumber,
                            "agent_finish",
                            actor,
                            userMessage,
                            finalStatus,
                            response.model(),
                            List.of(),
                            parsed.llmLatencyMs(),
                            response,
                            profile
                    );
                    break;
                }

                String toolName = agentAction.toolName();
                Map<String, Object> toolArgs = agentAction.arguments() != null ? agentAction.arguments() : Map.of();
                long toolStartNanos = System.nanoTime();
                String executedTool = toolName;
                Map<String, Object> toolResult;
                if (profile == AgentProfile.OPERATOR) {
                    OperatorAgentTurnGuard.BlockDecision block = OperatorAgentTurnGuard.checkBeforeTool(
                            toolName,
                            toolArgs,
                            steps,
                            userMessage,
                            operatorScope
                    );
                    if (block.hasClarification()) {
                        if (applyClarificationFinish(
                                session, steps, stepNumber, userMessage, block.clarification(),
                                actor, response
                        )) {
                            finishSummary = block.clarification().summary();
                            finishResult = block.clarification().result();
                            finalStatus = AgentTurnStatus.OK;
                            break;
                        }
                    }
                    if (block.blocked() && "list_reports".equals(toolName)) {
                        var clarification = OperatorAgentClarificationBuilder.onRepeatListReports(steps, userMessage);
                        if (clarification.isPresent()) {
                            if (applyClarificationFinish(
                                    session, steps, stepNumber, userMessage, clarification.get(),
                                    actor, response
                            )) {
                                finishSummary = clarification.get().summary();
                                finishResult = clarification.get().result();
                                finalStatus = AgentTurnStatus.OK;
                                break;
                            }
                        }
                    }
                    if (block.blocked()) {
                        toolResult = new LinkedHashMap<>();
                        toolResult.put("status", "ERROR");
                        toolResult.put("error", block.error());
                        if (block.hint() != null) {
                            toolResult.put("hint", block.hint());
                        }
                        executedTool = toolName;
                    } else if (!toolRegistry.isKnownTool(toolName)) {
                        toolResult = new LinkedHashMap<>(toolRegistry.unknownToolResult(toolName));
                        executedTool = toolName;
                    } else if (!AgentToolSurface.isToolActive(session.runState(), toolName)) {
                        toolResult = new LinkedHashMap<>(
                                AgentToolSurface.inactiveToolResult(toolName, session.runState())
                        );
                        executedTool = toolName;
                    } else {
                        toolResult = executeToolWithTransientRetry(toolName, toolArgs, context);
                        if ("list_reports".equals(toolName)) {
                            toolResult = OperatorAgentTurnGuard.enrichListReportsResult(toolResult, userMessage);
                        }
                        executedTool = toolName;
                    }
                        } else {
                        Optional<AgentLoopGuard.BlockDecision> loopBlock =
                                AgentLoopGuard.checkHardBlock(toolName, steps);
                        if (loopBlock.isPresent() && loopBlock.get().blocked()) {
                            toolResult = new LinkedHashMap<>();
                            toolResult.put("status", "ERROR");
                            toolResult.put("error", loopBlock.get().error());
                            toolResult.put("hint", loopBlock.get().hint());
                            executedTool = toolName;
                            agentMetrics.recordGuardBlock("loopGuard");
                        } else if (!toolRegistry.isKnownTool(toolName)) {
                            toolResult = new LinkedHashMap<>(toolRegistry.unknownToolResult(toolName));
                            executedTool = toolName;
                        } else if (!AgentToolSurface.isToolActive(session.runState(), toolName)) {
                            toolResult = new LinkedHashMap<>(
                                    AgentToolSurface.inactiveToolResult(toolName, session.runState())
                            );
                            executedTool = toolName;
                        } else {
                        Optional<AgentPreflightService.PreflightHint> preflight =
                                AgentPreflightService.checkBeforeTool(toolName, toolArgs, steps);
                        Optional<AgentGroundTruthGuard.BlockDecision> groundBlock =
                                AgentGroundTruthGuard.checkBeforeTool(
                                        toolName,
                                        toolArgs,
                                        steps,
                                        path -> ObjectTreePort.tree().findByPath(path).isPresent()
                                );
                        Optional<AgentWidgetBindingGuard.BlockDecision> widgetBlock =
                                AgentWidgetBindingGuard.checkBeforeTool(toolName, toolArgs, steps);
                        if (widgetBlock.isPresent() && widgetBlock.get().blocked()) {
                            AgentWidgetBindingGuard.BlockDecision decision = widgetBlock.get();
                            toolResult = new LinkedHashMap<>();
                            toolResult.put("status", "ERROR");
                            toolResult.put("error", decision.error());
                            toolResult.put("hint", decision.hint());
                            executedTool = toolName;
                            agentMetrics.recordGuardBlock("widgetBinding");
                        } else if (groundBlock.isPresent() && groundBlock.get().blocked()) {
                            AgentGroundTruthGuard.BlockDecision decision = groundBlock.get();
                            toolResult = new LinkedHashMap<>();
                            toolResult.put("status", "ERROR");
                            toolResult.put("error", decision.error());
                            if (decision.hint() != null) {
                                toolResult.put("hint", decision.hint());
                            }
                            Optional<Map<String, Object>> preflightOverlay = preflight.flatMap(
                                    AgentPreflightService.PreflightHint::asToolResultOverlay
                            );
                            if (preflightOverlay.isPresent()) {
                                toolResult.putAll(preflightOverlay.get());
                            }
                            executedTool = toolName;
                            agentMetrics.recordGuardBlock("groundTruth");
                        } else {
                            Optional<AgentPlanGuard.BlockDecision> planBlock =
                                    AgentPlanGuard.checkBeforeTool(session.runState(), toolName, profile);
                            Optional<AgentMutateApprovalGuard.BlockDecision> mutateBlock =
                                    AgentMutateApprovalGuard.checkBeforeTool(
                                            aiProperties.isAgentRequireApprovalForMutate(),
                                            session.runState(),
                                            toolName,
                                            profile
                                    );
                            if (planBlock.isPresent() && planBlock.get().blocked()) {
                                AgentPlanGuard.BlockDecision decision = planBlock.get();
                                toolResult = new LinkedHashMap<>();
                                toolResult.put("status", "ERROR");
                                toolResult.put("error", decision.error());
                                if (decision.hint() != null) {
                                    toolResult.put("hint", decision.hint());
                                }
                                executedTool = toolName;
                                agentMetrics.recordGuardBlock("planGuard");
                            } else if (mutateBlock.isPresent() && mutateBlock.get().blocked()) {
                                AgentMutateApprovalGuard.BlockDecision decision = mutateBlock.get();
                                toolResult = new LinkedHashMap<>();
                                toolResult.put("status", "ERROR");
                                toolResult.put("error", decision.error());
                                if (decision.hint() != null) {
                                    toolResult.put("hint", decision.hint());
                                }
                                executedTool = toolName;
                                agentMetrics.recordGuardBlock("mutateApproval");
                            } else {
                                toolResult = executeToolWithTransientRetry(toolName, toolArgs, context);
                                if ("list_reports".equals(toolName)) {
                                    toolResult = OperatorAgentTurnGuard.enrichListReportsResult(toolResult, userMessage);
                                }
                                if ("OK".equals(String.valueOf(toolResult.get("status")))) {
                                    session.runState().resetReworkRounds();
                                    AgentPlanGuard.markPlanStepCompleted(session.runState(), executedTool);
                                }
                                executedTool = toolName;
                            }
                        }
                        }
                    }

                Map<String, Object> toolStep = new LinkedHashMap<>();
                long toolLatencyMs = (System.nanoTime() - toolStartNanos) / 1_000_000L;
                toolStep.put("step", stepNumber);
                toolStep.put("type", "tool");
                toolStep.put("tool", executedTool);
                toolStep.put("label", AgentStepHumanizer.label("tool", executedTool, toolArgs, toolResult, null));
                toolStep.put("arguments", toolArgs);
                toolStep.put("result", toolResult);
                toolStep.put("latencyMs", toolLatencyMs);
                steps.add(toolStep);
                publishStep(session.sessionId(), toolStep);
                syncRunPlanState(session);

                if ("list_reports".equals(executedTool)
                        && "OK".equals(String.valueOf(toolResult.get("status")))) {
                    var clarification = OperatorAgentClarificationBuilder.maybeAfterListReports(steps, userMessage);
                    if (clarification.isPresent()) {
                        if (applyClarificationFinish(
                                session, steps, stepNumber + 1, userMessage, clarification.get(),
                                actor, response
                        )) {
                            finishSummary = clarification.get().summary();
                            finishResult = clarification.get().result();
                            finalStatus = AgentTurnStatus.OK;
                            break;
                        }
                    }
                }

                recordTurnAudit(
                        session,
                        turnId,
                        stepNumber,
                        "agent_tool_" + executedTool,
                        actor,
                        writeJson(toolArgs),
                        String.valueOf(toolResult.getOrDefault("status", "UNKNOWN")),
                        response.model(),
                        toolResult.containsKey("errors") ? (List<String>) toolResult.get("errors") : List.of(),
                        toolLatencyMs,
                        response,
                        profile
                );

                Map<String, Object> llmToolResult = AgentToolResultCompactor.compactForLlm(executedTool, toolResult);
                String toolResultJson = writeJson(llmToolResult);
                if (toolResultJson.length() > 14_000) {
                    toolResultJson = toolResultJson.substring(0, 13_999) + "…";
                }
                String continuation = profile == AgentProfile.OPERATOR
                        ? OperatorAgentTurnGuard.continuationHint(executedTool, steps, maxStepsTotal, userMessage)
                        : firstNonBlank(
                                AgentPlanGuard.askModeContinuationHint(session.runState(), steps, executedTool),
                                firstNonBlank(
                                        AgentPlanGuard.planningContinuationHint(session.runState(), steps, executedTool),
                                        AgentLoopGuard.continuationHint(executedTool, steps, maxStepsTotal)
                                )
                        );
                if (parsed.toolCallId() != null && !parsed.toolCallId().isBlank()) {
                    messages.add(new LlmMessage("assistant", response.content(), null, response.toolCalls(), null));
                    messages.add(new LlmMessage(
                            "tool",
                            toolResultJson + "\n\n" + continuation,
                            null,
                            List.of(),
                            parsed.toolCallId()
                    ));
                } else {
                    messages.add(new LlmMessage("assistant", response.content()));
                    messages.add(new LlmMessage(
                            "user",
                            "Tool result for " + executedTool + ":\n" + toolResultJson
                                    + "\n\n" + continuation
                    ));
                }
            }

            if (finishSummary == null && !AgentTurnStatus.CANCELLED.equals(finalStatus)) {
                finalStatus = AgentTurnStatus.OK;
                int completed = steps.size();
                finishSummary = "В этом turn выполнено " + completed + " шаг(ов) — достигнут мягкий лимит "
                        + maxStepsTotal + ". План и сессия сохранены; отправьте «Продолжай» для следующей порции.";
                finishResult = new LinkedHashMap<>();
                finishResult.put("interactive", true);
                finishResult.put("stepLimitReached", true);
                finishResult.put("stepsCompleted", completed);
                finishResult.put("suggestions", List.of(
                        Map.of(
                                "label", "Продолжить",
                                "message", "Продолжай выполнение с того места, где остановился",
                                "primary", true
                        )
                ));
            }

            return persistCompletedTurn(
                    session,
                    turnId,
                    userMessage,
                    finishSummary,
                    finalStatus,
                    steps,
                    finishResult,
                    attachmentMetadata,
                    actor,
                    profile
            );
        } finally {
            if (localHandle != null) {
                localHandle.close();
            }
            if (acquiredRateLimit) {
                turnRateLimiter.release(actor);
            }
        }
    }

    public Map<String, Object> cancelRun(String sessionId) {
        cancellationRegistry.cancel(sessionId);
        return Map.of("status", "OK", "sessionId", sessionId, "cancelRequested", true);
    }

    private void persistAsyncTurnFailure(
            String sessionId,
            String actor,
            String message,
            List<AgentAttachmentValidator.AttachmentInput> attachments,
            Exception failure
    ) {
        try {
            AgentSession session = sessionStore.require(sessionId, actor).orElse(null);
            if (session == null) {
                return;
            }
            AgentAttachmentValidator.PreparedUserMessage prepared;
            try {
                prepared = attachmentValidator.prepare(message, attachments);
            } catch (Exception prepEx) {
                prepared = new AgentAttachmentValidator.PreparedUserMessage(
                        message != null ? message : "",
                        message != null ? message : "",
                        List.of(),
                        List.of(),
                        false
                );
            }
            String summary = failure.getMessage() != null && !failure.getMessage().isBlank()
                    ? failure.getMessage()
                    : "Agent turn failed";
            Map<String, Object> errorStep = Map.of(
                    "step", 1,
                    "type", "error",
                    "label", "Ошибка выполнения",
                    "error", summary
            );
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("interactive", true);
            result.put("asyncFailure", true);
            persistCompletedTurn(
                    session,
                    UUID.randomUUID().toString(),
                    prepared.displayMessage(),
                    summary,
                    AgentTurnStatus.ERROR,
                    List.of(errorStep),
                    result,
                    prepared.attachmentMetadata(),
                    actor,
                    session.runState().agentProfile()
            );
        } catch (Exception persistEx) {
            log.error("Failed to persist async agent turn failure for session {}: {}", sessionId, persistEx.getMessage(), persistEx);
        }
    }

    public Map<String, Object> runProgress(String sessionId) {
        return cancellationRegistry.progress(sessionId);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    private void publishStep(String sessionId, Map<String, Object> step) {
        cancellationRegistry.recordStep(sessionId, step);
    }

    private void syncRunPlanState(AgentSession session) {
        cancellationRegistry.syncPlanState(session.sessionId(), session.runState().planStateSummary());
    }

    private static boolean runStateAntiReplanHint(AgentRunState runState, Map<String, Object> finishResult) {
        return runState != null
                && runState.planPhase() == AgentPlanPhase.APPROVED
                && AgentPlanGuard.isPlanFinish(finishResult);
    }

    @SuppressWarnings("unchecked")
    private static void captureHandoffMetadata(AgentRunState runState, Map<String, Object> finishResult) {
        if (runState == null || finishResult == null) {
            return;
        }
        Object handoff = finishResult.get("handoffFrame");
        if (handoff instanceof Map<?, ?> frame) {
            Object id = frame.get("handoffId");
            if (id != null && !String.valueOf(id).isBlank()) {
                runState.setHandoffId(String.valueOf(id));
            }
            Object adapter = frame.get("domainAdapter");
            if (adapter != null && !String.valueOf(adapter).isBlank()) {
                // stored in plan via capturePlan
            }
        }
        Object assignmentType = finishResult.get("assignmentType");
        if (assignmentType == null && finishResult.get("specBrief") instanceof Map<?, ?> brief) {
            assignmentType = brief.get("assignmentType");
        }
        if (assignmentType != null && !String.valueOf(assignmentType).isBlank()) {
            runState.setAssignmentType(String.valueOf(assignmentType));
        }
    }

    private Map<String, Object> persistCompletedTurn(
            AgentSession session,
            String turnId,
            String userMessage,
            String finishSummary,
            String finalStatus,
            List<Map<String, Object>> steps,
            Map<String, Object> finishResult,
            List<Map<String, Object>> attachments,
            String actor,
            AgentProfile profile
    ) {
        if (profile == AgentProfile.OPERATOR
                && session.runState().operatorAppId() != null
                && AgentTurnStatus.OK.equals(finalStatus)) {
            try {
                OperatorAgentScope scope = operatorScopeService.resolve(session.runState().operatorAppId());
                finishResult = operatorResultEnricher.enrich(
                        session.runState().operatorAppId(),
                        scope,
                        steps,
                        finishResult
                );
            } catch (Exception ignored) {
                // keep original finish result
            }
        }
        AgentTurn turn = AgentTurn.createWithId(
                turnId,
                userMessage,
                finishSummary,
                finalStatus,
                steps,
                finishResult,
                attachments,
                session.runState().interactionMode().storageValue()
        );
        session.addTurn(turn);
        sessionStore.persistAfterTurn(session, turn);
        agentMetrics.recordTurnCompleted(steps != null ? steps.size() : 0);
        if (profile == AgentProfile.OPERATOR
                && session.runState().operatorAppId() != null
                && AgentTurnStatus.OK.equals(finalStatus)) {
            operatorMemoryLearner.learnFromTurn(
                    session.runState().operatorAppId(),
                    userMessage,
                    finishSummary,
                    actor,
                    turn.turnId()
            );
        }
        return buildResponse(
                session,
                userMessage,
                finalStatus,
                finishSummary,
                steps,
                finishResult,
                turn.turnId(),
                steps.size(),
                aiProperties.getAgentMaxSteps(),
                profile,
                attachments
        );
    }

    public Map<String, Object> runOperatorTurn(
            AgentSession session,
            String operatorAppId,
            String message,
            Authentication authentication,
            String actor
    ) throws Exception {
        if (operatorAppId == null || operatorAppId.isBlank()) {
            throw new IllegalArgumentException("operatorAppId is required");
        }
        String appId = operatorAppId.trim();
        if (session.runState().agentProfile() != AgentProfile.OPERATOR) {
            throw new IllegalStateException("Session is not an operator agent session");
        }
        String boundApp = session.runState().operatorAppId();
        if (boundApp != null && !boundApp.equals(appId)) {
            throw new IllegalArgumentException("Session belongs to operator app: " + boundApp);
        }
        OperatorAgentScope scope = operatorScopeService.resolve(appId);
        session.runState().setOperatorAppId(appId);
        session.setRootPath(scope.briefingRoot());
        return runTurn(session, message, authentication, actor);
    }

    private OperatorAgentScope resolveOperatorScope(AgentSession session, AgentProfile profile) throws Exception {
        if (profile != AgentProfile.OPERATOR) {
            return null;
        }
        String appId = session.runState().operatorAppId();
        if (appId == null || appId.isBlank()) {
            throw new IllegalStateException("Operator session missing operatorAppId");
        }
        OperatorAgentScope scope = operatorScopeService.resolve(appId);
        session.setRootPath(scope.briefingRoot());
        return scope;
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
            int maxStepsTotal,
            AgentProfile profile,
            List<Map<String, Object>> attachments
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
        if (attachments != null && !attachments.isEmpty()) {
            result.put("attachments", attachments);
        }
        result.put("tools", toolRegistry.toolCatalog(profile));
        result.put("provider", llmProviderRegistry.status());
        result.put("contextPackVersion", contextPackService.contextPackVersion());
        result.put("stepsCompleted", stepsCompleted);
        result.put("maxSteps", maxStepsTotal);
        result.put("running", cancellationRegistry.isRunning(session.sessionId()));
        if (profile != AgentProfile.OPERATOR) {
            result.put("planState", session.runState().planStateSummary());
        }
        if (profile == AgentProfile.OPERATOR) {
            result.put("agentProfile", profile.storageValue());
            result.put("operatorAppId", session.runState().operatorAppId());
        }
        return result;
    }

    private LlmRequest buildAgentLlmRequest(
            List<LlmMessage> messages,
            AgentRunState runState,
            AgentProfile profile
    ) {
        Map<String, Object> providerOptions = aiProperties.isAgentDisableThinking()
                ? Map.of("chat_template_kwargs", Map.of("enable_thinking", false))
                : Map.of();
        List<LlmToolSpec> tools = nativeToolCallingEnabled()
                ? nativeToolSpecs(
                        AgentToolSurface.filterCatalog(
                                toolRegistry.toolCatalog(profile),
                                runState,
                                false
                        )
                )
                : List.of();
        return new LlmRequest(
                aiProperties.getModel(),
                messages,
                aiProperties.getAgentMaxTokens(),
                aiProperties.getTemperature(),
                providerOptions,
                tools,
                tools.isEmpty() ? null : "auto"
        );
    }

    private boolean nativeToolCallingEnabled() {
        String mode = aiProperties.getAgentNativeTools();
        String normalized = mode == null ? "auto" : mode.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "off", "false", "disabled" -> false;
            case "on", "true", "enabled" -> true;
            default -> llmProviderRegistry.supportsToolCalling();
        };
    }

    private static List<LlmToolSpec> nativeToolSpecs(List<Map<String, Object>> catalog) {
        List<LlmToolSpec> specs = new ArrayList<>();
        if (catalog != null) {
            for (Map<String, Object> row : catalog) {
                Object name = row.get("name");
                if (name == null || String.valueOf(name).isBlank()) {
                    continue;
                }
                specs.add(new LlmToolSpec(
                        String.valueOf(name),
                        String.valueOf(row.getOrDefault("description", "")),
                        schemaMap(row.get("inputSchema"))
                ));
            }
        }
        specs.add(new LlmToolSpec(
                "finish",
                "Finish the agent turn. Args: summary (string), result (object).",
                Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", Map.of(
                                "summary", Map.of("type", "string"),
                                "result", Map.of("type", "object")
                        ),
                        "required", List.of("summary", "result")
                )
        ));
        return List.copyOf(specs);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of("type", "object", "additionalProperties", true);
    }

    static String summarizeOlderHistory(List<AgentTurn> olderTurns, AgentRunState runState) {
        return AgentPromptAssembler.summarizeOlderHistory(olderTurns, runState);
    }

    private void ensureLlmAvailable() {
        if (!llmProviderRegistry.isGenerationAvailable()) {
            throw new IllegalStateException(
                    "LLM provider is not configured. Set ispf.ai.provider and base-url/model."
            );
        }
    }

    /**
     * BL-181: one automatic retry when the tool throws a transient exception.
     * Business ERROR maps are not retried (avoids double mutations).
     */
    private Map<String, Object> executeToolWithTransientRetry(
            String toolName,
            Map<String, Object> toolArgs,
            AgentContext context
    ) {
        try {
            return toolRegistry.execute(toolName, toolArgs, context);
        } catch (Exception ex) {
            if (!AgentToolTransientRetry.isTransient(ex)) {
                return toolErrorResult(ex, false);
            }
            agentMetrics.recordGuardBlock("toolTransientRetry");
            try {
                Map<String, Object> retried = toolRegistry.execute(toolName, toolArgs, context);
                Map<String, Object> out = new LinkedHashMap<>(retried);
                out.put("retried", true);
                return out;
            } catch (Exception retryEx) {
                return toolErrorResult(retryEx, true);
            }
        }
    }

    private static Map<String, Object> toolErrorResult(Exception ex, boolean retried) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("status", "ERROR");
        error.put("error", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        if (retried) {
            error.put("retried", true);
        }
        return error;
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

    private boolean applyClarificationFinish(
            AgentSession session,
            List<Map<String, Object>> steps,
            int stepNumber,
            String userMessage,
            OperatorAgentClarificationBuilder.ClarificationFinish clarification,
            String actor,
            LlmResponse response
    ) {
        Map<String, Object> finishStep = Map.of(
                "step", stepNumber,
                "type", "finish",
                "summary", clarification.summary(),
                "label", AgentStepHumanizer.label("finish", null, null, null, clarification.summary()),
                "result", clarification.result(),
                "clarification", true
        );
        steps.add(finishStep);
        publishStep(session.sessionId(), finishStep);
        auditService.record(
                "agent_finish_clarify",
                session.sessionId(),
                actor,
                userMessage,
                AgentTurnStatus.OK,
                llmProviderRegistry.activeProvider().providerId(),
                response != null ? response.model() : null,
                contextPackService.contextPackVersion(),
                List.of()
        );
        return true;
    }

    private void recordTurnAudit(
            AgentSession session,
            String turnId,
            int stepNo,
            String toolName,
            String actor,
            String body,
            String status,
            String modelId,
            List<String> errors,
            Long latencyMs,
            LlmResponse response,
            AgentProfile profile
    ) {
        auditService.record(
                toolName,
                session.sessionId(),
                actor,
                body,
                status,
                llmProviderRegistry.activeProvider().providerId(),
                modelId,
                contextPackService.contextPackVersion(),
                errors,
                AgentAuditMetrics.of(
                        latencyMs,
                        response != null ? response.usage() : null,
                        turnId,
                        stepNo,
                        session.runState().interactionMode().storageValue(),
                        AgentPromptVersions.profileFor(profile, session.runState().interactionMode())
                )
        );
    }
}
