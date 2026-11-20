-- ADR-0078 Gate C1: monthly partitions for variable_samples (empty DB only).
-- ADR-0064: never rewrite in place after rows exist — record SKIPPED_NONEMPTY and leave the table alone.
-- Contract: RANGE RIGHT DATETIME2 UTC month starts; PRIMARY filegroup; LOCK_ESCALATION = AUTO;
-- PRIMARY KEY (sampled_at, id); aligned unique + lookup indexes on the scheme.
-- Does not touch event_history. Does not SWITCH / TRUNCATE. Does not auto-replay.

IF OBJECT_ID(N'dbo.mssql_historian_partition_status', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.mssql_historian_partition_status (
        table_name  VARCHAR(128) NOT NULL
            CONSTRAINT pk_mssql_historian_partition_status PRIMARY KEY,
        status      VARCHAR(32)  NOT NULL,
        detail      NVARCHAR(512) NULL,
        recorded_at DATETIME2    NOT NULL
    );
END
GO

SET NOCOUNT ON;

DECLARE @existingStatus VARCHAR(32) = NULL;
SELECT @existingStatus = status
FROM dbo.mssql_historian_partition_status
WHERE table_name = N'variable_samples';

IF @existingStatus = N'APPLIED'
BEGIN
    PRINT N'V101.2: variable_samples partition status already APPLIED — no-op.';
END
ELSE IF @existingStatus = N'SKIPPED_NONEMPTY'
BEGIN
    PRINT N'V101.2: variable_samples previously SKIPPED_NONEMPTY — no-op (ADR-0064).';
END
ELSE
BEGIN
    DECLARE @isPartitioned BIT = 0;
    IF OBJECT_ID(N'dbo.variable_samples', N'U') IS NOT NULL
       AND EXISTS (
            SELECT 1
            FROM sys.tables t
            INNER JOIN sys.indexes i
                ON i.object_id = t.object_id
               AND i.is_primary_key = 1
            INNER JOIN sys.partition_schemes ps
                ON i.data_space_id = ps.data_space_id
            WHERE t.object_id = OBJECT_ID(N'dbo.variable_samples')
       )
        SET @isPartitioned = 1;

    IF @isPartitioned = 1
    BEGIN
        MERGE dbo.mssql_historian_partition_status AS tgt
        USING (SELECT N'variable_samples' AS table_name) AS src
            ON tgt.table_name = src.table_name
        WHEN MATCHED THEN
            UPDATE SET status = N'APPLIED',
                       detail = N'Detected existing partition scheme on primary key',
                       recorded_at = SYSUTCDATETIME()
        WHEN NOT MATCHED THEN
            INSERT (table_name, status, detail, recorded_at)
            VALUES (N'variable_samples', N'APPLIED',
                    N'Detected existing partition scheme on primary key', SYSUTCDATETIME());
        PRINT N'V101.2: variable_samples already partitioned — recorded APPLIED.';
    END
    ELSE
    BEGIN
        DECLARE @hasRows BIT = 0;
        IF OBJECT_ID(N'dbo.variable_samples', N'U') IS NOT NULL
           AND EXISTS (SELECT 1 FROM dbo.variable_samples WITH (TABLOCKX, HOLDLOCK))
            SET @hasRows = 1;

        IF @hasRows = 1
        BEGIN
            MERGE dbo.mssql_historian_partition_status AS tgt
            USING (SELECT N'variable_samples' AS table_name) AS src
                ON tgt.table_name = src.table_name
            WHEN MATCHED THEN
                UPDATE SET status = N'SKIPPED_NONEMPTY',
                           detail = N'Table has rows; PK rewrite refused (ADR-0064). Retention stays DELETE + max-retention-days.',
                           recorded_at = SYSUTCDATETIME()
            WHEN NOT MATCHED THEN
                INSERT (table_name, status, detail, recorded_at)
                VALUES (
                    N'variable_samples',
                    N'SKIPPED_NONEMPTY',
                    N'Table has rows; PK rewrite refused (ADR-0064). Retention stays DELETE + max-retention-days.',
                    SYSUTCDATETIME()
                );
            PRINT N'V101.2: SKIPPED_NONEMPTY — variable_samples not empty; no partition rewrite.';
        END
        ELSE
        BEGIN
            -- Empty table (or missing after a failed mid-run): rebuild on monthly scheme.
            IF OBJECT_ID(N'dbo.variable_samples', N'U') IS NOT NULL
                DROP TABLE dbo.variable_samples;

            -- Deterministic empty horizon: month starts 2024-01-01 .. 2030-01-01 (RANGE RIGHT).
            -- Operator SPLIT extends beyond this; JVM never SPLITs.
            IF NOT EXISTS (SELECT 1 FROM sys.partition_functions WHERE name = N'pf_variable_samples_month')
            BEGIN
                DECLARE @boundaryStart DATE = '2024-01-01';
                DECLARE @boundaryEnd   DATE = '2030-01-01';
                DECLARE @d DATE = @boundaryStart;
                DECLARE @vals NVARCHAR(MAX) = N'';
                WHILE @d <= @boundaryEnd
                BEGIN
                    IF LEN(@vals) > 0 SET @vals = @vals + N',';
                    SET @vals = @vals + N'''' + CONVERT(CHAR(10), @d, 23) + N'''';
                    SET @d = DATEADD(MONTH, 1, @d);
                END;
                EXEC (N'CREATE PARTITION FUNCTION pf_variable_samples_month (DATETIME2) AS RANGE RIGHT FOR VALUES ('
                      + @vals + N')');
            END

            IF NOT EXISTS (SELECT 1 FROM sys.partition_schemes WHERE name = N'ps_variable_samples_month')
            BEGIN
                CREATE PARTITION SCHEME ps_variable_samples_month
                    AS PARTITION pf_variable_samples_month
                    ALL TO ([PRIMARY]);
            END

            CREATE TABLE dbo.variable_samples (
                id            BIGINT IDENTITY(1,1) NOT NULL,
                object_path   VARCHAR(512) NOT NULL,
                variable_name VARCHAR(128) NOT NULL,
                field_name    VARCHAR(128) NOT NULL CONSTRAINT df_variable_samples_field_name DEFAULT ('value'),
                sampled_at    DATETIME2    NOT NULL,
                observed_at   DATETIME2    NOT NULL,
                value_double  FLOAT NULL,
                value_text    NVARCHAR(MAX) NULL,
                quality       VARCHAR(32) NULL,
                CONSTRAINT pk_variable_samples PRIMARY KEY CLUSTERED (sampled_at, id)
            ) ON ps_variable_samples_month (sampled_at);

            ALTER TABLE dbo.variable_samples SET (LOCK_ESCALATION = AUTO);

            CREATE UNIQUE INDEX uq_variable_samples_series_time
                ON dbo.variable_samples (object_path, variable_name, field_name, sampled_at)
                ON ps_variable_samples_month (sampled_at);

            CREATE INDEX idx_variable_samples_lookup
                ON dbo.variable_samples (object_path, variable_name, field_name, sampled_at DESC)
                ON ps_variable_samples_month (sampled_at);

            MERGE dbo.mssql_historian_partition_status AS tgt
            USING (SELECT N'variable_samples' AS table_name) AS src
                ON tgt.table_name = src.table_name
            WHEN MATCHED THEN
                UPDATE SET status = N'APPLIED',
                           detail = N'Empty-table rebuild: PK (sampled_at, id) on ps_variable_samples_month',
                           recorded_at = SYSUTCDATETIME()
            WHEN NOT MATCHED THEN
                INSERT (table_name, status, detail, recorded_at)
                VALUES (
                    N'variable_samples',
                    N'APPLIED',
                    N'Empty-table rebuild: PK (sampled_at, id) on ps_variable_samples_month',
                    SYSUTCDATETIME()
                );
            PRINT N'V101.2: APPLIED — variable_samples rebuilt on monthly partitions.';
        END
    END
END
GO
