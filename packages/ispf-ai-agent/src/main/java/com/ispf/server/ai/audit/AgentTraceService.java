package com.ispf.server.ai.audit;

import com.ispf.server.ai.agent.AgentSession;
import com.ispf.server.ai.agent.AgentTurn;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentTraceService {

    private final AiToolAuditStore auditStore;

    public AgentTraceService(AiToolAuditStore auditStore) {
        this.auditStore = auditStore;
    }

    public Map<String, Object> trace(AgentSession session, String turnId) {
        AgentTurn turn = resolveTurn(session, turnId);
        if (turn == null) {
            return Map.of("sessionId", session.sessionId(), "turns", List.of());
        }
        List<Map<String, Object>> auditRows = auditStore.listByAppId(session.sessionId(), 5000).stream()
                .filter(row -> turn.turnId().equals(String.valueOf(row.get("turnId"))))
                .toList();
        List<Map<String, Object>> traceSteps = buildTraceSteps(turn.steps(), auditRows);

        long totalLatencyMs = auditRows.stream()
                .map(row -> row.get("latencyMs"))
                .filter(Number.class::isInstance)
                .mapToLong(v -> ((Number) v).longValue())
                .sum();
        int promptTokens = auditRows.stream()
                .map(row -> row.get("promptTokens"))
                .filter(Number.class::isInstance)
                .mapToInt(v -> ((Number) v).intValue())
                .sum();
        int completionTokens = auditRows.stream()
                .map(row -> row.get("completionTokens"))
                .filter(Number.class::isInstance)
                .mapToInt(v -> ((Number) v).intValue())
                .sum();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.sessionId());
        payload.put("turnId", turn.turnId());
        payload.put("status", turn.status());
        payload.put("userMessage", turn.userMessage());
        payload.put("assistantSummary", turn.assistantSummary());
        payload.put("createdAt", turn.createdAt().toString());
        payload.put("steps", traceSteps);
        payload.put("auditRows", auditRows);
        payload.put("totals", Map.of(
                "latencyMs", totalLatencyMs,
                "promptTokens", promptTokens,
                "completionTokens", completionTokens,
                "auditRowCount", auditRows.size()
        ));
        return payload;
    }

    public Map<String, Object> traceAllTurns(AgentSession session) {
        List<Map<String, Object>> turns = new ArrayList<>();
        for (AgentTurn turn : session.turns()) {
            turns.add(trace(session, turn.turnId()));
        }
        return Map.of("sessionId", session.sessionId(), "turns", turns);
    }

    private static AgentTurn resolveTurn(AgentSession session, String turnId) {
        if (session.turns().isEmpty()) {
            return null;
        }
        if (turnId == null || turnId.isBlank()) {
            return session.turns().getLast();
        }
        return session.turns().stream()
                .filter(t -> turnId.equals(t.turnId()))
                .findFirst()
                .orElse(null);
    }

    private static List<Map<String, Object>> buildTraceSteps(
            List<Map<String, Object>> steps,
            List<Map<String, Object>> auditRows
    ) {
        List<Map<String, Object>> traceSteps = new ArrayList<>();
        for (Map<String, Object> step : steps) {
            Map<String, Object> row = new LinkedHashMap<>(step);
            int stepNo = step.get("step") instanceof Number n ? n.intValue() : -1;
            String toolName = step.get("tool") != null ? String.valueOf(step.get("tool")) : null;
            String type = String.valueOf(step.get("type"));
            Map<String, Object> audit = auditRows.stream()
                    .filter(a -> stepNo == asInt(a.get("stepNo")))
                    .filter(a -> matchesAuditTool(type, toolName, String.valueOf(a.get("toolName"))))
                    .findFirst()
                    .orElse(null);
            if (audit != null) {
                copyMetrics(row, audit);
            } else if (step.get("latencyMs") != null) {
                row.put("latencyMs", step.get("latencyMs"));
            }
            traceSteps.add(row);
        }
        for (Map<String, Object> audit : auditRows) {
            String toolName = String.valueOf(audit.get("toolName"));
            if ("agent_llm_round".equals(toolName)) {
                Map<String, Object> llmStep = new LinkedHashMap<>();
                llmStep.put("step", audit.get("stepNo"));
                llmStep.put("type", "llm");
                llmStep.put("label", "LLM round");
                copyMetrics(llmStep, audit);
                traceSteps.add(llmStep);
            }
        }
        traceSteps.sort((a, b) -> Integer.compare(asInt(a.get("step")), asInt(b.get("step"))));
        return traceSteps;
    }

    private static boolean matchesAuditTool(String stepType, String toolName, String auditToolName) {
        if ("tool".equals(stepType) && toolName != null) {
            return auditToolName.equals("agent_tool_" + toolName);
        }
        if ("finish".equals(stepType)) {
            return auditToolName.startsWith("agent_finish");
        }
        if ("error".equals(stepType)) {
            return "agent_parse_error".equals(auditToolName);
        }
        return false;
    }

    private static void copyMetrics(Map<String, Object> target, Map<String, Object> audit) {
        if (audit.get("latencyMs") != null) {
            target.put("latencyMs", audit.get("latencyMs"));
        }
        if (audit.get("promptTokens") != null) {
            target.put("promptTokens", audit.get("promptTokens"));
        }
        if (audit.get("completionTokens") != null) {
            target.put("completionTokens", audit.get("completionTokens"));
        }
        if (audit.get("promptProfile") != null) {
            target.put("promptProfile", audit.get("promptProfile"));
        }
    }

    private static int asInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}
