# ADR-0079 A-A (active-active) evidence collector against the stand from stand-aa-local.ps1 (:18095 / :18096).
# Records results to -Out. Writes only what it measured; owner decisions go in a separate JSON, never into -Out.
# Crashes replica A once (docker kill, not a graceful stop), restarts it, and boots refusal JVMs against a
# throwaway database and a throwaway NATS server so they cannot talk to the stand's cluster.
param(
    [string]$BaseA = "http://127.0.0.1:18095",
    [string]$BaseB = "http://127.0.0.1:18096",
    [string]$Jar = "",
    [string]$Password = $env:MYSQL_LAB_PASSWORD,
    [string]$Net = "ispf-mysql-b1-net",
    [string]$NameSql = "ispf-mysql-b1-stand",
    [string]$NameA = "ispf-mysql-aa-a",
    [string]$NameB = "ispf-mysql-aa-b",
    [string]$NameNatsFence = "ispf-mysql-aa-nats-fence",
    [string]$DbName = "ispf_aa",
    [string]$FenceDb = "ispf_aa_fence",
    [string]$Out = "",
    [string]$EngineNote = "MySQL lab cell (set -EngineNote)",
    [int]$ReclaimSloSec = 45,
    [int]$LiveLagSloMs = 5000,
    [int]$BurstPerReplica = 10
)

$ErrorActionPreference = "Continue"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
if (-not $Password) { throw "Set MYSQL_LAB_PASSWORD or pass -Password" }
if (-not $Out) { $Out = Join-Path $env:TEMP "mysql-aa-gate-evidence.json" }
if (-not $Jar) {
    $Jar = (Get-ChildItem -Path (Join-Path $RepoRoot "packages\ispf-server\build\libs") -Filter "ispf-server-*.jar" |
        Where-Object { $_.Name -notlike "*-plain.jar" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}
if (-not $Jar -or -not (Test-Path $Jar)) { throw "bootJar not found (pass -Jar)" }
Add-Type -AssemblyName System.Net.Http

$ImageJre = "eclipse-temurin:25-jre-jammy"
$Run = [DateTime]::UtcNow.ToString("HHmmss")
$Parent = "root.platform.devices"
$Device = "$Parent.mysql-aa-$Run"
$DriverDevice = "$Parent.mysql-aa-drv-$Run"
# Singleton jobs whose lease TTL is at most 30 s (PlatformLeaderLockService callers).
$ShortTtlLocks = @("platform_scheduler", "binding_periodic_rules", "alert_rule_periodic", "application_sql_bindings",
    "workflow_retry_scheduler", "workflow_due_timer_scheduler", "process_program_runner", "analytics_engine_scheduler")
$LeaseDuplicate = "for key 'platform_leader_locks.PRIMARY'"
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
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds(120)
    try {
        $msg = New-Object System.Net.Http.HttpRequestMessage((New-Object System.Net.Http.HttpMethod($Method)), $Url)
        if ($Token) { $msg.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $Token) }
        if ($Body) { $msg.Content = New-Object System.Net.Http.StringContent($Body, [System.Text.Encoding]::UTF8, "application/json") }
        $resp = $client.SendAsync($msg).Result
        # Error bodies (problem details) are kept: Invoke-WebRequest on Windows PowerShell drops them.
        return @{ Code = [int]$resp.StatusCode; Body = $resp.Content.ReadAsStringAsync().Result }
    } catch {
        return @{ Code = 0; Body = "$($_.Exception.GetBaseException().Message)" }
    } finally {
        $client.Dispose()
    }
}

