package com.ispf.server.workflow;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.workflow.InstanceStatus;
import com.ispf.plugin.workflow.WorkflowEngine;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.spi.WorkflowObjectAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowSignalMissingObjectTest {

    @Mock
    private WorkflowService workflows;
    @Mock
    private WorkflowObjectAccess objects;
    @Mock
    private WorkflowEngine workflowEngine;
    @Mock
    private WorkflowInstanceStore instanceStore;
    @Mock
    private WorkflowTaskExecutor taskExecutor;
    @Mock
    private WorkflowConditionFactory conditionFactory;
    @Mock
    private WorkflowInstanceRepository instanceRepository;

    @Test
    void missingWorkflowIsRejectedBeforeWaitingInstances() {
        String path = "root.platform.workflows.cc-wave-1192-missing";
        when(objects.require(path)).thenThrow(new ObjectNotFoundException(path));

        assertThrows(
                ObjectNotFoundException.class,
                () -> control().deliverSignalByWorkflowPath(path, "probe", "operator")
        );
        verify(instanceRepository, never()).findByWorkflowPathAndStatus(any(), any());
    }

    @Test
    void existingWorkflowWithoutWaitingInstancesSignalsNone() throws Exception {
        String path = "root.platform.workflows.signal-empty";
        when(objects.require(path)).thenReturn(org.mockito.Mockito.mock(PlatformObject.class));
        when(instanceRepository.findByWorkflowPathAndStatus(path, InstanceStatus.WAITING.name()))
                .thenReturn(List.of());

        Map<String, Object> result = control().deliverSignalByWorkflowPath(path, "probe", "operator");

        assertEquals(0, result.get("signaledCount"));
        assertEquals(List.of(), result.get("instanceIds"));
    }

    private WorkflowInstanceControl control() {
        return new WorkflowInstanceControl(
                workflows,
                objects,
                workflowEngine,
                instanceStore,
                taskExecutor,
                conditionFactory,
                instanceRepository
        );
    }
}
