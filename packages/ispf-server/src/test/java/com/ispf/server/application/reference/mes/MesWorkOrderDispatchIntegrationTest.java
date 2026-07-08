package com.ispf.server.application.reference.mes;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-166 hardening: work-order dispatch BPMN full cycle (run → work-queue → confirm → COMPLETED).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesWorkOrderDispatchIntegrationTest {

    private static final String HUB = "root.platform.devices.mes-platform-hub";
    private static final String WORKFLOW = "root.platform.workflows.mes-work-order-dispatch";
    private static final String OPERATOR_APP = "mes-platform";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void dispatchWorkflowRunsOperatorConfirmTaskToCompletion() throws Exception {
        deployBundle();

        mockMvc.perform(get("/api/v1/workflows/by-path").param("path", WORKFLOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.operatorAppId").value(OPERATOR_APP));

        mockMvc.perform(post("/api/v1/workflows/by-path/run")
                        .param("path", WORKFLOW)
                        .param("triggerObjectPath", HUB))
                .andExpect(status().isOk());

        String taskId = awaitWorkQueueTask();
        mockMvc.perform(post("/api/v1/work-queue/claim")
                        .param("taskId", taskId)
                        .param("operatorId", "operator"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/work-queue/complete")
                        .param("taskId", taskId)
                        .param("operatorId", "operator"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workflows/by-path").param("path", WORKFLOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceState").value(containsString("COMPLETED")));
    }

    private void deployBundle() throws Exception {
        String bundle = new ClassPathResource("mes-platform-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/applications/mes-platform/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    private String awaitWorkQueueTask() throws Exception {
        for (int i = 0; i < 80; i++) {
            MvcResult result = mockMvc.perform(get("/api/v1/work-queue").param("operatorAppId", OPERATOR_APP))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode tasks = objectMapper.readTree(result.getResponse().getContentAsString());
            if (tasks.isArray() && !tasks.isEmpty()) {
                return tasks.get(0).get("id").asText();
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Work queue task did not appear");
    }
}
