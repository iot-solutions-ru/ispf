package com.ispf.server.ai.generation;

import com.ispf.ai.LlmProvider;
import com.ispf.ai.LlmResponse;
import com.ispf.server.ai.audit.AiToolAuditService;
import com.ispf.server.ai.context.ContextPackService;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import com.ispf.server.ai.tool.AiToolRegistry;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiBundleGenerationShipCanonTest {

    @Mock
    private LlmProviderRegistry llmProviderRegistry;
    @Mock
    private ContextPackService contextPackService;
    @Mock
    private AiToolRegistry toolRegistry;
    @Mock
    private AiToolAuditService auditService;
    @Mock
    private LlmProvider llmProvider;

    @Test
    void generatePromotesShipCanonAndStaysUnpublishableWhenValidationFails() throws Exception {
        AiProperties properties = new AiProperties();
        properties.setModel("test-model");
        when(llmProviderRegistry.isGenerationAvailable()).thenReturn(true);
        when(llmProviderRegistry.complete(any())).thenReturn(new LlmResponse(
                "{\"version\":\"1.0.0\",\"displayName\":\"Demo\",\"schemaName\":\"app_demo\"}",
                "test-model",
                null
        ));
        when(llmProviderRegistry.activeProvider()).thenReturn(llmProvider);
        when(llmProvider.providerId()).thenReturn("test");
        when(llmProviderRegistry.status()).thenReturn(Map.of("status", "OK"));
        when(contextPackService.contextPackVersion()).thenReturn("test-pack");
        when(toolRegistry.validateBundle(eq("demo"), any(), eq("admin"), eq(true)))
                .thenReturn(Map.of("status", "ERROR", "shipCanon", true, "errors", java.util.List.of("LOGIC_HOST_DEVICE")));
        when(toolRegistry.dryRunDeploy(eq("demo"), any(), eq("admin"), eq(true)))
                .thenReturn(Map.of("status", "ERROR", "shipCanon", true));

        AiBundleGenerationService service = new AiBundleGenerationService(
                llmProviderRegistry,
                contextPackService,
                toolRegistry,
                auditService,
                properties,
                new ObjectMapper()
        );
        Map<String, Object> result = service.generate("demo", "build a hub", null, "admin");

        assertEquals(false, result.get("publishable"));
        verify(toolRegistry).validateBundle(
                eq("demo"),
                any(ApplicationBundleDeployService.BundleManifest.class),
                eq("admin"),
                eq(true)
        );
        verify(toolRegistry).dryRunDeploy(
                eq("demo"),
                any(ApplicationBundleDeployService.BundleManifest.class),
                eq("admin"),
                eq(true)
        );
    }
}
