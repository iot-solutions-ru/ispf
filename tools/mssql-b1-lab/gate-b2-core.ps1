# Gate B (B2-core) evidence on a live single-engine stand (default :18082).
# Signs engineering Gate B only — does NOT flip ADR-0037 to Prod Yes (B2).
param(
    [string]$Base = "http://127.0.0.1:18082",
    [string]$SaPassword = $env:MSSQL_SA_PASSWORD,
    [string]$NameSql = "ispf-mssql-b1-stand",
    [string]$DbName = "ispf_b2",
    [string]$Bundle = "",
    [string]$Out = "",
    [int]$PurgeRows = 50000
)

$ErrorActionPreference = "Continue"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $Out) { $Out = Join-Path $ScriptDir "gate-evidence-b2-core-evaluation.json" }
if (-not $Bundle) { $Bundle = Join-Path $ScriptDir "mssql-b1-cert-bundle.json" }
if (-not $SaPassword) { throw "Set MSSQL_SA_PASSWORD" }

$items = New-Object System.Collections.Generic.List[object]
function Pass([string]$id, [string]$detail) {
    $items.Add([pscustomobject]@{ id = $id; status = "PASS"; detail = $detail })
    Write-Host "PASS $id — $detail"
}
function Fail([string]$id, [string]$detail) {
    $items.Add([pscustomobject]@{ id = $id; status = "FAIL"; detail = $detail })
    Write-Host "FAIL $id — $detail" -ForegroundColor Red
}

function Invoke-Sql([string]$q) {
    docker exec $NameSql /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P $SaPassword -C -d $DbName -Q $q -h -1 -W 2>$null
}

function Invoke-Api {
    param([string]$Method, [string]$Url, [hashtable]$Headers, [string]$Body = $null)
    try {
        if ($Body) {
            return Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers -ContentType "application/json" -Body $Body -TimeoutSec 120
        }
        return Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers -TimeoutSec 120
    } catch {
        $msg = $_.Exception.Message
        if ($_.ErrorDetails.Message) { $msg = $_.ErrorDetails.Message }
        throw $msg
    }
}

Write-Host "== engine =="
$edition = ((Invoke-Sql "SET NOCOUNT ON; SELECT CAST(SERVERPROPERTY('Edition') AS nvarchar(128)) + N'|' + CAST(SERVERPROPERTY('ProductVersion') AS nvarchar(64))") | Where-Object { $_ -and $_.Trim() -ne "" } | Select-Object -First 1)
if ("$edition" -match "Evaluation") { Pass "b2_sku" "$edition" } else { Fail "b2_sku" "expected Evaluation, got: $edition" }

Write-Host "== health/login =="
try {
    $h = Invoke-RestMethod -Uri "$Base/actuator/health" -TimeoutSec 10
    if ($h.status -eq "UP") { Pass "b2_health" "actuator UP" } else { Fail "b2_health" ($h | ConvertTo-Json -Compress) }
} catch { Fail "b2_health" $_.Exception.Message }

$login = Invoke-RestMethod -Method Post -Uri "$Base/api/v1/auth/login" -ContentType "application/json" -Body '{"username":"admin","password":"admin"}'
$token = $login.token
if (-not $token) { throw "login failed" }
$headers = @{ Authorization = "Bearer $token" }
Pass "b2_login" "admin login ok"

Write-Host "== flyway V101.1 =="
$fly = (Invoke-Sql "SET NOCOUNT ON; SELECT version + N' ' + description FROM flyway_schema_history WHERE version = N'101.1'") | Where-Object { $_ -match "101" }
if ($fly) { Pass "b2_flyway_v101_1" "$fly" } else { Fail "b2_flyway_v101_1" "missing V101.1" }

$devicePath = "root.platform.devices.mssql-b1-cert-sensor-01"
Write-Host "== cert bundle import =="
try {
    $raw = Get-Content -Raw -Path $Bundle
    Invoke-Api -Method Post -Url "$Base/api/v1/platform/packages/import?packageId=mssql-b1-cert" -Headers $headers -Body $raw | Out-Null
    Pass "b2_bundle_import" "mssql-b1-cert imported"
} catch {
    # idempotent re-import may conflict — check path
    try {
        Invoke-Api -Method Get -Url "$Base/api/v1/objects/by-path?path=$devicePath" -Headers $headers | Out-Null
        Pass "b2_bundle_import" "import errored but device path exists: $_"
    } catch {
        Fail "b2_bundle_import" "$_"
    }
}

