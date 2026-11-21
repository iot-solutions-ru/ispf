-- ADR-0081 amendment: empty SWITCH staging table only (PRIMARY).
-- No SWITCH / TRUNCATE / ALTER PARTITION. Lab script remains the only mutation entrypoint.
-- Shape matches variable_samples columns + IDENTITY + quality for SWITCH identity checks.

IF OBJECT_ID(N'dbo.variable_samples_switch_staging', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.variable_samples_switch_staging (
        id            BIGINT IDENTITY(1,1) NOT NULL,
        object_path   VARCHAR(512) NOT NULL,
        variable_name VARCHAR(128) NOT NULL,
        field_name    VARCHAR(128) NOT NULL CONSTRAINT df_variable_samples_switch_staging_field DEFAULT ('value'),
        sampled_at    DATETIME2    NOT NULL,
        observed_at   DATETIME2    NOT NULL,
        value_double  FLOAT NULL,
        value_text    NVARCHAR(MAX) NULL,
        quality       VARCHAR(32) NULL,
        CONSTRAINT pk_variable_samples_switch_staging PRIMARY KEY CLUSTERED (sampled_at, id)
    ) ON [PRIMARY];

    CREATE UNIQUE INDEX uq_variable_samples_switch_staging_series_time
        ON dbo.variable_samples_switch_staging (object_path, variable_name, field_name, sampled_at)
        ON [PRIMARY];

    CREATE INDEX idx_variable_samples_switch_staging_lookup
        ON dbo.variable_samples_switch_staging (object_path, variable_name, field_name, sampled_at DESC)
        ON [PRIMARY];
END
GO
