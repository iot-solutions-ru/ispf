package com.ispf.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "alarm_shelves")
public class AlarmShelfEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "object_path", nullable = false, length = 512)
    private String objectPath;

    @Column(name = "event_name", nullable = false, length = 128)
    private String eventName;

    @Column(name = "alert_rule_path", length = 512)
    private String alertRulePath;

    @Column(name = "shelved_by", nullable = false, length = 128)
    private String shelvedBy;

    @Column(name = "shelved_at", nullable = false)
    private Instant shelvedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "comment", length = 1024)
    private String comment;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObjectPath() {
        return objectPath;
    }

    public void setObjectPath(String objectPath) {
        this.objectPath = objectPath;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getAlertRulePath() {
        return alertRulePath;
    }

    public void setAlertRulePath(String alertRulePath) {
        this.alertRulePath = alertRulePath;
    }

    public String getShelvedBy() {
        return shelvedBy;
    }

    public void setShelvedBy(String shelvedBy) {
        this.shelvedBy = shelvedBy;
    }

    public Instant getShelvedAt() {
        return shelvedAt;
    }

    public void setShelvedAt(Instant shelvedAt) {
        this.shelvedAt = shelvedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
