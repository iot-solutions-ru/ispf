package com.ispf.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "correlator_hits")
public class CorrelatorHitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correlator_id", nullable = false)
    private String correlatorId;

    @Column(name = "object_path", nullable = false)
    private String objectPath;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCorrelatorId() {
        return correlatorId;
    }

    public void setCorrelatorId(String correlatorId) {
        this.correlatorId = correlatorId;
    }

    public String getObjectPath() {
        return objectPath;
    }

    public void setObjectPath(String objectPath) {
        this.objectPath = objectPath;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
