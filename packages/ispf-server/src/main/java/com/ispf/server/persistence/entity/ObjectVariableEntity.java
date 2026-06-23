package com.ispf.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "object_variables",
        uniqueConstraints = @UniqueConstraint(columnNames = {"object_path", "name"})
)
public class ObjectVariableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_path", nullable = false, length = 512)
    private String objectPath;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "schema_json", nullable = false, columnDefinition = "TEXT")
    private String schemaJson;

    @Column(name = "value_json", columnDefinition = "TEXT")
    private String valueJson;

    @Column(nullable = false)
    private boolean readable = true;

    @Column(nullable = false)
    private boolean writable;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "history_enabled", nullable = false)
    private boolean historyEnabled;

    @Column(name = "history_retention_days")
    private Integer historyRetentionDays;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSchemaJson() {
        return schemaJson;
    }

    public void setSchemaJson(String schemaJson) {
        this.schemaJson = schemaJson;
    }

    public String getValueJson() {
        return valueJson;
    }

    public void setValueJson(String valueJson) {
        this.valueJson = valueJson;
    }

    public boolean isReadable() {
        return readable;
    }

    public void setReadable(boolean readable) {
        this.readable = readable;
    }

    public boolean isWritable() {
        return writable;
    }

    public void setWritable(boolean writable) {
        this.writable = writable;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isHistoryEnabled() {
        return historyEnabled;
    }

    public void setHistoryEnabled(boolean historyEnabled) {
        this.historyEnabled = historyEnabled;
    }

    public Integer getHistoryRetentionDays() {
        return historyRetentionDays;
    }

    public void setHistoryRetentionDays(Integer historyRetentionDays) {
        this.historyRetentionDays = historyRetentionDays;
    }
}
