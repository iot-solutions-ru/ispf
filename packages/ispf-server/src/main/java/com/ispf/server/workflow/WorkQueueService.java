package com.ispf.server.workflow;

import com.ispf.plugin.workflow.UserTaskDefinition;
import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.server.persistence.WorkflowUserTaskRepository;
import com.ispf.server.persistence.entity.WorkflowUserTaskEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class WorkQueueService {

    private final WorkflowUserTaskRepository userTaskRepository;
    private final WorkflowService workflowService;

    public WorkQueueService(WorkflowUserTaskRepository userTaskRepository, WorkflowService workflowService) {
        this.userTaskRepository = userTaskRepository;
        this.workflowService = workflowService;
    }

    @Transactional(readOnly = true)
    public List<WorkQueueItem> listOpenTasks(int limit) {
        return userTaskRepository
                .findByStatusInOrderByCreatedAtDesc(List.of("OPEN", "CLAIMED"), PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(this::toItem)
                .toList();
    }

    @Transactional
    public WorkQueueItem claimTask(String taskId, String operatorId) throws WorkflowException {
        WorkflowUserTaskEntity task = userTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        if (!"OPEN".equals(task.getStatus()) && !operatorId.equals(task.getAssignee())) {
            throw new IllegalStateException("Task is already claimed");
        }
        task.setStatus("CLAIMED");
        task.setAssignee(operatorId);
        task.setClaimedAt(Instant.now());
        userTaskRepository.save(task);
        workflowService.claimInstance(task.getInstanceId(), operatorId);
        return toItem(task);
    }

    @Transactional
    public WorkQueueItem completeTask(String taskId, String operatorId) throws WorkflowException {
        WorkflowUserTaskEntity task = userTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        if ("COMPLETED".equals(task.getStatus())) {
            return toItem(task);
        }
        if ("CLAIMED".equals(task.getStatus()) && task.getAssignee() != null
                && !task.getAssignee().equals(operatorId)) {
            throw new IllegalStateException("Task is assigned to another operator");
        }
        workflowService.completeUserTask(task.getInstanceId(), task.getTaskNodeId(), operatorId);
        markTaskCompleted(taskId);
        WorkflowUserTaskEntity updated = userTaskRepository.findById(taskId).orElseThrow();
        return toItem(updated);
    }

    @Transactional
    public void markTaskCompleted(String taskId) {
        WorkflowUserTaskEntity task = userTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        if (!"COMPLETED".equals(task.getStatus())) {
            task.setStatus("COMPLETED");
            task.setCompletedAt(Instant.now());
            userTaskRepository.save(task);
        }
    }

    @Transactional
    public void markInstanceTasksCompleted(String instanceId) {
        userTaskRepository.findByInstanceId(instanceId).stream()
                .filter(task -> !"COMPLETED".equals(task.getStatus()))
                .forEach(task -> {
                    task.setStatus("COMPLETED");
                    task.setCompletedAt(Instant.now());
                    userTaskRepository.save(task);
                });
    }

    private WorkQueueItem toItem(WorkflowUserTaskEntity task) {
        return new WorkQueueItem(
                task.getId(),
                task.getInstanceId(),
                task.getWorkflowPath(),
                task.getTaskNodeId(),
                task.getTitle(),
                task.getInstructions(),
                task.getAssigneeRole(),
                task.getStatus(),
                task.getAssignee(),
                task.getCreatedAt(),
                task.getClaimedAt(),
                task.getCompletedAt()
        );
    }

    public record WorkQueueItem(
            String id,
            String instanceId,
            String workflowPath,
            String taskNodeId,
            String title,
            String instructions,
            String assigneeRole,
            String status,
            String assignee,
            Instant createdAt,
            Instant claimedAt,
            Instant completedAt
    ) {
    }
}
