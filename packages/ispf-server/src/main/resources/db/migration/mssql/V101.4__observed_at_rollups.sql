-- ADR-0078 Gate C chart-prep: observed_at rollup table + arrival cursor (sampled_at watermark).
-- Default-off JDBC writer recomputes touched observed_at buckets from raw rows still present.
-- Does NOT cut over /history/aggregate or operator charts. Not a SWITCH seal.

IF OBJECT_ID(N'dbo.variable_sample_observed_rollups', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.variable_sample_observed_rollups (
        object_path    VARCHAR(512) NOT NULL,
        variable_name  VARCHAR(128) NOT NULL,
        field_name     VARCHAR(128) NOT NULL,
        bucket_start   DATETIME2    NOT NULL,
        bucket_seconds INT          NOT NULL,
        avg_val        FLOAT NULL,
        min_val        FLOAT NULL,
        max_val        FLOAT NULL,
        sample_count   BIGINT       NOT NULL,
        CONSTRAINT pk_variable_sample_observed_rollups
            PRIMARY KEY (object_path, variable_name, field_name, bucket_seconds, bucket_start)
    );
END
GO

IF OBJECT_ID(N'dbo.mssql_historian_observed_rollup_cursor', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.mssql_historian_observed_rollup_cursor (
        bucket_seconds          INT       NOT NULL
            CONSTRAINT pk_mssql_historian_observed_rollup_cursor PRIMARY KEY,
        sampled_covered_through DATETIME2 NOT NULL,
        updated_at              DATETIME2 NOT NULL
    );
END
GO
