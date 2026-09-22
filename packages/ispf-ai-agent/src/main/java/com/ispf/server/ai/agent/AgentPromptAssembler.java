package com.ispf.server.ai.agent;

import com.ispf.ai.LlmContentPart;
import com.ispf.ai.LlmMessage;
import com.ispf.server.ai.context.PlatformBriefingService;
import com.ispf.server.config.AiProperties;
import com.ispf.server.operator.OperatorAgentMemoryService;
import com.ispf.server.operator.OperatorAppDocumentService;
import com.ispf.server.operator.OperatorAppUiService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the LLM message list for one agent turn: system prompt, rolled history, and the current user message.
 */
final class AgentPromptAssembler {

    private static final int HISTORY_SUMMARY_MAX_LEN = 800;
    private static final int HISTORY_USER_MESSAGE_MAX_LEN = 4_000;

    private final AiProperties aiProperties;
    private final PlatformBriefingService platformBriefingService;
    private final PlatformAgentToolRegistry toolRegistry;
    private final OperatorAgentMemoryService operatorMemoryService;
    private final OperatorAppDocumentService operatorDocumentService;
    private final OperatorAppUiService operatorAppUiService;
    private final AgentSessionDocumentService sessionDocumentService;

    AgentPromptAssembler(
            AiProperties aiProperties,
            PlatformBriefingService platformBriefingService,
            PlatformAgentToolRegistry toolRegistry,
            OperatorAgentMemoryService operatorMemoryService,
            OperatorAppDocumentService operatorDocumentService,
            OperatorAppUiService operatorAppUiService,
            AgentSessionDocumentService sessionDocumentService
    ) {
        this.aiProperties = aiProperties;
        this.platformBriefingService = platformBriefingService;
        this.toolRegistry = toolRegistry;
        this.operatorMemoryService = operatorMemoryService;
        this.operatorDocumentService = operatorDocumentService;
        this.operatorAppUiService = operatorAppUiService;
        this.sessionDocumentService = sessionDocumentService;
    }

    List<LlmMessage> buildMessagesWithHistory(
            AgentSession session,
            AgentAttachmentValidator.PreparedUserMessage prepared,
            AgentProfile profile,
            OperatorAgentScope operatorScope
    ) throws Exception {
        String llmUserText = prepared.llmText();
        List<LlmMessage> messages = new ArrayList<>();
        boolean includeStatic = aiProperties.isBriefingEveryTurn() || session.turns().isEmpty();
        String briefing = platformBriefingService.buildBriefing(session.rootPath(), includeStatic);
        List<Map<String, Object>> activeTools = AgentToolSurface.filterCatalog(
                toolRegistry.toolCatalog(profile),
                session.runState(),
                true
        );
        String systemPrompt;
        if (profile == AgentProfile.OPERATOR && operatorScope != null) {
            String memorySection = operatorMemoryService.formatPromptSection(
                    operatorScope.appId(),
                    llmUserText
            );
            String knowledgeSection = operatorDocumentService.formatPromptSection(
                    operatorScope.appId(),
                    llmUserText,
                    operatorAppUiService.getAgentInstructions(operatorScope.appId())
            );
            systemPrompt = AgentOperatorPromptBuilder.build(
                    operatorScope,
                    toolRegistry.toolCatalog(profile),
                    briefing,
                    memorySection,
                    knowledgeSection
            );
        } else if ("copilot".equalsIgnoreCase(session.runState().clientChannel())) {
            systemPrompt = AgentCopilotPromptBuilder.build(
                    session.rootPath(),
                    toolRegistry.toolCatalog(profile),
                    prepared.hasImages()
            );
            if (hasTextAttachment(prepared.attachmentMetadata())) {
                systemPrompt += AgentAttachmentPromptSection.forTextAttachments();
            }
        } else if (session.runState().interactionMode() == AgentInteractionMode.ASK) {
            String sessionDocs = sessionDocumentService.formatPromptSection(
                    session.sessionId(),
                    llmUserText
            );
            boolean uiFocusPresent = session.runState().clientFocus() != null
                    && !session.runState().clientFocus().isEmpty();
            systemPrompt = AgentAskPromptBuilder.build(
                    session.rootPath(),
                    activeTools,
                    briefing,
                    prepared.hasImages(),
                    sessionDocs,
                    !uiFocusPresent
            );
            if (hasTextAttachment(prepared.attachmentMetadata())) {
                systemPrompt += AgentAttachmentPromptSection.forTextAttachments();
            }
        } else {
            systemPrompt = AgentPromptBuilder.build(
                    session.rootPath(),
                    activeTools,
                    briefing
            );
            systemPrompt += AgentPlanPromptSection.forRunState(session.runState());
            systemPrompt += sessionDocumentService.formatPromptSection(session.sessionId(), llmUserText);
            if (prepared.hasImages()) {
                systemPrompt += AgentPlanPromptSection.forImageAttachments(session.runState());
            }
            if (hasTextAttachment(prepared.attachmentMetadata())) {
                systemPrompt += AgentAttachmentPromptSection.forTextAttachments();
            }
        }
        String localeLead = AgentUiLocalePromptSection.format(session.runState().uiLocale());
        String focusLead = AgentClientFocusPromptSection.formatChannel(session.runState().clientChannel());
        String focusBody = AgentClientFocusPromptSection.format(session.runState().clientFocus());
        if (!localeLead.isBlank() || !focusLead.isBlank() || !focusBody.isBlank()) {
            StringBuilder lead = new StringBuilder();
            if (!localeLead.isBlank()) {
                lead.append(localeLead.trim()).append("\n\n");
            }
            if (!focusLead.isBlank()) {
                lead.append(focusLead.trim()).append("\n\n");
            }
            if (!focusBody.isBlank()) {
                lead.append(focusBody.trim()).append("\n\n");
            }
            systemPrompt = lead + systemPrompt;
        }
        messages.add(new LlmMessage("system", systemPrompt));

        boolean copilotHereAndNow = "copilot".equalsIgnoreCase(session.runState().clientChannel());
        if (!copilotHereAndNow) {
            List<AgentTurn> history = session.turns();
            int maxTurns = Math.max(1, aiProperties.getAgentMaxHistoryTurns());
            int start = Math.max(0, history.size() - maxTurns);
            if (start > 0) {
                String rolledSummary = summarizeOlderHistory(
                        history.subList(0, start),
                        session.runState()
                );
                messages.add(new LlmMessage(
                        "user",
                        "[Earlier session context — condensed]\n" + rolledSummary
                ));
                messages.add(new LlmMessage(
                        "assistant",
                        "Understood prior context; continuing from the recent turns below."
                ));
            }
            for (int i = start; i < history.size(); i++) {
                AgentTurn turn = history.get(i);
                messages.add(new LlmMessage("user", truncateHistoryUserMessage(turn.userMessage())));
                messages.add(new LlmMessage("assistant", truncateForHistory(turn.assistantSummary())));
            }
        }
        String liveSnapshot = AgentClientFocusPromptSection.formatLiveSnapshotReminder(
                session.runState().clientChannel(),
                session.runState().clientFocus()
        );
        if (!liveSnapshot.isBlank()) {
            messages.add(new LlmMessage("system", liveSnapshot));
        }
        messages.add(buildCurrentUserMessage(prepared, session));
        return messages;
    }

