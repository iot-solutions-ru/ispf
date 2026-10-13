package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTurnToolErrorsTest {

    @Test
    void ensureSingletonCatalogMissRecoveredAfterListAndDeploy() {
        List<Map<String, Object>> steps = List.of(
                tool("deploy_tree_function", "ERROR", "Path is ObjectType.DEVICE"),
                tool("ensure_singleton_instance", "ERROR",
                        "Cannot apply blueprint: \"virtual-lab-v1\" was not returned by a catalog tool in this turn"),
                tool("list_singleton_blueprints", "OK", null),
                tool("create_object", "OK", null),
                tool("deploy_tree_function", "OK", null)
        );
        assertThat(AgentTurnToolErrors.hasUnresolvedErrors(steps)).isFalse();
        assertThat(AgentTurnToolErrors.unresolvedErrorSummaries(steps)).isEmpty();
    }

    @Test
    void ensureSingletonCatalogMissStillBlocksWithoutRecovery() {
        List<Map<String, Object>> steps = List.of(
                tool("ensure_singleton_instance", "ERROR",
                        "Cannot apply blueprint: \"virtual-lab-v1\" was not returned by a catalog tool in this turn")
        );
        assertThat(AgentTurnToolErrors.hasUnresolvedErrors(steps)).isTrue();
    }

    private static Map<String, Object> tool(String name, String status, String error) {
        Map<String, Object> result = error == null
                ? Map.of("status", status)
                : Map.of("status", status, "error", error);
        return Map.of(
                "type", "tool",
                "tool", name,
                "arguments", Map.of(),
                "result", result
        );
    }
}