# Sends every request at once on one HttpClient and returns the status codes in order.
function Invoke-Parallel([object[]]$requests) {
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds(120)
    try {
        $tasks = New-Object System.Collections.Generic.List[System.Threading.Tasks.Task[System.Net.Http.HttpResponseMessage]]
        foreach ($req in $requests) {
            $msg = New-Object System.Net.Http.HttpRequestMessage((New-Object System.Net.Http.HttpMethod($req.Method)), $req.Url)
            $msg.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $req.Token)
            if ($req.Body) {
                $msg.Content = New-Object System.Net.Http.StringContent($req.Body, [System.Text.Encoding]::UTF8, "application/json")
            }
            $tasks.Add($client.SendAsync($msg))
        }
        try { [System.Threading.Tasks.Task]::WaitAll($tasks.ToArray()) } catch {}
        $codes = @()
        foreach ($t in $tasks) {
            if ($t.Status -eq "RanToCompletion") { $codes += [int]$t.Result.StatusCode } else { $codes += 0 }
        }
        return $codes
    } finally {
        $client.Dispose()
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

function Get-Count([string]$query) {
    $raw = Invoke-Sql $query
    $line = ($raw -split "`r?`n" | Where-Object { $_ -match '^\d+$' } | Select-Object -First 1)
    if ($line) { return [int64]$line }
    return -1
}

function Url([string]$value) { return [uri]::EscapeDataString($value) }
function Iso([DateTime]$value) { return $value.ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ") }
function NowMs() { return [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() }
function Ok2xx([int]$code) { return $code -ge 200 -and $code -lt 300 }

function Login([string]$base) {
    $login = Invoke-Http "POST" "$base/api/v1/auth/login" "" '{"username":"admin","password":"admin"}'
    if ($login.Code -eq 200) { try { return ($login.Body | ConvertFrom-Json).token } catch {} }
    return ""
}

function Get-Health([string]$base) {
    $h = Invoke-Http "GET" "$base/actuator/health"
    return $h.Code -eq 200 -and $h.Body -match '"status":"UP"'
}

function Wait-Healthy([string]$base, [int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        if (Get-Health $base) { return $true }
        Start-Sleep 3
    }
    return $false
}

function Get-ClusterHealth([string]$base, [string]$token) {
    $r = Invoke-Http "GET" "$base/api/v1/platform/cluster/health" $token
    if ($r.Code -ne 200) { return $null }
    try { return $r.Body | ConvertFrom-Json } catch { return $null }
}

function New-Device([string]$base, [string]$token, [string]$path, [string]$label) {
    $name = $path.Substring($Parent.Length + 1)
    $body = '{"parentPath":"' + $Parent + '","name":"' + $name + '","type":"DEVICE","displayName":"' + $label + '"}'
    return Invoke-Http "POST" "$base/api/v1/objects" $token $body
}

function Wait-ByPath([string]$base, [string]$token, [string]$path, [int]$expectCode, [int]$seconds) {
    $start = NowMs
    $deadline = $start + $seconds * 1000
    while ((NowMs) -lt $deadline) {
        $r = Invoke-Http "GET" "$base/api/v1/objects/by-path?path=$(Url $path)" $token
        if ($r.Code -eq $expectCode) { return (NowMs) - $start }
        Start-Sleep -Milliseconds 250
    }
    return -1
}

function Get-VariableDetail([string]$base, [string]$token, [string]$path, [string]$name) {
    $r = Invoke-Http "GET" "$base/api/v1/objects/by-path/variables/detail?path=$(Url $path)&name=$name" $token
    if ($r.Code -ne 200) { return $null }
    try { return $r.Body | ConvertFrom-Json } catch { return $null }
}

function Read-StringValue([string]$base, [string]$token, [string]$path, [string]$name) {
    $detail = Get-VariableDetail $base $token $path $name
    if (-not $detail) { return "" }
    $rows = @(($detail.value).rows)
    if ($rows.Count -gt 0) { return [string]$rows[0].value }
    return ""
}

# Writes the nonce on one replica and requires 8 consecutive equal reads on the other within the SLO.
function Measure-LiveLag([string]$writeBase, [string]$writeToken, [string]$readBase, [string]$readToken, [string]$path, [string]$nonce) {
    $schema = '{"name":"lagProbe","fields":[{"name":"value","type":"STRING"}]}'
    $put = Invoke-Http "PUT" "$writeBase/api/v1/objects/by-path/variables?path=$(Url $path)&name=lagProbe" $writeToken ('{"schema":' + $schema + ',"rows":[{"value":"' + $nonce + '"}]}')
    if ($put.Code -ne 200) { return @{ Ms = -1; Detail = "put=$($put.Code) $($put.Body)" } }
    $start = NowMs
    $streak = 0
    $last = ""
    while ((NowMs) - $start -lt $LiveLagSloMs) {
        $last = Read-StringValue $readBase $readToken $path "lagProbe"
        if ($last -eq $nonce) { $streak++ } else { $streak = 0 }
        if ($streak -ge 8) { return @{ Ms = (NowMs) - $start; Detail = "8 consecutive reads" } }
        Start-Sleep -Milliseconds 100
    }
    return @{ Ms = -1; Detail = "last='$last' streak=$streak" }
}

# A live observer the way an HMI client is one: /ws/objects + subscribe. The subscription also registers
# cluster path interest in Redis, which is what makes the writing replica mirror values (ADR-0024 / 0029).
function Open-Observer([string]$base, [string]$token, [string]$path) {
    $ws = New-Object System.Net.WebSockets.ClientWebSocket
    $ws.Options.SetRequestHeader("Authorization", "Bearer $token")
    $uri = New-Object System.Uri((($base -replace '^http', 'ws')) + "/ws/objects")
    try {
        if (-not $ws.ConnectAsync($uri, [System.Threading.CancellationToken]::None).Wait(15000)) { return $null }
        $bytes = [System.Text.Encoding]::UTF8.GetBytes('{"type":"subscribe","paths":["' + $path + '"]}')
        $segment = New-Object System.ArraySegment[byte] -ArgumentList (, $bytes)
        $sent = $ws.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true,
            [System.Threading.CancellationToken]::None).Wait(15000)
        if (-not $sent) { return $null }
        return $ws
    } catch {
        Write-Host "observer on $base failed: $($_.Exception.GetBaseException().Message)"
        return $null
    }
}

function Close-Observer($ws) {
    if ($ws) {
        try { $ws.Abort() } catch {}
        $ws.Dispose()
    }
}

function Get-LeaderLocks() {
    $raw = Invoke-Sql "SELECT lock_name, holder_id, IF(expires_at > UTC_TIMESTAMP(6), 'live', 'expired') FROM platform_leader_locks ORDER BY lock_name"
    $rows = @()
    foreach ($line in ($raw -split "`r?`n")) {
        $cols = $line -split "`t"
        if ($cols.Count -eq 3) { $rows += [pscustomobject]@{ Name = $cols[0]; Holder = $cols[1]; State = $cols[2] } }
    }
    return $rows
}

function Get-DriverHolder([string]$path) {
    $raw = Invoke-Sql "SELECT holder_id FROM platform_driver_locks WHERE device_path='$path' AND expires_at > CURRENT_TIMESTAMP"
    return ($raw -split "`r?`n" | Where-Object { $_ -match '^mysql-aa-' } | Select-Object -First 1)
}

$ExpectEnv = @{
    "ISPF_MYSQL_SINGLE_ENGINE" = "true"; "ISPF_VARIABLE_HISTORY_STORE" = "jdbc"; "ISPF_EVENT_JOURNAL_ENABLED" = "true"
    "ISPF_EVENT_JOURNAL_STORE" = "jdbc"; "ISPF_CLUSTER_ENABLED" = "true"; "ISPF_NATS_ENABLED" = "true"
    "ISPF_NATS_JETSTREAM_ENABLED" = "true"; "ISPF_REDIS_ENABLED" = "true"; "ISPF_HISTORIAN_TIER_WARM_ENABLED" = "false"
    "SPRING_JPA_HIBERNATE_DDL_AUTO" = "validate"
}

Write-Host "== 0 engine identity =="
$engine = Invoke-Sql "SELECT CONCAT(VERSION(), ' | ', @@version_comment, ' | ', @@GLOBAL.transaction_isolation, ' | ', (SELECT default_collation_name FROM information_schema.schemata WHERE schema_name='$DbName'))"
Write-Host "engine=$engine"
$parts = $engine -split " \| "
if ($parts.Count -eq 4 -and $parts[0].StartsWith("8.4.") -and $parts[1] -like "MySQL Community Server*" `
        -and $parts[2] -eq "READ-COMMITTED" -and $parts[3] -eq "utf8mb4_0900_bin") {
    Pass "aa_engine" "MySQL $($parts[0]) ($($parts[1])), READ-COMMITTED, $DbName utf8mb4_0900_bin"
} else {
    Fail "aa_engine" "unexpected engine cell: $engine"
}

Write-Host "== 1 two replicas: health, login, shared session, config, flyway, dialect, cluster dependencies =="
$upA = Get-Health $BaseA
$upB = Get-Health $BaseB
if ($upA -and $upB) { Pass "aa_health" "replica A and replica B actuator health UP" } else { Fail "aa_health" "A=$upA B=$upB" }

$tokenA = Login $BaseA
$tokenB = Login $BaseB
if ($tokenA -and $tokenB) { Pass "aa_login" "admin login on A and on B" } else { Fail "aa_login" "A=$([bool]$tokenA) B=$([bool]$tokenB)" }

$crossAB = Invoke-Http "GET" "$BaseB/api/v1/platform/cluster/health" $tokenA
$crossBA = Invoke-Http "GET" "$BaseA/api/v1/platform/cluster/health" $tokenB
if ($crossAB.Code -eq 200 -and $crossBA.Code -eq 200) {
    Pass "aa_session_shared" "a token issued by A is accepted by B and the reverse (sessions in the shared MySQL database)"
} else {
    Fail "aa_session_shared" "A-token on B=$($crossAB.Code) B-token on A=$($crossBA.Code)"
}

$envBad = @()
foreach ($name in @($NameA, $NameB)) {
    foreach ($k in $ExpectEnv.Keys) {
        $v = Get-ContainerEnv $name $k
        if ($v -ne $ExpectEnv[$k]) { $envBad += "$name $k=$v" }
    }
}
$urlA = Get-ContainerEnv $NameA "SPRING_DATASOURCE_URL"
$urlB = Get-ContainerEnv $NameB "SPRING_DATASOURCE_URL"
$capsA = Get-ContainerEnv $NameA "ISPF_REPLICA_CAPABILITIES"
$capsB = Get-ContainerEnv $NameB "ISPF_REPLICA_CAPABILITIES"
$ridA = Get-ContainerEnv $NameA "ISPF_REPLICA_ID"
$ridB = Get-ContainerEnv $NameB "ISPF_REPLICA_ID"
if ($envBad.Count -eq 0 -and $urlA -eq $urlB -and $urlA -like "*/$DbName?*" -and $capsA -eq $capsB -and $ridA -ne $ridB) {
    Pass "aa_config" "both JVMs (docker inspect): single-engine on, history + journal JDBC, cluster on, NATS + JetStream on, Redis on, warm off, ddl-auto=validate; one ISPF_DB ($DbName); replica ids $ridA / $ridB; capabilities $capsA"
} else {
    Fail "aa_config" "env=$($envBad -join ', ') sameUrl=$($urlA -eq $urlB) caps=$capsA/$capsB ids=$ridA/$ridB"
}

$flywayOk = Get-Count "SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND success=1"
$flywayBad = Get-Count "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0"
if ($flywayOk -eq 1 -and $flywayBad -eq 0) { Pass "aa_flyway" "flyway mysql/V1 applied once (success=1), no failed rows" } else { Fail "aa_flyway" "v1=$flywayOk failed=$flywayBad" }

$logA = docker logs $NameA 2>&1 | Out-String
$logB = docker logs $NameB 2>&1 | Out-String
if ($logA.Contains("Database dialect: MysqlHibernateDialect") -and $logB.Contains("Database dialect: MysqlHibernateDialect") `
        -and -not $logA.Contains("MariaDBDialect") -and -not $logB.Contains("MariaDBDialect")) {
    Pass "aa_dialect" "Hibernate MysqlHibernateDialect with ddl-auto=validate on both JVMs"
} else {
    Fail "aa_dialect" "Hibernate dialect is not MysqlHibernateDialect on both JVMs"
}

$staleRefusal = $logA.Contains("is not certified on MySQL") -or $logB.Contains("is not certified on MySQL")
if ($upA -and $upB -and -not $staleRefusal) {
    Pass "aa_dialect_allows" "cluster + single-engine passes the MySQL boot fences (pre-Flyway and on context refresh) on both JVMs"
} else {
    Fail "aa_dialect_allows" "stale MySQL cluster refusal=$staleRefusal A=$upA B=$upB"
}

$session = Invoke-Sql "SELECT CONCAT(@@SESSION.transaction_isolation, ' | ', (SELECT default_collation_name FROM information_schema.schemata WHERE schema_name = DATABASE()))" "ispf"
if ($session -eq "READ-COMMITTED | utf8mb4_0900_bin" -and $upA -and $upB) {
    Pass "aa_preflight" "ispf session: $session; single-engine preflight passed on both JVMs"
} else {
    Fail "aa_preflight" "ispf session: $session A=$upA B=$upB"
}

$depsLine = "Cluster dependency check OK (jetStream=true, redis=true, liveMirror=true, pathInterest=true)"
if ($logA.Contains($depsLine) -and $logB.Contains($depsLine)) {
    Pass "aa_cluster_deps" "ClusterDependencyValidator on both JVMs: $depsLine"
} else {
    Fail "aa_cluster_deps" "validator OK line missing (A=$($logA.Contains($depsLine)) B=$($logB.Contains($depsLine)))"
}

$tzA = (docker exec $NameA date +%Z 2>&1 | Out-String).Trim()
$tzB = (docker exec $NameB date +%Z 2>&1 | Out-String).Trim()
$tzSql = Invoke-Sql "SELECT CONCAT(@@system_time_zone, ' | ', @@GLOBAL.time_zone)"
if ($tzA -eq "UTC" -and $tzB -eq "UTC" -and $tzSql -eq "UTC | SYSTEM") {
    Pass "aa_time_zone" "both JVM containers on UTC; MySQL system_time_zone UTC, time_zone SYSTEM (leases are zone-less DATETIME(6))"
} else {
    Fail "aa_time_zone" "A=$tzA B=$tzB mysql=$tzSql"
}

Write-Host "== 2 cluster membership =="
$hA = Get-ClusterHealth $BaseA $tokenA
$hB = Get-ClusterHealth $BaseB $tokenB
$memberOk = $hA -and $hB -and $hA.clusterEnabled -and $hB.clusterEnabled -and $hA.nodesUp -eq 2 -and $hB.nodesUp -eq 2 `
    -and $hA.nodesTotal -eq 2 -and $hB.nodesTotal -eq 2 -and $hA.liveVariableSyncEnabled -and $hA.natsEnabled
if ($memberOk) {
    Pass "aa_cluster_health" "GET /platform/cluster/health on A and on B: clusterEnabled, nodesUp=2/2, liveVariableSync on, NATS on"
} else {
    Fail "aa_cluster_health" "A=$($hA | ConvertTo-Json -Compress -Depth 2) B=$($hB.nodesUp)/$($hB.nodesTotal)"
}
$replicas = Invoke-Sql "SELECT GROUP_CONCAT(replica_id ORDER BY replica_id) FROM platform_cluster_replicas WHERE last_heartbeat_at > UTC_TIMESTAMP(6) - INTERVAL 60 SECOND"
if ($replicas -eq "mysql-aa-a,mysql-aa-b") {
    Pass "aa_replica_registry" "platform_cluster_replicas: $replicas with a heartbeat in the last 60 s"
} else {
    Fail "aa_replica_registry" "recent replicas: $replicas"
}

Write-Host "== 3 structure sync (NATS JetStream) =="
$c = New-Device $BaseA $tokenA $Device "MySQL A-A gate"
$seenMs = Wait-ByPath $BaseB $tokenB $Device 200 15
$del = Invoke-Http "DELETE" "$BaseA/api/v1/objects/by-path?path=$(Url $Device)" $tokenA
$goneMs = Wait-ByPath $BaseB $tokenB $Device 404 30
if ((Ok2xx $c.Code) -and $seenMs -ge 0 -and (Ok2xx $del.Code) -and $goneMs -ge 0) {
    Pass "aa_structure_sync" "device created on A visible on B after $seenMs ms; deleted on A, gone on B after $goneMs ms (no ghost)"
} else {
    Fail "aa_structure_sync" "create=$($c.Code) seenMs=$seenMs delete=$($del.Code) goneMs=$goneMs"
}

Write-Host "== 4 definitions created on A reach B on the object-level change =="
$c = New-Device $BaseA $tokenA $Device "MySQL A-A gate"
$schema = '{"name":"lagProbe","fields":[{"name":"value","type":"STRING"}]}'
$v1 = Invoke-Http "POST" "$BaseA/api/v1/objects/by-path/variables?path=$(Url $Device)" $tokenA ('{"name":"lagProbe","readable":true,"writable":true,"historyEnabled":false,"schema":' + $schema + ',"initialValue":{"schema":' + $schema + ',"rows":[{"value":"init"}]}}')
$hSchema = '{"name":"aa","fields":[{"name":"value","type":"DOUBLE"}]}'
$v2 = Invoke-Http "POST" "$BaseA/api/v1/objects/by-path/variables?path=$(Url $Device)" $tokenA ('{"name":"aa","schema":' + $hSchema + ',"readable":true,"writable":true,"historyEnabled":true,"historyRetentionDays":7}')
# With live sync on, a variable definition is not fanned out by itself (ADR-0024 / 0029); the object-level
# UPDATED below is, and B reloads the whole object from MySQL.
$patch = Invoke-Http "PATCH" "$BaseA/api/v1/objects/by-path?path=$(Url $Device)" $tokenA '{"eventJournalEnabled":true}'
$evDef = Invoke-Http "PUT" "$BaseA/api/v1/objects/by-path/events?path=$(Url $Device)" $tokenA '{"name":"aagate","description":"MySQL A-A gate","payloadSchema":{"name":"aagate","fields":[{"name":"note","type":"STRING"}]},"level":"INFO"}'
$defStart = NowMs
$defMs = -1
while ((NowMs) - $defStart -lt 30000) {
    $dl = Get-VariableDetail $BaseB $tokenB $Device "lagProbe"
    $da = Get-VariableDetail $BaseB $tokenB $Device "aa"
    if ($dl -and $da -and $dl.writable -and $da.writable -and $da.historyEnabled) { $defMs = (NowMs) - $defStart; break }
    Start-Sleep -Milliseconds 250
}
if ((Ok2xx $c.Code) -and (Ok2xx $v1.Code) -and (Ok2xx $v2.Code) -and $patch.Code -eq 200 -and $evDef.Code -eq 200 -and $defMs -ge 0) {
    Pass "aa_definition_sync" "variables lagProbe and aa (historized) and event aagate defined on A; after the object-level PATCH on A, B reloaded the object from MySQL and serves both definitions (writable, aa historyEnabled) in $defMs ms"
} else {
    Fail "aa_definition_sync" "device=$($c.Code) lagProbe=$($v1.Code) aa=$($v2.Code) patch=$($patch.Code) event=$($evDef.Code) defMs=$defMs"
}

Write-Host "== 5 live value both ways, with an observer on the reading replica =="
$wsB = Open-Observer $BaseB $tokenB $Device
$wsA = Open-Observer $BaseA $tokenA $Device
Start-Sleep 2
$ab = Measure-LiveLag $BaseA $tokenA $BaseB $tokenB $Device "ab-$Run"
$ba = Measure-LiveLag $BaseB $tokenB $BaseA $tokenA $Device "ba-$Run"
Close-Observer $wsA
Close-Observer $wsB
if ($wsA -and $wsB -and $ab.Ms -ge 0 -and $ba.Ms -ge 0) {
    Pass "aa_live_var" "WebSocket observer subscribed on each replica (/ws/objects, cluster path interest in Redis): PUT on A read on B in $($ab.Ms) ms, PUT on B read on A in $($ba.Ms) ms (8 consecutive reads each, SLO $LiveLagSloMs ms)"
} else {
    Fail "aa_live_var" "observers A=$([bool]$wsA) B=$([bool]$wsB) A->B=$($ab.Ms) ($($ab.Detail)) B->A=$($ba.Ms) ($($ba.Detail))"
}

Write-Host "== 6 historian: one writer per replica, no duplicate rows =="
$putA = Invoke-Http "PUT" "$BaseA/api/v1/objects/by-path/variables?path=$(Url $Device)&name=aa" $tokenA ('{"schema":' + $hSchema + ',"rows":[{"value":101.5}]}')
$putB = Invoke-Http "PUT" "$BaseB/api/v1/objects/by-path/variables?path=$(Url $Device)&name=aa" $tokenB ('{"schema":' + $hSchema + ',"rows":[{"value":202.5}]}')
$rows1 = -1; $rows2 = -1
for ($i = 0; $i -lt 20; $i++) {
    $rows1 = Get-Count "SELECT COUNT(*) FROM variable_samples WHERE object_path='$Device' AND variable_name='aa' AND value_double=101.5"
    $rows2 = Get-Count "SELECT COUNT(*) FROM variable_samples WHERE object_path='$Device' AND variable_name='aa' AND value_double=202.5"
    if ($rows1 -ge 1 -and $rows2 -ge 1) { break }
    Start-Sleep 1
}
# Let a mirrored replica write (the duplicate this item looks for) land before counting again.
Start-Sleep 5
$rows1 = Get-Count "SELECT COUNT(*) FROM variable_samples WHERE object_path='$Device' AND variable_name='aa' AND value_double=101.5"
$rows2 = Get-Count "SELECT COUNT(*) FROM variable_samples WHERE object_path='$Device' AND variable_name='aa' AND value_double=202.5"
$from = Iso ([DateTime]::UtcNow.AddHours(-1))
$to = Iso ([DateTime]::UtcNow.AddMinutes(5))
$readOk = $true
foreach ($pair in @(@($BaseA, $tokenA), @($BaseB, $tokenB))) {
    $raw = Invoke-Http "GET" "$($pair[0])/api/v1/objects/by-path/variables/history?path=$(Url $Device)&name=aa&from=$(Url $from)&to=$(Url $to)&limit=100" $pair[1]
    $values = @()
    if ($raw.Code -eq 200) { try { $values = @(($raw.Body | ConvertFrom-Json).samples | ForEach-Object { [double]$_.value }) } catch {} }
    if (-not ($values -contains 101.5 -and $values -contains 202.5)) { $readOk = $false }
}
if ($putA.Code -eq 200 -and $putB.Code -eq 200 -and $rows1 -eq 1 -and $rows2 -eq 1 -and $readOk) {
    Pass "aa_history_two_writers" "PUT 101.5 on A and 202.5 on B: exactly 1 variable_samples row each (no mirrored duplicate); GET /history on A and on B returns both"
} else {
    Fail "aa_history_two_writers" "putA=$($putA.Code) putB=$($putB.Code) $($putB.Body) rows101.5=$rows1 rows202.5=$rows2 readBoth=$readOk"
}

Write-Host "== 7 journal: one writer per replica, no duplicate rows =="
$evSchema = '{"name":"aagate","fields":[{"name":"note","type":"STRING"}]}'
$fireA = Invoke-Http "POST" "$BaseA/api/v1/events/fire?objectPath=$(Url $Device)&eventName=aagate" $tokenA ('{"schema":' + $evSchema + ',"rows":[{"note":"from-a"}]}')
$fireB = Invoke-Http "POST" "$BaseB/api/v1/events/fire?objectPath=$(Url $Device)&eventName=aagate" $tokenB ('{"schema":' + $evSchema + ',"rows":[{"note":"from-b"}]}')
$evRows = -1
for ($i = 0; $i -lt 20; $i++) {
    $evRows = Get-Count "SELECT COUNT(*) FROM event_history WHERE object_path='$Device' AND event_name='aagate'"
    if ($evRows -ge 2) { break }
    Start-Sleep 1
}
Start-Sleep 5
$evRows = Get-Count "SELECT COUNT(*) FROM event_history WHERE object_path='$Device' AND event_name='aagate'"
$fromA = Get-Count "SELECT COUNT(*) FROM event_history WHERE object_path='$Device' AND event_name='aagate' AND payload_json LIKE '%from-a%'"
$fromB = Get-Count "SELECT COUNT(*) FROM event_history WHERE object_path='$Device' AND event_name='aagate' AND payload_json LIKE '%from-b%'"
$before = Iso ([DateTime]::UtcNow.AddHours(1))
$listA = Invoke-Http "GET" "$BaseA/api/v1/events?objectPath=$(Url $Device)&limit=10&before=$(Url $before)" $tokenA
$listB = Invoke-Http "GET" "$BaseB/api/v1/events?objectPath=$(Url $Device)&limit=10&before=$(Url $before)" $tokenB
$listed = $listA.Body.Contains("from-a") -and $listA.Body.Contains("from-b") -and $listB.Body.Contains("from-a") -and $listB.Body.Contains("from-b")
if ($fireA.Code -eq 200 -and $fireB.Code -eq 200 -and $evRows -eq 2 -and $fromA -eq 1 -and $fromB -eq 1 -and $listed) {
    Pass "aa_journal_two_writers" "POST /events/fire on A and on B: exactly 2 event_history rows (1 per fire, no mirrored duplicate); GET /events?before= on A and on B lists both"
} else {
    Fail "aa_journal_two_writers" "fireA=$($fireA.Code) fireB=$($fireB.Code) rows=$evRows fromA=$fromA fromB=$fromB listed=$listed"
}

Write-Host "== 8 concurrent writes on both replicas + audit chain =="
$burst = @()
for ($i = 0; $i -lt $BurstPerReplica; $i++) {
    $burst += @{ Method = "POST"; Url = "$BaseA/api/v1/objects"; Token = $tokenA; Body = '{"parentPath":"' + $Parent + '","name":"mysql-aa-burst-' + $Run + '-a' + $i + '","type":"DEVICE","displayName":"burst"}' }
    $burst += @{ Method = "POST"; Url = "$BaseB/api/v1/objects"; Token = $tokenB; Body = '{"parentPath":"' + $Parent + '","name":"mysql-aa-burst-' + $Run + '-b' + $i + '","type":"DEVICE","displayName":"burst"}' }
}
$codes = Invoke-Parallel $burst
$okCodes = @($codes | Where-Object { Ok2xx $_ }).Count
$burstRows = Get-Count "SELECT COUNT(*) FROM object_nodes WHERE path LIKE '$Parent.mysql-aa-burst-$Run-%'"
$lastA = "$Parent.mysql-aa-burst-$Run-a$($BurstPerReplica - 1)"
$lastB = "$Parent.mysql-aa-burst-$Run-b$($BurstPerReplica - 1)"
$crossSeen = (Wait-ByPath $BaseB $tokenB $lastA 200 15) -ge 0 -and (Wait-ByPath $BaseA $tokenA $lastB 200 15) -ge 0
$total = 2 * $BurstPerReplica
if ($okCodes -eq $total -and $burstRows -eq $total -and $crossSeen) {
    Pass "aa_concurrent_writes" "$total concurrent POST /objects ($BurstPerReplica on A, $BurstPerReplica on B): all 2xx, $burstRows object_nodes rows, each replica sees the other's objects"
} else {
    Fail "aa_concurrent_writes" "ok=$okCodes/$total codes=$($codes -join ',') rows=$burstRows crossSeen=$crossSeen"
}
# Each delete appends to the audit hash chain; each replica deletes the objects the other one created.
$chainBefore = Get-Count "SELECT COUNT(*) FROM audit_event_chain"
$deletes = @()
for ($i = 0; $i -lt $BurstPerReplica; $i++) {
    $deletes += @{ Method = "DELETE"; Url = "$BaseA/api/v1/objects/by-path?path=$(Url "$Parent.mysql-aa-burst-$Run-b$i")"; Token = $tokenA; Body = "" }
    $deletes += @{ Method = "DELETE"; Url = "$BaseB/api/v1/objects/by-path?path=$(Url "$Parent.mysql-aa-burst-$Run-a$i")"; Token = $tokenB; Body = "" }
}
$delCodes = Invoke-Parallel $deletes
$delOk = @($delCodes | Where-Object { Ok2xx $_ }).Count
$leftRows = Get-Count "SELECT COUNT(*) FROM object_nodes WHERE path LIKE '$Parent.mysql-aa-burst-$Run-%'"
$chainAfter = Get-Count "SELECT COUNT(*) FROM audit_event_chain"
$verA = Invoke-Http "GET" "$BaseA/api/v1/audit/verify-chain" $tokenA
$verB = Invoke-Http "GET" "$BaseB/api/v1/audit/verify-chain" $tokenB
if ($delOk -eq $total -and $leftRows -eq 0 -and $chainAfter - $chainBefore -ge $total `
        -and $verA.Body -match '"intact"\s*:\s*true' -and $verB.Body -match '"intact"\s*:\s*true') {
    Pass "aa_audit_chain" "$total concurrent DELETE /objects across both JVMs (each deletes the other's objects): audit_event_chain $chainBefore -> $chainAfter; GET /audit/verify-chain intact on A and on B"
} else {
    Fail "aa_audit_chain" "deletes=$delOk/$total codes=$($delCodes -join ',') left=$leftRows chain $chainBefore -> $chainAfter verifyA=$($verA.Body) verifyB=$($verB.Body)"
}

Write-Host "== 9 driver lease on A, crash A, reclaim on B =="
$c = New-Device $BaseA $tokenA $DriverDevice "MySQL A-A driver"
Start-Sleep 2
$cfg = Invoke-Http "PUT" "$BaseA/api/v1/drivers/runtime/configure?devicePath=$(Url $DriverDevice)" $tokenA '{"driverId":"virtual","pollIntervalMs":1000,"autoStart":true}'
$holder = ""
for ($i = 0; $i -lt 20; $i++) {
    $holder = Get-DriverHolder $DriverDevice
    if ($holder) { break }
    Start-Sleep 1
}
$lockRows = Get-Count "SELECT COUNT(*) FROM platform_driver_locks WHERE device_path='$DriverDevice'"
$runningA = $cfg.Code -eq 200 -and $cfg.Body -match '"status"\s*:\s*"RUNNING"'
if ((Ok2xx $c.Code) -and $runningA -and $holder -eq "mysql-aa-a" -and $lockRows -eq 1) {
    Pass "aa_driver_lease" "virtual driver configured and started through A (RUNNING): one platform_driver_locks row, holder mysql-aa-a, live"
} else {
    Fail "aa_driver_lease" "create=$($c.Code) configure=$($cfg.Code) holder=$holder rows=$lockRows body=$($cfg.Body)"
}
$bindingOnB = Read-StringValue $BaseB $tokenB $DriverDevice "driverId"
Start-Sleep 5

$leadersBefore = Get-LeaderLocks
$shortBefore = @($leadersBefore | Where-Object { $ShortTtlLocks -contains $_.Name -and $_.State -eq "live" })
$holdersBefore = @($shortBefore | Select-Object -ExpandProperty Holder -Unique)

$killedAt = [DateTime]::UtcNow
docker kill $NameA | Out-Null
$reclaimSec = -1
$newHolder = ""
while (([DateTime]::UtcNow - $killedAt).TotalSeconds -lt ($ReclaimSloSec + 15)) {
    $newHolder = Get-DriverHolder $DriverDevice
    if ($newHolder -eq "mysql-aa-b") { $reclaimSec = [math]::Round(([DateTime]::UtcNow - $killedAt).TotalSeconds, 1); break }
    Start-Sleep 1
}
$statusB = Invoke-Http "GET" "$BaseB/api/v1/drivers/runtime/status?devicePath=$(Url $DriverDevice)" $tokenB
$runningB = $statusB.Code -eq 200 -and $statusB.Body -match '"status"\s*:\s*"RUNNING"'
if ($reclaimSec -ge 0 -and $reclaimSec -le $ReclaimSloSec -and $runningB) {
    Pass "aa_crash_reclaim" "docker kill A (no graceful release): B reclaimed the driver lease in $reclaimSec s (TTL 15 s, scan 5 s, SLO $ReclaimSloSec s); driver RUNNING on B"
} else {
    Fail "aa_crash_reclaim" "reclaimSec=$reclaimSec holder=$newHolder driverId on B before the kill='$bindingOnB' statusB=$($statusB.Code) $($statusB.Body)"
}

$infoB = Invoke-Http "GET" "$BaseB/api/v1/info"
$tokenB2 = Login $BaseB
$putSurvivor = Invoke-Http "PUT" "$BaseB/api/v1/objects/by-path/variables?path=$(Url $Device)&name=aa" $tokenB ('{"schema":' + $hSchema + ',"rows":[{"value":303.5}]}')
$fireSurvivor = Invoke-Http "POST" "$BaseB/api/v1/events/fire?objectPath=$(Url $Device)&eventName=aagate" $tokenB ('{"schema":' + $evSchema + ',"rows":[{"note":"survivor"}]}')
$survRows = -1; $survEvents = -1
for ($i = 0; $i -lt 20; $i++) {
    $survRows = Get-Count "SELECT COUNT(*) FROM variable_samples WHERE object_path='$Device' AND variable_name='aa' AND value_double=303.5"
    $survEvents = Get-Count "SELECT COUNT(*) FROM event_history WHERE object_path='$Device' AND event_name='aagate' AND payload_json LIKE '%survivor%'"
    if ($survRows -ge 1 -and $survEvents -ge 1) { break }
    Start-Sleep 1
}
if ($infoB.Code -eq 200 -and $tokenB2 -and $putSurvivor.Code -eq 200 -and $fireSurvivor.Code -eq 200 -and $survRows -eq 1 -and $survEvents -eq 1) {
    Pass "aa_survivor_serves" "with A down: B answers /info and login, and its historian and journal writes land in MySQL (1 sample row, 1 event row)"
} else {
    Fail "aa_survivor_serves" "info=$($infoB.Code) login=$([bool]$tokenB2) put=$($putSurvivor.Code) fire=$($fireSurvivor.Code) rows=$survRows events=$survEvents"
}

# Leases of at most 30 s held by the dead JVM have expired by now; the survivor must hold every live one.
$waitLeft = 45 - ([DateTime]::UtcNow - $killedAt).TotalSeconds
if ($waitLeft -gt 0) { Start-Sleep -Seconds ([math]::Ceiling($waitLeft)) }
$leadersAfter = Get-LeaderLocks
$shortAfter = @($leadersAfter | Where-Object { $ShortTtlLocks -contains $_.Name -and $_.State -eq "live" })
$holdersAfter = @($shortAfter | Select-Object -ExpandProperty Holder -Unique)
$survivor = if ($holdersAfter.Count -eq 1) { $holdersAfter[0] } else { "" }
$survivorNames = @($shortAfter | Where-Object { $_.Holder -eq $survivor } | Select-Object -ExpandProperty Name)
$takenOver = @($shortBefore | Where-Object { $_.Holder -ne $survivor -and $survivorNames -contains $_.Name }).Count
$beforeText = ($shortBefore | ForEach-Object { "$($_.Name)" }) -join ","
$afterText = ($shortAfter | ForEach-Object { "$($_.Name)" }) -join ","
if ($holdersBefore.Count -ge 1 -and $holdersBefore.Count -le 2 -and $holdersAfter.Count -eq 1 -and $shortAfter.Count -ge 1) {
    Pass "aa_leader_survivor" "before the kill $($shortBefore.Count) live short-TTL leases on $($holdersBefore.Count) holder(s) [$beforeText]; 45 s after it $($shortAfter.Count) live, all on one holder (the survivor) [$afterText]; $takenOver taken over from the dead JVM"
} else {
    Fail "aa_leader_survivor" "before holders=$($holdersBefore.Count) [$beforeText] after holders=$($holdersAfter.Count) [$afterText]"
}

Write-Host "== 10 restart A: rejoin without stealing the lease =="
docker start $NameA | Out-Null
$rejoinUp = Wait-Healthy $BaseA 300
$nodesUp = 0
for ($i = 0; $i -lt 30; $i++) {
    $h = Get-ClusterHealth $BaseB $tokenB
    if ($h) { $nodesUp = $h.nodesUp }
    if ($nodesUp -eq 2) { break }
    Start-Sleep 3
}
Start-Sleep 10
$holderAfter = Get-DriverHolder $DriverDevice
$rowsAfter = Get-Count "SELECT COUNT(*) FROM platform_driver_locks WHERE device_path='$DriverDevice'"
if ($rejoinUp -and $nodesUp -eq 2 -and $holderAfter -eq "mysql-aa-b" -and $rowsAfter -eq 1) {
    Pass "aa_rejoin" "A restarted and rejoined (nodesUp=2 from B); the driver lease stayed with mysql-aa-b, one lock row (no dual owner)"
} else {
    Fail "aa_rejoin" "aUp=$rejoinUp nodesUp=$nodesUp holder=$holderAfter rows=$rowsAfter"
}
$tokenA = Login $BaseA
$verA = Invoke-Http "GET" "$BaseA/api/v1/audit/verify-chain" $tokenA
$verB = Invoke-Http "GET" "$BaseB/api/v1/audit/verify-chain" $tokenB
if ($verA.Body -match '"intact"\s*:\s*true' -and $verB.Body -match '"intact"\s*:\s*true') {
    Pass "aa_audit_after_crash" "GET /audit/verify-chain intact on A and on B after the crash, survivor writes and rejoin"
} else {
    Fail "aa_audit_after_crash" "verifyA=$($verA.Code) $($verA.Body) verifyB=$($verB.Code) $($verB.Body)"
}
$logA = docker logs $NameA 2>&1 | Out-String
$logB = docker logs $NameB 2>&1 | Out-String
$dupA = ([regex]::Matches($logA, [regex]::Escape($LeaseDuplicate))).Count
$dupB = ([regex]::Matches($logB, [regex]::Escape($LeaseDuplicate))).Count
# A follower that tried to INSERT a held lease every tick logged several duplicate-key lines per second.
if ($dupA + $dupB -le 3) {
    Pass "aa_lease_log_noise" "leader-lease duplicate-key lines over the whole run: A=$dupA, B=$dupB (followers do not INSERT a held lease)"
} else {
    Fail "aa_lease_log_noise" "leader-lease duplicate-key lines: A=$dupA, B=$dupB"
}

Write-Host "== 11 boot refusals (throwaway database + throwaway NATS) =="
docker rm -f $NameNatsFence 2>$null | Out-Null
docker run -d --name $NameNatsFence --hostname $NameNatsFence --network $Net "nats:2.10-alpine" -js | Out-Null
Invoke-Sql "DROP DATABASE IF EXISTS $FenceDb; CREATE DATABASE $FenceDb CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin; GRANT ALL PRIVILEGES ON $FenceDb.* TO 'ispf'@'%'" "root" "mysql" | Out-Null
$FenceEnv = [ordered]@{}
foreach ($line in (docker inspect --format "{{range .Config.Env}}{{println .}}{{end}}" $NameB)) {
    $eq = $line.IndexOf("=")
    if ($eq -gt 0 -and ($line.StartsWith("ISPF_") -or $line.StartsWith("SPRING_"))) { $FenceEnv[$line.Substring(0, $eq)] = $line.Substring($eq + 1) }
}
$FenceEnv["SPRING_DATASOURCE_URL"] = "jdbc:mysql://${NameSql}:3306/${FenceDb}?permitMysqlScheme=true&allowPublicKeyRetrieval=true"
$FenceEnv["ISPF_NATS_URL"] = "nats://${NameNatsFence}:4222"
# No Redis server on the fence host: a fence that reached Redis would fail with a different message.
$FenceEnv["ISPF_REDIS_HOST"] = $NameNatsFence
$FenceEnv["ISPF_REPLICA_ID"] = "mysql-aa-fence"

function Run-Fence([string]$id, [hashtable]$overrides, [string]$expect) {
    $name = "ispf-mysql-aa-fence-$id"
    docker rm -f $name 2>$null | Out-Null
    $dockerArgs = @("run", "-d", "--name", $name, "--network", $Net, "-v", "${Jar}:/app/ispf-server.jar:ro")
    $envAll = [ordered]@{}
    foreach ($k in $FenceEnv.Keys) { $envAll[$k] = $FenceEnv[$k] }
    foreach ($k in $overrides.Keys) { $envAll[$k] = $overrides[$k] }
    foreach ($k in $envAll.Keys) { $dockerArgs += @("-e", "$k=$($envAll[$k])") }
    $dockerArgs += @($ImageJre, "java", "-jar", "/app/ispf-server.jar")
    docker @dockerArgs | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail $id "docker run failed"; return }
    # Dialect fences stop the context; ClusterDependencyValidator runs after "Started", so wait for exit too.
    $deadline = (Get-Date).AddSeconds(300)
    $log = ""
    $found = $false
    while ((Get-Date) -lt $deadline) {
        $log = docker logs $name 2>&1 | Out-String
        if ($log.Contains($expect)) { $found = $true; break }
        $running = (docker inspect -f "{{.State.Running}}" $name 2>$null | Out-String).Trim()
        if ($running -ne "true") {
            $log = docker logs $name 2>&1 | Out-String
            $found = $log.Contains($expect)
            break
        }
        Start-Sleep 2
    }
    $exited = (docker inspect -f "{{.State.Running}}" $name 2>$null | Out-String).Trim() -ne "true"
    if (-not $exited) {
        Start-Sleep 10
        $exited = (docker inspect -f "{{.State.Running}}" $name 2>$null | Out-String).Trim() -ne "true"
    }
    docker rm -f $name 2>$null | Out-Null
    if ($log.Contains("ispf.cluster.enabled=true is not certified on MySQL")) { Fail $id "stale MySQL cluster refusal in the log"; return }
    if ($found -and $exited) { Pass $id "refused with: $expect" } else { Fail $id "expect=$expect found=$found exited=$exited" }
}