    static String summarizeOlderHistory(List<AgentTurn> olderTurns, AgentRunState runState) {
        StringBuilder sb = new StringBuilder();
        if (runState != null) {
            Map<String, Object> plan = runState.storedPlan();
            if (plan != null && !plan.isEmpty()) {
                Object goal = plan.get("goal");
                if (goal != null && !String.valueOf(goal).isBlank()) {
                    sb.append("Goal: ").append(truncateForHistory(String.valueOf(goal))).append('\n');
                }
            }
            if (runState.planPhase() != null && runState.planPhase() != AgentPlanPhase.NONE) {
                sb.append("Plan phase: ").append(runState.planPhase().storageValue()).append('\n');
            }
            if (runState.interactionMode() != null) {
                sb.append("Mode: ").append(runState.interactionMode().storageValue()).append('\n');
            }
        }
        int limit = Math.min(olderTurns == null ? 0 : olderTurns.size(), 12);
        for (int i = 0; i < limit; i++) {
            AgentTurn turn = olderTurns.get(i);
            sb.append("- User: ").append(truncateForHistory(turn.userMessage())).append('\n');
            String summary = turn.assistantSummary();
            if (summary != null && !summary.isBlank()) {
                sb.append("  Agent: ").append(truncateForHistory(summary)).append('\n');
            }
            Map<String, Object> result = turn.result();
            if (result != null) {
                for (String key : List.of("devicePath", "dashboardPath", "mimicPath", "workflowPath", "path", "appId")) {
                    Object value = result.get(key);
                    if (value instanceof String path && !path.isBlank()) {
                        sb.append("  ").append(key).append("=").append(path).append('\n');
                    }
                }
            }
        }
        if (olderTurns != null && olderTurns.size() > limit) {
            sb.append("(+").append(olderTurns.size() - limit).append(" earlier turns omitted)\n");
        }
        String text = sb.toString().trim();
        return text.isBlank() ? "Prior turns exist but had little extractable context." : text;
    }

    private static LlmMessage buildCurrentUserMessage(
            AgentAttachmentValidator.PreparedUserMessage prepared,
            AgentSession session
    ) {
        String text = prepared.llmText() == null ? "" : prepared.llmText();
        String prefix = AgentClientFocusPromptSection.formatUserTurnPrefix(
                session.runState().clientChannel(),
                session.runState().clientFocus()
        );
        if (!prefix.isBlank()) {
            text = text.isBlank() ? prefix : prefix + "\n\n" + text;
        }
        if (prepared.imageParts().isEmpty()) {
            return new LlmMessage("user", text);
        }
        List<LlmContentPart> parts = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            parts.add(LlmContentPart.text(text));
        }
        parts.addAll(prepared.imageParts());
        return new LlmMessage("user", text, List.copyOf(parts));
    }

    static boolean hasTextAttachment(List<Map<String, Object>> attachmentMetadata) {
        if (attachmentMetadata == null || attachmentMetadata.isEmpty()) {
            return false;
        }
        return attachmentMetadata.stream()
                .anyMatch(meta -> "text".equals(String.valueOf(meta.get("kind"))));
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

    private static String truncateHistoryUserMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String trimmed = message.trim();
        if (trimmed.length() <= HISTORY_USER_MESSAGE_MAX_LEN) {
            return trimmed;
        }
        return trimmed.substring(0, HISTORY_USER_MESSAGE_MAX_LEN - 1) + "…";
    }
}
