package com.ispf.server.workflow;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.plugin.workflow.BpmnProcess;
import com.ispf.plugin.workflow.InstanceStatus;
import com.ispf.plugin.workflow.UserTaskDefinition;
import com.ispf.plugin.workflow.WorkflowInstance;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowOperatorAppResolveTest {

    private static final String PATH = "root.platform.workflows.operator-app";

    @Mock
    private WorkflowInstanceRepository instanceRepository;
    @Mock
    private WorkflowUserTaskRepository userTaskRepository;
    @Mock
    private WorkflowExecutionStepRepository stepRepository;
    @Mock
    private WorkflowObjectAccess objects;

    @Test
    void missingWorkflowFailsUserTaskCreateInsteadOfDroppingTheApp() {
        when(objects.require(PATH)).thenThrow(new ObjectNotFoundException(PATH));
        BpmnProcess process = mock(BpmnProcess.class);
        when(process.id()).thenReturn("proc");
        WorkflowInstance instance = WorkflowInstance.restore(
                "inst-operator-app",
                PATH,
                InstanceStatus.WAITING,
                "ack",
                Instant.parse("2026-10-06T06:00:00Z"),
                null,
                null,
                "ack",
                List.of("START"),
                Map.of(),
                null,
                null
        );
        WorkflowInstanceStore store = new WorkflowInstanceStore(
                instanceRepository,
                userTaskRepository,
                stepRepository,
                objects,
                new ObjectMapper()
        );

        assertThrows(ObjectNotFoundException.class, () -> store.save(
                instance,
                process,
                null,
                new UserTaskDefinition("ack", "Ack", "Ack", "", "", Map.of())
        ));
        verify(userTaskRepository, never()).save(any());
    }
}
