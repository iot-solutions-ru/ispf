package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-177: end-to-end agent deploy integration — playbook tools in sequence with
 * mes-platform-production bundle (no LLM).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentE2eDeployIntegrationTest {

    private static final String APP_ID = "mes-platform-production";

    @Autowired
    private PlatformAgentToolRegistry toolRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mesPlatformProductionDeployPlaybookPipelineWithoutLlm() throws Exception {
        String bundleJson = new ClassPathResource("mes-platform-production-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = objectMapper.readValue(bundleJson, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> operatorUi = (Map<String, Object>) manifest.get("operatorUi");

        AgentContext context = new AgentContext("admin", null, new AgentRunState());

        assertEquals("OK", step(toolRegistry, "get_deploy_playbook", Map.of(), context).get("status"));
        assertEquals(9, step(toolRegistry, "get_deploy_playbook", Map.of(), context).get("stepCount"));

        for (String guidanceStep : List.of(
                "deploy_step_discover",
                "deploy_step_blueprint"
        )) {
            assertEquals("OK", step(toolRegistry, guidanceStep, Map.of(), context).get("status"));
        }

        Map<String, Object> blueprintArgs = Map.of("appId", APP_ID);
        Map<String, Object> blueprint = step(toolRegistry, "get_example_bundle", blueprintArgs, context);
        assertEquals("OK", blueprint.get("status"));

        Map<String, Object> manifestArgs = Map.of("appId", APP_ID, "manifest", manifest);
        assertEquals("OK", step(toolRegistry, "deploy_step_validate", manifestArgs, context).get("status"));
        assertEquals("OK", step(toolRegistry, "deploy_step_dry_run", manifestArgs, context).get("status"));
        assertEquals("OK", step(toolRegistry, "deploy_step_import", manifestArgs, context).get("status"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dashboards = (List<Map<String, Object>>) operatorUi.get("dashboards");
        Map<String, Object> operatorUiArgs = new LinkedHashMap<>();
        operatorUiArgs.put("appId", operatorUi.get("appId"));
        operatorUiArgs.put("title", operatorUi.get("title"));
        operatorUiArgs.put("defaultDashboard", operatorUi.get("defaultDashboard"));
        operatorUiArgs.put("dashboards", dashboards);
        assertEquals("OK", step(toolRegistry, "deploy_step_operator_ui", operatorUiArgs, context).get("status"));

        assertEquals("OK", step(toolRegistry, "deploy_step_verify", Map.of("appId", APP_ID), context).get("status"));
        assertEquals("OK", step(toolRegistry, "deploy_step_automation", Map.of(), context).get("status"));
        assertEquals("OK", step(toolRegistry, "deploy_step_finish", Map.of("appId", APP_ID), context).get("status"));

        var completed = context.runState().completedPlanSteps();
        assertTrue(completed.contains("deploy:validate"));
        assertTrue(completed.contains("deploy:import"));
        assertTrue(completed.contains("deploy:operator_ui"));

        mockMvc.perform(get("/api/v1/operator-apps/" + APP_ID + "/ui"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value(APP_ID))
                .andExpect(jsonPath("$.dashboards[0].path").exists());

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "root.platform.devices.mes-platform-production-hub",
                                  "functionName": "mes_platform_listLines",
                                  "input": {
                                    "schema": { "name": "in", "fields": [] },
                                    "rows": [{}]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"));

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "root.platform.devices.mes-platform-production-hub",
                                  "functionName": "mes_batch_getStatus",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [{ "name": "batchPath", "type": "STRING" }]
                                    },
                                    "rows": [{
                                      "batchPath": "root.platform.mes.lots.batch-line-a01-001"
                                    }]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.phase").value("charge"));
    }

    private static Map<String, Object> step(
            PlatformAgentToolRegistry registry,
            String toolName,
            Map<String, Object> args,
            AgentContext context
    ) throws Exception {
        return registry.execute(toolName, args, context);
    }
}
