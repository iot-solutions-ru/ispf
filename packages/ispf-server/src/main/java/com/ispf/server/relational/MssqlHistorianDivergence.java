package com.ispf.server.relational;

/**
 * Greenfield target for MSSQL history. Partition/PK rewrite is not applied to {@code V1__baseline.sql}.
 * Foundation objects land in {@code V101.1}; empty-only monthly partitions in {@code V101.2}.
 * An in-place PK rewrite after rows exist is refused (ADR-0064) and recorded as {@code SKIPPED_NONEMPTY}.
 */
public final class MssqlHistorianDivergence {

    public static final String NOT_APPLIED =
            "Partition/PK rewrite not applied to V1. Monthly partitions require an empty variable_samples (V101.2) or a new database.";

    /** Applied by Flyway V101.1 on the mssql pack (not a Timescale twin; avoids squatting shared V102). */
    public static final String V101_1_FOUNDATION =
            "uq_variable_samples_series_time + variable_sample_rollups + historian_spool_batches";

    /**
     * Flyway V101.2 empty-table rebuild. Durable row in {@code mssql_historian_partition_status}:
     * {@code APPLIED} or {@code SKIPPED_NONEMPTY}. SWITCH retention and event_history partitions stay deferred.
     */
    public static final String V101_2_PARTITIONS =
            "Empty-only variable_samples PK (sampled_at, id) on pf/ps_variable_samples_month; status APPLIED|SKIPPED_NONEMPTY";

    public static final String STATUS_APPLIED = "APPLIED";
    public static final String STATUS_SKIPPED_NONEMPTY = "SKIPPED_NONEMPTY";

    /**
     * Gate C2: JDBC materializer into {@code variable_sample_rollups} (sampled_at 1h/1d) with
     * {@code mssql_historian_rollup_cursor}. Default off. Not a SWITCH seal; charts still use raw
     * {@code observed_at} scans; app DELETE retention stays.
     */
    public static final String V101_3_ROLLUP_CURSOR =
            "mssql_historian_rollup_cursor + JDBC sampled_at rollup materializer (opt-in)";

    private MssqlHistorianDivergence() {
    }

    /** Greenfield / V101.2 APPLIED shape. */
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
