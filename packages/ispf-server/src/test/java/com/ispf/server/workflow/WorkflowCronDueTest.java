package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.spi.LeaderLock;
import com.ispf.server.spi.WorkflowObjectAccess;
import com.ispf.server.spi.WorkflowStartTrigger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowCronDueTest {

    private static final String PATH = "root.platform.workflows.cron-due";

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
    void everyFiveMinutesIsDueAfterTheInterval() {
        Instant now = Instant.parse("2026-10-06T12:05:00Z");
        Instant last = Instant.parse("2026-10-06T12:00:00Z");
        assertThat(WorkflowCronDue.isDue(now, last, "every:5m")).isTrue();
        assertThat(WorkflowCronDue.isDue(now, last, "every:10m")).isFalse();
        assertThat(WorkflowCronDue.isDue(now, null, "every:1m")).isTrue();
    }

    @Test
    void fiveFieldCronUsesTheSameDueRuleAsSchedules() {
        Instant last = Instant.parse("2026-10-05T08:00:00Z");
        assertThat(WorkflowCronDue.isDue(Instant.parse("2026-10-06T07:59:00Z"), last, "0 8 * * *")).isFalse();
        assertThat(WorkflowCronDue.isDue(Instant.parse("2026-10-06T08:00:30Z"), last, "0 8 * * *")).isTrue();
    }

    @Test
    void pollRunsEveryFiveMinutesWhenDueAndSkipsAFreshTenMinuteSchedule() throws Exception {
        when(objects.isInitialized()).thenReturn(true);
        PlatformObject due = workflow("every:5m", "2026-10-06T06:00:00Z");
        PlatformObject fresh = workflow("every:10m", Instant.now().toString());
        when(due.path()).thenReturn(PATH + "-5");
        when(objects.childrenOf("root.platform.workflows")).thenReturn(List.of(due, fresh));

        leaderService().poll();

        verify(workflowService).runWorkflow(
                eq(PATH + "-5"),
                isNull(),
                eq(WorkflowStartTrigger.EVENT),
                eq(Map.of("cronExpression", "every:5m"))
        );
        verify(workflowService, never()).runWorkflow(
                eq(PATH + "-10"),
                any(),
                any(),
                any()
        );
    }

    @Test
    void pollDoesNotStartAnUnreadableCron() throws Exception {
        when(objects.isInitialized()).thenReturn(true);
        PlatformObject node = workflow("not-a-cron", null);
        when(objects.childrenOf("root.platform.workflows")).thenReturn(List.of(node));

        leaderService().poll();

        verify(workflowService, never()).runWorkflow(any(), any(), any(), any());
    }

    private WorkflowCronTriggerService leaderService() {
        when(clusterProperties.isSchedulerActive()).thenReturn(true);
        when(leaderLock.runIfLeader(eq(WorkflowCronTriggerService.LOCK_NAME), any(), any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        });
        when(leaderLock.isHeld(WorkflowCronTriggerService.LOCK_NAME)).thenReturn(true);
        return new WorkflowCronTriggerService(objects, workflowService, deadLetterService, leaderLock, clusterProperties);
    }

    private PlatformObject workflow(String cron, String lastRunAt) {
        PlatformObject node = org.mockito.Mockito.mock(PlatformObject.class);
        Variable status = stringVariable("ACTIVE");
        Variable cronVariable = stringVariable(cron);
        when(node.type()).thenReturn(ObjectType.WORKFLOW);
        when(node.getVariable("status")).thenReturn(Optional.of(status));
        when(node.getVariable("cronExpression")).thenReturn(Optional.of(cronVariable));
        if (lastRunAt == null) {
            when(node.getVariable("lastRunAt")).thenReturn(Optional.empty());
        } else {
            Variable last = stringVariable(lastRunAt);
            when(node.getVariable("lastRunAt")).thenReturn(Optional.of(last));
        }
        return node;
    }

    private Variable stringVariable(String value) {
        Variable variable = org.mockito.Mockito.mock(Variable.class);
        when(variable.value()).thenReturn(Optional.of(DataRecord.single(
                DataSchema.builder("value").field("value", FieldType.STRING).build(),
                Map.of("value", value)
        )));
        return variable;
    }
}
