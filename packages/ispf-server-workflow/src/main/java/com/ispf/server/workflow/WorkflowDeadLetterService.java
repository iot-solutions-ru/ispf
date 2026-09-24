package com.ispf.server.workflow;

import com.ispf.server.persistence.entity.WorkflowDeadLetterEntity;
import com.ispf.server.persistence.WorkflowDeadLetterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkflowDeadLetterService {

    private final WorkflowDeadLetterRepository repository;

    public WorkflowDeadLetterService(WorkflowDeadLetterRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public WorkflowDeadLetterEntity record(
            String instanceId,
            String workflowPath,
            int attemptCount,
            String lastError,
            String payloadJson
    ) {
        WorkflowDeadLetterEntity entity = new WorkflowDeadLetterEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setInstanceId(instanceId);
        entity.setWorkflowPath(workflowPath);
        entity.setAttemptCount(attemptCount);
        entity.setLastError(lastError);
        entity.setPayloadJson(payloadJson);
        entity.setCreatedAt(Instant.now());
        return repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<WorkflowDeadLetterEntity> listByPath(String workflowPath) {
        return repository.findByWorkflowPathOrderByCreatedAtDesc(workflowPath);
    }

    @Transactional(readOnly = true)
    public List<WorkflowDeadLetterEntity> listUnresolvedByPath(String workflowPath) {
        return repository.findByWorkflowPathAndResolvedAtIsNullOrderByCreatedAtDesc(workflowPath);
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowDeadLetterEntity> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return repository.findById(id);
    }

    @Transactional
    public WorkflowDeadLetterEntity resolve(String id) {
        WorkflowDeadLetterEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dead letter not found: " + id));
        if (entity.getResolvedAt() == null) {
            entity.setResolvedAt(Instant.now());
            repository.save(entity);
        }
        return entity;
    }
}
