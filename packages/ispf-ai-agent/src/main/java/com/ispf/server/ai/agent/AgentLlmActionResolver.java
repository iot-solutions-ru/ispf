package com.ispf.server.ai.agent;

import com.ispf.ai.LlmException;
import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.ai.LlmToolCall;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Calls the LLM and parses a tool/finish action with bounded retries and JSON-only nudges.
 */
final class AgentLlmActionResolver {

    private static final String JSON_NUDGE = """
            Your last reply was not a valid agent action. Reply with ONLY one JSON object, no markdown and no explanation:
            {"type":"tool","name":"<tool>","arguments":{...}}
            or {"type":"finish","summary":"...","result":{...}}
            Never put ```json code fences inside the summary string. Never dump large manifests in finish — summarize briefly.
            """;

    private static final String COMPACT_TOOL_NUDGE = """
            Your reply was truncated — invalid JSON. Reply with ONLY one compact JSON tool call. \
            Do NOT embed huge layoutJson or mimic elements inline.
            For dashboards prefer template=:
            {"type":"tool","name":"set_dashboard_layout","arguments":{"path":"<path>","template":"scada-facility-overview"}}
            Or add widgets one at a time via add_dashboard_widget. For mimics use save_mimic_diagram with a small elements array \
            or add_mimic_elements in chunks.
            """;

    private static final String ASK_ATTACHMENT_NUDGE = """
            User attached a file in Ask mode — read-only. Reply with ONLY one JSON object:
            {"type":"finish","summary":"Markdown summary of the document and how it relates to ISPF","result":{}}
            Or one discovery tool if you need platform context first. No phase=plan, no mutations.
            """;

    private static final String ATTACHMENT_PLAN_NUDGE = """
            User attached a spec/TZ file — do NOT analyze it in prose. Reply with ONLY one JSON object:
            Turn 1: {"type":"tool","name":"get_automation_schema","arguments":{"topic":"projectBlueprint"}}
            Turn 2 (bootstrap): specBrief + plan.sections[ground_truth,intent_scope] + executiveSummary.
            HARD LIMITS: max 2 sections, max 3 questions; gapMatrix/handoffFrame on SYNTHESIS/FINALIZE only.
            """;

    record ResolveContext(
            boolean textAttachment,
            boolean planningTurn,
            boolean executionTurn,
            boolean askTurn,
            AgentPhasedPlanIntake.Stage phasedStage,
            String uiLocale,
            Consumer<String> previewSink,
            BooleanSupplier cancelled
    ) {
        ResolveContext(
                boolean textAttachment,
                boolean planningTurn,
                boolean executionTurn,
                boolean askTurn,
                AgentPhasedPlanIntake.Stage phasedStage,
                String uiLocale
        ) {
            this(textAttachment, planningTurn, executionTurn, askTurn, phasedStage, uiLocale, null, null);
        }

        static ResolveContext none() {
            return new ResolveContext(
                    false, false, false, false, AgentPhasedPlanIntake.Stage.DISCOVERY, "");
        }
    }

    record ParseAttempt(
            LlmResponse response,
            AgentJsonProtocol.AgentAction action,
            boolean failed,
            String error,
            long llmLatencyMs,
            String toolCallId
    ) {
        ParseAttempt(LlmResponse response, AgentJsonProtocol.AgentAction action, boolean failed, String error) {
            this(response, action, failed, error, 0L, null);
        }

        ParseAttempt(
                LlmResponse response,
                AgentJsonProtocol.AgentAction action,
                boolean failed,
                String error,
                long llmLatencyMs
        ) {
            this(response, action, failed, error, llmLatencyMs, null);
        }
    }

    private AgentLlmActionResolver() {
    }

    static ParseAttempt resolve(
            ObjectMapper objectMapper,
            LlmProviderRegistry llmProviderRegistry,
            List<LlmMessage> messages,
            Function<List<LlmMessage>, LlmRequest> requestFactory,
            int maxAttempts
    ) throws Exception {
        return resolve(objectMapper, llmProviderRegistry, messages, requestFactory, maxAttempts, false, ResolveContext.none());
    }

