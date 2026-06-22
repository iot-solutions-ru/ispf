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
                Map.of("name", "list_objects", "description", "List children")
        ));
        assertTrue(prompt.contains("list_objects"));
        assertTrue(prompt.contains("root.platform.devices.snmp-localhost"));
        assertFalse(prompt.contains("%s"));
    }
}
