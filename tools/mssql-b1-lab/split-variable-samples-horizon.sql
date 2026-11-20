/*
  Gate C1 operator helper — append empty UTC month boundaries for variable_samples.

  Safe actions in this script:
    - Report partition status / alignment / current horizon
    - SPLIT the rightmost empty partition to add more month boundaries

  Refused here (and by Gate C1 product code):
    - SWITCH / TRUNCATE / DROP PARTITION as retention
    - Any mutation of raw sample rows
    - SPLIT when the rightmost partition already holds rows (Sch-M + data move)

  Parameter MonthsToAdd (default 12): number of month boundaries to append past the
  current rightmost boundary. Each successful run appends that many; it is not
  "ensure now+N". Range 1..120.

  Run as a maintenance login with ALTER on the database (lab sa is enough).
  The ISPF runtime login must not have ALTER.

  Usage (sqlcmd — always pass -v; ODBC sqlcmd skips the batch if the variable is undefined):
    sqlcmd -S <host> -d ispf -U <maint> -P <pwd> -v MonthsToAdd=12 -i split-variable-samples-horizon.sql
    sqlcmd ... -v MonthsToAdd=24 -i split-variable-samples-horizon.sql
  SSMS / JDBC without SQLCMD substitution keeps the literal token and defaults to 12.
*/

SET NOCOUNT ON;

-- sqlcmd -v MonthsToAdd=N substitutes the token; unsubstituted token → default 12.
DECLARE @monthsRaw NVARCHAR(32) = N'$(MonthsToAdd)';
DECLARE @monthsToAdd INT = 12;
IF @monthsRaw NOT LIKE (N'$' + N'(%)')
BEGIN
    SET @monthsToAdd = TRY_CAST(LTRIM(RTRIM(@monthsRaw)) AS INT);
    IF @monthsToAdd IS NULL
    BEGIN
        RAISERROR(N'MonthsToAdd must be an integer (got %s).', 16, 1, @monthsRaw);
        RETURN;
    END
END

IF @monthsToAdd < 1 OR @monthsToAdd > 120
BEGIN
    RAISERROR(N'MonthsToAdd must be between 1 and 120.', 16, 1);
    RETURN;
END

IF OBJECT_ID(N'dbo.mssql_historian_partition_status', N'U') IS NULL
BEGIN
    RAISERROR(N'mssql_historian_partition_status missing — V101.2 not applied.', 16, 1);
    RETURN;
END

DECLARE @status VARCHAR(32);
SELECT @status = status FROM dbo.mssql_historian_partition_status WHERE table_name = N'variable_samples';
IF @status IS NULL OR @status <> N'APPLIED'
BEGIN
    RAISERROR(N'variable_samples partition status is not APPLIED (got %s). Refuse SPLIT.', 16, 1, @status);
    RETURN;
END

IF NOT EXISTS (SELECT 1 FROM sys.partition_functions WHERE name = N'pf_variable_samples_month')
BEGIN
    RAISERROR(N'pf_variable_samples_month missing.', 16, 1);
    RETURN;
END

DECLARE @schemeId INT;
SELECT @schemeId = data_space_id
FROM sys.partition_schemes
WHERE name = N'ps_variable_samples_month';
IF @schemeId IS NULL
BEGIN
    RAISERROR(N'ps_variable_samples_month missing.', 16, 1);
    RETURN;
END

-- Every non-heap index on variable_samples must sit on the month scheme.
IF EXISTS (
    SELECT 1
    FROM sys.indexes i
    WHERE i.object_id = OBJECT_ID(N'dbo.variable_samples')
      AND i.type > 0
      AND i.data_space_id <> @schemeId
)
BEGIN
    RAISERROR(N'Non-aligned index on variable_samples — refuse SPLIT (would block SWITCH later).', 16, 1);
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

DECLARE @maxBoundary DATETIME2;
SELECT @maxBoundary = MAX(CAST(prv.value AS DATETIME2))
FROM sys.partition_functions pf
INNER JOIN sys.partition_range_values prv ON prv.function_id = pf.function_id
WHERE pf.name = N'pf_variable_samples_month';

IF @maxBoundary IS NULL
BEGIN
    RAISERROR(N'No range values on pf_variable_samples_month.', 16, 1);
    RETURN;
END

PRINT N'Current rightmost boundary: ' + CONVERT(NVARCHAR(30), @maxBoundary, 126);
PRINT N'Left catch-all partition is permanent — backdated sampled_at after a future SWITCH lands there.';

-- Refuse SPLIT if the unbounded right partition already has rows (data move + Sch-M).
IF EXISTS (SELECT 1 FROM dbo.variable_samples WHERE sampled_at >= @maxBoundary)
BEGIN
    DECLARE @maxBoundaryText NVARCHAR(30) = CONVERT(NVARCHAR(30), @maxBoundary, 126);
    RAISERROR(
        N'Rightmost partition is not empty (sampled_at >= %s). Refuse SPLIT to avoid schema lock + data move.',
        16, 1, @maxBoundaryText
    );
    RETURN;
END

DECLARE @next DATETIME2 = DATEADD(MONTH, 1, @maxBoundary);
DECLARE @i INT = 0;
WHILE @i < @monthsToAdd
BEGIN
    ALTER PARTITION SCHEME ps_variable_samples_month NEXT USED [PRIMARY];
    ALTER PARTITION FUNCTION pf_variable_samples_month() SPLIT RANGE (@next);
    PRINT N'SPLIT ' + CONVERT(NVARCHAR(30), @next, 126);
    SET @next = DATEADD(MONTH, 1, @next);
    SET @i = @i + 1;
END

PRINT N'Done. This script never SWITCH / TRUNCATE. Retention stays app DELETE + max-retention-days until Gate C2.';
GO
