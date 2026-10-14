package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Pre-finish judge phase — readonly conformance verdict (ISPF agent pipeline).
 */
public final class AgentJudgeService {

    /**
     * Optional live-tree sensors. When present, finish paths are checked against the real object tree
     * (not only tool step logs).
     */
    public record LiveTreeSensors(
            Predicate<String> pathExists,
            Function<String, Optional<String>> dashboardLayoutJson,
            Function<String, Optional<String>> mimicDiagramJson
    ) {
    }

    public enum Verdict {
        APPROVE,
        REWORK,
        BLOCKED,
        GAP_REQUIRED,
        USER_MODERATION_REQUIRED;

        /** Turn must end and wait for the user — no further LLM loop in this session turn. */
        public boolean requiresUserIntervention() {
            return this == GAP_REQUIRED || this == USER_MODERATION_REQUIRED;
        }
    }

    public record JudgeResult(Verdict verdict, String summary, String hint, List<String> issues) {
        boolean blocksFinish() {
            return verdict != Verdict.APPROVE;
        }
    }

    private AgentJudgeService() {
    }

    public static JudgeResult evaluate(
            List<Map<String, Object>> steps,
            AgentRunState runState,
            Map<String, Object> finishResult,
            String userMessage
    ) {
        return evaluate(steps, runState, finishResult, userMessage, null);
    }

    @SuppressWarnings("unchecked")
    public static JudgeResult evaluate(
            List<Map<String, Object>> steps,
            AgentRunState runState,
            Map<String, Object> finishResult,
            String userMessage,
            LiveTreeSensors liveTree
    ) {
        List<String> issues = new ArrayList<>();

        if (hasBlockingErrorSteps(steps)) {
            issues.add("Turn contains ERROR tool steps — fix before finish");
        }

        Map<String, Object> storedPlan = runState != null ? runState.storedPlan() : Map.of();
        Object handoffRaw = finishResult != null ? finishResult.get("handoffFrame") : null;
        if (handoffRaw == null && storedPlan.get("handoffFrame") instanceof Map<?, ?>) {
            handoffRaw = storedPlan.get("handoffFrame");
        }
        if (handoffRaw instanceof Map<?, ?> handoff) {
            List<String> blockingGaps = openBlockingGaps((Map<String, Object>) handoff);
            if (!blockingGaps.isEmpty()) {
                return new JudgeResult(
                        Verdict.GAP_REQUIRED,
                        "Open blocking GAPs: " + String.join(", ", blockingGaps),
                        problemBrief(blockingGaps, issues),
                        issues
                );
            }
        }

        AgentAssignmentType assignmentType = resolveAssignmentType(finishResult, storedPlan, userMessage, runState);
        if (assignmentType == AgentAssignmentType.APPLICATION_BUNDLE) {
            issues.addAll(verifyApplicationBundleTests(steps));
        } else if (AgentConformanceCatalog.requiresSmokeCases(assignmentType)) {
            // Evidence over paperwork: verify catalog smoke/invariants from tool steps.
            // Missing finish.conformance.smokeCases alone must not approve-fail execute turns
            // (SIF plans may still declare them for the UI; the judge checks reality).
            issues.addAll(AgentConformanceEvaluator.verifyInvariants(steps, assignmentType));
            issues.addAll(AgentConformanceEvaluator.verifySmokeCases(steps, assignmentType));
        }

        // Ship acceptance: logic.host fails LOGIC_HOST_DEVICE (REWORK). Interactive validate may stay WARNING.
        AcceptanceVerdict.Result acceptance = AcceptanceVerdict.evaluate(steps, userMessage, assignmentType.id());
        if (acceptance.checks() != null) {
            for (AcceptanceVerdict.Check check : acceptance.checks()) {
                if (!check.passed()) {
                    issues.add(check.id() + ": " + check.summary());
                }
            }
        }

        if (liveTree != null && liveTree.pathExists() != null) {
            issues.addAll(AgentLiveFinishVerifier.verify(
                    finishResult,
                    steps,
                    userMessage,
                    liveTree.pathExists(),
                    liveTree.dashboardLayoutJson(),
                    liveTree.mimicDiagramJson()
            ));
        }

        if (runState != null && runState.reworkRoundCount() >= 2 && hasBlockingErrorSteps(steps)) {
            return new JudgeResult(
                    Verdict.USER_MODERATION_REQUIRED,
                    "Repeated failures after 2 rework rounds",
                    problemBrief(List.of("USER_GATE"), issues),
                    issues
            );
        }

        if (!issues.isEmpty()) {
            return new JudgeResult(
                    Verdict.REWORK,
                    "Conformance check failed",
                    String.join("\n", issues) + "\n\nNever claim success for steps that returned ERROR.",
                    issues
            );
        }
        return new JudgeResult(Verdict.APPROVE, "Conformance OK", null, List.of());
    }

