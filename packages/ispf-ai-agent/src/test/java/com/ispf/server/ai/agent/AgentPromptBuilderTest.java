package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPromptBuilderTest {

    @Test
    void buildsPromptWithoutFormatPlaceholders() {
        String prompt = AgentPromptBuilder.build("root", List.of(
                Map.of("name", "list_objects", "description", "List children"),
                Map.of("name", "get_widget_catalog", "description", "Widget reference")
        ), "### Drivers\n- snmp | SNMP\n");
        assertTrue(prompt.contains("list_objects"));
        assertTrue(prompt.contains("get_widget_catalog"));
        assertTrue(prompt.contains("Platform knowledge"));
        assertTrue(prompt.contains("snmp"));
        assertTrue(prompt.contains("GROUND TRUTH"));
        assertTrue(prompt.contains("Playbook index"));
        assertTrue(prompt.contains("enable_agent_tool_pack"));
        assertFalse(prompt.contains("%s"));
    }

    @Test
    void omitsFullPlaybookBodies() {
        String prompt = AgentPromptBuilder.build("root", List.of(), "");
        assertTrue(prompt.contains("Playbook index"));
        assertFalse(prompt.contains("## SNMP localhost"));
        assertFalse(prompt.contains("## Virtual pump station"));
        assertFalse(prompt.contains("## SCADA mimic guide"));
        assertTrue(prompt.contains("get_automation_schema"));
        assertTrue(prompt.contains("search_context"));
    }
}
