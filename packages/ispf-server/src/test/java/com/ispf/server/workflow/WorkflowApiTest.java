package com.ispf.server.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowApiTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String DEMO_WORKFLOW = "root.platform.workflows.demo-alarm-handler";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void demoWorkflowIsActiveWithBpmn() throws Exception {
        mockMvc.perform(get("/api/v1/workflows/by-path")
                        .param("path", DEMO_WORKFLOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Demo Alarm Handler"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.bpmnXml").isNotEmpty());
    }

    @Test
    void runsWorkflowManually() throws Exception {
        // Honest default path: trigger present, condition evaluates to false → notify → COMPLETED.
        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", DEMO_DEVICE)
                        .param("name", "alarmAcknowledged")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": {
                                    "name": "alarmAcknowledged",
                                    "fields": [{"name": "value", "type": "BOOLEAN"}]
                                  },
                                  "rows": [{"value": true}]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workflows/by-path/run")
                        .param("path", DEMO_WORKFLOW)
                        .param("triggerObjectPath", DEMO_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.containsString("COMPLETED")));
    }

    @Test
    void manualRunWithoutTriggerFailsOnGatewayCondition() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/by-path/run")
                        .param("path", DEMO_WORKFLOW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Workflow gateway condition failed")))
                .andExpect(jsonPath("$.detail", containsString("missing trigger object")));
    }

    @Test
    void listsWorkflowModel() throws Exception {
        mockMvc.perform(get("/api/v1/blueprints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='workflow-v1')]").exists());
    }
}
