package com.ispf.server.config;

/**
 * One historian storage tier (hot / warm / cold) in a multi-tier deployment profile (BL-159).
 */
public class HistorianTierProfile {

    /** Write backend: {@code jdbc}, {@code clickhouse}, or {@code cold} (S3/parquet archive). */
    private String store = "jdbc";

    /** Default retention for samples assigned to this tier (days). */
    private int retentionDays = 90;

    /** Minimum interval between stored samples when tier is the hot write path. */
    private long minIntervalMs = 5_000;

    /** When hot tier uses JDBC, optionally mirror to ClickHouse warm tier. */
    private boolean dualWriteEnabled = false;

    private VariableHistoryProperties.ClickHouse clickhouse = new VariableHistoryProperties.ClickHouse();

    private ColdArchive cold = new ColdArchive();

    public static class ColdArchive {
        private String provider = "s3";
        private String bucket = "";
        private String prefix = "historian/";
        private String format = "parquet";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public long getMinIntervalMs() {
        return minIntervalMs;
    }

    public void setMinIntervalMs(long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }

    public boolean isDualWriteEnabled() {
        return dualWriteEnabled;
    }

    public void setDualWriteEnabled(boolean dualWriteEnabled) {
        this.dualWriteEnabled = dualWriteEnabled;
    }

    public VariableHistoryProperties.ClickHouse getClickhouse() {
        return clickhouse;
    }

    public void setClickhouse(VariableHistoryProperties.ClickHouse clickhouse) {
        this.clickhouse = clickhouse;
    }

    public ColdArchive getCold() {
        return cold;
    }

    public void setCold(ColdArchive cold) {
        this.cold = cold;
    }

    public boolean isJdbcStore() {
        return "jdbc".equalsIgnoreCase(store) || "postgres".equalsIgnoreCase(store)
                || "timescale".equalsIgnoreCase(store);
    }

    public boolean isClickHouseStore() {
        return "clickhouse".equalsIgnoreCase(store);
    }

    public boolean isColdArchiveStore() {
        return "cold".equalsIgnoreCase(store) || "s3".equalsIgnoreCase(store)
                || "parquet".equalsIgnoreCase(store);
    }
}
