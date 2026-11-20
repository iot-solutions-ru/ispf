package com.ispf.server.relational;

/**
 * Greenfield target for MSSQL history. Not applied to {@code V1__baseline.sql}.
 * An in-place rewrite after production B2 would break ADR-0064.
 */
public final class MssqlHistorianDivergence {

    public static final String NOT_APPLIED =
            "Not applied to V1. Monthly partitions. Partition column sampled_at must sit in the primary key.";

    private MssqlHistorianDivergence() {
    }

    /** Sketch only — Flyway does not run this. */
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
