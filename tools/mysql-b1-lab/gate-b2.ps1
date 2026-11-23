# ADR-0079 B2 (single-engine) evidence collector against the stand from stand-b2-local.ps1 (default :18094).
# Records results to -Out. Writes only what it measured; owner decisions go in a separate JSON, never into -Out.
param(
    [string]$Base = "http://127.0.0.1:18094",
    [string]$Jar = "",
    [string]$Password = $env:MYSQL_LAB_PASSWORD,
    [string]$Net = "ispf-mysql-b1-net",
    [string]$NameSql = "ispf-mysql-b1-stand",
    [string]$NameApp = "ispf-mysql-b2-app",
    [string]$DbName = "ispf_b2",
    [string]$Bundle = "",
    [string]$Out = "",
    [string]$EngineNote = "MySQL lab cell (set -EngineNote)",
    [int]$PurgeRows = 20000
)

$ErrorActionPreference = "Continue"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
if (-not $Password) { throw "Set MYSQL_LAB_PASSWORD or pass -Password" }
if (-not $Bundle) { $Bundle = Join-Path $ScriptDir "mysql-b1-cert-bundle.json" }
if (-not $Out) { $Out = Join-Path $env:TEMP "mysql-b2-gate-evidence.json" }
if (-not $Jar) {
    $Jar = (Get-ChildItem -Path (Join-Path $RepoRoot "packages\ispf-server\build\libs") -Filter "ispf-server-*.jar" |
        Where-Object { $_.Name -notlike "*-plain.jar" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}
if (-not $Jar -or -not (Test-Path $Jar)) { throw "bootJar not found (pass -Jar)" }

$ImageJre = "eclipse-temurin:25-jre-jammy"
$Device = "root.platform.devices.mysql-b1-cert-sensor-01"
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
        $params = @{ Method = $Method; Uri = $Url; Headers = $headers; UseBasicParsing = $true; TimeoutSec = 300 }
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
        $msg = "$($_.Exception.Message)"
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $msg = $_.ErrorDetails.Message }
        return @{ Code = $code; Body = $msg }
    }
}

function Get-ContainerEnv([string]$name, [string]$key) {
    $lines = docker inspect --format "{{range .Config.Env}}{{println .}}{{end}}" $name 2>$null
    foreach ($line in $lines) {
        if ($line.StartsWith("$key=")) { return $line.Substring($key.Length + 1) }
    }
    return $null
}

function Invoke-Sql([string]$query, [string]$user = "root", [string]$database = $DbName) {
    # Quote --host: PowerShell splits an unquoted -h127.0.0.1 at the first dot.
    $out = docker exec $NameSql mysql "--host=127.0.0.1" "-u$user" "-p$Password" "-D$database" -N -B -e $query 2>&1 |
        Where-Object { $_ -notmatch "Using a password on the command line" }
    return ($out | Out-String).Trim()
}

