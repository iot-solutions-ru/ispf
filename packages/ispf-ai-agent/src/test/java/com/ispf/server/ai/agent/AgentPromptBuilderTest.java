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
        assertTrue(prompt.contains("object-table"));
        assertTrue(prompt.contains("snmp"));
        assertTrue(prompt.contains("GROUND TRUTH"));
        assertTrue(prompt.contains("Ground truth"));
        assertFalse(prompt.contains("%s"));
    }

    @Test
    void includesGroundTruthPlaybookFirst() {
        String prompt = AgentPromptBuilder.build("root", List.of(), "");
        int playbooksIdx = prompt.indexOf("Playbooks:\n");
        int groundIdx = prompt.indexOf("## Ground truth", playbooksIdx);
        int snmpIdx = prompt.indexOf("## SNMP localhost", playbooksIdx);
        assertTrue(playbooksIdx >= 0);
        assertTrue(groundIdx > playbooksIdx);
        assertTrue(snmpIdx > groundIdx, "Ground truth guide should precede scenario playbooks");
    }
}
