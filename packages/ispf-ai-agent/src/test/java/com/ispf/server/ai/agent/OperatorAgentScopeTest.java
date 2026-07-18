package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorAgentScopeTest {

    @Test
    void allowsExactAndChildPaths() {
        OperatorAgentScope scope = new OperatorAgentScope(
                "demo",
                "Demo",
                List.of("root.platform.dashboards.demo-sensor"),
                "root.platform.dashboards.demo-sensor"
        );
        assertTrue(scope.isPathAllowed("root.platform.dashboards.demo-sensor"));
        assertTrue(scope.isPathAllowed("root.platform.dashboards.demo-sensor.widgets"));
        assertFalse(scope.isPathAllowed("root.platform.dashboards.other"));
    }
}
