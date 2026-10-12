package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformAgentToolRegistryAdminCatalogTest {

    @Test
    void adminCatalogHidesOperatorScopedTools() {
        for (String tool : PlatformAgentToolRegistry.OPERATOR_SCOPED_ONLY_TOOLS) {
            assertFalse(
                    PlatformAgentToolRegistry.isVisibleForProfile(tool, AgentProfile.ADMIN, Set.of()),
                    () -> "ADMIN must not see " + tool
            );
        }
        assertTrue(PlatformAgentToolRegistry.isVisibleForProfile("search_context", AgentProfile.ADMIN, Set.of()));
        assertTrue(PlatformAgentToolRegistry.isVisibleForProfile("list_objects", AgentProfile.ADMIN, Set.of()));
    }

    @Test
    void operatorCatalogKeepsScopedToolsWhenAllowed() {
        Set<String> allowed = Set.of("read_app_document", "search_context");
        assertTrue(PlatformAgentToolRegistry.isVisibleForProfile(
                "read_app_document", AgentProfile.OPERATOR, allowed));
        assertFalse(PlatformAgentToolRegistry.isVisibleForProfile(
                "search_context", AgentProfile.OPERATOR, Set.of("read_app_document")));
    }
}
