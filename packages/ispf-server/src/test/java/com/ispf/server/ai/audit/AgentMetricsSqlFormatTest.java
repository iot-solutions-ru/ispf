package com.ispf.server.ai.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AgentMetricsSqlFormatTest {

    @Test
    void topFailingToolsLikePatternSurvivesStringFormatted() {
        assertThatCode(() -> """
                SELECT tool_name, COUNT(*) AS cnt
                FROM %s
                WHERE created_at >= ?
                  AND status = 'ERROR'
                  AND tool_name LIKE 'agent_tool_%%'
                GROUP BY tool_name
                ORDER BY cnt DESC
                LIMIT 10
                """.formatted("ai_tool_audit"))
                .doesNotThrowAnyException();

        String sql = """
                SELECT tool_name
                FROM %s
                WHERE tool_name LIKE 'agent_tool_%%'
                """.formatted("ai_tool_audit");
        assertThat(sql).contains("FROM ai_tool_audit");
        assertThat(sql).contains("LIKE 'agent_tool_%'");
        assertThat(sql).doesNotContain("%%");
    }
}
