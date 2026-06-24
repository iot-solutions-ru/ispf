package com.ispf.server.workflow;

import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.ObjectType;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowEventTriggerTest {

    private static final String WORKFLOW = "root.platform.workflows.event-trigger-demo";
    private static final String DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String EVENT_NAME = "thresholdExceeded";

    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn">
              <process id="event-trigger-demo" name="Event Trigger Demo" isExecutable="true">
                <startEvent id="start"/>
                <serviceTask id="mark" name="Mark"
                             ispf:action="setVariable"
                             ispf:targetObject="root.platform.workflows.event-trigger-demo"
                             ispf:variable="lastAction"
                             ispf:value="event-triggered"/>
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
    private WorkflowEventTriggerIndex triggerIndex;

    @BeforeEach
    void ensureEventTriggerWorkflow() throws Exception {
        if (objectManager.tree().findByPath(WORKFLOW).isEmpty()) {
            objectManager.create(
                    "root.platform.workflows",
                    "event-trigger-demo",
                    ObjectType.WORKFLOW,
                    "Event Trigger Demo",
                    "Starts on matching platform event",
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
                "triggerJson",
                DataRecord.single(
                        DataSchema.builder("triggerJson").field("value", FieldType.STRING).build(),
                        Map.of("value", """
                                {"triggerType":"event","objectPath":"%s","eventName":"%s"}
                                """.formatted(DEVICE, EVENT_NAME).trim())
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
        triggerIndex.rebuild();
    }

    @Test
    void eventFiredStartsMatchingActiveWorkflow() throws Exception {
        mockMvc.perform(get("/api/v1/workflows/by-path").param("path", WORKFLOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("event-triggered"))));

        mockMvc.perform(post("/api/v1/events/fire")
                        .param("objectPath", DEVICE)
                        .param("eventName", EVENT_NAME))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workflows/by-path").param("path", WORKFLOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.containsString("COMPLETED")))
                .andExpect(jsonPath("$.lastRunAt").isNotEmpty());
    }
}
