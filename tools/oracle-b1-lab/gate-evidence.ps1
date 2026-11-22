# ADR-0080 B1 lab-green evidence collector against the stand from stand-local.ps1 (default :18092).
# Records results to -Out. Lab evidence only: Oracle Database Free is not a named production edition.
param(
    [string]$Base = "http://127.0.0.1:18092",
    [string]$Jar = "",
    [string]$OjdbcJar = "",
    [string]$Password = $env:ORACLE_LAB_PASSWORD,
    [string]$Net = "ispf-oracle-b1-net",
    [string]$NameSql = "ispf-oracle-b1-stand",
    [string]$NameApp = "ispf-oracle-b1-app",
    [string]$Bundle = "",
    [string]$Out = "",
    [string]$EngineNote = "Oracle lab cell (set -EngineNote)"
)

$ErrorActionPreference = "Continue"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
. (Join-Path $ScriptDir "lab-common.ps1")
if (-not $Password) { throw "Set ORACLE_LAB_PASSWORD or pass -Password" }
if (-not $Bundle) { $Bundle = Join-Path $ScriptDir "oracle-b1-cert-bundle.json" }
if (-not $Out) { $Out = Join-Path $env:TEMP "oracle-b1-gate-evidence.json" }
$Jar = Resolve-LabBootJar $RepoRoot $Jar
$OjdbcJar = Resolve-LabOjdbc $RepoRoot $OjdbcJar
$Jdbc = Get-OracleJdbcUrl $NameSql
$B1Env = Get-OracleB1Env
$items = New-Object System.Collections.ArrayList

function Pass([string]$id, [string]$detail) {
    [void]$items.Add([ordered]@{ id = $id; status = "PASS"; detail = $detail })
    Write-Host "PASS $id - $detail"
}
function Fail([string]$id, [string]$detail) {
    [void]$items.Add([ordered]@{ id = $id; status = "FAIL"; detail = $detail })
    Write-Host "FAIL $id - $detail"
}

function Invoke-Http([string]$Method, [string]$Url, [string]$Token = "", [string]$Body = "") {
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    try {
        $params = @{ Method = $Method; Uri = $Url; Headers = $headers; UseBasicParsing = $true; TimeoutSec = 120 }
        if ($Body) {
            $params["ContentType"] = "application/json"
            $params["Body"] = [System.Text.Encoding]::UTF8.GetBytes($Body)
        }
        $r = Invoke-WebRequest @params
        $content = $r.Content
        # Windows PowerShell returns non-text media types (actuator v3+json) as byte[].
        if ($content -is [byte[]]) { $content = [System.Text.Encoding]::UTF8.GetString($content) }
        return @{ Code = [int]$r.StatusCode; Body = $content }
    } catch {
        $code = 0
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        return @{ Code = $code; Body = "$($_.Exception.Message)" }
    }
}

function Wait-Log([string]$name, [string]$expect, [int]$seconds = 180) {
    $deadline = (Get-Date).AddSeconds($seconds)
    $log = ""
    while ((Get-Date) -lt $deadline) {
        $log = docker logs $name 2>&1 | Out-String
        if ($log.Contains($expect)) { return @{ Found = $true; Log = $log } }
        if ($log.Contains("Started IspfServerApplication")) { return @{ Found = $false; Log = $log } }
        $alive = docker ps --format "{{.Names}}" | Select-String -Pattern "^$name$"
        if (-not $alive) {
            $log = docker logs $name 2>&1 | Out-String
            return @{ Found = $log.Contains($expect); Log = $log }
        }
        Start-Sleep 2
    }
    return @{ Found = $false; Log = $log }
}

