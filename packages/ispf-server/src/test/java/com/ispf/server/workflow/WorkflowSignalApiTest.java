package com.ispf.server.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowSignalApiTest {

    private static final String SIGNAL_WORKFLOW = "root.platform.workflows.signal-demo";
    private static final String SIGNAL_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn">
              <process id="signal-demo" name="Signal Demo" isExecutable="true">
                <startEvent id="start"/>
                <intermediateCatchEvent id="waitIncident" ispf:signal="incidentRegistered"/>
                <serviceTask id="done" name="Done" ispf:action="log" ispf:message="Resumed"/>
                <endEvent id="end"/>
                <sequenceFlow sourceRef="start" targetRef="waitIncident"/>
                <sequenceFlow sourceRef="waitIncident" targetRef="done"/>
                <sequenceFlow sourceRef="done" targetRef="end"/>
              </process>
            </definitions>
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private WorkflowService workflowService;

    @BeforeEach
    void ensureSignalWorkflow() throws Exception {
        if (objectManager.tree().findByPath(SIGNAL_WORKFLOW).isEmpty()) {
            objectManager.create(
                    "root.platform.workflows",
                    "signal-demo",
                    ObjectType.WORKFLOW,
                    "Signal Demo",
                    "Waits for BPMN signal catch",
                    "workflow-v1"
            );
            workflowService.ensureWorkflowStructure(SIGNAL_WORKFLOW);
        }
        mockMvc.perform(put("/api/v1/workflows/by-path/bpmn")
                        .param("path", SIGNAL_WORKFLOW)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("bpmnXml", SIGNAL_BPMN))))
                .andExpect(status().isOk());
    }

    @Test
    void runWaitsForSignalThenCompletesViaInstanceApi() throws Exception {
        MvcResult runResult = mockMvc.perform(post("/api/v1/workflows/by-path/run").param("path", SIGNAL_WORKFLOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.containsString("WAITING")))
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.containsString("incidentRegistered")))
                .andReturn();

        JsonNode state = objectMapper.readTree(
                objectMapper.readTree(runResult.getResponse().getContentAsString()).path("instanceState").asText()
        );
        String instanceId = state.path("instanceId").asText();

        mockMvc.perform(post("/api/v1/workflows/instances/" + instanceId + "/signal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"signal":"incidentRegistered","operatorId":"operator-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(post("/api/v1/workflows/by-path/run").param("path", SIGNAL_WORKFLOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.containsString("WAITING")));
    }

    @Test
    void broadcastSignalByWorkflowPath() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/by-path/run").param("path", SIGNAL_WORKFLOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.containsString("WAITING")));

        mockMvc.perform(post("/api/v1/workflows/signal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workflowPath":"root.platform.workflows.signal-demo",
                                  "signal":"incidentRegistered",
                                  "operatorId":"operator-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signaledCount").value(1));
    }
}
