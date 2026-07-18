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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private PlatformAgentTool requireTool(String name) {
        return tools.stream()
                .filter(tool -> name.equals(tool.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing tool: " + name));
    }
}
