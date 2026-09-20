package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentHistorySummaryTest {

    @Test
    void summarizeOlderHistoryIncludesGoalPhaseAndPaths() {
        AgentRunState state = new AgentRunState();
        state.setStoredPlan(Map.of("goal", "Build pump station SCADA"));
        state.setPlanPhase(AgentPlanPhase.AWAITING_APPROVAL);
        state.setInteractionMode(AgentInteractionMode.PLAN);

        List<AgentTurn> older = new ArrayList<>();
        older.add(AgentTurn.create(
                "Create pumps under devices",
                "Created device root.devices.pumps",
                "OK",
                List.of(),
                Map.of("devicePath", "root.devices.pumps")
        ));
        older.add(AgentTurn.create(
                "Add dashboard",
                "Dashboard ready",
                "OK",
                List.of(),
                Map.of("dashboardPath", "root.dashboards.overview")
        ));

        String summary = TreeFirstAgentService.summarizeOlderHistory(older, state);
        assertThat(summary).contains("Build pump station SCADA");
        assertThat(summary).contains("awaiting_approval");
        assertThat(summary).contains("root.devices.pumps");
        assertThat(summary).contains("root.dashboards.overview");
        assertThat(summary).contains("Create pumps");
    }
}
