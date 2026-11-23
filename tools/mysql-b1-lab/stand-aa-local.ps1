# Local A-A (active-active) lab stand: MySQL 8.4 Community + NATS JetStream + Redis + two branch bootJar JVMs
# (:18095 / :18096) in cluster mode on one single-engine database (ADR-0079 § Amendment 6).
# Reuses the B1 MySQL container (:13306) and creates a dedicated ispf_aa database on it. Startup is staged as
# docs/en/cluster.md requires on an empty database: replica A runs Flyway and first boot alone, then B joins.
# Both replicas get the same explicit capabilities (public API + drivers), so either one can take over drivers.
param(
    [string]$Jar = "",
    [string]$Password = $env:MYSQL_LAB_PASSWORD,
    [string]$Net = "ispf-mysql-b1-net",
    [string]$NameSql = "ispf-mysql-b1-stand",
    [string]$NameNats = "ispf-mysql-aa-nats",
    [string]$NameRedis = "ispf-mysql-aa-redis",
    [string]$NameA = "ispf-mysql-aa-a",
    [string]$NameB = "ispf-mysql-aa-b",
    [string]$DbName = "ispf_aa",
    [int]$PortA = 18095,
    [int]$PortB = 18096,
    [string]$BindHost = $(if ($env:BIND_HOST) { $env:BIND_HOST } else { "127.0.0.1" }),
    [string]$ImageSql = $(if ($env:MYSQL_IMAGE) { $env:MYSQL_IMAGE } else { "mysql:8.4" }),
    [string]$ImageNats = "nats:2.10-alpine",
    [string]$ImageRedis = "redis:7.4.2-alpine",
    # The gate's driver-lease failover runs the virtual driver; the bootJar does not embed driver packs.
    [string]$VirtualPack = ""
)

$ErrorActionPreference = "Continue"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..\..")

if (-not $Password) {
    throw "Set MYSQL_LAB_PASSWORD (lab throwaway; root and the ispf user) or pass -Password"
}

if (-not $Jar) {
    $candidates = Get-ChildItem -Path (Join-Path $RepoRoot "packages\ispf-server\build\libs") `
        -Filter "ispf-server-*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*-plain.jar" } |
        Sort-Object LastWriteTime -Descending
    if (-not $candidates) { throw "No bootJar under packages/ispf-server/build/libs (build first or pass -Jar)" }
    $Jar = $candidates[0].FullName
}
if (-not (Test-Path $Jar)) { throw "Jar not found: $Jar" }
# Snapshot the bootJar (and virtual pack) away from Gradle's build/libs. A bind-mount of the
# live build output is rewritten when another agent runs bootJar; Spring's nested classloader
# then loses MariaDB classes (QuitPacket CNFE) and the Hikari pool dies mid-gate.
$LabRunDir = Join-Path $ScriptDir ".run-aa"
New-Item -ItemType Directory -Force -Path $LabRunDir | Out-Null
$JarSnapshot = Join-Path $LabRunDir "ispf-server-aa.jar"
Copy-Item -LiteralPath $Jar -Destination $JarSnapshot -Force
$Jar = $JarSnapshot
Write-Host "jar=$Jar (snapshot from build output)"
if (-not $VirtualPack) { $VirtualPack = Join-Path $RepoRoot "build\driver-packs\ispf-driver-virtual" }
if (-not (Test-Path (Join-Path $VirtualPack "driver-pack.json"))) {
    throw "Virtual driver pack not found at $VirtualPack (run .\gradlew.bat syncDevDriverPacks or pass -VirtualPack)"
}
$PackSnapshot = Join-Path $LabRunDir "ispf-driver-virtual"
if (Test-Path $PackSnapshot) { Remove-Item -LiteralPath $PackSnapshot -Recurse -Force }
Copy-Item -LiteralPath $VirtualPack -Destination $PackSnapshot -Recurse -Force
$VirtualPack = $PackSnapshot
Write-Host "virtual_pack=$VirtualPack"

$ImageJre = "eclipse-temurin:25-jre-jammy"

docker network inspect $Net 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    docker network create $Net | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "failed to create docker network $Net" }
}

$running = docker ps --format "{{.Names}}" | Select-String -Pattern "^$NameSql$"
if (-not $running) {
    docker rm -f $NameSql 2>$null | Out-Null
    # Same server flags as the B1 and B2 stands: READ-COMMITTED, utf8mb4_0900_bin.
    $cid = docker run -d --name $NameSql --hostname $NameSql --network $Net `
        -e "MYSQL_ROOT_PASSWORD=$Password" `
        -e "MYSQL_DATABASE=ispf" `
        -e "MYSQL_USER=ispf" `
        -e "MYSQL_PASSWORD=$Password" `
        -p "${BindHost}:13306:3306" `
        $ImageSql `
        --transaction-isolation=READ-COMMITTED `
        --character-set-server=utf8mb4 `
        --collation-server=utf8mb4_0900_bin
    if (-not $cid) { throw "failed to start MySQL container" }
    Write-Host "mysql_cid=$cid"
}

