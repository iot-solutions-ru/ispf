package com.ispf.server.ai.agent;

import com.ispf.server.ai.tool.AiToolRegistry;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.operator.OperatorAppUiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentDeployPlaybookToolsTest {

    @Mock
    private AiToolRegistry aiToolRegistry;
    @Mock
    private ApplicationBundleDeployService bundleDeployService;
    @Mock
    private OperatorAppUiService operatorAppUiService;

    private List<PlatformAgentTool> tools;
    private AgentContext context;

    @BeforeEach
    void setUp() {
        tools = AgentDeployPlaybookTools.all(
                new ObjectMapper(),
                aiToolRegistry,
                bundleDeployService,
                operatorAppUiService,
                org.mockito.Mockito.mock(com.ispf.server.ai.context.ContextPackSearchService.class)
        );
        context = new AgentContext("admin", null, new AgentRunState());
        lenient().when(aiToolRegistry.validateBundle(any(), any(), any(), eq(true)))
                .thenReturn(okShip());
        lenient().when(aiToolRegistry.dryRunDeploy(any(), any(), any(), eq(true)))
                .thenReturn(okShip());
    }

    private static Map<String, Object> okShip() {
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("status", "OK");
        ok.put("shipCanon", true);
        return ok;
    }

    private static Map<String, Object> logicHostError() {
        Map<String, Object> shipError = new LinkedHashMap<>();
        shipError.put("status", "ERROR");
        shipError.put("errors", List.of("[LOGIC_HOST_DEVICE] functions[0].objectPath: hosted on DEVICE"));
        shipError.put("issues", List.of(Map.of("code", "LOGIC_HOST_DEVICE", "severity", "ERROR")));
        shipError.put("shipCanon", true);
        return shipError;
    }

    @Test
    void deployPlaybookHasNineSteps() {
        assertEquals(9, AgentDeployPlaybook.steps().size());
    }

    @Test
    void getDeployPlaybookReturnsAllSteps() throws Exception {
        PlatformAgentTool tool = requireTool("get_deploy_playbook");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = tool.execute(Map.of(), context);
        assertEquals("OK", result.get("status"));
        assertEquals(9, result.get("stepCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) result.get("steps");
        assertEquals("discover", steps.get(0).get("id"));
        assertEquals("finish", steps.get(8).get("id"));
    }

    @Test
    void deployStepDiscoverMarksProgress() throws Exception {
        PlatformAgentTool tool = requireTool("deploy_step_discover");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = tool.execute(Map.of(), context);
        assertEquals("OK", result.get("status"));
        assertEquals("blueprint", result.get("nextStep"));
        assertTrue(context.runState().completedPlanSteps().contains("deploy:discover"));
    }

    @Test
    void runDeployPlaybookToolRegistered() {
        assertTrue(tools.stream().anyMatch(t -> "run_deploy_playbook".equals(t.name())));
    }

    @Test
    void deployStepImportPreservesPartialStatusAndDoesNotMarkCompleted() throws Exception {
        context.runState().markBundleValidated("demo-app");
        Map<String, Object> deployResult = new LinkedHashMap<>();
        deployResult.put("status", "PARTIAL");
        deployResult.put("errors", List.of("dashboard:x: boom"));
        when(bundleDeployService.deploy(eq("demo-app"), any())).thenReturn(deployResult);

        PlatformAgentTool tool = requireTool("deploy_step_import");
        Map<String, Object> args = Map.of(
                "appId", "demo-app",
                "manifest", Map.of(
                        "version", "1.0.0",
                        "displayName", "Demo"
                )
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> result = tool.execute(args, context);

        assertEquals("PARTIAL", result.get("status"));
        assertEquals("import", result.get("playbookStep"));
        assertFalse(context.runState().completedPlanSteps().contains("deploy:import"));
    }

    @Test
    void deployStepImportMarksCompletedOnlyWhenOk() throws Exception {
        context.runState().markBundleValidated("demo-app");
        Map<String, Object> deployResult = new LinkedHashMap<>();
        deployResult.put("status", "OK");
        deployResult.put("applied", List.of("register"));
        when(bundleDeployService.deploy(eq("demo-app"), any())).thenReturn(deployResult);

        PlatformAgentTool tool = requireTool("deploy_step_import");
        Map<String, Object> args = Map.of(
                "appId", "demo-app",
                "manifest", Map.of(
                        "version", "1.0.0",
                        "displayName", "Demo"
                )
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> result = tool.execute(args, context);

        assertEquals("OK", result.get("status"));
        assertEquals("import", result.get("playbookStep"));
        assertTrue(context.runState().completedPlanSteps().contains("deploy:import"));
    }

    @Test
    void deployStepValidateUsesShipCanonAndDoesNotMarkBundleOnLogicHostDevice() throws Exception {
        when(aiToolRegistry.validateBundle(eq("demo-app"), any(), eq("admin"), eq(true)))
                .thenReturn(logicHostError());
        PlatformAgentTool tool = requireTool("deploy_step_validate");
        Map<String, Object> result = tool.execute(manifestArgs("demo-app"), context);
        assertEquals("ERROR", result.get("status"));
        assertFalse(context.runState().isBundleValidated("demo-app"));
        verify(aiToolRegistry).validateBundle(eq("demo-app"), any(), eq("admin"), eq(true));
    }

    @Test
    void deployStepImportRefusesShipCanonFailureWithoutDeploying() throws Exception {
        context.runState().markBundleValidated("demo-app");
        when(aiToolRegistry.validateBundle(eq("demo-app"), any(), eq("admin"), eq(true)))
                .thenReturn(logicHostError());
        PlatformAgentTool tool = requireTool("deploy_step_import");
        Map<String, Object> result = tool.execute(manifestArgs("demo-app"), context);
        assertEquals("ERROR", result.get("status"));
        assertEquals("import", result.get("playbookStep"));
        assertFalse(context.runState().completedPlanSteps().contains("deploy:import"));
        verify(bundleDeployService, never()).deploy(eq("demo-app"), any());
    }

    private static Map<String, Object> manifestArgs(String appId) {
        return Map.of(
                "appId", appId,
                "manifest", Map.of(
                        "version", "1.0.0",
                        "displayName", "Demo"
                )
        );
    }

    private PlatformAgentTool requireTool(String name) {
        return tools.stream()
                .filter(tool -> name.equals(tool.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing tool: " + name));
    }
}
