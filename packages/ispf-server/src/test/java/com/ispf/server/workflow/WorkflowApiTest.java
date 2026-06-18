package com.ispf.server.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void demoWorkflowIsActiveWithBpmn() throws Exception {
        mockMvc.perform(get("/api/v1/workflows/by-path")
                        .param("path", "root.platform.workflows.demo-alarm-handler"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Demo Alarm Handler"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.bpmnXml").isNotEmpty());
    }

    @Test
    void runsWorkflowManually() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/by-path/run")
                        .param("path", "root.platform.workflows.demo-alarm-handler"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.containsString("COMPLETED")));
    }

    @Test
    void listsWorkflowModel() throws Exception {
        mockMvc.perform(get("/api/v1/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='workflow-v1')]").exists());
    }
}