Write-Host "== wait MySQL =="
$ready = $false
for ($i = 1; $i -le 90; $i++) {
    # Quote --host: PowerShell splits an unquoted -h127.0.0.1 at the first dot.
    docker exec $NameSql mysql "--host=127.0.0.1" -uroot "-p$Password" -e "SELECT 1" 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "sql_ready attempt=$i"
        $ready = $true
        break
    }
    Start-Sleep 2
}
if (-not $ready) { throw "MySQL not ready" }

function Invoke-Sql([string]$q) {
    docker exec $NameSql mysql "--host=127.0.0.1" -uroot "-p$Password" -e $q 2>&1 |
        Where-Object { $_ -notmatch "Using a password on the command line" }
    if ($LASTEXITCODE -ne 0) { throw "sql failed: $q" }
}

Write-Host "== prepare $DbName (dedicated A-A database on this lab server) =="
# Stop the previous JVMs first so DROP does not wait on their connections.
docker rm -f $NameA $NameB 2>$null | Out-Null
Invoke-Sql "DROP DATABASE IF EXISTS $DbName"
Invoke-Sql "CREATE DATABASE $DbName CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin"
Invoke-Sql "GRANT ALL PRIVILEGES ON $DbName.* TO 'ispf'@'%'"
# Flyway reads this to reset user variables between migrations; without it Flyway warns and skips.
Invoke-Sql "GRANT SELECT ON performance_schema.user_variables_by_thread TO 'ispf'@'%'"
Invoke-Sql "SELECT schema_name, default_character_set_name, default_collation_name FROM information_schema.schemata WHERE schema_name='$DbName'"
Write-Host "== engine =="
Invoke-Sql "SELECT VERSION() AS version, @@version_comment AS edition, @@transaction_isolation AS isolation, @@collation_server AS collation_server, @@system_time_zone AS system_tz, @@time_zone AS tz"

Write-Host "== NATS JetStream + Redis (fresh) =="
docker rm -f $NameNats $NameRedis 2>$null | Out-Null
docker run -d --name $NameNats --hostname $NameNats --network $Net $ImageNats -js -m 8222 | Out-Null
if ($LASTEXITCODE -ne 0) { throw "failed to start $NameNats" }
docker run -d --name $NameRedis --hostname $NameRedis --network $Net $ImageRedis | Out-Null
if ($LASTEXITCODE -ne 0) { throw "failed to start $NameRedis" }
Start-Sleep 3

# Lab only: allowPublicKeyRetrieval for caching_sha2_password without TLS — production uses sslMode=verify-full.
$Jdbc = "jdbc:mysql://${NameSql}:3306/${DbName}?permitMysqlScheme=true&allowPublicKeyRetrieval=true"

$AaEnv = [ordered]@{
    "SPRING_PROFILES_ACTIVE" = "local"
    "SPRING_JPA_HIBERNATE_DDL_AUTO" = "validate"
    "ISPF_METADATA_DB_KIND" = "mysql"
    "SPRING_DATASOURCE_URL" = $Jdbc
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
    "ISPF_CLUSTER_ENABLED" = "true"
    # ADR-0032 refuses jobs + drivers on one JVM, so a clustered replica cannot stay unified.
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
    "ISPF_SECURITY_STRICT_STARTUP" = "false"
    "ISPF_BOOTSTRAP_FIXTURES_ENABLED" = "false"
    "ISPF_LICENSE_ENFORCE" = "false"
    "ISPF_DRIVER_PACKS_DIR" = "/data/drivers"
}

function Start-Replica([string]$name, [string]$replicaId, [int]$port) {
    $dockerArgs = @("run", "-d", "--name", $name, "--hostname", $name, "--network", $Net,
        "-p", "${BindHost}:${port}:8080", "-v", "${Jar}:/app/ispf-server.jar:ro",
        "-v", "${VirtualPack}:/data/drivers/ispf-driver-virtual:ro")
    foreach ($k in $AaEnv.Keys) { $dockerArgs += @("-e", "$k=$($AaEnv[$k])") }
    $dockerArgs += @("-e", "ISPF_REPLICA_ID=$replicaId", $ImageJre, "java", "-jar", "/app/ispf-server.jar")
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

Write-Host "== start replica A alone (Flyway + first boot on the empty database) =="
Start-Replica $NameA "mysql-aa-a" $PortA
Wait-Healthy $NameA $PortA

Write-Host "== start replica B (joins the cluster) =="
Start-Replica $NameB "mysql-aa-b" $PortB
Wait-Healthy $NameB $PortB
exit 0
