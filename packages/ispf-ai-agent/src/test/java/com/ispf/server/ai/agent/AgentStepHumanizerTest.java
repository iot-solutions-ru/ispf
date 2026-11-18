package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentStepHumanizerTest {

    @Test
    void labelsSnmpStepsInPlainLanguage() {
        String label = AgentStepHumanizer.label(
                "tool",
                "configure_driver",
                Map.of("devicePath", "root.platform.devices.snmp-localhost"),
                Map.of("connected", true),
                null,
                "ru"
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
                "SNMP localhost настроен, дашборд готов.",
                "ru"
        );
        assertTrue(label.contains("SNMP localhost"));
    }

    @Test
    void labelsDeleteObjectInRussian() {
        String label = AgentStepHumanizer.label(
                "tool",
                "delete_object",
                Map.of("path", "root.platform.devices.snmp-localhost"),
                null,
                null,
                "ru"
        );
        assertTrue(label.contains("snmp-localhost"));
        assertTrue(label.contains("Удаляю"));
    }

    @Test
    void labelsListObjectsInEnglishWhenUiLocaleIsEn() {
        String label = AgentStepHumanizer.label(
                "tool",
                "list_objects",
                Map.of("parent", "root"),
                null,
                null,
                "en"
        );
        assertEquals("Listing contents of \"root\"", label);
    }

    @Test
    void labelsDefaultToolCallInEnglish() {
        String label = AgentStepHumanizer.label(
                "tool",
                "get_operator_scope",
                Map.of(),
                null,
                null,
                "en-US"
        );
        assertEquals("Calling get_operator_scope", label);
    }

    @Test
    void statusLabelsFollowLocale() {
        assertEquals("Preparing request…", AgentStepHumanizer.preparingRequest("en"));
        assertEquals("Подготовка запроса…", AgentStepHumanizer.preparingRequest("ru"));
        assertEquals("Anfrage wird vorbereitet…", AgentStepHumanizer.preparingRequest("de"));
        assertEquals("正在准备请求…", AgentStepHumanizer.preparingRequest("zh"));
        assertEquals("Failed to parse model response", AgentStepHumanizer.parseErrorLabel("en", false));
        assertEquals("Ответ обрезан", AgentStepHumanizer.parseErrorLabel("ru", true));
    }

    @Test
    void llmRoundLabelFollowsLocaleAndAction() {
        assertEquals("Model → call list_objects", AgentStepHumanizer.llmRoundLabel("en", "tool", "list_objects"));
        assertEquals("Модель → вызов list_objects", AgentStepHumanizer.llmRoundLabel("ru", "tool", "list_objects"));
        assertEquals("Model → finish answer", AgentStepHumanizer.llmRoundLabel("en", "finish", null));
        assertEquals("Модель → готовый ответ", AgentStepHumanizer.llmRoundLabel("ru", "finish", null));
        assertEquals("Model reply", AgentStepHumanizer.llmRoundLabel("en", "clarify", null));
    }

    @Test
    void listObjectsFollowsAllConsoleLocales() {
        assertEquals(
                "Listing contents of \"root\"",
                AgentStepHumanizer.label("tool", "list_objects", Map.of("parent", "root"), null, null, "en"));
        assertTrue(AgentStepHumanizer.label("tool", "list_objects", Map.of("parent", "root"), null, null, "de")
                .contains("Liste Inhalt"));
        assertTrue(AgentStepHumanizer.label("tool", "list_objects", Map.of("parent", "root"), null, null, "zh")
                .contains("正在查看"));
    }
}
