package com.ispf.server.ai.audit;

import com.ispf.server.ai.agent.AgentSession;
import com.ispf.server.ai.agent.AgentTurn;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentAuditExportService {

    private final AiToolAuditStore auditStore;
    private final ObjectMapper objectMapper;

    public AgentAuditExportService(AiToolAuditStore auditStore, ObjectMapper objectMapper) {
        this.auditStore = auditStore;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> exportJson(AgentSession session) {
        String sessionId = session.sessionId();
        List<Map<String, Object>> dbRows = auditStore.listByAppId(sessionId, 5000);
        List<Map<String, Object>> toolSteps = extractToolSteps(session);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("sessionTitle", session.title());
        payload.put("actor", session.actor());
        payload.put("exportedAt", Instant.now().toString());
        payload.put("auditRows", dbRows);
        payload.put("toolInvocations", toolSteps);
        payload.put("auditRowCount", dbRows.size());
        payload.put("toolInvocationCount", toolSteps.size());
        return payload;
    }

    public String exportCsv(AgentSession session) {
        Map<String, Object> json = exportJson(session);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> auditRows = (List<Map<String, Object>>) json.get("auditRows");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolSteps = (List<Map<String, Object>>) json.get("toolInvocations");

        StringBuilder csv = new StringBuilder();
        csv.append("source,createdAt,toolName,status,actor,step,argumentsJson,requestHash,errors\n");

        for (Map<String, Object> row : auditRows) {
            csv.append(csvLine(
                    "db",
                    stringValue(row.get("createdAt")),
                    stringValue(row.get("toolName")),
                    stringValue(row.get("status")),
                    stringValue(row.get("actor")),
                    "",
                    "",
                    stringValue(row.get("requestHash")),
                    stringValue(row.get("errors"))
            ));
        }

        for (Map<String, Object> step : toolSteps) {
            csv.append(csvLine(
                    "session",
                    stringValue(step.get("createdAt")),
                    stringValue(step.get("tool")),
                    stringValue(step.get("status")),
                    stringValue(session.actor()),
                    stringValue(step.get("step")),
                    writeJson(step.get("arguments")),
                    "",
                    ""
            ));
        }

        return csv.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractToolSteps(AgentSession session) {
        List<Map<String, Object>> steps = new ArrayList<>();
        for (AgentTurn turn : session.turns()) {
            for (Map<String, Object> step : turn.steps()) {
                if (!"tool".equals(String.valueOf(step.get("type")))) {
                    continue;
                }
                Map<String, Object> rawArgs = step.get("arguments") instanceof Map<?, ?> map
                        ? (Map<String, Object>) map
                        : Map.of();
                Map<String, Object> result = step.get("result") instanceof Map<?, ?> map
                        ? (Map<String, Object>) map
                        : Map.of();
                Map<String, Object> exported = new LinkedHashMap<>();
                exported.put("turnId", turn.turnId());
                exported.put("step", step.get("step"));
                exported.put("tool", step.get("tool"));
                exported.put("label", step.get("label"));
                exported.put("status", result.getOrDefault("status", "UNKNOWN"));
                exported.put("arguments", AgentAuditRedactor.redactArguments(rawArgs));
                exported.put("createdAt", turn.createdAt().toString());
                steps.add(exported);
            }
        }
        return steps;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "\"serialization-error\"";
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String csvLine(String... columns) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escapeCsv(columns[i]));
        }
        line.append('\n');
        return line.toString();
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
