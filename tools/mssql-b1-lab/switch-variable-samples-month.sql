/*
  ADR-0081 lab helper — SWITCH + TRUNCATE one oldest eligible UTC month of variable_samples.

  Safe by default:
    - Dry-run (DryRun=1, or unsubstituted DryRun under SSMS/JDBC): run every guard, print plan, RETURN
    - Mutation requires both DryRun=0 and ConfirmSwitch=YES (exact)

  Required sqlcmd variables (ODBC sqlcmd skips the batch if any $(Var) is undefined):
    MaxRetentionDays  — positive integer; NO default (must match live ispf.relational.mssql.max-retention-days)
    DryRun            — 0|1 (pass 1 for dry-run under ODBC sqlcmd)
    ConfirmSwitch     — YES only with DryRun=0; pass NO for dry-run under ODBC sqlcmd

  Usage (sqlcmd — always pass all -v; use -b):
    sqlcmd -S <host> -d ispf -U <maint> -P <pwd> -b -v MaxRetentionDays=90 -v DryRun=1 -v ConfirmSwitch=NO -i switch-variable-samples-month.sql
    sqlcmd -S <host> -d ispf -U <maint> -P <pwd> -b -v MaxRetentionDays=90 -v DryRun=0 -v ConfirmSwitch=YES -i switch-variable-samples-month.sql

  Requires Flyway V101.5 empty dbo.variable_samples_switch_staging on PRIMARY (script refuses if missing).
  Maintenance login only. ISPF runtime login must not have ALTER.
  Product retention remains app DELETE + max-retention-days until operators run this deliberately.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

DECLARE @retRaw NVARCHAR(32) = N'$(MaxRetentionDays)';
DECLARE @dryRaw NVARCHAR(32) = N'$(DryRun)';
DECLARE @confirmRaw NVARCHAR(32) = N'$(ConfirmSwitch)';

DECLARE @maxRetentionDays INT = NULL;
DECLARE @dryRun BIT = 1;
DECLARE @confirm NVARCHAR(32) = N'';

IF @retRaw LIKE (N'$' + N'(%)')
BEGIN
    RAISERROR(N'MaxRetentionDays is required (no default). Pass -v MaxRetentionDays=<live ceiling>.', 16, 1);
    RETURN;
END
SET @maxRetentionDays = TRY_CAST(LTRIM(RTRIM(@retRaw)) AS INT);
IF @maxRetentionDays IS NULL OR @maxRetentionDays < 1
BEGIN
    RAISERROR(N'MaxRetentionDays must be an integer >= 1 (got %s).', 16, 1, @retRaw);
    RETURN;
END

IF @dryRaw NOT LIKE (N'$' + N'(%)')
BEGIN
    IF LTRIM(RTRIM(@dryRaw)) = N'0'
        SET @dryRun = 0;
    ELSE IF LTRIM(RTRIM(@dryRaw)) = N'1'
        SET @dryRun = 1;
    ELSE
    BEGIN
        RAISERROR(N'DryRun must be 0 or 1 (got %s).', 16, 1, @dryRaw);
        RETURN;
    END
END

IF @confirmRaw NOT LIKE (N'$' + N'(%)')
    SET @confirm = LTRIM(RTRIM(@confirmRaw));

IF @dryRun = 0 AND @confirm COLLATE Latin1_General_100_BIN2 <> N'YES'
BEGIN
    RAISERROR(N'Mutation refused: DryRun=0 requires ConfirmSwitch=YES (exact).', 16, 1);
    RETURN;
END

IF OBJECT_ID(N'dbo.mssql_historian_partition_status', N'U') IS NULL
BEGIN
    RAISERROR(N'mssql_historian_partition_status missing — V101.2 not applied. Stay on DELETE.', 16, 1);
    RETURN;
END

IF OBJECT_ID(N'dbo.variable_samples', N'U') IS NULL
BEGIN
    RAISERROR(N'dbo.variable_samples missing — refuse SWITCH.', 16, 1);
    RETURN;
END

DECLARE @status VARCHAR(32);
SELECT @status = status FROM dbo.mssql_historian_partition_status WHERE table_name = N'variable_samples';
IF @status IS NULL OR @status <> N'APPLIED'
BEGIN
    RAISERROR(
        N'variable_samples partition status is not APPLIED (got %s). Refuse SWITCH; stay on DELETE. SKIPPED_NONEMPTY is permanent for this database.',
        16, 1, @status
    );
    RETURN;
END

IF NOT EXISTS (
    SELECT 1 FROM sys.partition_functions
    WHERE name = N'pf_variable_samples_month'
      AND boundary_value_on_right = 1
)
BEGIN
    RAISERROR(N'pf_variable_samples_month missing or not RANGE RIGHT.', 16, 1);
    RETURN;
END

DECLARE @schemeId INT;
SELECT @schemeId = ps.data_space_id
FROM sys.partition_schemes ps
INNER JOIN sys.partition_functions pf
    ON pf.function_id = ps.function_id
   AND pf.name = N'pf_variable_samples_month'
   AND pf.boundary_value_on_right = 1
WHERE ps.name = N'ps_variable_samples_month';
IF @schemeId IS NULL
BEGIN
    RAISERROR(N'ps_variable_samples_month missing or not bound to RANGE RIGHT pf_variable_samples_month.', 16, 1);
    RETURN;
END

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.variable_samples')
      AND index_id = 1
      AND is_primary_key = 1
      AND data_space_id = @schemeId
)
BEGIN
    RAISERROR(N'variable_samples clustered PK (index_id=1) missing or not on ps_variable_samples_month.', 16, 1);
    RETURN;
END

IF EXISTS (
    SELECT 1
    FROM sys.indexes i
    WHERE i.object_id = OBJECT_ID(N'dbo.variable_samples')
      AND i.type > 0
      AND i.data_space_id <> @schemeId
)
BEGIN
    RAISERROR(N'Non-aligned index on variable_samples — refuse SWITCH.', 16, 1);
    RETURN;
END

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.variable_samples')
      AND name = N'uq_variable_samples_series_time'
      AND data_space_id = @schemeId
)
   OR NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.variable_samples')
      AND name = N'idx_variable_samples_lookup'
      AND data_space_id = @schemeId
)
BEGIN
    RAISERROR(N'Required aligned indexes missing on ps_variable_samples_month.', 16, 1);
    RETURN;
END

IF EXISTS (
    SELECT 1
    FROM sys.foreign_keys fk
    WHERE fk.parent_object_id = OBJECT_ID(N'dbo.variable_samples')
       OR fk.referenced_object_id = OBJECT_ID(N'dbo.variable_samples')
)
BEGIN
    RAISERROR(N'Foreign key touches variable_samples — refuse SWITCH.', 16, 1);
    RETURN;
END

-- Staging guards before month selection so missing/bad V101.5 fails even when no month is eligible.
DECLARE @stagingFg INT;
SELECT @stagingFg = data_space_id FROM sys.filegroups WHERE name = N'PRIMARY';
IF @stagingFg IS NULL
BEGIN
    RAISERROR(N'PRIMARY filegroup missing — refuse.', 16, 1);
    RETURN;
END

IF OBJECT_ID(N'dbo.variable_samples_switch_staging', N'U') IS NULL
BEGIN
    RAISERROR(
        N'Staging dbo.variable_samples_switch_staging missing — refuse. Apply Flyway V101.5 (empty PRIMARY staging); script no longer auto-creates.',
        16, 1
    );
    RETURN;
END

IF EXISTS (SELECT 1 FROM dbo.variable_samples_switch_staging)
BEGIN
    RAISERROR(N'Staging dbo.variable_samples_switch_staging is nonempty — refuse (no pre-SWITCH TRUNCATE/DROP).', 16, 1);
    RETURN;
END

IF EXISTS (
    SELECT 1 FROM sys.foreign_keys fk
    WHERE fk.parent_object_id = OBJECT_ID(N'dbo.variable_samples_switch_staging')
       OR fk.referenced_object_id = OBJECT_ID(N'dbo.variable_samples_switch_staging')
)
BEGIN
    RAISERROR(N'Foreign key touches staging — refuse.', 16, 1);
    RETURN;
END

IF EXISTS (
    SELECT 1
    FROM sys.indexes i
    WHERE i.object_id = OBJECT_ID(N'dbo.variable_samples_switch_staging')
      AND i.type > 0
      AND (i.data_space_id <> @stagingFg OR i.is_disabled = 1)
)
BEGIN
    RAISERROR(N'Staging index not on PRIMARY or disabled — refuse.', 16, 1);
    RETURN;
END

IF EXISTS (
    SELECT 1
    FROM (
        SELECT c.column_id, c.name, c.system_type_id, c.user_type_id, c.max_length,
               c.precision, c.scale, c.is_nullable, c.is_identity, c.collation_name, c.is_computed
        FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.variable_samples')
        EXCEPT
        SELECT c.column_id, c.name, c.system_type_id, c.user_type_id, c.max_length,
               c.precision, c.scale, c.is_nullable, c.is_identity, c.collation_name, c.is_computed
        FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.variable_samples_switch_staging')
    ) d
)
OR EXISTS (
    SELECT 1
    FROM (
        SELECT c.column_id, c.name, c.system_type_id, c.user_type_id, c.max_length,
               c.precision, c.scale, c.is_nullable, c.is_identity, c.collation_name, c.is_computed
        FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.variable_samples_switch_staging')
        EXCEPT
        SELECT c.column_id, c.name, c.system_type_id, c.user_type_id, c.max_length,
               c.precision, c.scale, c.is_nullable, c.is_identity, c.collation_name, c.is_computed
        FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'dbo.variable_samples')
    ) d
)
BEGIN
    RAISERROR(N'Staging columns are not identical to variable_samples — refuse.', 16, 1);
    RETURN;
END

IF EXISTS (
    SELECT 1
    FROM (
        SELECT
            i.type, i.is_unique, i.is_primary_key, i.has_filter, i.filter_definition,
            (
                SELECT STRING_AGG(
                    CONCAT(c.name, N':', ic.key_ordinal, N':', ic.is_descending_key, N':', ic.is_included_column),
                    N'|'
                ) WITHIN GROUP (ORDER BY ic.key_ordinal, ic.index_column_id)
                FROM sys.index_columns ic
                INNER JOIN sys.columns c
                    ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
            ) AS col_sig
        FROM sys.indexes i
        WHERE i.object_id = OBJECT_ID(N'dbo.variable_samples')
          AND i.type > 0
        EXCEPT
        SELECT
            i.type, i.is_unique, i.is_primary_key, i.has_filter, i.filter_definition,
            (
                SELECT STRING_AGG(
                    CONCAT(c.name, N':', ic.key_ordinal, N':', ic.is_descending_key, N':', ic.is_included_column),
                    N'|'
                ) WITHIN GROUP (ORDER BY ic.key_ordinal, ic.index_column_id)
                FROM sys.index_columns ic
                INNER JOIN sys.columns c
                    ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
            ) AS col_sig
        FROM sys.indexes i
        WHERE i.object_id = OBJECT_ID(N'dbo.variable_samples_switch_staging')
          AND i.type > 0
    ) d
)
OR EXISTS (
    SELECT 1
    FROM (
        SELECT
            i.type, i.is_unique, i.is_primary_key, i.has_filter, i.filter_definition,
            (
                SELECT STRING_AGG(
                    CONCAT(c.name, N':', ic.key_ordinal, N':', ic.is_descending_key, N':', ic.is_included_column),
                    N'|'
                ) WITHIN GROUP (ORDER BY ic.key_ordinal, ic.index_column_id)
                FROM sys.index_columns ic
                INNER JOIN sys.columns c
                    ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
            ) AS col_sig
        FROM sys.indexes i
        WHERE i.object_id = OBJECT_ID(N'dbo.variable_samples_switch_staging')
          AND i.type > 0
        EXCEPT
        SELECT
            i.type, i.is_unique, i.is_primary_key, i.has_filter, i.filter_definition,
            (
                SELECT STRING_AGG(
                    CONCAT(c.name, N':', ic.key_ordinal, N':', ic.is_descending_key, N':', ic.is_included_column),
                    N'|'
                ) WITHIN GROUP (ORDER BY ic.key_ordinal, ic.index_column_id)
                FROM sys.index_columns ic
                INNER JOIN sys.columns c
                    ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
            ) AS col_sig
        FROM sys.indexes i
        WHERE i.object_id = OBJECT_ID(N'dbo.variable_samples')
          AND i.type > 0
    ) d
)
BEGIN
    RAISERROR(N'Staging index structure does not match variable_samples — refuse.', 16, 1);
    RETURN;
END

DECLARE @nowUtc DATETIME2 = SYSUTCDATETIME();
DECLARE @cutoff DATETIME2 = DATEADD(DAY, -@maxRetentionDays, @nowUtc);

PRINT N'ADR-0081 SWITCH helper';
PRINT N'  now (UTC)           = ' + CONVERT(NVARCHAR(30), @nowUtc, 126);
PRINT N'  MaxRetentionDays    = ' + CAST(@maxRetentionDays AS NVARCHAR(12));
PRINT N'  cutoff (now - days) = ' + CONVERT(NVARCHAR(30), @cutoff, 126);
PRINT N'  eligibility         = exclusive month end <= cutoff (not month_start <= cutoff)';
PRINT N'  dry-run             = ' + CASE WHEN @dryRun = 1 THEN N'yes' ELSE N'no (ConfirmSwitch=YES)' END;
PRINT N'Oracle: now 2026-11-21 + 90d → cutoff 2026-08-23; July eligible; August not (Aug 23–31 still inside window).';
PRINT N'Destruction list (entire sampled_at month, including non-rollup series):';
PRINT N'  non-numeric / value_text; field_name=$record; history_enabled=0; quality;';
PRINT N'  observed_at calendar month <> sampled_at calendar month.';
PRINT N'Rollup cursor / ingest-rollups are not a seal and are not read here.';

DECLARE @partNum INT = NULL;
DECLARE @rangeStart DATETIME2 = NULL;
DECLARE @rangeEnd DATETIME2 = NULL;

;WITH parts AS (
    SELECT
        p.partition_number,
        CAST(prv_lo.value AS DATETIME2) AS range_start,
        CAST(prv_hi.value AS DATETIME2) AS range_end
    FROM sys.partitions p
    INNER JOIN sys.indexes i
        ON i.object_id = p.object_id
       AND i.index_id = p.index_id
       AND i.index_id = 1
    INNER JOIN sys.partition_schemes ps
        ON ps.data_space_id = i.data_space_id
       AND ps.name = N'ps_variable_samples_month'
    INNER JOIN sys.partition_functions pf
        ON pf.function_id = ps.function_id
       AND pf.name = N'pf_variable_samples_month'
       AND pf.boundary_value_on_right = 1
    LEFT JOIN sys.partition_range_values prv_lo
        ON prv_lo.function_id = pf.function_id
       AND prv_lo.boundary_id = p.partition_number - 1
    LEFT JOIN sys.partition_range_values prv_hi
        ON prv_hi.function_id = pf.function_id
       AND prv_hi.boundary_id = p.partition_number
    WHERE p.object_id = OBJECT_ID(N'dbo.variable_samples')
)
SELECT TOP (1)
    @partNum = partition_number,
    @rangeStart = range_start,
    @rangeEnd = range_end
FROM parts
WHERE range_start IS NOT NULL
  AND range_end IS NOT NULL
  AND range_start = CAST(DATEFROMPARTS(YEAR(range_start), MONTH(range_start), 1) AS DATETIME2)
  AND range_end = DATEADD(MONTH, 1, range_start)
  AND range_end <= @cutoff
  AND EXISTS (
        SELECT 1
        FROM dbo.variable_samples vs
        WHERE vs.sampled_at >= range_start
          AND vs.sampled_at < range_end
      )
ORDER BY range_start ASC;

IF @partNum IS NULL
BEGIN
    PRINT N'No eligible nonempty month (successful no-op).';
    RETURN;
END

DECLARE @rowCount BIGINT;
SELECT @rowCount = COUNT_BIG(*)
FROM dbo.variable_samples
WHERE sampled_at >= @rangeStart
  AND sampled_at < @rangeEnd;

PRINT N'Chosen partition_number = ' + CAST(@partNum AS NVARCHAR(12));
PRINT N'  range [start, next)   = [' + CONVERT(NVARCHAR(30), @rangeStart, 126)
    + N', ' + CONVERT(NVARCHAR(30), @rangeEnd, 126) + N')';
PRINT N'  rows (sampled_at probe) = ' + CAST(@rowCount AS NVARCHAR(20));
PRINT N'  compare: next <= cutoff → ' + CONVERT(NVARCHAR(30), @rangeEnd, 126)
    + N' <= ' + CONVERT(NVARCHAR(30), @cutoff, 126);

IF @rangeStart < CONVERT(DATETIME2, '2024-01-01', 23)
BEGIN
    RAISERROR(N'Refuse left catch-all / pre-2024 slice.', 16, 1);
    RETURN;
END

DECLARE @partFg INT;
SELECT @partFg = dds.data_space_id
FROM sys.destination_data_spaces dds
INNER JOIN sys.partition_schemes ps ON ps.data_space_id = dds.partition_scheme_id
WHERE ps.name = N'ps_variable_samples_month'
  AND dds.destination_id = @partNum;

IF @partFg IS NULL OR @partFg <> @stagingFg
BEGIN
    RAISERROR(N'Partition filegroup is not PRIMARY (or unknown) — refuse SWITCH to PRIMARY staging.', 16, 1);
    RETURN;
END

IF @dryRun = 1
BEGIN
    PRINT N'Dry-run complete — no SWITCH/TRUNCATE. Re-run with -v DryRun=0 -v ConfirmSwitch=YES to mutate.';
    RETURN;
END

BEGIN TRANSACTION;

-- Dynamic $PARTITION: bare $PARTITION.pf_… fails compile-time when the function is absent (SKIPPED_NONEMPTY).
DECLARE @resolvedPart INT = NULL;
DECLARE @partProbe NVARCHAR(512) =
    N'SET @out = $PARTITION.pf_variable_samples_month(@in);';
EXEC sys.sp_executesql
    @partProbe,
    N'@in DATETIME2, @out INT OUTPUT',
    @in = @rangeStart,
    @out = @resolvedPart OUTPUT;

IF @resolvedPart IS NULL OR @resolvedPart <> @partNum
BEGIN
    ROLLBACK TRANSACTION;
    RAISERROR(N'Partition number drifted before SWITCH — rolled back.', 16, 1);
    RETURN;
END

DECLARE @sql NVARCHAR(512) =
    N'ALTER TABLE dbo.variable_samples SWITCH PARTITION '
    + CAST(@partNum AS NVARCHAR(12))
    + N' TO dbo.variable_samples_switch_staging;';
EXEC (@sql);

DECLARE @stagedMin DATETIME2;
DECLARE @stagedMax DATETIME2;
SELECT
    @stagedMin = MIN(sampled_at),
    @stagedMax = MAX(sampled_at)
FROM dbo.variable_samples_switch_staging;

IF @stagedMin IS NULL
   OR @stagedMax IS NULL
   OR @stagedMin < @rangeStart
   OR @stagedMax >= @rangeEnd
BEGIN
    ROLLBACK TRANSACTION;
    RAISERROR(N'After SWITCH staging row set invalid — rolled back.', 16, 1);
    RETURN;
END

TRUNCATE TABLE dbo.variable_samples_switch_staging;

COMMIT TRANSACTION;

PRINT N'SWITCH+TRUNCATE committed for partition '
    + CAST(@partNum AS NVARCHAR(12))
    + N' [' + CONVERT(NVARCHAR(30), @rangeStart, 126)
    + N', ' + CONVERT(NVARCHAR(30), @rangeEnd, 126) + N').';
PRINT N'Boundaries were not MERGED. Product charts stay on observed_at. Prod Yes B2 unchanged.';
GO
