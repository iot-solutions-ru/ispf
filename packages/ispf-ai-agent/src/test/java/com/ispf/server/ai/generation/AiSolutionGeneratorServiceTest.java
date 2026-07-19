package com.ispf.server.ai.generation;

import com.ispf.ai.LlmResponse;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.config.AiProperties;
import com.ispf.server.license.CommercialBundleLicenseSigner;
import com.ispf.server.object.ObjectTreePort;
import com.ispf.server.operator.OperatorAppUiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSolutionGeneratorServiceTest {

    @Mock
    private LlmProviderRegistry llmProviderRegistry;
    @Mock
    private ApplicationBundleDeployService bundleDeployService;
    @Mock
    private CommercialBundleLicenseSigner bundleLicenseSigner;
    @Mock
    private OperatorAppUiService operatorAppUiService;
    @Mock
    private ObjectTreePort ObjectTreePort;
    @Mock
    private AutomationTreeService automationTreeService;
    @Mock
    private ObjectTree objectTree;
    @Mock
    private PlatformObject hubNode;
    @Mock
    private Variable statusVariable;

    private AiSolutionGeneratorService service;

    private static final Set<String> EXPECTED_DOMAINS = Set.of(
            "mes", "oil-gas", "water", "energy", "hvac", "building", "warehouse", "lab", "pipeline", "scada"
    );

    @BeforeEach
    void setUp() {
        lenient().when(llmProviderRegistry.isGenerationAvailable()).thenReturn(false);
        lenient().when(bundleLicenseSigner.isConfigured()).thenReturn(false);
        service = new AiSolutionGeneratorService(
                llmProviderRegistry,
                new AiProperties(),
                new ObjectMapper(),
                bundleDeployService,
                bundleLicenseSigner,
                operatorAppUiService,
                ObjectTreePort,
                automationTreeService
        );
    }

    @Test
    void detectsAllTenDomains() {
        assertEquals("mes", service.generate("Deploy MES OEE dashboard").get("domain"));
        assertEquals("oil-gas", service.generate("Oil and gas pump station tank farm").get("domain"));
        assertEquals("water", service.generate("Wastewater treatment plant intake").get("domain"));
        assertEquals("energy", service.generate("Thermal power plant turbine kWh reporting").get("domain"));
        assertEquals("hvac", service.generate("HVAC zone comfort setpoints").get("domain"));
        assertEquals("building", service.generate("Smart building BMS lighting access control").get("domain"));
        assertEquals("warehouse", service.generate("Warehouse logistics cold chain inventory").get("domain"));
        assertEquals("lab", service.generate("Lab training virtual cluster simulator profile").get("domain"));
        assertEquals("pipeline", service.generate("Midstream pipeline valve station linepack").get("domain"));
        assertEquals("scada", service.generate("SCADA pump station mimic overview").get("domain"));
    }

    @Test
    void templateForCoversEveryDomain() {
        for (String domain : EXPECTED_DOMAINS) {
            String prompt = switch (domain) {
                case "mes" -> "MES manufacturing dispatch";
                case "oil-gas" -> "oil gas refinery";
                case "water" -> "water treatment";
                case "energy" -> "thermal power energy";
                case "hvac" -> "hvac comfort";
                case "building" -> "smart building bms";
                case "warehouse" -> "warehouse logistics";
                case "lab" -> "lab training";
                case "pipeline" -> "pipeline midstream";
                default -> "scada historian";
            };
            Map<String, Object> result = service.generate(prompt);
            assertEquals(domain, result.get("domain"), "domain mismatch for prompt: " + prompt);

            @SuppressWarnings("unchecked")
            Map<String, Object> draft = (Map<String, Object>) result.get("blueprintDraft");
            assertEquals(domain, draft.get("domain"));

            @SuppressWarnings("unchecked")
            Map<String, Object> referenceBundle = (Map<String, Object>) draft.get("referenceBundle");
            assertEquals("draft", result.get("mode"));
            assertEquals("keyword", result.get("domainSelection"));
            assertTrue(referenceBundle.containsKey("appId"));
            assertTrue(referenceBundle.containsKey("manifestPath"));
            assertDeployableBundleDraft((Map<String, Object>) result.get("bundleDraft"));
        }
    }

    @Test
    void hvacWinsOverBuildingWhenBothMentioned() {
        assertEquals("hvac", service.generate("HVAC building comfort zones with AHU setpoints").get("domain"));
    }

    @Test
    void blueprintDraftIncludesSpecBriefAndArtifacts() {
        Map<String, Object> result = service.generate("SCADA facility with historian trends");
        @SuppressWarnings("unchecked")
        Map<String, Object> draft = (Map<String, Object>) result.get("blueprintDraft");
        assertEquals("scada", draft.get("domain"));
        assertEquals("draft", result.get("mode"));
        assertNotNull(draft.get("specBrief"));
        assertNotNull(draft.get("suggestedArtifacts"));
        assertDeployableBundleDraft((Map<String, Object>) result.get("bundleDraft"));
    }

    @Test
    void bundleDraftIncludesOperatorUiAndFunctions() {
        Map<String, Object> result = service.generate("MES dispatch and OEE for assembly line");
        @SuppressWarnings("unchecked")
        Map<String, Object> bundleDraft = (Map<String, Object>) result.get("bundleDraft");
        assertDeployableBundleDraft(bundleDraft);

        @SuppressWarnings("unchecked")
        Map<String, Object> operatorUi = (Map<String, Object>) bundleDraft.get("operatorUi");
        assertFalse(operatorUi.get("appId").toString().isBlank());
        assertFalse(((List<?>) operatorUi.get("dashboards")).isEmpty());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> functions = (List<Map<String, Object>>) bundleDraft.get("functions");
        assertEquals(1, functions.size());
        assertEquals("mes_listOrders", functions.getFirst().get("functionName"));
    }

    @Test
    void blankPromptRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.generate("  "));
    }

    @Test
    void applyWithoutLlmFailsFast() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.generate("HVAC plant with AHU and comfort alert", true, "admin")
        );
        assertTrue(ex.getMessage().contains("LLM"));
    }

    @Test
    void liveApplyTrustsPlatformGeneratedUnsignedWhenSignerNotConfigured() throws Exception {
        when(llmProviderRegistry.isGenerationAvailable()).thenReturn(true);
        when(llmProviderRegistry.complete(any())).thenReturn(new LlmResponse(
                """
                {"title":"HVAC site","slug":"hvac-trust-demo","devices":[{"name":"ahu-1","displayName":"AHU 1"}]}
                """,
                "test-model",
                null
        ));
        when(bundleLicenseSigner.isConfigured()).thenReturn(false);
        when(bundleDeployService.deploy(anyString(), any(), eq(true))).thenReturn(Map.of("status", "OK"));
        when(ObjectTreePort.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(anyString())).thenReturn(Optional.of(hubNode));
        when(ObjectTreePort.require(anyString())).thenReturn(hubNode);
        when(hubNode.getVariable("status")).thenReturn(Optional.of(statusVariable));

        Map<String, Object> result = service.generate(
                "Describe a building HVAC plant with one AHU and zone comfort monitoring.",
                true,
                "admin"
        );

        assertEquals("live", result.get("mode"));
        assertEquals("platform-generated-unsigned", result.get("bundleTrust"));
        verify(bundleDeployService).deploy(anyString(), any(), eq(true));
        verify(bundleLicenseSigner).isConfigured();
    }

    @Test
    void liveApplySignsWhenSignerConfigured() throws Exception {
        when(llmProviderRegistry.isGenerationAvailable()).thenReturn(true);
        when(llmProviderRegistry.complete(any())).thenReturn(new LlmResponse(
                """
                {"title":"HVAC site","slug":"hvac-signed-demo","devices":[{"name":"ahu-1","displayName":"AHU 1"}]}
                """,
                "test-model",
                null
        ));
        when(bundleLicenseSigner.isConfigured()).thenReturn(true);
        when(bundleLicenseSigner.signManifestIfNeeded(anyString(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = new ObjectMapper().convertValue(inv.getArgument(1), Map.class);
            root.put("license", Map.of("bundleId", inv.getArgument(0), "signature", "demo"));
            return root;
        });
        ArgumentCaptor<Boolean> trustCaptor = ArgumentCaptor.forClass(Boolean.class);
        when(bundleDeployService.deploy(anyString(), any(), trustCaptor.capture())).thenReturn(Map.of("status", "OK"));
        when(ObjectTreePort.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(anyString())).thenReturn(Optional.of(hubNode));
        when(ObjectTreePort.require(anyString())).thenReturn(hubNode);
        when(hubNode.getVariable("status")).thenReturn(Optional.of(statusVariable));

        Map<String, Object> result = service.generate(
                "Describe a building HVAC plant with one AHU and zone comfort monitoring.",
                true,
                "admin"
        );

        assertEquals("live", result.get("mode"));
        assertEquals("signed", result.get("bundleTrust"));
        assertFalse(trustCaptor.getValue());
        verify(bundleLicenseSigner).signManifestIfNeeded(anyString(), any());
    }

    @Test
    void draftUsesCatalogCompositionNotPrimitives() {
        Map<String, Object> result = service.generate("SCADA pump station mimic");
        assertEquals("draft", result.get("mode"));
        assertEquals("catalog-template", result.get("composition"));
    }

    @SuppressWarnings("unchecked")
    private static void assertDeployableBundleDraft(Map<String, Object> bundleDraft) {
        assertNotNull(bundleDraft);
        assertFalse(String.valueOf(bundleDraft.get("version")).isBlank());
        assertTrue(bundleDraft.get("objects") instanceof List<?>);
        assertFalse(((List<?>) bundleDraft.get("objects")).isEmpty());
        assertTrue(bundleDraft.get("dashboards") instanceof List<?>);
        assertFalse(((List<?>) bundleDraft.get("dashboards")).isEmpty());
        assertTrue(bundleDraft.get("operatorUi") instanceof Map<?, ?>);
    }
}
