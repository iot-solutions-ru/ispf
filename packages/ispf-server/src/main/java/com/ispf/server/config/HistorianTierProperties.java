package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Multi-tier historian deploy profiles: hot (PG/Timescale) → warm (ClickHouse) → cold (S3/parquet) — BL-159.
 * Warm query routing: set {@code ispf.historian.tiers.warm.enabled=true}.
 */
@ConfigurationProperties(prefix = "ispf.historian")
public class HistorianTierProperties {

    /**
     * Named deploy profile selected by ops (documented in {@code docs/HISTORIAN_TIERS.md}).
     * Tier routing enforcement is follow-up work; this block is the configuration contract.
     */
    private String deployProfile = "three-tier";

    private Map<String, HistorianTierProfile> tiers = defaultTiers();

    private static Map<String, HistorianTierProfile> defaultTiers() {
        Map<String, HistorianTierProfile> map = new LinkedHashMap<>();

        HistorianTierProfile hot = new HistorianTierProfile();
        hot.setStore("jdbc");
        hot.setRetentionDays(7);
        hot.setMinIntervalMs(5_000);
        hot.setDualWriteEnabled(true);
        map.put("hot", hot);

        HistorianTierProfile warm = new HistorianTierProfile();
        warm.setStore("clickhouse");
        warm.setRetentionDays(90);
        warm.setEnabled(false);
        map.put("warm", warm);

        HistorianTierProfile cold = new HistorianTierProfile();
        cold.setStore("cold");
        cold.setRetentionDays(3_650);
        cold.getCold().setBucket("ispf-historian-archive");
        cold.getCold().setPrefix("variable-samples/");
        map.put("cold", cold);

        return map;
    }

    public String getDeployProfile() {
        return deployProfile;
    }

    public void setDeployProfile(String deployProfile) {
        this.deployProfile = deployProfile;
    }

    public Map<String, HistorianTierProfile> getTiers() {
        return tiers;
    }

    public void setTiers(Map<String, HistorianTierProfile> tiers) {
        this.tiers = tiers != null ? new LinkedHashMap<>(tiers) : new LinkedHashMap<>();
    }

    public Optional<HistorianTierProfile> tier(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tiers.get(name));
    }

    public HistorianTierProfile hotTier() {
        return tiers.getOrDefault("hot", new HistorianTierProfile());
    }

    public HistorianTierProfile warmTier() {
        return tiers.getOrDefault("warm", new HistorianTierProfile());
    }

    public HistorianTierProfile coldTier() {
        return tiers.getOrDefault("cold", new HistorianTierProfile());
    }
}
