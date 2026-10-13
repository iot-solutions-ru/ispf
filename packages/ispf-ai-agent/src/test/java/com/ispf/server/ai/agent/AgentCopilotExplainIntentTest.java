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
        assertFalse(AgentCopilotExplainIntent.shouldBlockDiscoveryTool(
                "list_objects",
                "Add a dashboard widget here",
                SYSTEM_AI_FOCUS
        ));
    }
}
