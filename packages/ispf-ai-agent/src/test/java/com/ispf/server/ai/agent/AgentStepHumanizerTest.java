package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentStepHumanizerTest {

    @Test
    void labelsSnmpStepsInPlainLanguage() {
        String label = AgentStepHumanizer.label(
                "tool",
                "configure_driver",
                Map.of("devicePath", "root.platform.devices.snmp-localhost"),
                Map.of("connected", true),
                null
        );
        assertTrue(label.contains("snmp-localhost"));
    }

    @Test
    void labelsFinishWithSummary() {
        String label = AgentStepHumanizer.label(
                "finish",
                null,
                null,
                null,
                "SNMP localhost настроен, дашборд готов."
        );
        assertTrue(label.contains("SNMP localhost"));
    }

    @Test
    void labelsDeleteObject() {
        String label = AgentStepHumanizer.label(
                "tool",
                "delete_object",
                Map.of("path", "root.platform.devices.snmp-localhost"),
                null,
                null
        );
        assertTrue(label.contains("snmp-localhost"));
        assertTrue(label.contains("Удаляю"));
    }
}