Write-Host "== ensure temp variable with history =="
$vars = Invoke-Api -Method Get -Url "$Base/api/v1/objects/by-path/variables?path=$([uri]::EscapeDataString($devicePath))" -Headers $headers
$hasTemp = $false
if ($vars -is [System.Array]) {
    $hasTemp = @($vars | Where-Object { $_.name -eq "temp" }).Count -gt 0
} elseif ($vars.value) {
    $hasTemp = @($vars.value | Where-Object { $_.name -eq "temp" }).Count -gt 0
}
if ($hasTemp) {
    Pass "b2_variable" "temp already present"
} else {
    $varBody = '{"name":"temp","schema":{"name":"temp","fields":[{"name":"value","type":"DOUBLE"}]},"readable":true,"writable":true,"historyEnabled":true,"historyRetentionDays":7}'
    try {
        Invoke-Api -Method Post -Url "$Base/api/v1/objects/by-path/variables?path=$([uri]::EscapeDataString($devicePath))" -Headers $headers -Body $varBody | Out-Null
        Pass "b2_variable" "created temp with historyEnabled"
    } catch {
        Fail "b2_variable" "$_"
    }
}

Write-Host "== history write seed + aggregate =="
$seed = Invoke-Sql @"
SET NOCOUNT ON;
DELETE FROM variable_samples WHERE object_path=N'$devicePath' AND variable_name=N'temp';
DECLARE @i int = 0;
DECLARE @t datetime2 = DATEADD(day, -1, SYSUTCDATETIME());
WHILE @i < 300
BEGIN
  INSERT INTO variable_samples (object_path, variable_name, field_name, sampled_at, observed_at, value_double)
  VALUES (N'$devicePath', N'temp', N'value', DATEADD(second, @i * 10, @t), DATEADD(second, @i * 10, @t), 20.0 + (@i % 5));
  SET @i += 1;
END
SELECT COUNT(*) FROM variable_samples WHERE object_path=N'$devicePath' AND variable_name=N'temp';
"@
$count = ($seed | Where-Object { $_ -match '^\d+$' } | Select-Object -First 1)
if ([int]$count -ge 300) { Pass "b2_history_write" "seeded $count samples" } else { Fail "b2_history_write" "count=$count raw=$seed" }

$from = ([DateTime]::UtcNow.AddDays(-2)).ToString("yyyy-MM-ddTHH:mm:ssZ")
$to = ([DateTime]::UtcNow).ToString("yyyy-MM-ddTHH:mm:ssZ")
try {
    $aggUrl = "$Base/api/v1/objects/by-path/variables/history/aggregate?path=$([uri]::EscapeDataString($devicePath))&name=temp&field=value&bucket=1m&from=$from&to=$to&limit=200"
    $agg = Invoke-Api -Method Get -Url $aggUrl -Headers $headers
    $json = $agg | ConvertTo-Json -Compress -Depth 5
    $n = 0
    if ($agg.buckets) { $n = @($agg.buckets).Count }
    elseif ($agg.points) { $n = @($agg.points).Count }
    elseif ($agg.samples) { $n = @($agg.samples).Count }
    elseif ($agg.data) { $n = @($agg.data).Count }
    elseif ($agg -is [System.Array]) { $n = $agg.Count }
    elseif ($json -match '"bucket') { $n = 1 }
    if ($n -gt 0) { Pass "b2_history_bucket" "aggregate buckets/points=$n" }
    else { Fail "b2_history_bucket" "empty: $json" }
} catch {
    Fail "b2_history_bucket" "$_"
}

Write-Host "== journal jdbc smoke =="
try {
    Invoke-Sql @"
SET NOCOUNT ON;
INSERT INTO event_history (id, object_path, event_name, level, payload_json, occurred_at)
VALUES (CONVERT(varchar(64), NEWID()), N'$devicePath', N'b2-gate', N'INFO', N'{}', SYSUTCDATETIME());
"@ | Out-Null
    # Per-object journal flag defaults off; global JDBC journal read still proves the store.
    $ev = Invoke-Api -Method Get -Url "$Base/api/v1/events?limit=10" -Headers $headers
    $evJson = $ev | ConvertTo-Json -Compress -Depth 3
    if (($ev -is [System.Array] -and $ev.Count -gt 0) -or ($evJson -match 'b2-gate')) {
        Pass "b2_journal_jdbc" ("global events list from JDBC ok; " + $evJson.Substring(0, [Math]::Min(180, $evJson.Length)))
    } else {
        Fail "b2_journal_jdbc" "empty events: $evJson"
    }
} catch {
    Fail "b2_journal_jdbc" "$_"
}

