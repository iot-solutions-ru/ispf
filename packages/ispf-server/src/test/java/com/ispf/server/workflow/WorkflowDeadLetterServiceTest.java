package com.ispf.server.workflow;

import com.ispf.server.persistence.WorkflowDeadLetterRepository;
import com.ispf.server.persistence.entity.WorkflowDeadLetterEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowDeadLetterServiceTest {

    @Mock
    private WorkflowDeadLetterRepository repository;

    @InjectMocks
    private WorkflowDeadLetterService service;

    @Test
    void recordPersistsEntity() {
        when(repository.save(any(WorkflowDeadLetterEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkflowDeadLetterEntity saved = service.record("inst-1", "root.platform.workflows.demo", 1, "boom", "{}");

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getInstanceId()).isEqualTo("inst-1");
        assertThat(saved.getWorkflowPath()).isEqualTo("root.platform.workflows.demo");
        assertThat(saved.getAttemptCount()).isEqualTo(1);
        assertThat(saved.getLastError()).isEqualTo("boom");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getResolvedAt()).isNull();
    }

    @Test
    void resolveSetsResolvedAtOnce() {
        WorkflowDeadLetterEntity entity = new WorkflowDeadLetterEntity();
        entity.setId("dl-1");
        entity.setInstanceId("inst-1");
        entity.setWorkflowPath("root.platform.workflows.demo");
        entity.setAttemptCount(1);
        entity.setCreatedAt(Instant.parse("2026-07-19T00:00:00Z"));
        when(repository.findById("dl-1")).thenReturn(Optional.of(entity));
        when(repository.save(any(WorkflowDeadLetterEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkflowDeadLetterEntity first = service.resolve("dl-1");
        assertThat(first.getResolvedAt()).isNotNull();
        Instant resolvedAt = first.getResolvedAt();

        WorkflowDeadLetterEntity second = service.resolve("dl-1");
        assertThat(second.getResolvedAt()).isEqualTo(resolvedAt);

        ArgumentCaptor<WorkflowDeadLetterEntity> captor = ArgumentCaptor.forClass(WorkflowDeadLetterEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getResolvedAt()).isEqualTo(resolvedAt);
    }

    @Test
    void resolveMissingThrows() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolve("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dead letter not found");
    }

    @Test
    void listUnresolvedDelegates() {
        when(repository.findByWorkflowPathAndResolvedAtIsNullOrderByCreatedAtDesc("p"))
                .thenReturn(List.of());
        assertThat(service.listUnresolvedByPath("p")).isEmpty();
    }
}
