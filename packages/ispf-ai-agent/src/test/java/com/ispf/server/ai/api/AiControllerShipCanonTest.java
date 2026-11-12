package com.ispf.server.ai.api;

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
class AiControllerShipCanonTest {

    @Mock
    private AiToolRegistry toolRegistry;

    @Test
    void studioValidateAndDryRunPromoteShipCanon() {
        when(toolRegistry.validateBundle(eq("demo-app"), any(), eq("anonymous"), eq(true)))
                .thenReturn(Map.of("status", "ERROR", "shipCanon", true));
        when(toolRegistry.dryRunDeploy(eq("demo-app"), any(), eq("anonymous"), eq(true)))
                .thenReturn(Map.of("status", "ERROR", "shipCanon", true));
        AiController controller = new AiController(
                toolRegistry,
                null, null, null, null, null, null,
                new ObjectMapper(),
                null, null, null, null, null, null
        );
        AiController.BundleToolRequest request = new AiController.BundleToolRequest(
                "demo-app",
                Map.of("version", "1.0.0", "displayName", "Demo")
        );

        controller.validateBundle(null, request);
        controller.dryRunDeploy(null, request);

        verify(toolRegistry).validateBundle(
                eq("demo-app"),
                any(ApplicationBundleDeployService.BundleManifest.class),
                eq("anonymous"),
                eq(true)
        );
        verify(toolRegistry).dryRunDeploy(
                eq("demo-app"),
                any(ApplicationBundleDeployService.BundleManifest.class),
                eq("anonymous"),
                eq(true)
        );
    }
}
