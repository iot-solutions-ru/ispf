package com.ispf.server.workflow;

import com.ispf.server.persistence.entity.WorkflowDeadLetterEntity;
import com.ispf.server.persistence.WorkflowDeadLetterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowDeadLetterService {

    private final WorkflowDeadLetterRepository repository;

    public WorkflowDeadLetterService(WorkflowDeadLetterRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String instanceId, String workflowPath, int attemptCount, String lastError, String payloadJson) {
        WorkflowDeadLetterEntity entity = new WorkflowDeadLetterEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setInstanceId(instanceId);
        entity.setWorkflowPath(workflowPath);
        entity.setAttemptCount(attemptCount);
        entity.setLastError(lastError);
        entity.setPayloadJson(payloadJson);
        entity.setCreatedAt(Instant.now());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<WorkflowDeadLetterEntity> listByPath(String workflowPath) {
        return repository.findByWorkflowPathOrderByCreatedAtDesc(workflowPath);
    }
}
