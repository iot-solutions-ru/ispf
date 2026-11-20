-- ADR-0078 Gate C2: contiguous forward coverage cursor for JDBC rollup materializer.
-- Not a SWITCH seal. Charts still read raw variable_samples (observed_at). App DELETE retention stays.

IF OBJECT_ID(N'dbo.mssql_historian_rollup_cursor', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.mssql_historian_rollup_cursor (
        bucket_seconds   INT         NOT NULL
            CONSTRAINT pk_mssql_historian_rollup_cursor PRIMARY KEY,
        covered_through  DATETIME2   NOT NULL,
        updated_at       DATETIME2   NOT NULL
    );
END
GO
