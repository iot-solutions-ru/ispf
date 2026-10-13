package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowTriggerIsolationTest {

    private static final String HEALTHY = "root.platform.workflows.trigger-isolation-healthy";
    private static final String BROKEN = "root.platform.workflows.trigger-isolation-broken";
    private static final String DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String EVENT_NAME = "triggerIsolationProbe";

    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn">
              <process id="trigger-isolation-healthy" name="Trigger Isolation Healthy" isExecutable="true">
                <startEvent id="start"/>
                <serviceTask id="mark" name="Mark"
                             ispf:action="setVariable"
                             ispf:targetObject="root.platform.workflows.trigger-isolation-healthy"
                             ispf:variable="lastAction"
                             ispf:value="isolation-ok"/>
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
    @Autowired
    private WorkflowInstanceRepository instanceRepository;
    @Autowired
    private WorkflowDeadLetterService deadLetterService;

    @BeforeEach
    void ensureWorkflows() throws Exception {
        ensureWorkflow("trigger-isolation-healthy", HEALTHY);
        ensureWorkflow("trigger-isolation-broken", BROKEN);
        mockMvc.perform(put("/api/v1/workflows/by-path/bpmn")
                        .param("path", HEALTHY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("bpmnXml", BPMN))))
                .andExpect(status().isOk());
        String trigger = """
                {"triggerType":"event","objectPath":"%s","eventName":"%s"}
                """.formatted(DEVICE, EVENT_NAME).trim();
        for (String path : List.of(HEALTHY, BROKEN)) {
            objectManager.setVariableValue(path, "triggerJson", stringRecord("triggerJson", trigger));
            objectManager.setVariableValue(path, "status", stringRecord("status", WorkflowLifecycleStatus.ACTIVE.name()));
            objectManager.persistNodeTree(path);
        }
        triggerIndex.rebuild();
    }

    @Test
    void failingWorkflowDoesNotRollBackTheSiblingStart() {
        int healthyRunsBefore = instanceRepository.findByWorkflowPathOrderByStartedAtDesc(HEALTHY).size();
        int brokenLettersBefore = deadLetterService.listByPath(BROKEN).size();

        assertThatThrownBy(() -> workflowService.handleEventTrigger(DEVICE, EVENT_NAME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(BROKEN)
                .hasMessageContaining("Workflow BPMN is empty");

        assertThat(instanceRepository.findByWorkflowPathOrderByStartedAtDesc(HEALTHY))
                .hasSize(healthyRunsBefore + 1);
        assertThat(deadLetterService.listByPath(BROKEN)).hasSize(brokenLettersBefore + 1);
    }

    private void ensureWorkflow(String name, String path) {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    "root.platform.workflows",
                    name,
                    ObjectType.WORKFLOW,
                    name,
                    "Trigger isolation probe",
                    "workflow-v1"
            );
            workflowService.ensureWorkflowStructure(path);
        }
    }

    private static DataRecord stringRecord(String name, String value) {
        return DataRecord.single(
                DataSchema.builder(name).field("value", FieldType.STRING).build(),
                Map.of("value", value)
        );
    }
}
