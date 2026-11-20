package com.ispf.server.api;

import com.ispf.server.workflow.WorkflowInstanceCancelService;
import com.ispf.server.workflow.WorkflowInstanceNotFoundException;
import com.ispf.server.workflow.WorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowInstanceControllerStepsMissingInstanceTest {

    @Mock
    private WorkflowInstanceCancelService cancelService;
    @Mock
    private WorkflowService workflowService;

    @Test
    void missingInstanceIsNotFound() {
        when(workflowService.listSteps("no-such-instance-716"))
                .thenThrow(new WorkflowInstanceNotFoundException("no-such-instance-716"));

        WorkflowInstanceNotFoundException exception = assertThrows(
                WorkflowInstanceNotFoundException.class,
                () -> controller().steps("no-such-instance-716")
        );

        assertEquals("Workflow instance not found: no-such-instance-716", exception.getMessage());
    }

    @Test
    void existingInstanceWithoutStepsReturnsEmpty() {
        when(workflowService.listSteps("present")).thenReturn(List.of());

        assertEquals(List.of(), controller().steps("present"));
    }

    private WorkflowInstanceController controller() {
        return new WorkflowInstanceController(cancelService, workflowService);
    }
}
