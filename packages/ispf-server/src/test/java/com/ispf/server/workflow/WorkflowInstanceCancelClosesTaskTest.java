package com.ispf.server.workflow;

import com.ispf.plugin.workflow.InstanceStatus;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.persistence.entity.WorkflowInstanceEntity;
import com.ispf.server.spi.WorkflowPlatformSql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowInstanceCancelClosesTaskTest {

    @Mock
    private WorkflowInstanceRepository instanceRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private WorkflowPlatformSql platformSql;
    @Mock
    private ObjectProvider<WorkflowInstanceCancelService> self;

    @Test
    void cancelClosesOpenTasksForTheInstance() {
        when(platformSql.table("workflow_cancel_journal")).thenReturn("workflow_cancel_journal");
        when(platformSql.table("workflow_user_tasks")).thenReturn("workflow_user_tasks");
        WorkflowInstanceEntity entity = instance("WAITING");
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(entity));

        Map<String, Object> result = service().cancel("inst-1", "proof", null, "operator");

        assertEquals(true, result.get("cancelled"));
        assertEquals(InstanceStatus.FAILED.name(), result.get("status"));
        verify(jdbcTemplate).update(
                ArgumentMatchers.contains("UPDATE workflow_user_tasks"),
                ArgumentMatchers.any(Timestamp.class),
                ArgumentMatchers.eq("inst-1")
        );
    }

    @Test
    void alreadyFinalInstanceDoesNotCloseTasks() {
        when(platformSql.table("workflow_cancel_journal")).thenReturn("workflow_cancel_journal");
        when(platformSql.table("workflow_user_tasks")).thenReturn("workflow_user_tasks");
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(instance("COMPLETED")));

        Map<String, Object> result = service().cancel("inst-1", "proof", null, "operator");

        assertEquals(false, result.get("cancelled"));
        verify(instanceRepository, never()).save(ArgumentMatchers.any());
        verifyNoInteractions(jdbcTemplate);
    }

    private WorkflowInstanceEntity instance(String status) {
        WorkflowInstanceEntity entity = new WorkflowInstanceEntity();
        entity.setId("inst-1");
        entity.setStatus(status);
        entity.setWorkflowPath("root.platform.workflows.cancel-proof");
        return entity;
    }

    private WorkflowInstanceCancelService service() {
        return new WorkflowInstanceCancelService(
                instanceRepository,
                jdbcTemplate,
                platformSql,
                self
        );
    }
}
