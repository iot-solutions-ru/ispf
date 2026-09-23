package com.ispf.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "partner_enrollments")
public class PartnerEnrollmentEntity {

    @Id
    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    @Column(name = "company_name", length = 256)
    private String companyName;

    @Column(name = "contact_email", length = 256)
    private String contactEmail;

    @Column(name = "tier_id", nullable = false, length = 32)
    private String tierId;

    @Column(name = "verticals_json", columnDefinition = "TEXT")
    private String verticalsJson;

    @Column(name = "regions_json", columnDefinition = "TEXT")
    private String regionsJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getTierId() {
        return tierId;
    }

    public void setTierId(String tierId) {
        this.tierId = tierId;
    }

    public String getVerticalsJson() {
        return verticalsJson;
    }

    public void setVerticalsJson(String verticalsJson) {
        this.verticalsJson = verticalsJson;
    }

    public String getRegionsJson() {
        return regionsJson;
    }

    public void setRegionsJson(String regionsJson) {
        this.regionsJson = regionsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
