package com.ispf.server.ai.agent;

import com.ispf.server.ai.tool.AiToolRegistry;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTreeBundleCanonTest {

    @Mock
    private AiToolRegistry aiToolRegistry;

    @Test
    void validateBundlePromotesShipCanon() throws Exception {
        when(aiToolRegistry.validateBundle(eq("demo-app"), any(), eq("admin"), eq(true)))
                .thenReturn(Map.of("status", "ERROR", "shipCanon", true));
        PlatformAgentTool tool = tool("validate_bundle");
        tool.execute(manifestArgs(), new AgentContext("admin", null, new AgentRunState()));
        verify(aiToolRegistry).validateBundle(
                eq("demo-app"),
                any(ApplicationBundleDeployService.BundleManifest.class),
                eq("admin"),
                eq(true)
        );
    }

    @Test
    void dryRunDeployPromotesShipCanon() throws Exception {
        when(aiToolRegistry.dryRunDeploy(eq("demo-app"), any(), eq("admin"), eq(true)))
                .thenReturn(Map.of("status", "OK", "shipCanon", true));
        PlatformAgentTool tool = tool("dry_run_deploy");
        tool.execute(manifestArgs(), new AgentContext("admin", null, new AgentRunState()));
        verify(aiToolRegistry).dryRunDeploy(
                eq("demo-app"),
                any(ApplicationBundleDeployService.BundleManifest.class),
                eq("admin"),
                eq(true)
        );
    }

    private PlatformAgentTool tool(String name) {
        return AgentTreeTools.all(
                null, null, null, null, null,
                new ObjectMapper(),
                null, null, null, null, null, null, null, null, null, null,
                aiToolRegistry,
                null
        ).stream().filter(item -> name.equals(item.name())).findFirst().orElseThrow();
    }

    private static Map<String, Object> manifestArgs() {
        return Map.of(
                "appId", "demo-app",
                "manifest", Map.of("version", "1.0.0", "displayName", "Demo")
        );
    }
}
