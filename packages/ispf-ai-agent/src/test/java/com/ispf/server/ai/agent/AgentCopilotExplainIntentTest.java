package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCopilotExplainIntentTest {

    private static final Map<String, Object> SYSTEM_AI_FOCUS = Map.of(
            "surface", "system",
            "detail", Map.of(
                    "screenTitle", "System > Settings",
                    "systemTab", "settings",
                    "settingsTab", "ai",
                    "visibleSettingIds", List.of("ai.enabled", "ai.provider", "ai.model")
            )
    );

    @Test
    void detectsExplainScreenOnSystemFocus() {
        assertTrue(AgentCopilotExplainIntent.isExplainOnly(
                "Explain what to do on this System/Settings screen",
                SYSTEM_AI_FOCUS
        ));
        assertTrue(AgentCopilotExplainIntent.isExplainOnly(
                "Объясни этот экран",
                SYSTEM_AI_FOCUS
        ));
    }

    @Test
    void explainNotVetoedByEnabledSettingIdsInFocusDump() {
        String enriched = """
                Explain what to do on this System/Settings screen

                UI focus screen: System > Settings
                System tab: settings
                Settings section: ai
                Hint: Settings section «ai» — runtime platform config rows
                Visible settings: ai.enabled, ai.provider, ai.model, mcp.enabled
                """;
        assertTrue(AgentCopilotExplainIntent.isExplainOnly(enriched, SYSTEM_AI_FOCUS));
        assertTrue(AgentCopilotExplainIntent.shouldBlockDiscoveryTool(
                "get_automation_schema", enriched, SYSTEM_AI_FOCUS));
        assertTrue(AgentCopilotExplainIntent.shouldBlockDiscoveryTool(
                "search_context", enriched, SYSTEM_AI_FOCUS));
    }

    @Test
    void doesNotTreatConfigureAsExplain() {
        assertFalse(AgentCopilotExplainIntent.isExplainOnly(
                "Configure ai.model to gpt-4",
                SYSTEM_AI_FOCUS
        ));
    }

    @Test
    void blocksDiscoveryToolsForExplain() {
        assertTrue(AgentCopilotExplainIntent.shouldBlockDiscoveryTool(
                "list_objects",
                "Explain this screen",
                SYSTEM_AI_FOCUS
        ));
        assertTrue(AgentCopilotExplainIntent.shouldBlockDiscoveryTool(
                "search_context",
                "Explain this screen",
                SYSTEM_AI_FOCUS
        ));
        assertTrue(AgentCopilotExplainIntent.shouldBlockDiscoveryTool(
                "get_automation_schema",
                "Explain this screen",
                SYSTEM_AI_FOCUS
        ));
        assertFalse(AgentCopilotExplainIntent.shouldBlockDiscoveryTool(
                "list_objects",
                "Add a dashboard widget here",
                SYSTEM_AI_FOCUS
        ));
    }

    @Test
    void directResponderListsVisibleSettings() {
        String summary = AgentCopilotExplainResponder.buildSummary(SYSTEM_AI_FOCUS, "en");
        assertTrue(summary.contains("ai.enabled"));
        assertTrue(summary.contains("ai.provider"));
        assertTrue(summary.contains("restart required") || summary.contains("Restart server"));
        String ru = AgentCopilotExplainResponder.buildSummary(SYSTEM_AI_FOCUS, "ru");
        assertTrue(ru.contains("Что на этом экране") || ru.contains("Видимые параметры"));
    }
}