function Run-Fence([string]$id, [hashtable]$overrides, [string]$expect, [switch]$NoDriver) {
    $envExtra = @{}
    foreach ($k in $B1Env.Keys) { $envExtra[$k] = $B1Env[$k] }
    foreach ($k in $overrides.Keys) { $envExtra[$k] = $overrides[$k] }
    $name = "ispf-oracle-b1-fence-$id"
    if (-not (Start-OracleIspf -Name $name -Net $Net -Jar $Jar -OjdbcJar $OjdbcJar -Password $Password `
            -Jdbc $Jdbc -EnvExtra $envExtra -NoDriver:$NoDriver)) {
        Fail $id "docker run failed"
        return
    }
    $r = Wait-Log $name $expect
    docker rm -f $name 2>$null | Out-Null
    if ($r.Found) { Pass $id "refused with: $expect" } else { Fail $id "did not see expect=$expect" }
}

Write-Host "== 0 engine identity =="
$banner = Invoke-OracleSql $NameSql "system" $Password "SELECT banner_full FROM v`$version;"
$cell = Invoke-OracleSql $NameSql "system" $Password @"
SELECT (SELECT value FROM nls_database_parameters WHERE parameter = 'NLS_CHARACTERSET') || ' | ' ||
       (SELECT value FROM v`$parameter WHERE name = 'max_string_size') || ' | ' ||
       (SELECT value FROM v`$parameter WHERE name = 'db_block_size') FROM dual;
"@
$bannerLine = ($banner -split "`n")[0].Trim()
Write-Host "engine=$bannerLine | $cell"
if ($bannerLine -like "*Free Release 23.*" -and $cell -eq "AL32UTF8 | STANDARD | 8192") {
    Pass "g0_engine" "$bannerLine; AL32UTF8, MAX_STRING_SIZE=STANDARD, 8 KiB blocks"
} else {
    Fail "g0_engine" "unexpected engine cell: $bannerLine | $cell"
}

Write-Host "== 1 health / login / flyway / dialect / driver =="
$h = Invoke-Http "GET" "$Base/actuator/health"
if ($h.Code -eq 200 -and $h.Body -match '"status":"UP"') { Pass "g1_health" "actuator health UP" } else { Fail "g1_health" "health code=$($h.Code)" }

$login = Invoke-Http "POST" "$Base/api/v1/auth/login" "" '{"username":"admin","password":"admin"}'
$token = ""
if ($login.Code -eq 200) { try { $token = ($login.Body | ConvertFrom-Json).token } catch {} }
if ($token) { Pass "g1_login" "admin login ok" } else { Fail "g1_login" "login code=$($login.Code)" }

# Flyway quotes its history table verbatim, so on Oracle it is the lowercase "flyway_schema_history".
$flyway = Invoke-OracleSql $NameSql "ispf" $Password "SELECT COUNT(*) FROM `"flyway_schema_history`" WHERE `"version`" = '1' AND `"success`" = 1;"
if ($flyway -eq "1") { Pass "g1_flyway" "flyway oracle/V1 success=1" } else { Fail "g1_flyway" "flyway V1 rows=$flyway" }

$appLog = docker logs $NameApp 2>&1 | Out-String
if ($appLog -match "Database dialect: OracleDialect") {
    Pass "g1_dialect" "Hibernate OracleDialect (auto-detected)"
} else {
    Fail "g1_dialect" "Hibernate dialect is not OracleDialect"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($Jar)
try {
    $bundledDrivers = @($zip.Entries | Where-Object { $_.FullName -match "ojdbc|oracle/jdbc/" }).Count
} finally {
    $zip.Dispose()
}
if ($bundledDrivers -eq 0) {
    Pass "g1_driver_external" "bootJar carries no ojdbc; driver loaded from loader.path=/app/lib ($(Split-Path -Leaf $OjdbcJar))"
} else {
    Fail "g1_driver_external" "bootJar contains $bundledDrivers ojdbc entries"
}
Run-Fence "g1_driver_missing" @{} "needs the Oracle JDBC thin driver" -NoDriver

Write-Host "== 2 bundle import + paths =="
if ($token) {
    $bundleJson = [System.IO.File]::ReadAllText($Bundle)
    $imp = Invoke-Http "POST" "$Base/api/v1/platform/packages/import?packageId=oracle-b1-cert" $token $bundleJson
    if ($imp.Code -eq 200) { Pass "g2_import" "cert bundle import 200" } else { Fail "g2_import" "import code=$($imp.Code) body=$($imp.Body)" }
    $pathOk = $true
    foreach ($p in @(
            "root.platform.applications.oracle-b1-cert",
            "root.platform.devices.oracle-b1-cert-sensor-01",
            "root.platform.dashboards.oracle-b1-cert-overview",
            "root.platform.data-sources.oracle-b1-cert")) {
        $r = Invoke-Http "GET" "$Base/api/v1/objects/by-path?path=$p" $token
        Write-Host "path $p -> $($r.Code)"
        if ($r.Code -ne 200) { $pathOk = $false }
    }
    if ($pathOk) { Pass "g2_paths" "all cert paths 200" } else { Fail "g2_paths" "one or more paths not 200" }
} else {
    Fail "g2_import" "no token"
    Fail "g2_paths" "no token"
}

Write-Host "== 3 second JVM on shared ispf schema =="
$lease = "ispf-oracle-b1-lease2"
$leaseEnv = @{ "ISPF_REPLICA_ID" = "lease-replica-2" }
foreach ($k in $B1Env.Keys) { $leaseEnv[$k] = $B1Env[$k] }
if (Start-OracleIspf -Name $lease -Net $Net -Jar $Jar -OjdbcJar $OjdbcJar -Password $Password -Jdbc $Jdbc `
        -EnvExtra $leaseEnv -Publish "127.0.0.1:18093:8080") {
    $up = $false
    for ($i = 1; $i -le 60; $i++) {
        $r = Invoke-Http "GET" "http://127.0.0.1:18093/actuator/health"
        if ($r.Code -eq 200 -and $r.Body -match '"status":"UP"') { $up = $true; break }
        Start-Sleep 3
    }
    if ($up) {
        Pass "g3_dual_jvm" "second non-clustered JVM health UP on :18093 against shared ispf schema; live lease reclaim not asserted (cluster off). Lease failover is CI: OracleFlywayBaselineTckTest"
    } else {
        Fail "g3_dual_jvm" "second JVM did not become healthy"
    }
} else {
    Fail "g3_dual_jvm" "docker run failed"
}
docker rm -f $lease 2>$null | Out-Null

Write-Host "== 4 audit chain + serial reimport =="
if ($token) {
    $ser = Invoke-Http "POST" "$Base/api/v1/platform/packages/import?packageId=oracle-b1-cert" $token ([System.IO.File]::ReadAllText($Bundle))
    $auditRows = Invoke-OracleSql $NameSql "ispf" $Password "SELECT COUNT(*) FROM audit_event_chain;"
    if ($ser.Code -eq 200 -and [int]$auditRows -gt 0) {
        Pass "g4_audit" "serial reimport 200; audit_event_chain rows=$auditRows. Concurrent append + verify is CI: OracleFlywayBaselineTckTest (8 writers)"
    } else {
        Fail "g4_audit" "serial=$($ser.Code) audit_rows=$auditRows"
    }
} else {
    Fail "g4_audit" "no token"
}

Write-Host "== 5-7 fence boots =="
Run-Fence "g5_rls" @{ "ISPF_TENANT_DB_ROW_ISOLATION" = "true" } "db-row-isolation=true is unsupported on Oracle"
Run-Fence "g6_hard" @{ "ISPF_TENANT_ISOLATION_MODE" = "HARD" } "isolation-mode=HARD is unsupported on Oracle"
Run-Fence "g7_hist" @{ "ISPF_VARIABLE_HISTORY_ENABLED" = "true"; "ISPF_VARIABLE_HISTORY_STORE" = "jdbc" } "variable-history store falls back to ISPF_DB JDBC on Oracle"
Run-Fence "g7_dual" @{ "ISPF_VARIABLE_HISTORY_ENABLED" = "true"; "ISPF_VARIABLE_HISTORY_STORE" = "clickhouse"; "ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED" = "true" } "dual-write-enabled=true is refused on Oracle"
Run-Fence "g7_journal" @{ "ISPF_EVENT_JOURNAL_ENABLED" = "true"; "ISPF_EVENT_JOURNAL_STORE" = "jdbc" } "event-journal store falls back to ISPF_DB JDBC on Oracle"
Run-Fence "g7_cluster" @{ "ISPF_CLUSTER_ENABLED" = "true" } "ispf.cluster.enabled=true is not certified on Oracle"

Write-Host "== 8 hot-reload JDBC journal guard =="
if ($token) {
    $patch = Invoke-Http "PATCH" "$Base/api/v1/platform/runtime-settings" $token '{"values":{"event-journal.enabled":"true"}}'
    if ($patch.Code -eq 200 -and $patch.Body -match "ADR-0080 B1") {
        Pass "g8_hot_reload" "PATCH event-journal.enabled=true refused on the JDBC store (ADR-0080 B1)"
    } else {
        Fail "g8_hot_reload" "patch code=$($patch.Code) body=$($patch.Body)"
    }
} else {
    Fail "g8_hot_reload" "no token"
}

$fails = @($items | Where-Object { $_.status -eq "FAIL" }).Count
$doc = [ordered]@{
    generated_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    base = $Base
    engine_note = $EngineNote
    edition = "$bannerLine | $cell"
    jdbc_client = "Oracle JDBC thin $(Split-Path -Leaf $OjdbcJar) (lab classpath via loader.path; not shipped by ISPF)"
    fail_count = $fails
    pass_count = @($items | Where-Object { $_.status -eq "PASS" }).Count
    items = $items
}
$json = $doc | ConvertTo-Json -Depth 5
$python = Get-Command python -ErrorAction SilentlyContinue
if ($python) {
    $tmp = [System.IO.Path]::GetTempFileName()
    [System.IO.File]::WriteAllText($tmp, $json, (New-Object System.Text.UTF8Encoding($false)))
    $pretty = & python -c "import json,sys; print(json.dumps(json.load(open(sys.argv[1], encoding='utf-8')), indent=2, ensure_ascii=False))" $tmp
    if ($LASTEXITCODE -eq 0) { $json = ($pretty -join "`n") }
    Remove-Item $tmp -ErrorAction SilentlyContinue
}
[System.IO.File]::WriteAllText($Out, $json + "`n", (New-Object System.Text.UTF8Encoding($false)))
Write-Host "wrote $Out (pass=$($doc.pass_count) fail=$fails)"
if ($fails -gt 0) { exit 1 }
exit 0
