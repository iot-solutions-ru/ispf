package com.ispf.server.workflow;

import com.ispf.server.persistence.WorkflowRetryScheduleRepository;
import com.ispf.server.persistence.entity.WorkflowRetryScheduleEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Durable async retry schedule for failed workflow runs (ADR-0049 Wave 3).
 */
@Service
public class WorkflowRetryService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CLAIMED = "CLAIMED";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private final WorkflowRetryScheduleRepository repository;
    private final ObjectMapper objectMapper;

    public WorkflowRetryService(WorkflowRetryScheduleRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowRetryScheduleEntity schedule(
            String workflowPath,
            String sourceInstanceId,
            int nextAttempt,
            Instant dueAt,
            Map<String, String> input,
            String lastError
    ) {
        WorkflowRetryScheduleEntity entity = new WorkflowRetryScheduleEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setWorkflowPath(workflowPath);
        entity.setSourceInstanceId(sourceInstanceId);
        entity.setAttempt(nextAttempt);
        entity.setDueAt(dueAt != null ? dueAt : Instant.now());
        entity.setStatus(STATUS_PENDING);
        entity.setInputJson(writeInput(input));
        entity.setLastError(lastError);
        entity.setCreatedAt(Instant.now());
        return repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<WorkflowRetryScheduleEntity> listDue(Instant now) {
        return repository.findDue(now != null ? now : Instant.now());
    }

    @Transactional
    public boolean claim(String id) {
        WorkflowRetryScheduleEntity entity = repository.findById(id).orElse(null);
        if (entity == null || !STATUS_PENDING.equals(entity.getStatus())) {
            return false;
        }
        entity.setStatus(STATUS_CLAIMED);
        entity.setClaimedAt(Instant.now());
        repository.save(entity);
        return true;
    }

    @Transactional
    public void markDone(String id) {
        WorkflowRetryScheduleEntity entity = repository.findById(id).orElse(null);
        if (entity == null) {
            return;
        }
        entity.setStatus(STATUS_DONE);
        entity.setCompletedAt(Instant.now());
        repository.save(entity);
    }

    @Transactional
    public void markFailed(String id, String error) {
        WorkflowRetryScheduleEntity entity = repository.findById(id).orElse(null);
        if (entity == null) {
            return;
        }
        entity.setStatus(STATUS_FAILED);
        entity.setLastError(error);
        entity.setCompletedAt(Instant.now());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<WorkflowRetryScheduleEntity> listByPath(String workflowPath) {
        return repository.findByWorkflowPathOrderByCreatedAtDesc(workflowPath);
    }

    public Map<String, String> readInput(WorkflowRetryScheduleEntity entity) {
        if (entity == null || entity.getInputJson() == null || entity.getInputJson().isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = objectMapper.readValue(entity.getInputJson(), Map.class);
            Map<String, String> input = new HashMap<>();
            if (raw != null) {
                raw.forEach((k, v) -> {
                    if (k != null) {
                        input.put(k, v == null ? "" : String.valueOf(v));
                    }
                });
            }
            return input;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writeInput(Map<String, String> input) {
        try {
            return objectMapper.writeValueAsString(input == null ? Map.of() : input);
        } catch (Exception e) {
            return "{}";
        }
    }
}
