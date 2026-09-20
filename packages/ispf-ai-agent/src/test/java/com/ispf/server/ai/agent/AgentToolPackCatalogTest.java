package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolPackCatalogTest {

    @Test
    void everyKnownSchemaToolMapsToAPack() {
        // Smoke: catalogSize covers registered schema names; leftovers must not NPE.
        assertThat(AgentToolPackCatalog.packFor("list_objects")).isEqualTo(AgentToolPackCatalog.DISCOVERY);
        assertThat(AgentToolPackCatalog.packFor("create_object")).isEqualTo(AgentToolPackCatalog.DEVICES);
        assertThat(AgentToolPackCatalog.packFor("set_dashboard_layout")).isEqualTo(AgentToolPackCatalog.DASHBOARDS);
        assertThat(AgentToolPackCatalog.packFor("run_workflow")).isEqualTo(AgentToolPackCatalog.AUTOMATION);
        assertThat(AgentToolPackCatalog.packFor("import_package")).isEqualTo(AgentToolPackCatalog.BUNDLES);
        assertThat(AgentToolPackCatalog.packFor("save_mimic_diagram")).isEqualTo(AgentToolPackCatalog.SCADA);
        assertThat(AgentToolPackCatalog.packFor("enable_agent_tool_pack")).isEqualTo(AgentToolPackCatalog.CORE);
        assertThat(AgentToolPackCatalog.packFor("totally_unknown_tool_xyz")).isEqualTo(AgentToolPackCatalog.MISC);
    }

    @Test
    void alwaysOnPacksAreCoreAndDiscovery() {
        assertThat(AgentToolPackCatalog.ALWAYS_ON)
                .containsExactlyInAnyOrder(AgentToolPackCatalog.CORE, AgentToolPackCatalog.DISCOVERY);
    }

    @Test
    void keywordHintsExpandPlanExecutePacks() {
        assertThat(AgentToolPackCatalog.hintPacks("создай SNMP устройство и дашборд"))
                .contains(AgentToolPackCatalog.DEVICES, AgentToolPackCatalog.DASHBOARDS);
        assertThat(AgentToolPackCatalog.hintPacks("deploy mes-reference bundle"))
                .contains(AgentToolPackCatalog.BUNDLES);
        assertThat(AgentToolPackCatalog.hintPacks("SCADA mimic panel"))
                .contains(AgentToolPackCatalog.SCADA);
    }

    @Test
    void inactiveToolResultSuggestsEnablePack() {
        AgentRunState state = new AgentRunState();
        AgentToolSurface.beginTurn(state, AgentInteractionMode.ASK, "list devices");
        assertThat(AgentToolSurface.isToolActive(state, "list_objects")).isTrue();
        assertThat(AgentToolSurface.isToolActive(state, "create_object")).isFalse();
        Map<String, Object> error = AgentToolSurface.inactiveToolResult("create_object", state);
        assertThat(error.get("status")).isEqualTo("ERROR");
        assertThat(String.valueOf(error.get("hint"))).contains("enable_agent_tool_pack");
        assertThat(error.get("pack")).isEqualTo(AgentToolPackCatalog.DEVICES);
    }

    @Test
    void enablePackUnlocksToolsForTurn() {
        AgentRunState state = new AgentRunState();
        AgentToolSurface.beginTurn(state, AgentInteractionMode.EXECUTE, "hello");
        assertThat(AgentToolSurface.isToolActive(state, "create_object")).isFalse();
        state.enableToolPack(AgentToolPackCatalog.DEVICES);
        assertThat(AgentToolSurface.isToolActive(state, "create_object")).isTrue();
        assertThat(AgentToolSurface.isToolActive(state, "list_agent_tools")).isTrue();
    }

    @Test
    void filterCatalogOmitsInactivePacks() {
        AgentRunState state = new AgentRunState();
        AgentToolSurface.beginTurn(state, AgentInteractionMode.ASK, "inventory");
        List<Map<String, Object>> full = List.of(
                Map.of("name", "list_objects", "description", "List children of a folder."),
                Map.of("name", "create_object", "description", "Create a tree node."),
                Map.of("name", "list_agent_tools", "description", "List tools.")
        );
        List<Map<String, Object>> filtered = AgentToolSurface.filterCatalog(full, state, true);
        Set<String> names = new HashSet<>();
        for (Map<String, Object> row : filtered) {
            names.add(String.valueOf(row.get("name")));
        }
        assertThat(names).contains("list_objects", "list_agent_tools").doesNotContain("create_object");
    }
}