    static ParseAttempt resolve(
            ObjectMapper objectMapper,
            LlmProviderRegistry llmProviderRegistry,
            List<LlmMessage> messages,
            Function<List<LlmMessage>, LlmRequest> requestFactory,
            int maxAttempts,
            boolean allowPlainTextFinish
    ) throws Exception {
        return resolve(
                objectMapper,
                llmProviderRegistry,
                messages,
                requestFactory,
                maxAttempts,
                allowPlainTextFinish,
                ResolveContext.none()
        );
    }

    static ParseAttempt resolve(
            ObjectMapper objectMapper,
            LlmProviderRegistry llmProviderRegistry,
            List<LlmMessage> messages,
            Function<List<LlmMessage>, LlmRequest> requestFactory,
            int maxAttempts,
            boolean allowPlainTextFinish,
            ResolveContext context
    ) throws Exception {
        int attempts = Math.max(1, maxAttempts);
        LlmResponse lastResponse = null;
        String lastError = null;
        long totalLlmLatencyMs = 0L;
        ResolveContext ctx = context != null ? context : ResolveContext.none();
        for (int attempt = 0; attempt < attempts; attempt++) {
            long llmStart = System.nanoTime();
            if (ctx.previewSink() != null) {
                lastResponse = llmProviderRegistry.completeStreaming(
                        requestFactory.apply(messages),
                        ctx.previewSink(),
                        ctx.cancelled()
                );
            } else {
                lastResponse = llmProviderRegistry.complete(requestFactory.apply(messages));
            }
            totalLlmLatencyMs += (System.nanoTime() - llmStart) / 1_000_000L;
            if (isCancelled(ctx)) {
                throw new LlmException("Agent run cancelled");
            }
            try {
                Optional<ParseAttempt> nativeAction = parseNativeToolCall(
                        objectMapper, lastResponse, totalLlmLatencyMs, ctx);
                if (nativeAction.isPresent()) {
                    return nativeAction.get();
                }
                AgentJsonProtocol.AgentAction action = AgentJsonProtocol.normalizeAction(
                        AgentJsonProtocol.parse(objectMapper, lastResponse.content())
                );
                return new ParseAttempt(lastResponse, action, false, null, totalLlmLatencyMs);
            } catch (Exception ex) {
                if (ex instanceof LlmException llmEx) {
                    throw llmEx;
                }
                if (isCancelled(ctx)) {
                    throw new LlmException("Agent run cancelled");
                }
                lastError = ex.getMessage();
                if (attempt < attempts - 1) {
                    String assistantEcho = assistantEchoForRetry(lastResponse);
                    messages.add(new LlmMessage("assistant", assistantEcho));
                    messages.add(new LlmMessage("user", nudgeForRetry(lastResponse, ctx, attempt)));
                }
            }
        }
        if (allowPlainTextFinish && lastResponse != null) {
            Optional<AgentJsonProtocol.AgentAction> plain = AgentJsonProtocol.tryParsePlainTextFinish(
                    lastResponse.content()
            );
            if (plain.isPresent()) {
                return new ParseAttempt(lastResponse, plain.get(), false, null, totalLlmLatencyMs);
            }
        }
        Optional<AgentJsonProtocol.AgentAction> salvaged = trySalvageAnyTruncatedFinish(
                objectMapper, lastResponse, ctx);
        if (salvaged.isPresent()) {
            return new ParseAttempt(lastResponse, salvaged.get(), false, null, totalLlmLatencyMs);
        }
        if (lastResponse != null && ctx.planningTurn() && looksTruncated(lastResponse)) {
            // Planning turns must not hard-fail on oversized plans — keep a continue-plan finish.
            return new ParseAttempt(
                    lastResponse,
                    softTruncatedPlanFinish(ctx.uiLocale()),
                    false,
                    null,
                    totalLlmLatencyMs
            );
        }
        if (isCancelled(ctx)) {
            throw new LlmException("Agent run cancelled");
        }
        return new ParseAttempt(lastResponse, null, true, lastError, totalLlmLatencyMs);
    }

