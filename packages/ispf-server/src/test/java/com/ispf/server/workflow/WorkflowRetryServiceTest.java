package com.ispf.server.workflow;

import com.ispf.server.persistence.WorkflowRetryScheduleRepository;
import com.ispf.server.persistence.entity.WorkflowRetryScheduleEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowRetryServiceTest {

    @Mock
    private WorkflowRetryScheduleRepository repository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WorkflowRetryService service;

    @Test
    void schedulePersistsPendingRow() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"k\":\"v\"}");
        when(repository.save(any(WorkflowRetryScheduleEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant due = Instant.parse("2026-07-19T12:00:00Z");
        WorkflowRetryScheduleEntity saved = service.schedule(
                "root.platform.workflows.demo",
                "inst-1",
                1,
                due,
                Map.of("k", "v"),
                "boom"
        );

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getStatus()).isEqualTo(WorkflowRetryService.STATUS_PENDING);
        assertThat(saved.getDueAt()).isEqualTo(due);
        assertThat(saved.getAttempt()).isEqualTo(1);
        assertThat(saved.getLastError()).isEqualTo("boom");
    }

    @Test
    void claimTransitionsPendingToClaimed() {
        WorkflowRetryScheduleEntity entity = new WorkflowRetryScheduleEntity();
        entity.setId("r-1");
        entity.setStatus(WorkflowRetryService.STATUS_PENDING);
        when(repository.findById("r-1")).thenReturn(Optional.of(entity));
        when(repository.save(any(WorkflowRetryScheduleEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.claim("r-1")).isTrue();
        ArgumentCaptor<WorkflowRetryScheduleEntity> captor = ArgumentCaptor.forClass(WorkflowRetryScheduleEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(WorkflowRetryService.STATUS_CLAIMED);
        assertThat(captor.getValue().getClaimedAt()).isNotNull();
    }
}
