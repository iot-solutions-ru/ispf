package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AgentToolPackCoverageTest {

    @Autowired
    private PlatformAgentToolRegistry toolRegistry;

    @Test
    void everyRegisteredToolHasAPackAndMetaToolsExist() {
        var catalog = toolRegistry.toolCatalog();
        assertThat(catalog).isNotEmpty();
        assertThat(toolRegistry.isKnownTool("list_agent_tools")).isTrue();
        assertThat(toolRegistry.isKnownTool("describe_agent_tool")).isTrue();
        assertThat(toolRegistry.isKnownTool("enable_agent_tool_pack")).isTrue();

        for (Map<String, Object> row : catalog) {
            String name = String.valueOf(row.get("name"));
            String pack = AgentToolPackCatalog.packFor(name);
            assertThat(pack)
                    .as("pack for %s", name)
                    .isIn(AgentToolPackCatalog.ALL_PACKS);
        }
    }

    @Test
    void metaToolsExecuteAgainstFullCatalog() throws Exception {
        AgentRunState state = new AgentRunState();
        AgentToolSurface.beginTurn(state, AgentInteractionMode.ASK, "what tools exist?");
        AgentContext ctx = new AgentContext("test", null, state);

        Map<String, Object> listed = toolRegistry.execute("list_agent_tools", Map.of(), ctx);
        assertThat(listed.get("status")).isEqualTo("OK");
        assertThat(((Number) listed.get("count")).intValue()).isGreaterThan(10);

        Map<String, Object> enabled = toolRegistry.execute(
                "enable_agent_tool_pack",
                Map.of("pack", "devices"),
                ctx
        );
        assertThat(enabled.get("status")).isEqualTo("OK");
        assertThat(AgentToolSurface.isToolActive(state, "create_object")).isTrue();
    }
}