    private static boolean isCancelled(ResolveContext ctx) {
        return ctx != null && ctx.cancelled() != null && ctx.cancelled().getAsBoolean();
    }

    private static Optional<ParseAttempt> parseNativeToolCall(
            ObjectMapper objectMapper,
            LlmResponse response,
            long totalLlmLatencyMs,
            ResolveContext ctx
    ) throws Exception {
        if (response == null || response.toolCalls() == null || response.toolCalls().isEmpty()) {
            return Optional.empty();
        }
        LlmToolCall call = response.toolCalls().get(0);
        if (AgentJsonProtocol.isFinishToolName(call.name())) {
            Optional<Map<String, Object>> parsedArgs = tryParseArguments(objectMapper, call.argumentsJson());
            if (parsedArgs.isPresent()) {
                AgentJsonProtocol.AgentAction action =
                        AgentJsonProtocol.finishActionFromToolArgs(parsedArgs.get());
                return Optional.of(new ParseAttempt(response, action, false, null, totalLlmLatencyMs, call.id()));
            }
            // Let resolve() retry with compactFinishNudge before post-loop salvage.
            throw new IllegalArgumentException("Native finish tool call has invalid/truncated arguments JSON");
        }
        Map<String, Object> args = parseArguments(objectMapper, call.argumentsJson());
        AgentJsonProtocol.AgentAction action = new AgentJsonProtocol.AgentAction(
                "tool",
                call.name(),
                args,
                null,
                null
        );
        return Optional.of(new ParseAttempt(response, action, false, null, totalLlmLatencyMs, call.id()));
    }

