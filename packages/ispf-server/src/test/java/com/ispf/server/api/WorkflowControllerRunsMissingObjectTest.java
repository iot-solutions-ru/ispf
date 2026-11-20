package com.ispf.server.api;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.workflow.WorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowControllerRunsMissingObjectTest {

    @Mock
    private WorkflowService workflowService;
    @Mock
    private ObjectAccessService objectAccessService;

    @Test
    void missingObjectRejectsRunsAndDeadLetters() {
        String path = "root.platform.workflows.NO_SUCH";
        when(workflowService.getWorkflow(path)).thenThrow(new ObjectNotFoundException(path));

        assertThrows(ObjectNotFoundException.class, () -> controller().runs(path));
        assertThrows(ObjectNotFoundException.class, () -> controller().deadLetters(path, true));
        verify(workflowService, never()).listRuns(any());
        verify(workflowService, never()).listDeadLetters(any(), anyBoolean());
    }

    @Test
    void nonWorkflowRejectsRunsAndDeadLetters() {
        String path = "root.platform.devices.room";
        when(workflowService.getWorkflow(path))
                .thenThrow(new IllegalArgumentException("Not a workflow object: " + path));

        IllegalArgumentException runs = assertThrows(IllegalArgumentException.class, () -> controller().runs(path));
        IllegalArgumentException deadLetters = assertThrows(
                IllegalArgumentException.class,
                () -> controller().deadLetters(path, true)
        );

        assertEquals("Not a workflow object: " + path, runs.getMessage());
        assertEquals("Not a workflow object: " + path, deadLetters.getMessage());
        verify(workflowService, never()).listRuns(any());
        verify(workflowService, never()).listDeadLetters(any(), anyBoolean());
    }

    @Test
    void workflowWithoutRunsReturnsEmptyLists() {
        String path = "root.platform.workflows.empty";
        when(workflowService.getWorkflow(path)).thenReturn(new WorkflowService.WorkflowView(
                path,
                "empty",
                WorkflowLifecycleStatus.DRAFT,
                "",
                "{}",
                null,
                "{}",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
        when(workflowService.listRuns(path)).thenReturn(List.of());
        when(workflowService.listDeadLetters(path, true)).thenReturn(List.of());

        assertEquals(List.of(), controller().runs(path));
        assertEquals(List.of(), controller().deadLetters(path, true));
    }

    private WorkflowController controller() {
        return new WorkflowController(workflowService, objectAccessService);
    }
}
