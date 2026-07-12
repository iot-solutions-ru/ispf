package com.ispf.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "variable_samples",
        indexes = {
                @Index(
                        name = "idx_variable_samples_lookup",
                        columnList = "object_path, variable_name, field_name, sampled_at"
                )
        }
)
public class VariableSampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_path", nullable = false, length = 512)
    private String objectPath;

    @Column(name = "variable_name", nullable = false, length = 128)
    private String variableName;

    @Column(name = "field_name", nullable = false, length = 128)
    private String fieldName;

    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "value_double")
    private Double valueDouble;

    @Column(name = "value_text", columnDefinition = "TEXT")
    private String valueText;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getObjectPath() {
        return objectPath;
    }

    public void setObjectPath(String objectPath) {
        this.objectPath = objectPath;
    }

    public String getVariableName() {
        return variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public Instant getSampledAt() {
        return sampledAt;
    }

    public void setSampledAt(Instant sampledAt) {
        this.sampledAt = sampledAt;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }

    /** Chart/query timestamp: device measurement when present, else ingest time. */
    public Instant effectiveTimestamp() {
        return observedAt != null ? observedAt : sampledAt;
    }

    public Double getValueDouble() {
        return valueDouble;
    }

    public void setValueDouble(Double valueDouble) {
        this.valueDouble = valueDouble;
    }

    public String getValueText() {
        return valueText;
    }

    public void setValueText(String valueText) {
        this.valueText = valueText;
    }
}
