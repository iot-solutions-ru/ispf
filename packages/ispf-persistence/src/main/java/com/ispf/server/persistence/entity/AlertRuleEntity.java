package com.ispf.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "alert_rules")
public class AlertRuleEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "object_path", nullable = false)
    private String objectPath;

    @Column(name = "watch_variable", nullable = false)
    private String watchVariable;

    @Column(name = "condition_expr", nullable = false)
    private String conditionExpr;

    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(name = "payload_variable")
    private String payloadVariable;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "edge_trigger", nullable = false)
    private boolean edgeTrigger = true;

    @Column(name = "last_condition_met")
    private Boolean lastConditionMet;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getObjectPath() {
        return objectPath;
    }

    public void setObjectPath(String objectPath) {
        this.objectPath = objectPath;
    }

    public String getWatchVariable() {
        return watchVariable;
    }

    public void setWatchVariable(String watchVariable) {
        this.watchVariable = watchVariable;
    }

    public String getConditionExpr() {
        return conditionExpr;
    }

    public void setConditionExpr(String conditionExpr) {
        this.conditionExpr = conditionExpr;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getPayloadVariable() {
        return payloadVariable;
    }

    public void setPayloadVariable(String payloadVariable) {
        this.payloadVariable = payloadVariable;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEdgeTrigger() {
        return edgeTrigger;
    }

    public void setEdgeTrigger(boolean edgeTrigger) {
        this.edgeTrigger = edgeTrigger;
    }

    public Boolean getLastConditionMet() {
        return lastConditionMet;
    }

    public void setLastConditionMet(Boolean lastConditionMet) {
        this.lastConditionMet = lastConditionMet;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
