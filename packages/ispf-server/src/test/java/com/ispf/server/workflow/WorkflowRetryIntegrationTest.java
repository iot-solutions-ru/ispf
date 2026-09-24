package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.object.ObjectManager;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

        // findDue only returns rows already due. A backoff larger than that window, or a
        // late insert under CI load, leaves the schedule invisible to findDue(now + 5s).
        WorkflowRetryScheduleEntity pending = awaitDueSchedule();
        assertThat(pending.getStatus()).isEqualTo(WorkflowRetryService.STATUS_PENDING);
        assertThat(deadLetterService.listUnresolvedByPath(WORKFLOW)).isEmpty();

        retryScheduler.runDueRetries();

        List<WorkflowRetryScheduleEntity> after = awaitTerminalSchedule();
        assertThat(after).anyMatch(WorkflowRetryIntegrationTest::isTerminal);
        assertThat(deadLetterService.listUnresolvedByPath(WORKFLOW)).isNotEmpty();
    }

    private WorkflowRetryScheduleEntity awaitDueSchedule() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        WorkflowRetryScheduleEntity seen = null;
        while (System.nanoTime() < deadline) {
            seen = retryService.listByPath(WORKFLOW).stream()
                    .filter(row -> WorkflowRetryService.STATUS_PENDING.equals(row.getStatus()))
                    .findFirst()
                    .orElse(null);
            if (seen != null) {
                long waitMs = Duration.between(Instant.now(), seen.getDueAt()).toMillis();
                if (waitMs > 0) {
                    Thread.sleep(Math.min(waitMs + 50, 35_000));
                }
                if (!seen.getDueAt().isAfter(Instant.now())) {
                    return seen;
                }
            }
            Thread.sleep(50);
        }
        assertThat(seen).as("retry schedule for %s", WORKFLOW).isNotNull();
        assertThat(seen.getDueAt()).isBeforeOrEqualTo(Instant.now());
        return seen;
    }

    private List<WorkflowRetryScheduleEntity> awaitTerminalSchedule() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        List<WorkflowRetryScheduleEntity> after = List.of();
        while (System.nanoTime() < deadline) {
            after = retryService.listByPath(WORKFLOW);
            if (after.stream().anyMatch(WorkflowRetryIntegrationTest::isTerminal)
                    && !deadLetterService.listUnresolvedByPath(WORKFLOW).isEmpty()) {
                return after;
            }
            Thread.sleep(50);
        }
        return retryService.listByPath(WORKFLOW);
    }

    private static boolean isTerminal(WorkflowRetryScheduleEntity row) {
        return WorkflowRetryService.STATUS_DONE.equals(row.getStatus())
                || WorkflowRetryService.STATUS_FAILED.equals(row.getStatus());
    }
}
