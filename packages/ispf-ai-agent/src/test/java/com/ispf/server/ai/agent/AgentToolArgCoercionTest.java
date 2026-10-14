package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolArgCoercionTest {

    @Test
    void mapsAlternatePackKeysForEnableAgentToolPack() {
        Map<String, Object> coerced = AgentToolArgCoercion.coerce(
                "enable_agent_tool_pack",
                Map.of("name", "devices")
        );
        assertThat(coerced.get("pack")).isEqualTo("devices");
    }

    @Test
    void keepsExplicitPack() {
        Map<String, Object> coerced = AgentToolArgCoercion.coerce(
                "enable_agent_tool_pack",
                Map.of("pack", "Dashboards")
        );
        assertThat(coerced.get("pack")).isEqualTo("dashboards");
    }

    @Test
    void leavesEmptyPackUntouchedWhenNoAlias() {
        Map<String, Object> coerced = AgentToolArgCoercion.coerce(
                "enable_agent_tool_pack",
                Map.of()
        );
        assertThat(coerced.containsKey("pack")).isFalse();
    }
}
