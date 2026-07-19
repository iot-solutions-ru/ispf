package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.WorkflowRetryScheduleRepository;
import com.ispf.server.persistence.entity.WorkflowRetryScheduleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowRetryIntegrationTest {

    private static final String WORKFLOW = "root.platform.workflows.retry-demo";

    private static final String FAILING_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:ispf="http://ispf.io/bpmn">
              <process id="retry-demo" name="Retry Demo" isExecutable="true">
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

    @Autowired
    private WorkflowRetryService retryService;

    @Autowired
    private WorkflowRetryScheduler retryScheduler;

    @Autowired
    private WorkflowRetryScheduleRepository retryRepository;

    @Autowired
    private WorkflowDeadLetterService deadLetterService;

    @BeforeEach
    void ensureWorkflow() throws Exception {
        if (objectManager.tree().findByPath(WORKFLOW).isEmpty()) {
            objectManager.create(
                    "root.platform.workflows",
                    "retry-demo",
                    ObjectType.WORKFLOW,
                    "Retry Demo",
                    "Fails for async retry coverage",
                    "workflow-v1"
            );
            workflowService.ensureWorkflowStructure(WORKFLOW);
        }

        mockMvc.perform(put("/api/v1/workflows/by-path/bpmn")
                        .param("path", WORKFLOW)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("bpmnXml", FAILING_BPMN))))
                .andExpect(status().isOk());

        DataSchema stringSchema = DataSchema.builder("stringValue").field("value", FieldType.STRING).build();
        objectManager.setVariableValue(WORKFLOW, "status",
                DataRecord.single(stringSchema, Map.of("value", WorkflowLifecycleStatus.DRAFT.name())));
        objectManager.setVariableValue(WORKFLOW, "retryMaxAttempts",
                DataRecord.single(stringSchema, Map.of("value", "2")));
        objectManager.setVariableValue(WORKFLOW, "retryBackoffSeconds",
                DataRecord.single(stringSchema, Map.of("value", "0")));
        objectManager.persistNodeTree(WORKFLOW);
    }

    @Test
    void failedRunSchedulesRetryThenDeadLettersAfterExhaustion() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/by-path/run").param("path", WORKFLOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceState").value(org.hamcrest.Matchers.containsString("FAILED")));

        List<WorkflowRetryScheduleEntity> pending = retryRepository.findDue(Instant.now().plusSeconds(5));
        assertThat(pending).isNotEmpty();
        assertThat(pending.get(0).getWorkflowPath()).isEqualTo(WORKFLOW);
        assertThat(pending.get(0).getStatus()).isEqualTo(WorkflowRetryService.STATUS_PENDING);
        assertThat(deadLetterService.listUnresolvedByPath(WORKFLOW)).isEmpty();

        retryScheduler.runDueRetries();

        List<WorkflowRetryScheduleEntity> after = retryService.listByPath(WORKFLOW);
        assertThat(after).anyMatch(r -> WorkflowRetryService.STATUS_DONE.equals(r.getStatus())
                || WorkflowRetryService.STATUS_FAILED.equals(r.getStatus()));
        assertThat(deadLetterService.listUnresolvedByPath(WORKFLOW)).isNotEmpty();
    }
}