    @SuppressWarnings("unchecked")
    private static List<String> openBlockingGaps(Map<String, Object> handoff) {
        List<String> open = new ArrayList<>();
        Object gapMatrix = handoff.get("gapMatrix");
        if (!(gapMatrix instanceof List<?> list)) {
            return open;
        }
        for (Object row : list) {
            if (!(row instanceof Map<?, ?> rawMap)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) rawMap;
            if (Boolean.TRUE.equals(map.get("blocksDev"))) {
                String gapId = String.valueOf(map.getOrDefault("gapId", "?"));
                String status = String.valueOf(map.getOrDefault("gapStatus", "open"));
                if (!"closed".equalsIgnoreCase(status)) {
                    open.add(gapId);
                }
            }
        }
        Object blocking = handoff.get("blockingGaps");
        if (blocking instanceof List<?> blockingList) {
            for (Object item : blockingList) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    open.add(String.valueOf(item));
                }
            }
        }
        return open.stream().distinct().toList();
    }

    private static boolean hasBlockingErrorSteps(List<Map<String, Object>> steps) {
        return AgentTurnToolErrors.hasUnresolvedErrors(steps);
    }

    @SuppressWarnings("unchecked")
    private static List<String> verifyApplicationBundleTests(List<Map<String, Object>> steps) {
        List<String> issues = new ArrayList<>();
        boolean hasPass = false;
        if (steps != null) {
            for (Map<String, Object> step : steps) {
                if (!"tool".equals(String.valueOf(step.get("type")))) {
                    continue;
                }
                String tool = String.valueOf(step.get("tool")).toLowerCase(Locale.ROOT);
                if (!"test_function".equals(tool) && !"run_bundle_tests".equals(tool)) {
                    continue;
                }
                Map<String, Object> result = step.get("result") instanceof Map<?, ?> map
                        ? (Map<String, Object>) map
                        : Map.of();
                String status = String.valueOf(result.get("status")).toUpperCase(Locale.ROOT);
                if ("PASS".equals(status)) {
                    hasPass = true;
                }
                if ("FAIL".equals(status) || "ERROR".equals(status)) {
                    issues.add(tool + " returned " + status);
                }
            }
        }
        if (!hasPass && issues.isEmpty()) {
            issues.add("APPLICATION_BUNDLE requires PASS from test_function or run_bundle_tests");
        }
        return issues;
    }

    private static boolean hasErrorSteps(List<Map<String, Object>> steps) {
        return hasBlockingErrorSteps(steps);
    }

    private static AgentAssignmentType resolveAssignmentType(
            Map<String, Object> finishResult,
            Map<String, Object> storedPlan,
            String userMessage,
            AgentRunState runState
    ) {
        String raw = extractAssignmentType(finishResult);
        if (raw == null) {
            raw = extractAssignmentType(storedPlan);
        }
        if (raw == null && runState != null) {
            String fromState = runState.assignmentType();
            if (fromState != null && !fromState.isBlank()) {
                raw = fromState;
            }
        }
        if (raw != null) {
            return AgentAssignmentType.fromString(raw);
        }
        return AgentAssignmentClassifier.classify(userMessage).type();
    }

    private static String extractAssignmentType(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Object direct = map.get("assignmentType");
        if (direct instanceof String s && !s.isBlank()) {
            return s;
        }
        Object specBrief = map.get("specBrief");
        if (specBrief instanceof Map<?, ?> brief) {
            Object nested = brief.get("assignmentType");
            if (nested instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    private static String problemBrief(List<String> gaps, List<String> issues) {
        StringBuilder sb = new StringBuilder("Problem Brief:\n");
        if (!gaps.isEmpty()) {
            sb.append("Blocking GAPs: ").append(String.join(", ", gaps)).append("\n");
            sb.append("User Gate: resolve blocking GAPs (out_of_scope items) or confirm explicit exclusion before execution.\n");
        }
        for (String issue : issues) {
            sb.append("- ").append(issue).append("\n");
        }
        return sb.toString().trim();
    }
}
