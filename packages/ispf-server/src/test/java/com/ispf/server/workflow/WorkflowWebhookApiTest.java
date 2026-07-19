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
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowWebhookApiTest {

    private static final String WORKFLOW = "root.platform.workflows.webhook-trigger-demo";
    private static final String SLUG = "webhook-trigger-demo";

    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn">
              <process id="webhook-trigger-demo" name="Webhook Trigger Demo" isExecutable="true">
                <startEvent id="start"/>
                <serviceTask id="mark" name="Mark"
                             ispf:action="setVariable"
                             ispf:targetObject="root.platform.workflows.webhook-trigger-demo"
                             ispf:variable="lastAction"
                             ispf:value="webhook-triggered"/>
                <endEvent id="end"/>
                <sequenceFlow sourceRef="start" targetRef="mark"/>
                <sequenceFlow sourceRef="mark" targetRef="end"/>
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

    @Autowired
    private WorkflowWebhookIndex webhookIndex;

    @BeforeEach
    void ensureWebhookWorkflow() throws Exception {
        if (objectManager.tree().findByPath(WORKFLOW).isEmpty()) {
            objectManager.create(
                    "root.platform.workflows",
                    "webhook-trigger-demo",
                    ObjectType.WORKFLOW,
                    "Webhook Trigger Demo",
                    "Starts from inbound webhook slug",
                    "workflow-v1"
            );
            workflowService.ensureWorkflowStructure(WORKFLOW);
        }

        mockMvc.perform(put("/api/v1/workflows/by-path/bpmn")
                        .param("path", WORKFLOW)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("bpmnXml", BPMN))))
                .andExpect(status().isOk());

        objectManager.setVariableValue(
                WORKFLOW,
                "webhookSlug",
                DataRecord.single(
                        DataSchema.builder("webhookSlug").field("value", FieldType.STRING).build(),
                        Map.of("value", SLUG)
                )
        );
        objectManager.setVariableValue(
                WORKFLOW,
                "status",
                DataRecord.single(
                        DataSchema.builder("status").field("value", FieldType.STRING).build(),
                        Map.of("value", WorkflowLifecycleStatus.ACTIVE.name())
                )
        );
        objectManager.persistNodeTree(WORKFLOW);
        webhookIndex.indexPath(WORKFLOW);
    }

    @Test
    void webhookStartsActiveWorkflowWithPayload() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/workflows/" + SLUG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "alarmId", "A-9",
                                "severity", "high"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.workflowPath").value(WORKFLOW))
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.containsString("COMPLETED")));

        mockMvc.perform(get("/api/v1/workflows/by-path").param("path", WORKFLOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.containsString("COMPLETED")))
                .andExpect(jsonPath("$.lastRunAt").isNotEmpty());
    }

    @Test
    void unknownWebhookSlugIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/workflows/no-such-slug")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }
}
