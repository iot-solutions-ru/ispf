package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Documented analytics platform SLO defaults (BL-210). Used by lab gates and SLA endpoint.
 */
@ConfigurationProperties(prefix = "ispf.analytics.slo")
public class AnalyticsSloProperties {

    /** Multi-tag query gate: number of series (BL-206 / BL-210). */
    private int multiTagQueryTags = 10;

    /** Multi-tag query gate: calendar range in days. */
    private int multiTagQueryRangeDays = 7;

    /** Multi-tag query gate: historian bucket. */
    private String multiTagQueryBucket = "1h";

    /** Target p95 latency for multi-tag query at lab gate (milliseconds). */
    private long multiTagQueryP95LatencyMs = 3_000L;

    /** Enterprise L lab gate: minimum history-enabled tags in catalog. */
    private int catalogMinTags = 50_000;

    /** Materializer lag SLO — max seconds behind wall clock (lab). */
    private long materializerMaxLagSeconds = 300L;

    /** Enterprise L lab gate: minimum ClickHouse variable_samples rows. */
    private long clickhouseMinSamples = 1_000_000_000L;

    public int getMultiTagQueryTags() {
        return multiTagQueryTags;
    }

    public void setMultiTagQueryTags(int multiTagQueryTags) {
        this.multiTagQueryTags = Math.max(1, multiTagQueryTags);
    }

    public int getMultiTagQueryRangeDays() {
        return multiTagQueryRangeDays;
    }

    public void setMultiTagQueryRangeDays(int multiTagQueryRangeDays) {
        this.multiTagQueryRangeDays = Math.max(1, multiTagQueryRangeDays);
    }

    public String getMultiTagQueryBucket() {
        return multiTagQueryBucket;
    }

    public void setMultiTagQueryBucket(String multiTagQueryBucket) {
        this.multiTagQueryBucket = multiTagQueryBucket != null ? multiTagQueryBucket : "1h";
    }

    public long getMultiTagQueryP95LatencyMs() {
        return multiTagQueryP95LatencyMs;
    }

    public void setMultiTagQueryP95LatencyMs(long multiTagQueryP95LatencyMs) {
        this.multiTagQueryP95LatencyMs = Math.max(1, multiTagQueryP95LatencyMs);
    }

    public int getCatalogMinTags() {
        return catalogMinTags;
    }

    public void setCatalogMinTags(int catalogMinTags) {
        this.catalogMinTags = Math.max(1, catalogMinTags);
    }

    public long getMaterializerMaxLagSeconds() {
        return materializerMaxLagSeconds;
    }

    public void setMaterializerMaxLagSeconds(long materializerMaxLagSeconds) {
        this.materializerMaxLagSeconds = Math.max(1, materializerMaxLagSeconds);
    }

    public long getClickhouseMinSamples() {
        return clickhouseMinSamples;
    }

    public void setClickhouseMinSamples(long clickhouseMinSamples) {
        this.clickhouseMinSamples = Math.max(1, clickhouseMinSamples);
    }

    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("multiTagQueryTags", multiTagQueryTags);
        map.put("multiTagQueryRangeDays", multiTagQueryRangeDays);
        map.put("multiTagQueryBucket", multiTagQueryBucket);
        map.put("multiTagQueryP95LatencyMs", multiTagQueryP95LatencyMs);
        map.put("catalogMinTags", catalogMinTags);
        map.put("materializerMaxLagSeconds", materializerMaxLagSeconds);
        map.put("clickhouseMinSamples", clickhouseMinSamples);
        return map;
    }
}