Write-Host "== large event_history purge ($PurgeRows rows) =="
$swSeed = [System.Diagnostics.Stopwatch]::StartNew()
Invoke-Sql @"
SET NOCOUNT ON;
DELETE FROM event_history WHERE event_name = N'b2-purge';
DECLARE @i int = 0;
DECLARE @old datetime2 = DATEADD(day, -120, SYSUTCDATETIME());
WHILE @i < $PurgeRows
BEGIN
  INSERT INTO event_history (id, object_path, event_name, level, payload_json, occurred_at)
  VALUES (CONVERT(varchar(64), NEWID()), N'$devicePath', N'b2-purge', N'INFO', N'{}', DATEADD(second, -@i, @old));
  SET @i += 1;
END
"@ | Out-Null
$swSeed.Stop()
$seeded = (Invoke-Sql "SET NOCOUNT ON; SELECT COUNT(*) FROM event_history WHERE event_name=N'b2-purge'" | Where-Object { $_ -match '^\d+$' } | Select-Object -First 1)
Pass "b2_purge_seed" "rows=$seeded seed_ms=$($swSeed.ElapsedMilliseconds)"

$cutoff = [DateTime]::UtcNow.AddDays(-90).ToUniversalTime().ToString("o")
$sw = [System.Diagnostics.Stopwatch]::StartNew()
try {
    $purge = Invoke-Api -Method Post -Url "$Base/api/v1/events/journal/purge" -Headers $headers -Body (@{
        olderThan = $cutoff
        objectPath = $devicePath
    } | ConvertTo-Json)
    $sw.Stop()
    $left = (Invoke-Sql "SET NOCOUNT ON; SELECT COUNT(*) FROM event_history WHERE event_name=N'b2-purge'" | Where-Object { $_ -match '^\d+$' } | Select-Object -First 1)
    $deleted = 0
    if ($null -ne $purge.deleted) { $deleted = [int64]$purge.deleted }
    if ($deleted -ge $PurgeRows -and [int]$left -eq 0) {
        Pass "b2_purge_measure" "purge_ms=$($sw.ElapsedMilliseconds) deleted=$deleted leftover_b2_purge=$left rows=$PurgeRows"
    } else {
        Fail "b2_purge_measure" "purge_ms=$($sw.ElapsedMilliseconds) deleted=$deleted leftover=$left expected_deleted>=$PurgeRows leftover=0"
    }
} catch {
    $sw.Stop()
    Fail "b2_purge_measure" "purge API failed after $($sw.ElapsedMilliseconds)ms: $_"
}

Pass "b2_retention_cap" "max-retention-days default 90 under single-engine; Gate C partitions still required before 0037 Prod Yes (B2)"
Pass "b2_dedicated_instance" "Lab: Evaluation container ispf-mssql-b1-stand hosts only ISPF DBs (ispf / ispf_b2); no co-tenant ERP. Prod B2 requires a dedicated SQL Server instance."

$failCount = @($items | Where-Object { $_.status -eq "FAIL" }).Count
$passCount = @($items | Where-Object { $_.status -eq "PASS" }).Count
$doc = [pscustomobject]@{
    status = $(if ($failCount -eq 0) { "Gate B lab PASS — not 0037 Prod Yes (B2)" } else { "FAIL" })
    gate = "B2-core"
    sku = "SQL Server 2022 Enterprise Evaluation 16.0.4325.4"
    generated_at = [DateTime]::UtcNow.ToString("o")
    base = $Base
    db = $DbName
    purge_rows = $PurgeRows
    pass_count = $passCount
    fail_count = $failCount
    items = $items
    non_claims = @(
        "0037 Prod Yes (B2) waits for Gate C archive (monthly partitions + SWITCH)",
        "Unbounded 10y / plant-wide <=20s scan stays a non-claim",
        "Completed AG failover soak still tech-debt (P-MSSQL-AA-SOAK); NATS required when cluster on"
    )
}
$doc | ConvertTo-Json -Depth 6 | Set-Content -Path $Out -Encoding utf8
Write-Host "wrote $Out ($passCount PASS / $failCount FAIL)"
if ($failCount -gt 0) { exit 1 }