Run-Fence "aa_fence_b1_cluster" @{ "ISPF_MYSQL_SINGLE_ENGINE" = "false"; "ISPF_VARIABLE_HISTORY_ENABLED" = "false"; "ISPF_EVENT_JOURNAL_ENABLED" = "false" } `
    "ispf.cluster.enabled=true on MySQL requires ISPF_MYSQL_SINGLE_ENGINE=true"
Run-Fence "aa_fence_nats_off" @{ "ISPF_NATS_ENABLED" = "false" } "ispf.cluster.enabled=true requires ispf.nats.enabled=true"
Run-Fence "aa_fence_jetstream_off" @{ "ISPF_NATS_JETSTREAM_ENABLED" = "false" } "ispf.cluster.enabled=true requires ispf.nats.jet-stream-enabled=true"
Run-Fence "aa_fence_redis_off" @{ "ISPF_REDIS_ENABLED" = "false" } "requires ispf.redis.enabled=true"
Run-Fence "aa_fence_dual" @{ "ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED" = "true" } "dual-write-enabled=true is refused on MySQL"
Run-Fence "aa_fence_warm" @{ "ISPF_HISTORIAN_TIER_WARM_ENABLED" = "true" } "MySQL single-engine refuses ClickHouse warm-tier routing"
Run-Fence "aa_fence_spool" @{ "ISPF_VARIABLE_HISTORY_SPOOL_ENABLED" = "true" } "ISPF_VARIABLE_HISTORY_SPOOL_ENABLED is the ClickHouse spool"
docker rm -f $NameNatsFence 2>$null | Out-Null
Invoke-Sql "DROP DATABASE IF EXISTS $FenceDb" "root" "mysql" | Out-Null

$fails = @($items | Where-Object { $_.status -eq "FAIL" }).Count
$doc = [ordered]@{
    generated_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    gate = "A-A (single-engine + cluster, two JVMs)"
    base_a = $BaseA
    base_b = $BaseB
    db = $DbName
    engine_note = $EngineNote
    edition = $engine
    jdbc_client = "MariaDB Connector/J (LGPL-2.1) org.mariadb.jdbc.Driver, jdbc:mysql: + permitMysqlScheme"
    topology = "2 JVMs, same capabilities ($capsA), one ISPF_DB; NATS 2.10 JetStream; Redis 7.4; staged start (A then B)"
    reclaim_slo_sec = $ReclaimSloSec
    live_lag_slo_ms = $LiveLagSloMs
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
