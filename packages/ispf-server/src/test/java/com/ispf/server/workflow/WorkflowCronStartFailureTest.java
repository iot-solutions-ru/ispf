package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.spi.LeaderLock;
import com.ispf.server.spi.WorkflowObjectAccess;
import com.ispf.server.spi.WorkflowStartTrigger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowCronStartFailureTest {

    private static final String FAILED = "root.platform.workflows.cron-start-fail";
    private static final String NEXT = "root.platform.workflows.cron-start-next";

    @Mock
    private WorkflowObjectAccess objects;
    @Mock
    private WorkflowService workflowService;
    @Mock
    private WorkflowDeadLetterService deadLetterService;
    @Mock
    private LeaderLock leaderLock;
    @Mock
    private ClusterProperties clusterProperties;

    @Test
    void failedCronStartIsStoredAndTheNextWorkflowStillRuns() throws Exception {
        when(clusterProperties.isSchedulerActive()).thenReturn(true);
        when(leaderLock.runIfLeader(eq(WorkflowCronTriggerService.LOCK_NAME), any(), any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        });
        when(leaderLock.isHeld(WorkflowCronTriggerService.LOCK_NAME)).thenReturn(true);
        when(objects.isInitialized()).thenReturn(true);
        PlatformObject failed = workflow(FAILED);
        PlatformObject next = workflow(NEXT);
        when(objects.childrenOf("root.platform.workflows")).thenReturn(List.of(failed, next));
        doThrow(new WorkflowException("Workflow BPMN is empty: " + FAILED)).when(workflowService).runWorkflow(
                eq(FAILED),
                isNull(),
                eq(WorkflowStartTrigger.EVENT),
                eq(Map.of("cronExpression", "every:1m"))
        );

        new WorkflowCronTriggerService(objects, workflowService, deadLetterService, leaderLock, clusterProperties).poll();

        verify(deadLetterService).recordCommitted(
                eq("cron-start"),
                eq(FAILED),
                eq(1),
                eq("Workflow BPMN is empty: " + FAILED),
                contains("\"cronExpression\":\"every:1m\"")
        );
        ArgumentCaptor<DataRecord> state = ArgumentCaptor.forClass(DataRecord.class);
        verify(objects).setVariableValue(eq(FAILED), eq("instanceState"), state.capture());
        assertThat(state.getValue().firstRow().get("value").toString())
                .contains("FAILED")
                .contains("Workflow BPMN is empty: " + FAILED);
        verify(objects, never()).setVariableValue(eq(FAILED), eq("lastRunAt"), any());
        verify(workflowService).runWorkflow(
                eq(NEXT),
                isNull(),
                eq(WorkflowStartTrigger.EVENT),
                eq(Map.of("cronExpression", "every:1m"))
        );
    }

    private PlatformObject workflow(String path) {
        PlatformObject node = mock(PlatformObject.class);
        Variable status = stringVariable("ACTIVE");
        Variable cron = stringVariable("every:1m");
        when(node.type()).thenReturn(ObjectType.WORKFLOW);
        when(node.path()).thenReturn(path);
        when(node.getVariable("status")).thenReturn(Optional.of(status));
        when(node.getVariable("cronExpression")).thenReturn(Optional.of(cron));
        return node;
    }

    private Variable stringVariable(String value) {
        Variable variable = mock(Variable.class);
        when(variable.value()).thenReturn(Optional.of(DataRecord.single(
                com.ispf.core.model.DataSchema.builder("value").field("value", com.ispf.core.model.FieldType.STRING).build(),
                Map.of("value", value)
        )));
        return variable;
    }
}
