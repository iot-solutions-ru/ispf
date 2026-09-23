package com.ispf.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "partner_directory")
public class PartnerDirectoryEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "certification_level", nullable = false, length = 64)
    private String certificationLevel;

    @Column(name = "tier_id", nullable = false, length = 32)
    private String tierId;

    @Column(name = "regions_json", columnDefinition = "TEXT")
    private String regionsJson;

    @Column(name = "verticals_json", columnDefinition = "TEXT")
    private String verticalsJson;

    @Column(name = "marketplace_url", length = 512)
    private String marketplaceUrl;

    @Column(name = "certified_since", length = 16)
    private String certifiedSince;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

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

    public String getCertificationLevel() {
        return certificationLevel;
    }

    public void setCertificationLevel(String certificationLevel) {
        this.certificationLevel = certificationLevel;
    }

    public String getTierId() {
        return tierId;
    }

    public void setTierId(String tierId) {
        this.tierId = tierId;
    }

    public String getRegionsJson() {
        return regionsJson;
    }

    public void setRegionsJson(String regionsJson) {
        this.regionsJson = regionsJson;
    }

    public String getVerticalsJson() {
        return verticalsJson;
    }

    public void setVerticalsJson(String verticalsJson) {
        this.verticalsJson = verticalsJson;
    }

    public String getMarketplaceUrl() {
        return marketplaceUrl;
    }

    public void setMarketplaceUrl(String marketplaceUrl) {
        this.marketplaceUrl = marketplaceUrl;
    }

    public String getCertifiedSince() {
        return certifiedSince;
    }

    public void setCertifiedSince(String certifiedSince) {
        this.certifiedSince = certifiedSince;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
