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

    private PlatformAgentTool requireTool(String name) {
        return tools.stream()
                .filter(tool -> name.equals(tool.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing tool: " + name));
    }
}
