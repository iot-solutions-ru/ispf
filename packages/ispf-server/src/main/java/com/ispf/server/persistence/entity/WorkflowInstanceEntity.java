package com.ispf.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "workflow_instances")
public class WorkflowInstanceEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "workflow_path", nullable = false, length = 512)
    private String workflowPath;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "current_node_id", length = 128)
    private String currentNodeId;

    @Column(length = 128)
    private String assignee;

    @Column(name = "trigger_object_path", length = 512)
    private String triggerObjectPath;

    @Column(name = "state_json", columnDefinition = "TEXT")
    private String stateJson;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkflowPath() {
        return workflowPath;
    }

    public void setWorkflowPath(String workflowPath) {
        this.workflowPath = workflowPath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getTriggerObjectPath() {
        return triggerObjectPath;
    }

    public void setTriggerObjectPath(String triggerObjectPath) {
        this.triggerObjectPath = triggerObjectPath;
    }

    public String getStateJson() {
        return stateJson;
    }

    public void setStateJson(String stateJson) {
        this.stateJson = stateJson;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
