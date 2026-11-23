# Local A-A lab stand: Oracle Database Free + NATS JetStream + Redis + two branch bootJar JVMs
# (:18097 / :18098) in cluster mode on one single-engine schema (ADR-0080 A-A).
# Reuses the B1 Oracle container (:11521) and creates a dedicated ispf_aa schema user. Startup is
# staged: replica A runs Flyway and first boot alone, then B joins. Both replicas get the same
# explicit capabilities (public API + drivers). Jar/ojdbc/virtual-pack are snapshotted under .run-aa
# so a mid-run Gradle rebuild cannot corrupt the bind-mounted bootJar.
param(
    [string]$Jar = "",
    [string]$OjdbcJar = "",
    [string]$Password = $env:ORACLE_LAB_PASSWORD,
    [string]$Net = "ispf-oracle-b1-net",
    [string]$NameSql = "ispf-oracle-b1-stand",
    [string]$NameNats = "ispf-oracle-aa-nats",
    [string]$NameRedis = "ispf-oracle-aa-redis",
    [string]$NameA = "ispf-oracle-aa-a",
    [string]$NameB = "ispf-oracle-aa-b",
    [string]$SchemaUser = "ispf_aa",
    [int]$PortA = 18097,
    [int]$PortB = 18098,
    [string]$BindHost = $(if ($env:BIND_HOST) { $env:BIND_HOST } else { "127.0.0.1" }),
    [string]$ImageSql = $(if ($env:ORACLE_IMAGE) { $env:ORACLE_IMAGE } else { "gvenzl/oracle-free:23-slim-faststart" }),
    [string]$ImageNats = "nats:2.10-alpine",
    [string]$ImageRedis = "redis:7.4.2-alpine",
    [string]$VirtualPack = ""
)

$ErrorActionPreference = "Continue"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
. (Join-Path $ScriptDir "lab-common.ps1")

if (-not $Password) {
    throw "Set ORACLE_LAB_PASSWORD (lab throwaway; SYSTEM and schema users) or pass -Password"
}

$Jar = Resolve-LabBootJar $RepoRoot $Jar
$OjdbcJar = Resolve-LabOjdbc $RepoRoot $OjdbcJar
$LabRunDir = Join-Path $ScriptDir ".run-aa"
New-Item -ItemType Directory -Force -Path $LabRunDir | Out-Null
$JarSnapshot = Join-Path $LabRunDir "ispf-server-aa.jar"
Copy-Item -LiteralPath $Jar -Destination $JarSnapshot -Force
$Jar = $JarSnapshot
$OjdbcSnapshot = Join-Path $LabRunDir "ojdbc17.jar"
Copy-Item -LiteralPath $OjdbcJar -Destination $OjdbcSnapshot -Force
$OjdbcJar = $OjdbcSnapshot
Write-Host "jar=$Jar (snapshot)"
Write-Host "ojdbc=$OjdbcJar (snapshot)"
if (-not $VirtualPack) { $VirtualPack = Join-Path $RepoRoot "build\driver-packs\ispf-driver-virtual" }
if (-not (Test-Path (Join-Path $VirtualPack "driver-pack.json"))) {
    throw "Virtual driver pack not found at $VirtualPack (run .\gradlew.bat syncDevDriverPacks or pass -VirtualPack)"
}
$PackSnapshot = Join-Path $LabRunDir "ispf-driver-virtual"
if (Test-Path $PackSnapshot) { Remove-Item -LiteralPath $PackSnapshot -Recurse -Force }
Copy-Item -LiteralPath $VirtualPack -Destination $PackSnapshot -Recurse -Force
$VirtualPack = $PackSnapshot
Write-Host "virtual_pack=$VirtualPack"

docker network inspect $Net 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    docker network create $Net | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "failed to create docker network $Net" }
}

