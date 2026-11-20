package com.ispf.server.relational;

/**
 * Greenfield target for MSSQL history. Partition/PK rewrite is not applied to {@code V1__baseline.sql}.
 * Foundation objects (unique series+time key, rollup table, spool batch tokens) land in
 * {@code V101.1__single_engine_historian_divergence.sql}. An in-place PK rewrite after production B2
 * would break ADR-0064.
 */
public final class MssqlHistorianDivergence {

    public static final String NOT_APPLIED =
            "Partition/PK rewrite not applied to V1. Monthly partitions. Partition column sampled_at must sit in the primary key.";

    /** Applied by Flyway V101.1 on the mssql pack (not a Timescale twin; avoids squatting shared V102). */
    public static final String V101_1_FOUNDATION =
            "uq_variable_samples_series_time + variable_sample_rollups + historian_spool_batches";

    private MssqlHistorianDivergence() {
    }

    /** Sketch only — Flyway does not rewrite the IDENTITY PK yet. */
    public static String greenfieldPrimaryKeySketch() {
        return "PRIMARY KEY (sampled_at, id)";
    }

    public static String rollupTableSketch() {
        return """
                CREATE TABLE variable_sample_rollups (
                    object_path VARCHAR(512) NOT NULL,
                    variable_name VARCHAR(128) NOT NULL,
                    field_name VARCHAR(128) NOT NULL,
                    bucket_start DATETIME2 NOT NULL,
                    bucket_seconds INT NOT NULL,
                    avg_val FLOAT NULL,
                    min_val FLOAT NULL,
                    max_val FLOAT NULL,
                    sample_count BIGINT NOT NULL,
                    CONSTRAINT pk_variable_sample_rollups PRIMARY KEY (bucket_start, object_path, variable_name, field_name, bucket_seconds)
                )
                """;
    }
}