function Url([string]$value) { return [uri]::EscapeDataString($value) }
function Iso([DateTime]$value) { return $value.ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ") }

$B2Env = [ordered]@{
    "SPRING_PROFILES_ACTIVE" = "local"
    "SPRING_JPA_HIBERNATE_DDL_AUTO" = "validate"
    "ISPF_METADATA_DB_KIND" = "mysql"
    "SPRING_DATASOURCE_USERNAME" = "ispf"
    "SPRING_DATASOURCE_PASSWORD" = $Password
    "SPRING_DATASOURCE_DRIVER_CLASS_NAME" = "org.mariadb.jdbc.Driver"
    "ISPF_MYSQL_SINGLE_ENGINE" = "true"
    "ISPF_HISTORIAN_DEPLOY_PROFILE" = "mysql-single-engine"
    "ISPF_HISTORIAN_TIER_WARM_ENABLED" = "false"
    "ISPF_HISTORIAN_COLD_ARCHIVE_ENABLED" = "false"
    "ISPF_TENANT_DB_ROW_ISOLATION" = "false"
    "ISPF_TENANT_ISOLATION_MODE" = "logical"
    "ISPF_VARIABLE_HISTORY_ENABLED" = "true"
    "ISPF_VARIABLE_HISTORY_STORE" = "jdbc"
    "ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED" = "false"
    "ISPF_VARIABLE_HISTORY_SPOOL_ENABLED" = "false"
    "ISPF_EVENT_JOURNAL_ENABLED" = "true"
    "ISPF_EVENT_JOURNAL_STORE" = "jdbc"
    "ISPF_CLUSTER_ENABLED" = "false"
    "ISPF_NATS_ENABLED" = "false"
    "ISPF_REDIS_ENABLED" = "false"
    "ISPF_SECURITY_STRICT_STARTUP" = "false"
    "ISPF_BOOTSTRAP_FIXTURES_ENABLED" = "false"
    "ISPF_LICENSE_ENFORCE" = "false"
}

function Start-Ispf([string]$name, [hashtable]$overrides, [string]$database) {
    docker rm -f $name 2>$null | Out-Null
    $envAll = [ordered]@{}
    foreach ($k in $B2Env.Keys) { $envAll[$k] = $B2Env[$k] }
    $envAll["SPRING_DATASOURCE_URL"] = "jdbc:mysql://${NameSql}:3306/${database}?permitMysqlScheme=true&allowPublicKeyRetrieval=true"
    foreach ($k in $overrides.Keys) { $envAll[$k] = $overrides[$k] }
    $dockerArgs = @("run", "-d", "--name", $name, "--network", $Net, "-v", "${Jar}:/app/ispf-server.jar:ro")
    foreach ($k in $envAll.Keys) { $dockerArgs += @("-e", "$k=$($envAll[$k])") }
    $dockerArgs += @($ImageJre, "java", "-jar", "/app/ispf-server.jar")
    docker @dockerArgs | Out-Null
    return $LASTEXITCODE -eq 0
}

function Wait-Log([string]$name, [string]$expect, [int]$seconds = 240) {
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

function Reset-Database([string]$database, [string]$collation) {
    Invoke-Sql "DROP DATABASE IF EXISTS $database; CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE $collation; GRANT ALL PRIVILEGES ON $database.* TO 'ispf'@'%'" "root" "mysql" | Out-Null
}

function Run-Fence([string]$id, [hashtable]$overrides, [string]$expect, [string]$database = "ispf_b2_fence") {
    $name = "ispf-mysql-b2-fence-$id"
    if (-not (Start-Ispf $name $overrides $database)) { Fail $id "docker run failed"; return }
    $r = Wait-Log $name $expect
    docker rm -f $name 2>$null | Out-Null
    if ($r.Found) { Pass $id "refused with: $expect" } else { Fail $id "did not see expect=$expect" }
}

function Get-Count([string]$query) {
    $raw = Invoke-Sql $query
    $line = ($raw -split "`r?`n" | Where-Object { $_ -match '^\d+$' } | Select-Object -First 1)
    if ($line) { return [int64]$line }
    return -1
}

Write-Host "== 0 engine identity =="
$engine = Invoke-Sql "SELECT CONCAT(VERSION(), ' | ', @@version_comment, ' | ', @@GLOBAL.transaction_isolation, ' | ', (SELECT default_collation_name FROM information_schema.schemata WHERE schema_name='$DbName'))"
Write-Host "engine=$engine"
$parts = $engine -split " \| "
if ($parts.Count -eq 4 -and $parts[0].StartsWith("8.4.") -and $parts[1] -like "MySQL Community Server*" `
        -and $parts[2] -eq "READ-COMMITTED" -and $parts[3] -eq "utf8mb4_0900_bin") {
    Pass "b2_engine" "MySQL $($parts[0]) ($($parts[1])), READ-COMMITTED, $DbName utf8mb4_0900_bin"
} else {
    Fail "b2_engine" "unexpected engine cell: $engine"
}

Write-Host "== 1 health / login / config / flyway / dialect =="
$h = Invoke-Http "GET" "$Base/actuator/health"
$healthUp = $h.Code -eq 200 -and $h.Body -match '"status":"UP"'
if ($healthUp) { Pass "b2_health" "actuator health UP" } else { Fail "b2_health" "health code=$($h.Code)" }

$login = Invoke-Http "POST" "$Base/api/v1/auth/login" "" '{"username":"admin","password":"admin"}'
$token = ""
if ($login.Code -eq 200) { try { $token = ($login.Body | ConvertFrom-Json).token } catch {} }
if ($token) { Pass "b2_login" "admin login ok" } else { Fail "b2_login" "login code=$($login.Code)" }

$expectEnv = [ordered]@{
    "ISPF_MYSQL_SINGLE_ENGINE" = "true"
    "ISPF_VARIABLE_HISTORY_ENABLED" = "true"
    "ISPF_VARIABLE_HISTORY_STORE" = "jdbc"
    "ISPF_EVENT_JOURNAL_ENABLED" = "true"
    "ISPF_EVENT_JOURNAL_STORE" = "jdbc"
    "ISPF_CLUSTER_ENABLED" = "false"
    "ISPF_HISTORIAN_TIER_WARM_ENABLED" = "false"
    "SPRING_JPA_HIBERNATE_DDL_AUTO" = "validate"
}
$envBad = @()
foreach ($k in $expectEnv.Keys) {
    $v = Get-ContainerEnv $NameApp $k
    if ($v -ne $expectEnv[$k]) { $envBad += "$k=$v" }
}
$retentionEnv = Get-ContainerEnv $NameApp "ISPF_MYSQL_MAX_RETENTION_DAYS"
if ($envBad.Count -eq 0 -and $healthUp) {
    Pass "b2_config" "stand JVM (docker inspect): single-engine on, history + journal on JDBC, cluster off, warm off, ddl-auto=validate; ISPF_MYSQL_MAX_RETENTION_DAYS unset (default 90): $([string]::IsNullOrEmpty($retentionEnv))"
} else {
    Fail "b2_config" "unexpected stand env: $($envBad -join ', ') healthUp=$healthUp"
}

$flyway = Invoke-Sql "SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND success=1"
if ($flyway -eq "1") { Pass "b2_flyway" "flyway mysql/V1 success=1 on $DbName" } else { Fail "b2_flyway" "flyway V1 rows=$flyway" }

$appLog = docker logs $NameApp 2>&1 | Out-String
if ($appLog.Contains("Database dialect: MysqlHibernateDialect") -and -not $appLog.Contains("MariaDBDialect")) {
    Pass "b2_dialect" "Hibernate MysqlHibernateDialect with ddl-auto=validate"
} else {
    Fail "b2_dialect" "Hibernate dialect is not MysqlHibernateDialect"
}

# The ispf user's session defaults are what the pool's connections run; the stand only reaches UP past the preflight.
$session = Invoke-Sql "SELECT CONCAT(@@SESSION.transaction_isolation, ' | ', (SELECT default_collation_name FROM information_schema.schemata WHERE schema_name = DATABASE()))" "ispf"
if ($session -eq "READ-COMMITTED | utf8mb4_0900_bin" -and $healthUp) {
    Pass "b2_preflight" "ispf session: $session; single-engine preflight passed (stand UP)"
} else {
    Fail "b2_preflight" "ispf session: $session healthUp=$healthUp"
}

Write-Host "== 2 bundle import + historized variables =="
if ($token) {
    $imp = Invoke-Http "POST" "$Base/api/v1/platform/packages/import?packageId=mysql-b1-cert" $token ([System.IO.File]::ReadAllText($Bundle))
    $dev = Invoke-Http "GET" "$Base/api/v1/objects/by-path?path=$(Url $Device)" $token
    if ($imp.Code -eq 200 -and $dev.Code -eq 200) { Pass "b2_bundle_import" "cert bundle import 200; $Device 200" } else { Fail "b2_bundle_import" "import=$($imp.Code) device=$($dev.Code) body=$($imp.Body)" }

    $varsOk = $true
    foreach ($v in @(@{ name = "temp"; days = 7 }, @{ name = "live"; days = 7 }, @{ name = "retained"; days = 365 })) {
        $schema = '{"name":"' + $v.name + '","fields":[{"name":"value","type":"DOUBLE"}]}'
        $body = '{"name":"' + $v.name + '","schema":' + $schema + ',"readable":true,"writable":true,"historyEnabled":true,"historyRetentionDays":' + $v.days + '}'
        $r = Invoke-Http "POST" "$Base/api/v1/objects/by-path/variables?path=$(Url $Device)" $token $body
        if ($r.Code -ne 200 -and $r.Code -ne 201) {
            $detail = Invoke-Http "GET" "$Base/api/v1/objects/by-path/variables/detail?path=$(Url $Device)&name=$($v.name)" $token
            if ($detail.Code -ne 200) { $varsOk = $false; Write-Host "variable $($v.name): $($r.Code) $($r.Body)" }
        }
    }
    if ($varsOk) { Pass "b2_variables" "historized DOUBLE variables temp (7d), live (7d), retained (365d)" } else { Fail "b2_variables" "variable create failed" }
} else {
    Fail "b2_bundle_import" "no token"
    Fail "b2_variables" "no token"
}

Write-Host "== 3 live historian write (platform -> JdbcVariableHistoryWriteStore) + raw read =="
if ($token) {
    $schema = '{"name":"live","fields":[{"name":"value","type":"DOUBLE"}]}'
    $written = @(11.5, 12.5, 13.5)
    $putCodes = @()
    foreach ($value in $written) {
        $r = Invoke-Http "PUT" "$Base/api/v1/objects/by-path/variables?path=$(Url $Device)&name=live" $token ('{"schema":' + $schema + ',"rows":[{"value":' + $value + '}]}')
        $putCodes += $r.Code
        # Default ispf.variable-history.min-interval-ms is 5000 per series.
        Start-Sleep -Milliseconds 5500
    }
    $rows = -1
    for ($i = 1; $i -le 20; $i++) {
        $rows = Get-Count "SELECT COUNT(*) FROM variable_samples WHERE object_path='$Device' AND variable_name='live' AND value_double IN (11.5, 12.5, 13.5)"
        if ($rows -ge 3) { break }
        Start-Sleep 1
    }
    $from = Iso ([DateTime]::UtcNow.AddHours(-1))
    $to = Iso ([DateTime]::UtcNow.AddMinutes(5))
    $raw = Invoke-Http "GET" "$Base/api/v1/objects/by-path/variables/history?path=$(Url $Device)&name=live&from=$(Url $from)&to=$(Url $to)&limit=100" $token
    $values = @()
    if ($raw.Code -eq 200) { try { $values = @(($raw.Body | ConvertFrom-Json).samples | ForEach-Object { [double]$_.value }) } catch {} }
    $allBack = ($written | Where-Object { $values -notcontains $_ }).Count -eq 0
    $shown = ($values | ForEach-Object { $_.ToString([Globalization.CultureInfo]::InvariantCulture) }) -join " / "
    if (($putCodes | Where-Object { $_ -ne 200 }).Count -eq 0 -and $rows -ge 3 -and $allBack) {
        Pass "b2_history_live" "3 PUT /variables -> $rows rows in variable_samples via the historian JDBC writer; GET /history returned values $shown"
    } else {
        Fail "b2_history_live" "put=$($putCodes -join ',') rows=$rows history=$($raw.Code) values=$shown"
    }
} else {
    Fail "b2_history_live" "no token"
}

Write-Host "== 4 seeded history: bucket aggregate + raw =="
$seedSql = @"
SET SESSION cte_max_recursion_depth = 1000;
DELETE FROM variable_samples WHERE object_path='$Device' AND variable_name='temp';
INSERT INTO variable_samples (object_path, variable_name, field_name, sampled_at, observed_at, value_double)
WITH RECURSIVE seq (n) AS (SELECT 0 UNION ALL SELECT n + 1 FROM seq WHERE n < 299)
SELECT '$Device', 'temp', 'value', ts, ts, 20.0 + (n MOD 5)
FROM (SELECT n, TIMESTAMPADD(SECOND, n * 10, UTC_TIMESTAMP(6) - INTERVAL 1 DAY) AS ts FROM seq) s;
"@
Invoke-Sql $seedSql | Out-Null
$seeded = Get-Count "SELECT COUNT(*) FROM variable_samples WHERE object_path='$Device' AND variable_name='temp'"
if ($seeded -eq 300) { Pass "b2_history_seed" "seeded 300 samples (10 s apart, observed_at = sampled_at, last 24 h)" } else { Fail "b2_history_seed" "count=$seeded" }

if ($token) {
    $from = Iso ([DateTime]::UtcNow.AddDays(-2))
    $to = Iso ([DateTime]::UtcNow.AddMinutes(1))
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $agg = Invoke-Http "GET" "$Base/api/v1/objects/by-path/variables/history/aggregate?path=$(Url $Device)&name=temp&field=value&bucket=1m&from=$(Url $from)&to=$(Url $to)&limit=200" $token
    $sw.Stop()
    $buckets = @()
    $dataSource = ""
    if ($agg.Code -eq 200) { try { $parsed = $agg.Body | ConvertFrom-Json; $buckets = @($parsed.buckets); $dataSource = $parsed.dataSource } catch {} }
    $sum = 0
    $aligned = $true
    foreach ($b in $buckets) {
        $sum += [int]$b.count
        # PowerShell 7 ConvertFrom-Json already returns DateTime for ISO strings; 5.1 returns the string.
        $ts = if ($b.ts -is [DateTime]) { $b.ts.ToUniversalTime() } else {
            [DateTime]::Parse([string]$b.ts, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::AdjustToUniversal)
        }
        if ($ts.Second -ne 0 -or $ts.Millisecond -ne 0) { $aligned = $false }
    }
    if ($buckets.Count -ge 50 -and $sum -eq 300 -and $aligned -and $dataSource -eq "raw") {
        Pass "b2_history_bucket" "GET /history/aggregate bucket=1m: $($buckets.Count) buckets, counts sum $sum, minute-aligned, dataSource=$dataSource, $($sw.ElapsedMilliseconds) ms (native MySQL bucket SQL)"
    } else {
        Fail "b2_history_bucket" "code=$($agg.Code) buckets=$($buckets.Count) sum=$sum aligned=$aligned dataSource=$dataSource"
    }

    $raw = Invoke-Http "GET" "$Base/api/v1/objects/by-path/variables/history?path=$(Url $Device)&name=temp&from=$(Url $from)&to=$(Url $to)&limit=500" $token
    $n = -1
    if ($raw.Code -eq 200) { try { $n = @(($raw.Body | ConvertFrom-Json).samples).Count } catch {} }
    if ($n -eq 300) { Pass "b2_history_raw" "GET /history returned 300 samples" } else { Fail "b2_history_raw" "code=$($raw.Code) samples=$n" }
} else {
    Fail "b2_history_bucket" "no token"
    Fail "b2_history_raw" "no token"
}

Write-Host "== 5 retention ceiling (variable 365 d, single-engine ceiling 90 d) =="
Invoke-Sql @"
DELETE FROM variable_samples WHERE object_path='$Device' AND variable_name='retained';
INSERT INTO variable_samples (object_path, variable_name, field_name, sampled_at, observed_at, value_double) VALUES
('$Device', 'retained', 'value', UTC_TIMESTAMP(6) - INTERVAL 100 DAY, UTC_TIMESTAMP(6) - INTERVAL 100 DAY, 1.0),
('$Device', 'retained', 'value', UTC_TIMESTAMP(6) - INTERVAL 10 DAY, UTC_TIMESTAMP(6) - INTERVAL 10 DAY, 2.0);
"@ | Out-Null
if ($token) {
    $detail = Invoke-Http "GET" "$Base/api/v1/objects/by-path/variables/detail?path=$(Url $Device)&name=retained" $token
    $configured = $null
    if ($detail.Code -eq 200) { try { $configured = ($detail.Body | ConvertFrom-Json).historyRetentionDays } catch {} }
    # No from: the window start is now minus the variable retention, clamped to the single-engine ceiling.
    $agg = Invoke-Http "GET" "$Base/api/v1/objects/by-path/variables/history/aggregate?path=$(Url $Device)&name=retained&field=value&bucket=1d&limit=500" $token
    $buckets = @()
    if ($agg.Code -eq 200) { try { $buckets = @(($agg.Body | ConvertFrom-Json).buckets) } catch {} }
    if ("$configured" -eq "365" -and $buckets.Count -eq 1 -and [double]$buckets[0].avg -eq 2.0) {
        Pass "b2_retention_cap" "variable historyRetentionDays=365; aggregate without from returned only the 10-day-old sample, not the 100-day-old one (window clamped to max-retention-days=90)"
    } else {
        Fail "b2_retention_cap" "configured=$configured code=$($agg.Code) buckets=$($buckets.Count) body=$($agg.Body)"
    }
} else {
    Fail "b2_retention_cap" "no token"
}

Write-Host "== 6 live journal (platform -> JdbcEventJournalStore) + keyset read =="
if ($token) {
    $patch = Invoke-Http "PATCH" "$Base/api/v1/objects/by-path?path=$(Url $Device)" $token '{"eventJournalEnabled":true}'
    $evDef = Invoke-Http "PUT" "$Base/api/v1/objects/by-path/events?path=$(Url $Device)" $token '{"name":"b2gate","description":"MySQL B2 gate","payloadSchema":{"name":"b2gate","fields":[{"name":"note","type":"STRING"}]},"level":"INFO"}'
    $fire = Invoke-Http "POST" "$Base/api/v1/events/fire?objectPath=$(Url $Device)&eventName=b2gate" $token '{"schema":{"name":"b2gate","fields":[{"name":"note","type":"STRING"}]},"rows":[{"note":"mysql-b2"}]}'
    $evRows = -1
    for ($i = 1; $i -le 20; $i++) {
        $evRows = Get-Count "SELECT COUNT(*) FROM event_history WHERE object_path='$Device' AND event_name='b2gate'"
        if ($evRows -ge 1) { break }
        Start-Sleep 1
    }
    $before = Iso ([DateTime]::UtcNow.AddHours(1))
    $list = Invoke-Http "GET" "$Base/api/v1/events?objectPath=$(Url $Device)&limit=10&before=$(Url $before)" $token
    $listed = $list.Code -eq 200 -and $list.Body.Contains('"eventName":"b2gate"')
    if ($patch.Code -eq 200 -and $evDef.Code -eq 200 -and $fire.Code -eq 200 -and $evRows -ge 1 -and $listed) {
        Pass "b2_journal_live" "POST /events/fire -> $evRows row(s) in event_history; GET /events?before= (keyset, JDBC store) lists it"
    } else {
        Fail "b2_journal_live" "patch=$($patch.Code) define=$($evDef.Code) fire=$($fire.Code) rows=$evRows list=$($list.Code)"
    }
} else {
    Fail "b2_journal_live" "no token"
}

Write-Host "== 7 journal purge, DELETE ... LIMIT batches ($PurgeRows rows) =="
$swSeed = [System.Diagnostics.Stopwatch]::StartNew()
Invoke-Sql @"
SET SESSION cte_max_recursion_depth = $($PurgeRows + 10);
DELETE FROM event_history WHERE event_name = 'b2-purge';
INSERT INTO event_history (id, object_path, event_name, level, payload_json, occurred_at)
WITH RECURSIVE seq (n) AS (SELECT 0 UNION ALL SELECT n + 1 FROM seq WHERE n < $($PurgeRows - 1))
SELECT UUID(), '$Device', 'b2-purge', 'INFO', '{}', TIMESTAMPADD(SECOND, -n, UTC_TIMESTAMP(6) - INTERVAL 120 DAY) FROM seq;
"@ | Out-Null
$swSeed.Stop()
$seededEvents = Get-Count "SELECT COUNT(*) FROM event_history WHERE event_name='b2-purge'"
if ($seededEvents -eq $PurgeRows) { Pass "b2_purge_seed" "rows=$seededEvents seed_ms=$($swSeed.ElapsedMilliseconds)" } else { Fail "b2_purge_seed" "rows=$seededEvents" }

if ($token) {
    $cutoff = [DateTime]::UtcNow.AddDays(-90).ToString("o")
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $purge = Invoke-Http "POST" "$Base/api/v1/events/journal/purge" $token ('{"olderThan":"' + $cutoff + '","objectPath":"' + $Device + '"}')
    $sw.Stop()
    $deleted = -1
    if ($purge.Code -eq 200) { try { $deleted = [int64]($purge.Body | ConvertFrom-Json).deleted } catch {} }
    $left = Get-Count "SELECT COUNT(*) FROM event_history WHERE event_name='b2-purge'"
    $kept = Get-Count "SELECT COUNT(*) FROM event_history WHERE object_path='$Device' AND event_name='b2gate'"
    if ($deleted -eq $PurgeRows -and $left -eq 0 -and $kept -ge 1) {
        Pass "b2_purge_measure" "POST /events/journal/purge: deleted=$deleted in $($sw.ElapsedMilliseconds) ms, leftover=0, recent b2gate rows kept=$kept"
    } else {
        Fail "b2_purge_measure" "code=$($purge.Code) deleted=$deleted leftover=$left kept=$kept ms=$($sw.ElapsedMilliseconds)"
    }
} else {
    Fail "b2_purge_measure" "no token"
}

Write-Host "== 8 boot refusals under single-engine (throwaway database) =="
Reset-Database "ispf_b2_fence" "utf8mb4_0900_bin"
# Since A-A (ADR-0079 § Amendment 6) cluster runs with the flag; gate-aa.ps1 covers that cell and its NATS refusals.
Run-Fence "b2_fence_cluster_b1" @{ "ISPF_MYSQL_SINGLE_ENGINE" = "false"; "ISPF_VARIABLE_HISTORY_ENABLED" = "false"; "ISPF_EVENT_JOURNAL_ENABLED" = "false"; "ISPF_CLUSTER_ENABLED" = "true" } "ispf.cluster.enabled=true on MySQL requires ISPF_MYSQL_SINGLE_ENGINE=true"
Run-Fence "b2_fence_warm" @{ "ISPF_HISTORIAN_TIER_WARM_ENABLED" = "true" } "MySQL single-engine refuses ClickHouse warm-tier routing"
Run-Fence "b2_fence_cold" @{ "ISPF_HISTORIAN_COLD_ARCHIVE_ENABLED" = "true" } "MySQL single-engine refuses cold Parquet archive"
Run-Fence "b2_fence_dual" @{ "ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED" = "true" } "dual-write-enabled=true is refused on MySQL"
Run-Fence "b2_fence_store" @{ "ISPF_VARIABLE_HISTORY_STORE" = "clickhouse" } "MySQL single-engine requires ISPF_VARIABLE_HISTORY_STORE=jdbc"
Run-Fence "b2_fence_b1" @{ "ISPF_MYSQL_SINGLE_ENGINE" = "false" } "variable-history store falls back to ISPF_DB JDBC on MySQL"

Write-Host "== 9 preflight refusals (Flyway migrates the throwaway database first) =="
Run-Fence "b2_preflight_isolation" @{ "SPRING_DATASOURCE_HIKARI_TRANSACTION_ISOLATION" = "TRANSACTION_REPEATABLE_READ" } "MySQL single-engine requires transaction_isolation=READ-COMMITTED"
Reset-Database "ispf_b2_ci" "utf8mb4_0900_ai_ci"
Run-Fence "b2_preflight_collation" @{} "MySQL single-engine requires the ISPF database on utf8mb4_0900_bin" "ispf_b2_ci"
Invoke-Sql "DROP DATABASE IF EXISTS ispf_b2_fence; DROP DATABASE IF EXISTS ispf_b2_ci" "root" "mysql" | Out-Null

$fails = @($items | Where-Object { $_.status -eq "FAIL" }).Count
$doc = [ordered]@{
    generated_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    gate = "B2 (single-engine)"
    base = $Base
    db = $DbName
    engine_note = $EngineNote
    edition = $engine
    jdbc_client = "MariaDB Connector/J (LGPL-2.1) org.mariadb.jdbc.Driver, jdbc:mysql: + permitMysqlScheme"
    purge_rows = $PurgeRows
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
