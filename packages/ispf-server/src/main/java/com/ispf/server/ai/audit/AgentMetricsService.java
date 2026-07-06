package com.ispf.server.ai.audit;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentMetricsService {

    private final JdbcTemplate jdbcTemplate;
    private final String turnsTable;
    private final String auditTable;

    public AgentMetricsService(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.turnsTable = platformSqlCatalog.table("agent_turns");
        this.auditTable = platformSqlCatalog.table("ai_tool_audit");
    }

    public Map<String, Object> metrics(int days) {
        int windowDays = Math.min(Math.max(days, 1), 90);
        Instant since = Instant.now().minus(windowDays, ChronoUnit.DAYS);
        Timestamp sinceTs = Timestamp.from(since);

        Map<String, Long> turnsByStatus = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT status, COUNT(*) AS cnt
                FROM %s
                WHERE created_at >= ?
                GROUP BY status
                """.formatted(turnsTable),
                rs -> {
                    turnsByStatus.put(rs.getString("status"), rs.getLong("cnt"));
                },
                sinceTs
        );

        Double avgSteps = jdbcTemplate.queryForObject("""
                SELECT AVG(step_count) FROM (
                    SELECT MAX(COALESCE(step_no, 0)) AS step_count
                    FROM %s
                    WHERE created_at >= ?
                      AND turn_id IS NOT NULL
                    GROUP BY turn_id
                ) t
                """.formatted(auditTable),
                Double.class,
                sinceTs
        );

        List<Map<String, Object>> topFailingTools = jdbcTemplate.query("""
                SELECT tool_name, COUNT(*) AS cnt
                FROM %s
                WHERE created_at >= ?
                  AND status = 'ERROR'
                  AND tool_name LIKE 'agent_tool_%'
                GROUP BY tool_name
                ORDER BY cnt DESC
                LIMIT 10
                """.formatted(auditTable),
                (rs, rowNum) -> Map.of(
                        "tool", rs.getString("tool_name"),
                        "errorCount", rs.getLong("cnt")
                ),
                sinceTs
        );

        Map<String, Object> tokenTotals = jdbcTemplate.queryForMap("""
                SELECT
                    COALESCE(SUM(prompt_tokens), 0) AS prompt_tokens,
                    COALESCE(SUM(completion_tokens), 0) AS completion_tokens,
                    COALESCE(SUM(latency_ms), 0) AS latency_ms
                FROM %s
                WHERE created_at >= ?
                """.formatted(auditTable),
                sinceTs
        );

        long judgeRework = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM %s
                WHERE created_at >= ?
                  AND tool_name = 'agent_finish_judge'
                """.formatted(auditTable),
                Long.class,
                sinceTs
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("days", windowDays);
        payload.put("since", since.toString());
        payload.put("turnsByStatus", turnsByStatus);
        payload.put("avgStepsPerTurn", avgSteps != null ? avgSteps : 0.0);
        payload.put("topFailingTools", topFailingTools);
        payload.put("judgeFinishBlocks", judgeRework);
        payload.put("promptTokensSum", tokenTotals.get("prompt_tokens"));
        payload.put("completionTokensSum", tokenTotals.get("completion_tokens"));
        payload.put("latencyMsSum", tokenTotals.get("latency_ms"));
        payload.put("promptVersions", List.of(
                com.ispf.server.ai.agent.AgentPromptVersions.ADMIN_AGENT,
                com.ispf.server.ai.agent.AgentPromptVersions.ASK_AGENT,
                com.ispf.server.ai.agent.AgentPromptVersions.OPERATOR_AGENT
        ));
        return payload;
    }
}
