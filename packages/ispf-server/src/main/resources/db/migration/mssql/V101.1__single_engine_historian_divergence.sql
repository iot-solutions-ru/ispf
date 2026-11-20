-- ADR-0078 Gate C foundation (named divergence, not a Timescale twin).
-- Does NOT rewrite the IDENTITY primary key into monthly partitions yet.
-- Unique series+time key enables idempotent replay later. Rollups avoid raw multi-year scans.

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'uq_variable_samples_series_time'
      AND object_id = OBJECT_ID('variable_samples')
)
BEGIN
    CREATE UNIQUE INDEX uq_variable_samples_series_time
        ON variable_samples (object_path, variable_name, field_name, sampled_at);
END
GO

IF OBJECT_ID('variable_sample_rollups', 'U') IS NULL
BEGIN
    CREATE TABLE variable_sample_rollups (
        object_path   VARCHAR(512) NOT NULL,
        variable_name VARCHAR(128) NOT NULL,
        field_name    VARCHAR(128) NOT NULL,
        bucket_start  DATETIME2    NOT NULL,
        bucket_seconds INT         NOT NULL,
        avg_val       FLOAT NULL,
        min_val       FLOAT NULL,
        max_val       FLOAT NULL,
        sample_count  BIGINT       NOT NULL,
        CONSTRAINT pk_variable_sample_rollups
            PRIMARY KEY (bucket_start, object_path, variable_name, field_name, bucket_seconds)
    );
END
GO

IF OBJECT_ID('historian_spool_batches', 'U') IS NULL
BEGIN
    CREATE TABLE historian_spool_batches (
        batch_token   VARCHAR(128) NOT NULL PRIMARY KEY,
        recorded_at   DATETIME2    NOT NULL,
        sample_count  INT          NOT NULL
    );
END
GO
