package com.ispf.server.workflow;

import com.ispf.plugin.workflow.BpmnProcess;
import com.ispf.plugin.workflow.InstanceStatus;
import com.ispf.plugin.workflow.WorkflowInstance;
import com.ispf.plugin.workflow.WorkflowStepRecord;
import com.ispf.server.persistence.WorkflowExecutionStepRepository;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.persistence.WorkflowUserTaskRepository;
import com.ispf.server.spi.WorkflowObjectAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowStepSerializeFailureTest {

    @Mock
    private WorkflowInstanceRepository instanceRepository;
    @Mock
    private WorkflowUserTaskRepository userTaskRepository;
    @Mock
    private WorkflowExecutionStepRepository stepRepository;
    @Mock
    private WorkflowObjectAccess objects;

    @Test
    void unserializableStepPayloadFailsTheJournalWrite() {
        BpmnProcess process = mock(BpmnProcess.class);
        when(process.id()).thenReturn("proc");
        WorkflowInstance instance = WorkflowInstance.restore(
                "inst-step",
                "root.platform.workflows.step-json",
                InstanceStatus.COMPLETED,
                "end",
                Instant.parse("2026-10-06T06:00:00Z"),
                Instant.parse("2026-10-06T06:00:01Z"),
                null,
                null,
                List.of("START"),
                Map.of(),
                null,
                null
        );
        Map<String, Object> loop = new HashMap<>();
        loop.put("self", loop);
        instance.addPendingStep(new WorkflowStepRecord(
                "tok",
                1,
                "boom",
                "serviceTask",
                Instant.parse("2026-10-06T06:00:00Z"),
                Instant.parse("2026-10-06T06:00:01Z"),
                "FAILED",
                1,
                loop,
                Map.of(),
                Map.of()
        ));
        WorkflowInstanceStore store = new WorkflowInstanceStore(
                instanceRepository,
                userTaskRepository,
                stepRepository,
                objects,
                new ObjectMapper()
        );

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> store.save(instance, process, null, null));
        assertThat(error.getMessage()).contains("Failed to serialize workflow step");
        verify(stepRepository, never()).save(any());
    }
}
