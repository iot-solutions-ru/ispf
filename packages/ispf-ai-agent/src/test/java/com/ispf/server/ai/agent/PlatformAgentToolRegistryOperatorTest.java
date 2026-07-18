package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformAgentToolRegistryOperatorTest {

    @Test
    void operatorProfileExcludesWriteTools() {
        // Smoke: allowlist is curated and excludes create_object
        assertTrue(PlatformAgentToolRegistry.OPERATOR_TOOLS.contains("list_variables"));
        assertTrue(PlatformAgentToolRegistry.OPERATOR_TOOLS.contains("list_app_documents"));
        assertTrue(PlatformAgentToolRegistry.OPERATOR_TOOLS.contains("get_operator_link"));
        assertFalse(PlatformAgentToolRegistry.OPERATOR_TOOLS.contains("create_object"));
        assertFalse(PlatformAgentToolRegistry.OPERATOR_TOOLS.contains("delete_object"));
        assertFalse(PlatformAgentToolRegistry.OPERATOR_TOOLS.contains("set_variable"));
    }
}
