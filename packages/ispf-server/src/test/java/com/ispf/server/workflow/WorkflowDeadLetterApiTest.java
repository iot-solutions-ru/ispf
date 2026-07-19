package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowDeadLetterApiTest {

    private static final String WORKFLOW = "root.platform.workflows.dead-letter-demo";

    private static final String FAILING_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn">
              <process id="dead-letter-demo" name="Dead Letter Demo" isExecutable="true">
                <startEvent id="start"/>
                <serviceTask id="boom" name="Boom"
                             ispf:action="setVariable"
                             ispf:variable="lastAction"
                             ispf:value="should-fail"/>
                <endEvent id="end"/>
                <sequenceFlow sourceRef="start" targetRef="boom"/>
                <sequenceFlow sourceRef="boom" targetRef="end"/>
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
    void ensureFailingWorkflow() throws Exception {
        if (objectManager.tree().findByPath(WORKFLOW).isEmpty()) {
            objectManager.create(
                    "root.platform.workflows",
                    "dead-letter-demo",
                    ObjectType.WORKFLOW,
                    "Dead Letter Demo",
                    "Fails on purpose for DLQ coverage",
                    "workflow-v1"
            );
            workflowService.ensureWorkflowStructure(WORKFLOW);
        }

        mockMvc.perform(put("/api/v1/workflows/by-path/bpmn")
                        .param("path", WORKFLOW)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("bpmnXml", FAILING_BPMN))))
                .andExpect(status().isOk());

        objectManager.setVariableValue(
                WORKFLOW,
                "status",
                DataRecord.single(
                        DataSchema.builder("status").field("value", FieldType.STRING).build(),
                        Map.of("value", WorkflowLifecycleStatus.DRAFT.name())
                )
        );
        objectManager.persistNodeTree(WORKFLOW);
    }

    @Test
    void failedRunRecordsDeadLetterAndSupportsResolve() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/by-path/run").param("path", WORKFLOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.containsString("FAILED")));

        MvcResult listed = mockMvc.perform(get("/api/v1/workflows/by-path/dead-letters")
                        .param("path", WORKFLOW)
                        .param("unresolvedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workflowPath").value(WORKFLOW))
                .andExpect(jsonPath("$[0].instanceId").isNotEmpty())
                .andExpect(jsonPath("$[0].lastError").isNotEmpty())
                .andExpect(jsonPath("$[0].resolvedAt").doesNotExist())
                .andReturn();

        JsonNode rows = objectMapper.readTree(listed.getResponse().getContentAsString());
        assertThat(rows.isArray()).isTrue();
        assertThat(rows.size()).isGreaterThanOrEqualTo(1);
        String id = rows.get(0).get("id").asText();

        mockMvc.perform(post("/api/v1/workflows/dead-letters/" + id + "/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/workflows/by-path/dead-letters")
                        .param("path", WORKFLOW)
                        .param("unresolvedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')]").doesNotExist());

        mockMvc.perform(get("/api/v1/workflows/by-path/dead-letters")
                        .param("path", WORKFLOW)
                        .param("unresolvedOnly", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].resolvedAt").exists());
    }
}
