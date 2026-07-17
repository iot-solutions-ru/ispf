package com.ispf.server.ai.agent;

import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-177 platform velocity: deploy playbook on a domain-agnostic fixture (no MES/HVAC asserts).
 * Proves tree + dashboard + operator UI path without vertical BFF checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentPlatformPrimitiveDeployIntegrationTest {

    private static final String APP_ID = "platform-primitive";
    private static final String HUB_PATH = "root.platform.devices.platform-primitive-hub";
    private static final String DASHBOARD_PATH = "root.platform.dashboards.platform-primitive-overview";

    @Autowired
    private PlatformAgentToolRegistry toolRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void platformPrimitiveDeployPlaybookWithoutLlm() throws Exception {
        String bundleJson = new ClassPathResource("platform-primitive-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = objectMapper.readValue(bundleJson, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> operatorUi = (Map<String, Object>) manifest.get("operatorUi");

        AgentContext context = new AgentContext("admin", null, new AgentRunState());
        Map<String, Object> manifestArgs = Map.of("appId", APP_ID, "manifest", manifest);

        assertEquals("OK", step("get_deploy_playbook", Map.of(), context).get("status"));
        assertEquals("OK", step("deploy_step_discover", Map.of(), context).get("status"));
        assertEquals("OK", step("deploy_step_blueprint", Map.of(), context).get("status"));
        assertEquals("OK", step("deploy_step_validate", manifestArgs, context).get("status"));
        assertEquals("OK", step("deploy_step_dry_run", manifestArgs, context).get("status"));
        assertEquals("OK", step("deploy_step_import", manifestArgs, context).get("status"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dashboards = (List<Map<String, Object>>) operatorUi.get("dashboards");
        Map<String, Object> operatorUiArgs = new LinkedHashMap<>();
        operatorUiArgs.put("appId", operatorUi.get("appId"));
        operatorUiArgs.put("title", operatorUi.get("title"));
        operatorUiArgs.put("defaultDashboard", operatorUi.get("defaultDashboard"));
        operatorUiArgs.put("dashboards", dashboards);
        assertEquals("OK", step("deploy_step_operator_ui", operatorUiArgs, context).get("status"));
        assertEquals("OK", step("deploy_step_verify", Map.of("appId", APP_ID), context).get("status"));
        assertEquals("OK", step("deploy_step_finish", Map.of("appId", APP_ID), context).get("status"));

        assertTrue(context.runState().completedPlanSteps().contains("deploy:import"));
        assertTrue(objectManager.tree().findByPath(HUB_PATH).isPresent(), "hub missing");
        assertTrue(objectManager.tree().findByPath(DASHBOARD_PATH).isPresent(), "dashboard missing");

        mockMvc.perform(get("/api/v1/operator-apps/" + APP_ID + "/ui"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value(APP_ID))
                .andExpect(jsonPath("$.defaultDashboard").value(DASHBOARD_PATH))
                .andExpect(jsonPath("$.dashboards[0].path").value(DASHBOARD_PATH));
    }

    private Map<String, Object> step(String toolName, Map<String, Object> args, AgentContext context)
            throws Exception {
        return toolRegistry.execute(toolName, args, context);
    }
}
