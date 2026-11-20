package com.ispf.server.workflow;

import com.ispf.server.persistence.WorkflowUserTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkQueueServiceLimitTest {

    @Mock
    private WorkflowUserTaskRepository userTaskRepository;
    @Mock
    private WorkflowService workflowService;

    @Test
    void zeroLimitReturnsEmptyWithoutQuery() {
        assertEquals(List.of(), service().listOpenTasks(0));
        verify(userTaskRepository, never()).findByStatusInOrderByCreatedAtDesc(anyList(), any());
    }

    @Test
    void negativeLimitIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().listOpenTasks(-1)
        );

        assertEquals("Work queue limit must not be negative", exception.getMessage());
        verify(userTaskRepository, never()).findByStatusInOrderByCreatedAtDesc(anyList(), any());
    }

    @Test
    void positiveLimitQueriesThatPageSize() {
        when(userTaskRepository.findByStatusInOrderByCreatedAtDesc(
                List.of("OPEN", "CLAIMED"),
                PageRequest.of(0, 5)
        )).thenReturn(List.of());

        assertEquals(List.of(), service().listOpenTasks(5));
        verify(userTaskRepository).findByStatusInOrderByCreatedAtDesc(
                List.of("OPEN", "CLAIMED"),
                PageRequest.of(0, 5)
        );
    }

    private WorkQueueService service() {
        return new WorkQueueService(userTaskRepository, workflowService);
    }
}
