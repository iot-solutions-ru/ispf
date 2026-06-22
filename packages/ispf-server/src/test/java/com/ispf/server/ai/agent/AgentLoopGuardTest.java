package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopGuardTest {

    @Test
    void warnsOnRepeatedSearchContext() {
        List<Map<String, Object>> steps = List.of(
                step("search_context"),
                step("search_context"),
                step("search_context")
        );
        String hint = AgentLoopGuard.continuationHint("search_context", steps, 18);
        assertTrue(hint.toLowerCase().contains("search_context"));
    }

    @Test
    void warnsNearStepBudget() {
        List<Map<String, Object>> steps = List.of(
                step("list_objects"),
                step("get_object"),
                step("set_variable"),
                step("list_variables"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget")
        );
        String hint = AgentLoopGuard.continuationHint("add_dashboard_widget", steps, 18);
        assertTrue(hint.contains("finish"));
    }

    private static Map<String, Object> step(String tool) {
        return Map.of("type", "tool", "tool", tool);
    }
}