    private static Optional<AgentJsonProtocol.AgentAction> trySalvageAnyTruncatedFinish(
            ObjectMapper objectMapper,
            LlmResponse response,
            ResolveContext ctx
    ) {
        if (response == null) {
            return Optional.empty();
        }
        boolean planning = ctx != null && ctx.planningTurn();
        String uiLocale = ctx != null ? ctx.uiLocale() : "";
        if (AgentJsonProtocol.looksLikeTruncatedContent(response.content())
                || (response.content() != null && looksLikeTruncatedArgs(response.content()))) {
            Optional<AgentJsonProtocol.AgentAction> fromContent =
                    AgentJsonProtocol.trySalvageTruncatedFinish(
                            objectMapper, response.content(), uiLocale, planning);
            if (fromContent.isPresent()) {
                return fromContent;
            }
        }
        if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
            LlmToolCall call = response.toolCalls().get(0);
            if (AgentJsonProtocol.isFinishToolName(call.name())) {
                return salvageNativeFinishArgs(objectMapper, call.argumentsJson(), uiLocale, planning);
            }
        }
        return Optional.empty();
    }

    private static Optional<AgentJsonProtocol.AgentAction> salvageNativeFinishArgs(
            ObjectMapper objectMapper,
            String argumentsJson,
            String uiLocale,
            boolean planningMode
    ) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Optional.empty();
        }
        // Keep args truncated: strip only the leading '{', do not invent a closing brace.
        String trimmed = argumentsJson.trim();
        String wrapped = trimmed.startsWith("{")
                ? "{\"type\":\"finish\"," + trimmed.substring(1)
                : "{\"type\":\"finish\"," + trimmed;
        return AgentJsonProtocol.trySalvageTruncatedFinish(objectMapper, wrapped, uiLocale, planningMode);
    }

    private static AgentJsonProtocol.AgentAction softTruncatedPlanFinish(String uiLocale) {
        boolean ru = AgentStepHumanizer.isRussian(uiLocale);
        // Omit synthetic goal — mergePlans must not overwrite a real stored goal with a placeholder.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phase", "plan");
        result.put("interactive", true);
        result.put("planPartial", true);
        result.put("suggestions", List.of(
                Map.of(
                        "label", ru ? "Продолжи план" : "Continue plan",
                        "message", ru ? "продолжи план" : "continue plan",
                        "primary", true
                ),
                Map.of(
                        "label", ru ? "Добавь следующие разделы" : "Add next sections",
                        "message", ru ? "добавь следующие разделы" : "add the next sections"
                )
        ));
        return new AgentJsonProtocol.AgentAction(
                "finish",
                null,
                null,
                AgentStepHumanizer.modelParseFailedSummary(uiLocale, true),
                result
        );
    }

    private static boolean looksTruncated(LlmResponse response) {
        if (response == null) {
            return false;
        }
        if (response.truncatedByLength() || AgentJsonProtocol.looksLikeTruncatedContent(response.content())) {
            return true;
        }
        if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
            return looksLikeTruncatedArgs(response.toolCalls().get(0).argumentsJson());
        }
        return false;
    }

    private static boolean looksLikeTruncatedArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return false;
        }
        return AgentJsonProtocol.looksLikeTruncatedContent(argumentsJson)
                || (!argumentsJson.trim().endsWith("}") && argumentsJson.contains("{"));
    }

    private static String assistantEchoForRetry(LlmResponse response) {
        if (response == null) {
            return "";
        }
        if (response.content() != null && !response.content().isBlank()) {
            return response.content();
        }
        if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
            LlmToolCall call = response.toolCalls().get(0);
            return "tool_call name=" + call.name() + " args=" + call.argumentsJson();
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArguments(ObjectMapper objectMapper, String argumentsJson) throws Exception {
        Optional<Map<String, Object>> parsed = tryParseArguments(objectMapper, argumentsJson);
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("Tool call arguments are not a JSON object");
        }
        return parsed.get();
    }

    @SuppressWarnings("unchecked")
    private static Optional<Map<String, Object>> tryParseArguments(ObjectMapper objectMapper, String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Optional.of(Map.of());
        }
        try {
            JsonNode node = objectMapper.readTree(argumentsJson);
            if (!node.isObject()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.convertValue(node, Map.class));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String nudgeForRetry(LlmResponse response, ResolveContext context, int attemptIndex) {
        AgentPhasedPlanIntake.Stage stage = context.phasedStage() != null
                ? context.phasedStage()
                : AgentPhasedPlanIntake.Stage.BOOTSTRAP;
        if (response != null && looksTruncated(response)) {
            if (context.planningTurn()) {
                return AgentPhasedPlanIntake.compactFinishNudge(stage);
            }
            if (context.executionTurn()
                    || looksLikeHeavyToolPayload(response.content())
                    || looksLikeHeavyNativeTool(response)) {
                return COMPACT_TOOL_NUDGE;
            }
            return AgentPhasedPlanIntake.compactFinishNudge(stage);
        }
        if (context.textAttachment() && attemptIndex == 0) {
            if (context.askTurn()) {
                return ASK_ATTACHMENT_NUDGE;
            }
            return ATTACHMENT_PLAN_NUDGE;
        }
        if (context.askTurn()) {
            return JSON_NUDGE;
        }
        if (context.planningTurn()) {
            return AgentPhasedPlanIntake.compactFinishNudge(stage);
        }
        return JSON_NUDGE;
    }

    private static boolean looksLikeHeavyToolPayload(String content) {
        if (content == null) {
            return false;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        return lower.contains("layoutjson")
                || lower.contains("set_dashboard_layout")
                || lower.contains("save_mimic_diagram")
                || lower.contains("\"elements\"");
    }

    private static boolean looksLikeHeavyNativeTool(LlmResponse response) {
        if (response == null || response.toolCalls() == null || response.toolCalls().isEmpty()) {
            return false;
        }
        LlmToolCall call = response.toolCalls().get(0);
        String name = call.name() != null ? call.name().toLowerCase(Locale.ROOT) : "";
        String args = call.argumentsJson() != null ? call.argumentsJson().toLowerCase(Locale.ROOT) : "";
        return name.contains("dashboard")
                || name.contains("mimic")
                || args.contains("layoutjson")
                || args.contains("\"elements\"");
    }
}