$running = docker ps --format "{{.Names}}" | Select-String -Pattern "^$NameSql$"
if (-not $running) {
    docker rm -f $NameSql 2>$null | Out-Null
    $cid = docker run -d --name $NameSql --hostname $NameSql --network $Net `
        -e "ORACLE_PASSWORD=$Password" `
        -e "APP_USER=ispf" `
        -e "APP_USER_PASSWORD=$Password" `
        -p "${BindHost}:11521:1521" `
        $ImageSql
    if (-not $cid) { throw "failed to start Oracle container" }
    Write-Host "oracle_cid=$cid"
}

Write-Host "== wait Oracle =="
$ready = $false
for ($i = 1; $i -le 120; $i++) {
    $log = docker logs $NameSql 2>&1 | Out-String
    if ($log.Contains("DATABASE IS READY TO USE!")) {
        Write-Host "sql_ready attempt=$i"
        $ready = $true
        break
    }
    Start-Sleep 3
}
if (-not $ready) { throw "Oracle not ready" }

Write-Host "== prepare schema user $SchemaUser (dedicated A-A schema) =="
docker rm -f $NameA $NameB 2>$null | Out-Null
# Drop/recreate the A-A schema user so Flyway starts on an empty schema.
Invoke-OracleSql $NameSql "system" $Password @"
BEGIN
  EXECUTE IMMEDIATE 'DROP USER $SchemaUser CASCADE';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -1918 THEN RAISE; END IF;
END;
/
CREATE USER $SchemaUser IDENTIFIED BY "$Password" QUOTA UNLIMITED ON USERS;
GRANT CONNECT, RESOURCE, CREATE VIEW, CREATE SEQUENCE, CREATE TRIGGER TO $SchemaUser;
"@
Invoke-OracleSql $NameSql "system" $Password "SELECT banner_full FROM v`$version;"

Write-Host "== NATS JetStream + Redis (fresh) =="
docker rm -f $NameNats $NameRedis 2>$null | Out-Null
docker run -d --name $NameNats --hostname $NameNats --network $Net $ImageNats -js -m 8222 | Out-Null
if ($LASTEXITCODE -ne 0) { throw "failed to start $NameNats" }
docker run -d --name $NameRedis --hostname $NameRedis --network $Net $ImageRedis | Out-Null
if ($LASTEXITCODE -ne 0) { throw "failed to start $NameRedis" }
Start-Sleep 3

$Jdbc = Get-OracleJdbcUrl $NameSql

$AaEnv = [ordered]@{
    "ISPF_ORACLE_SINGLE_ENGINE" = "true"
    "ISPF_HISTORIAN_DEPLOY_PROFILE" = "oracle-single-engine"
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
    "ISPF_CLUSTER_ENABLED" = "true"
    "ISPF_REPLICA_CAPABILITIES" = "http-public,ws,replica-sync,config-write,schedulers,drivers"
    "ISPF_CLUSTER_LIVE_VARIABLE_SYNC" = "true"
    "ISPF_CLUSTER_PATH_INTEREST" = "true"
    "ISPF_CLUSTER_DRIVER_LOCK_TTL_SECONDS" = "15"
    "ISPF_CLUSTER_DRIVER_FAILOVER_SCAN_MS" = "5000"
    "ISPF_NATS_ENABLED" = "true"
    "ISPF_NATS_URL" = "nats://${NameNats}:4222"
    "ISPF_NATS_REPLICA_EVENTS" = "true"
    "ISPF_NATS_JETSTREAM_ENABLED" = "true"
    "ISPF_REDIS_ENABLED" = "true"
    "ISPF_REDIS_HOST" = $NameRedis
    "ISPF_DRIVER_PACKS_DIR" = "/data/drivers"
    "SPRING_DATASOURCE_USERNAME" = $SchemaUser
}

function Start-AaReplica([string]$name, [string]$replicaId, [int]$port) {
    $extra = @{}
    foreach ($k in $AaEnv.Keys) { $extra[$k] = $AaEnv[$k] }
    $extra["ISPF_REPLICA_ID"] = $replicaId
    docker rm -f $name 2>$null | Out-Null
    $dockerArgs = @("run", "-d", "--name", $name, "--hostname", $name, "--network", $Net,
        "-p", "${BindHost}:${port}:8080",
        "-v", "${Jar}:/app/ispf-server.jar:ro",
        "-v", "${OjdbcJar}:/app/lib/ojdbc17.jar:ro",
        "-v", "${VirtualPack}:/data/drivers/ispf-driver-virtual:ro",
        "-e", "SPRING_PROFILES_ACTIVE=local",
        "-e", "ISPF_METADATA_DB_KIND=oracle",
        "-e", "SPRING_DATASOURCE_URL=$Jdbc",
        "-e", "SPRING_DATASOURCE_USERNAME=$SchemaUser",
        "-e", "SPRING_DATASOURCE_PASSWORD=$Password",
        "-e", "SPRING_DATASOURCE_DRIVER_CLASS_NAME=oracle.jdbc.OracleDriver",
        "-e", "ISPF_SECURITY_STRICT_STARTUP=false",
        "-e", "ISPF_BOOTSTRAP_FIXTURES_ENABLED=false",
        "-e", "ISPF_LICENSE_ENFORCE=false")
    foreach ($k in $extra.Keys) { $dockerArgs += @("-e", "$k=$($extra[$k])") }
    $dockerArgs += @($ImageJre, "java", "-Dloader.path=/app/lib", "-cp", "/app/ispf-server.jar",
        "org.springframework.boot.loader.launch.PropertiesLauncher")
    docker @dockerArgs | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "failed to start $name (is :$port free?)" }
}

function Wait-Healthy([string]$name, [int]$port) {
    for ($i = 1; $i -le 120; $i++) {
        try {
            $h = Invoke-RestMethod -Uri "http://127.0.0.1:${port}/actuator/health" -TimeoutSec 3
            if ($h.status -eq "UP") {
                Write-Host "ispf_up $name port=$port attempt=$i"
                Invoke-RestMethod -Uri "http://127.0.0.1:${port}/api/v1/info" |
                    Select-Object replicaId, clusterEnabled, replicaProfile, version | ConvertTo-Json -Compress
                return
            }
        } catch {}
        $alive = docker ps --format "{{.Names}}" | Select-String -Pattern "^$name$"
        if (-not $alive) {
            docker logs $name 2>&1 | Select-Object -Last 80
            throw "ISPF replica $name exited"
        }
        Start-Sleep 5
    }
    docker logs $name 2>&1 | Select-Object -Last 80
    throw "ISPF replica $name not healthy"
}

Write-Host "== start replica A alone (Flyway + first boot on the empty schema) =="
Start-AaReplica $NameA "oracle-aa-a" $PortA
Wait-Healthy $NameA $PortA

Write-Host "== start replica B (joins the cluster) =="
Start-AaReplica $NameB "oracle-aa-b" $PortB
Wait-Healthy $NameB $PortB
