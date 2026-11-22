# ADR-0079 B1 lab-green evidence collector against the stand from stand-local.ps1 (default :18090).
# Records results to -Out. Lab evidence only: it does not sign a named-SKU Prod Yes cell.
# Writes only what it measured; owner decisions go in a separate JSON, never into -Out.
param(
    [string]$Base = "http://127.0.0.1:18090",
    [string]$Jar = "",
    [string]$Password = $env:MYSQL_LAB_PASSWORD,
    [string]$Net = "ispf-mysql-b1-net",
    [string]$NameSql = "ispf-mysql-b1-stand",
    [string]$NameApp = "ispf-mysql-b1-app",
    [string]$Bundle = "",
    [string]$Out = "",
    [string]$EngineNote = "MySQL lab cell (set -EngineNote)",
    [string]$ImageMariaDb = "mariadb:11.4",
    [switch]$SkipMariaDb
)

$ErrorActionPreference = "Continue"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
if (-not $Password) { throw "Set MYSQL_LAB_PASSWORD or pass -Password" }
if (-not $Bundle) { $Bundle = Join-Path $ScriptDir "mysql-b1-cert-bundle.json" }
if (-not $Out) { $Out = Join-Path $env:TEMP "mysql-b1-gate-evidence.json" }
if (-not $Jar) {
    $Jar = (Get-ChildItem -Path (Join-Path $RepoRoot "packages\ispf-server\build\libs") -Filter "ispf-server-*.jar" |
        Where-Object { $_.Name -notlike "*-plain.jar" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}
if (-not $Jar -or -not (Test-Path $Jar)) { throw "bootJar not found (pass -Jar)" }

$ImageJre = "eclipse-temurin:25-jre-jammy"
$Jdbc = "jdbc:mysql://${NameSql}:3306/ispf?permitMysqlScheme=true&allowPublicKeyRetrieval=true"
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

function Get-ContainerEnv([string]$name, [string]$key) {
    $lines = docker inspect --format "{{range .Config.Env}}{{println .}}{{end}}" $name 2>$null
    foreach ($line in $lines) {
        if ($line.StartsWith("$key=")) { return $line.Substring($key.Length + 1) }
    }
    return $null
}

function Invoke-Sql([string]$query) {
    # Quote --host: PowerShell splits an unquoted -h127.0.0.1 at the first dot.
    $out = docker exec $NameSql mysql "--host=127.0.0.1" -uroot "-p$Password" -N -B -e $query 2>&1 |
        Where-Object { $_ -notmatch "Using a password on the command line" }
    return ($out | Out-String).Trim()
}

function Start-Ispf([string]$name, [hashtable]$envExtra, [string]$jdbcUrl = $Jdbc, [string]$publish = "") {
    docker rm -f $name 2>$null | Out-Null
    $envBase = [ordered]@{
        "SPRING_PROFILES_ACTIVE" = "local"
        "SPRING_JPA_HIBERNATE_DDL_AUTO" = "validate"
        "ISPF_METADATA_DB_KIND" = "mysql"
        "SPRING_DATASOURCE_URL" = $jdbcUrl
        "SPRING_DATASOURCE_USERNAME" = "ispf"
        "SPRING_DATASOURCE_PASSWORD" = $Password
        "SPRING_DATASOURCE_DRIVER_CLASS_NAME" = "org.mariadb.jdbc.Driver"
        "ISPF_NATS_ENABLED" = "false"
        "ISPF_REDIS_ENABLED" = "false"
        "ISPF_SECURITY_STRICT_STARTUP" = "false"
        "ISPF_BOOTSTRAP_FIXTURES_ENABLED" = "false"
        "ISPF_LICENSE_ENFORCE" = "false"
    }
    foreach ($k in $envExtra.Keys) { $envBase[$k] = $envExtra[$k] }
    $dockerArgs = @("run", "-d", "--name", $name, "--network", $Net, "-v", "${Jar}:/app/ispf-server.jar:ro")
    if ($publish) { $dockerArgs += @("-p", $publish) }
    foreach ($k in $envBase.Keys) { $dockerArgs += @("-e", "$k=$($envBase[$k])") }
    $dockerArgs += @($ImageJre, "java", "-jar", "/app/ispf-server.jar")
    docker @dockerArgs | Out-Null
    return $LASTEXITCODE -eq 0
}

function Wait-Log([string]$name, [string]$expect, [int]$seconds = 150) {
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

$B1Env = @{
    "ISPF_TENANT_DB_ROW_ISOLATION" = "false"
    "ISPF_TENANT_ISOLATION_MODE" = "logical"
    "ISPF_VARIABLE_HISTORY_ENABLED" = "false"
    "ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED" = "false"
    "ISPF_EVENT_JOURNAL_ENABLED" = "false"
    "ISPF_CLUSTER_ENABLED" = "false"
}

function Run-Fence([string]$id, [hashtable]$overrides, [string]$expect) {
    $envExtra = @{}
    foreach ($k in $B1Env.Keys) { $envExtra[$k] = $B1Env[$k] }
    foreach ($k in $overrides.Keys) { $envExtra[$k] = $overrides[$k] }
    $name = "ispf-mysql-b1-fence-$id"
    if (-not (Start-Ispf $name $envExtra)) { Fail $id "docker run failed"; return }
    $r = Wait-Log $name $expect
    docker rm -f $name 2>$null | Out-Null
    if ($r.Found) { Pass $id "refused with: $expect" } else { Fail $id "did not see expect=$expect" }
}

Write-Host "== 0 engine identity =="
$engine = Invoke-Sql "SELECT CONCAT(VERSION(), ' | ', @@version_comment, ' | ', @@transaction_isolation, ' | ', (SELECT default_collation_name FROM information_schema.schemata WHERE schema_name='ispf'), ' | ', @@lower_case_table_names)"
Write-Host "engine=$engine"
$parts = $engine -split " \| "
$lowerCaseTableNames = $null
if ($parts.Count -eq 5 -and $parts[4] -match '^[012]$') { $lowerCaseTableNames = [int]$parts[4] }
if ($parts.Count -eq 5 -and $parts[0].StartsWith("8.4.") -and $parts[1] -like "MySQL Community Server*" `
        -and $parts[2] -eq "READ-COMMITTED" -and $parts[3] -eq "utf8mb4_0900_bin" -and $null -ne $lowerCaseTableNames) {
    Pass "g0_engine" "MySQL $($parts[0]) ($($parts[1])), READ-COMMITTED, ispf utf8mb4_0900_bin, lower_case_table_names=$lowerCaseTableNames"
} else {
    Fail "g0_engine" "unexpected engine cell: $engine"
}

Write-Host "== 1 health / login / flyway / dialect =="
$h = Invoke-Http "GET" "$Base/actuator/health"
$healthUp = $h.Code -eq 200 -and $h.Body -match '"status":"UP"'
if ($healthUp) { Pass "g1_health" "actuator health UP" } else { Fail "g1_health" "health code=$($h.Code)" }

$login = Invoke-Http "POST" "$Base/api/v1/auth/login" "" '{"username":"admin","password":"admin"}'
$token = ""
if ($login.Code -eq 200) { try { $token = ($login.Body | ConvertFrom-Json).token } catch {} }
if ($token) { Pass "g1_login" "admin login ok" } else { Fail "g1_login" "login code=$($login.Code)" }

$flyway = Invoke-Sql "SELECT COUNT(*) FROM ispf.flyway_schema_history WHERE version='1' AND success=1"
if ($flyway -eq "1") { Pass "g1_flyway" "flyway mysql/V1 success=1" } else { Fail "g1_flyway" "flyway V1 rows=$flyway" }

$appLog = docker logs $NameApp 2>&1 | Out-String
if ($appLog.Contains("Database dialect: MysqlHibernateDialect") -and -not $appLog.Contains("MariaDBDialect")) {
    Pass "g1_dialect" "Hibernate MysqlHibernateDialect (pinned MySQLDialect subclass; MariaDB Connector/J would auto-select MariaDBDialect)"
} else {
    Fail "g1_dialect" "Hibernate dialect is not MysqlHibernateDialect"
}

# The stand's local profile sets ddl-auto=none; the container env must override it with validate.
$appDdlAuto = Get-ContainerEnv $NameApp "SPRING_JPA_HIBERNATE_DDL_AUTO"
if ($appDdlAuto -eq "validate" -and $healthUp) {
    Pass "g1_validate" "primary JVM $NameApp has SPRING_JPA_HIBERNATE_DDL_AUTO=validate (docker inspect) and is UP, so Hibernate schema validation passed at boot"
} else {
    Fail "g1_validate" "primary JVM $NameApp SPRING_JPA_HIBERNATE_DDL_AUTO=$appDdlAuto, health UP=$healthUp"
}

Write-Host "== 2 bundle import + paths =="
if ($token) {
    $bundleJson = [System.IO.File]::ReadAllText($Bundle)
    $imp = Invoke-Http "POST" "$Base/api/v1/platform/packages/import?packageId=mysql-b1-cert" $token $bundleJson
    if ($imp.Code -eq 200) { Pass "g2_import" "cert bundle import 200" } else { Fail "g2_import" "import code=$($imp.Code) body=$($imp.Body)" }
    $pathOk = $true
    foreach ($p in @(
            "root.platform.applications.mysql-b1-cert",
            "root.platform.devices.mysql-b1-cert-sensor-01",
            "root.platform.dashboards.mysql-b1-cert-overview",
            "root.platform.data-sources.mysql-b1-cert")) {
        $r = Invoke-Http "GET" "$Base/api/v1/objects/by-path?path=$p" $token
        Write-Host "path $p -> $($r.Code)"
        if ($r.Code -ne 200) { $pathOk = $false }
    }
    if ($pathOk) { Pass "g2_paths" "all cert paths 200" } else { Fail "g2_paths" "one or more paths not 200" }
} else {
    Fail "g2_import" "no token"
    Fail "g2_paths" "no token"
}

Write-Host "== 3 second JVM on shared ispf =="
$lease = "ispf-mysql-b1-lease2"
$leaseEnv = @{ "ISPF_REPLICA_ID" = "lease-replica-2" }
foreach ($k in $B1Env.Keys) { $leaseEnv[$k] = $B1Env[$k] }
if (Start-Ispf $lease $leaseEnv $Jdbc "127.0.0.1:18091:8080") {
    $up = $false
    for ($i = 1; $i -le 60; $i++) {
        $r = Invoke-Http "GET" "http://127.0.0.1:18091/actuator/health"
        if ($r.Code -eq 200 -and $r.Body -match '"status":"UP"') { $up = $true; break }
        Start-Sleep 3
    }
    $leaseDdlAuto = Get-ContainerEnv $lease "SPRING_JPA_HIBERNATE_DDL_AUTO"
    if ($up -and $leaseDdlAuto -eq "validate") {
        Pass "g3_dual_jvm" "second non-clustered JVM (SPRING_JPA_HIBERNATE_DDL_AUTO=validate, docker inspect) health UP on :18091 against shared ispf; live lease reclaim not asserted (cluster off). Lease failover is CI: MysqlFlywayBaselineTckTest"
    } elseif ($up) {
        Fail "g3_dual_jvm" "second JVM UP but SPRING_JPA_HIBERNATE_DDL_AUTO=$leaseDdlAuto"
    } else {
        Fail "g3_dual_jvm" "second JVM did not become healthy"
    }
} else {
    Fail "g3_dual_jvm" "docker run failed"
}
docker rm -f $lease 2>$null | Out-Null

Write-Host "== 4 audit chain + serial reimport =="
if ($token) {
    $ser = Invoke-Http "POST" "$Base/api/v1/platform/packages/import?packageId=mysql-b1-cert" $token ([System.IO.File]::ReadAllText($Bundle))
    $auditRows = Invoke-Sql "SELECT COUNT(*) FROM ispf.audit_event_chain"
    if ($ser.Code -eq 200 -and [int]$auditRows -gt 0) {
        Pass "g4_audit" "serial reimport 200; audit_event_chain rows=$auditRows. Concurrent append + verify is CI: MysqlFlywayBaselineTckTest (8 writers)"
    } else {
        Fail "g4_audit" "serial=$($ser.Code) audit_rows=$auditRows"
    }
} else {
    Fail "g4_audit" "no token"
}

Write-Host "== 9 platform metrics + job queue =="
if ($token) {
    $metrics = Invoke-Http "GET" "$Base/api/v1/platform/metrics" $token
    if ($metrics.Code -eq 200 -and $metrics.Body.Contains('"runningJobs"')) {
        Pass "g9_metrics" "GET /api/v1/platform/metrics 200 with runningJobs (running-job list ordered NULLs last without NULLS LAST)"
    } else {
        Fail "g9_metrics" "metrics code=$($metrics.Code)"
    }

    # Tree-variables reports read the object tree, so the run needs no app schema (unsupported on MySQL).
    $jobReport = "root.platform.reports.mysql-b1-cert-jobs"
    $create = Invoke-Http "POST" "$Base/api/v1/objects" $token '{"parentPath":"root.platform.reports","name":"mysql-b1-cert-jobs","type":"REPORT","displayName":"MySQL B1 cert job queue","templateId":"report-v1"}'
    $def = Invoke-Http "PUT" "$Base/api/v1/reports/by-path/tree-variables-definition?path=$jobReport" $token '{"title":"MySQL B1 cert job queue","devicePathPattern":"root.platform.devices.mysql-b1-cert-*","variableName":"value"}'
    $run = Invoke-Http "POST" "$Base/api/v1/reports/by-path/run-async?path=$jobReport" $token '{}'
    $jobId = ""
    if ($run.Code -eq 202) { try { $jobId = ($run.Body | ConvertFrom-Json).jobId } catch {} }
    $jobStatus = ""
    if ($jobId) {
        for ($i = 1; $i -le 60; $i++) {
            $job = Invoke-Http "GET" "$Base/api/v1/platform/jobs/$jobId" $token
            if ($job.Code -eq 200) { try { $jobStatus = ($job.Body | ConvertFrom-Json).status } catch {} }
            if ($jobStatus -eq "COMPLETED" -or $jobStatus -eq "FAILED") { break }
            Start-Sleep 1
        }
    }
    if ($jobStatus -eq "COMPLETED") {
        Pass "g9_job_queue" "report run-async job $jobId QUEUED -> COMPLETED through the platform job worker (claim reads job_id from CHAR(36)); GET /api/v1/platform/jobs/{id} 200"
    } else {
        Fail "g9_job_queue" "create=$($create.Code) definition=$($def.Code) run=$($run.Code) job=$jobId status=$jobStatus"
    }
} else {
    Fail "g9_metrics" "no token"
    Fail "g9_job_queue" "no token"
}

Write-Host "== 5-7 fence boots =="
Run-Fence "g5_rls" @{ "ISPF_TENANT_DB_ROW_ISOLATION" = "true" } "db-row-isolation=true is unsupported on MySQL"
Run-Fence "g6_hard" @{ "ISPF_TENANT_ISOLATION_MODE" = "HARD" } "isolation-mode=HARD is unsupported on MySQL"
Run-Fence "g7_hist" @{ "ISPF_VARIABLE_HISTORY_ENABLED" = "true"; "ISPF_VARIABLE_HISTORY_STORE" = "jdbc" } "variable-history store falls back to ISPF_DB JDBC on MySQL"
Run-Fence "g7_dual" @{ "ISPF_VARIABLE_HISTORY_ENABLED" = "true"; "ISPF_VARIABLE_HISTORY_STORE" = "clickhouse"; "ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED" = "true" } "dual-write-enabled=true is refused on MySQL"
Run-Fence "g7_journal" @{ "ISPF_EVENT_JOURNAL_ENABLED" = "true"; "ISPF_EVENT_JOURNAL_STORE" = "jdbc" } "event-journal store falls back to ISPF_DB JDBC on MySQL"
Run-Fence "g7_cluster" @{ "ISPF_CLUSTER_ENABLED" = "true" } "ispf.cluster.enabled=true is not certified on MySQL"

if (-not $SkipMariaDb) {
    Write-Host "== 8 MariaDB server refused (client != server) =="
    $maria = "ispf-mysql-b1-mariadb"
    docker rm -f $maria 2>$null | Out-Null
    docker run -d --name $maria --hostname $maria --network $Net `
        -e "MARIADB_ROOT_PASSWORD=$Password" -e "MARIADB_DATABASE=ispf" `
        -e "MARIADB_USER=ispf" -e "MARIADB_PASSWORD=$Password" $ImageMariaDb | Out-Null
    $mready = $false
    for ($i = 1; $i -le 60; $i++) {
        docker exec $maria mariadb "--host=127.0.0.1" -uroot "-p$Password" -e "SELECT 1" 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $mready = $true; break }
        Start-Sleep 2
    }
    if ($mready) {
        $mariaVersion = (docker exec $maria mariadb "--host=127.0.0.1" -uroot "-p$Password" -N -B -e "SELECT VERSION()" 2>$null | Out-String).Trim()
        $mariaJdbc = "jdbc:mysql://${maria}:3306/ispf?permitMysqlScheme=true"
        $expect = "MariaDB is not a certified ISPF_DB engine"
        foreach ($case in @(@{ id = "g8_mariadb_kind"; kind = "mysql" }, @{ id = "g8_mariadb_detect"; kind = "" })) {
            $envExtra = @{}
            foreach ($k in $B1Env.Keys) { $envExtra[$k] = $B1Env[$k] }
            $envExtra["ISPF_METADATA_DB_KIND"] = $case.kind
            $name = "ispf-mysql-b1-fence-$($case.id)"
            if (Start-Ispf $name $envExtra $mariaJdbc) {
                $r = Wait-Log $name $expect
                docker rm -f $name 2>$null | Out-Null
                $label = if ($case.kind) { "ISPF_METADATA_DB_KIND=mysql" } else { "auto-detect" }
                if ($r.Found) { Pass $case.id "MariaDB $mariaVersion refused ($label): $expect" } else { Fail $case.id "MariaDB $mariaVersion not refused ($label)" }
            } else {
                Fail $case.id "docker run failed"
            }
        }
    } else {
        Fail "g8_mariadb_kind" "MariaDB container not ready"
        Fail "g8_mariadb_detect" "MariaDB container not ready"
    }
    docker rm -f $maria 2>$null | Out-Null
}

$fails = @($items | Where-Object { $_.status -eq "FAIL" }).Count
$doc = [ordered]@{
    generated_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    base = $Base
    engine_note = $EngineNote
    edition = $engine
    lower_case_table_names = $lowerCaseTableNames
    primary_jvm_ddl_auto = $appDdlAuto
    jdbc_client = "MariaDB Connector/J (LGPL-2.1) org.mariadb.jdbc.Driver, jdbc:mysql: + permitMysqlScheme"
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
