# Local B2 (single-engine) lab stand: MySQL 8.4 Community + branch bootJar on :18094 (ADR-0079 § Amendment 5).
# Reuses the B1 MySQL container (:13306) and creates a dedicated ispf_b2 database on it.
param(
    [string]$Jar = "",
    [string]$Password = $env:MYSQL_LAB_PASSWORD,
    [string]$Net = "ispf-mysql-b1-net",
    [string]$NameSql = "ispf-mysql-b1-stand",
    [string]$NameApp = "ispf-mysql-b2-app",
    [string]$DbName = "ispf_b2",
    [int]$AppPort = 18094,
    [string]$BindHost = $(if ($env:BIND_HOST) { $env:BIND_HOST } else { "127.0.0.1" }),
    [string]$ImageSql = $(if ($env:MYSQL_IMAGE) { $env:MYSQL_IMAGE } else { "mysql:8.4" })
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
Write-Host "jar=$Jar"

$ImageJre = "eclipse-temurin:25-jre-jammy"

docker network inspect $Net 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    docker network create $Net | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "failed to create docker network $Net" }
}

$running = docker ps --format "{{.Names}}" | Select-String -Pattern "^$NameSql$"
if (-not $running) {
    docker rm -f $NameSql 2>$null | Out-Null
    # Same server flags as the B1 stand: READ-COMMITTED, utf8mb4_0900_bin.
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

Write-Host "== prepare $DbName (dedicated B2 database on this lab server) =="
# Stop the previous app first so DROP does not wait on its connections.
docker rm -f $NameApp 2>$null | Out-Null
Invoke-Sql "DROP DATABASE IF EXISTS $DbName"
Invoke-Sql "CREATE DATABASE $DbName CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin"
Invoke-Sql "GRANT ALL PRIVILEGES ON $DbName.* TO 'ispf'@'%'"
# Flyway reads this to reset user variables between migrations; without it Flyway warns and skips.
Invoke-Sql "GRANT SELECT ON performance_schema.user_variables_by_thread TO 'ispf'@'%'"
Invoke-Sql "SELECT schema_name, default_character_set_name, default_collation_name FROM information_schema.schemata WHERE schema_name='$DbName'"
Write-Host "== engine =="
Invoke-Sql "SELECT VERSION() AS version, @@version_comment AS edition, @@transaction_isolation AS isolation, @@collation_server AS collation_server"

# Lab only: allowPublicKeyRetrieval for caching_sha2_password without TLS — production uses sslMode=verify-full.
$Jdbc = "jdbc:mysql://${NameSql}:3306/${DbName}?permitMysqlScheme=true&allowPublicKeyRetrieval=true"

Write-Host "== start ISPF single-engine (B2) =="
docker run -d --name $NameApp --network $Net `
    -p "${BindHost}:${AppPort}:8080" `
    -v "${Jar}:/app/ispf-server.jar:ro" `
    -e "SPRING_PROFILES_ACTIVE=local" `
    -e "SPRING_JPA_HIBERNATE_DDL_AUTO=validate" `
    -e "ISPF_METADATA_DB_KIND=mysql" `
    -e "SPRING_DATASOURCE_URL=$Jdbc" `
    -e "SPRING_DATASOURCE_USERNAME=ispf" `
    -e "SPRING_DATASOURCE_PASSWORD=$Password" `
    -e "SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.mariadb.jdbc.Driver" `
    -e "ISPF_MYSQL_SINGLE_ENGINE=true" `
    -e "ISPF_HISTORIAN_DEPLOY_PROFILE=mysql-single-engine" `
    -e "ISPF_HISTORIAN_TIER_WARM_ENABLED=false" `
    -e "ISPF_HISTORIAN_COLD_ARCHIVE_ENABLED=false" `
    -e "ISPF_TENANT_DB_ROW_ISOLATION=false" `
    -e "ISPF_TENANT_ISOLATION_MODE=logical" `
    -e "ISPF_VARIABLE_HISTORY_ENABLED=true" `
    -e "ISPF_VARIABLE_HISTORY_STORE=jdbc" `
    -e "ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=false" `
    -e "ISPF_VARIABLE_HISTORY_SPOOL_ENABLED=false" `
    -e "ISPF_EVENT_JOURNAL_ENABLED=true" `
    -e "ISPF_EVENT_JOURNAL_STORE=jdbc" `
    -e "ISPF_CLUSTER_ENABLED=false" `
    -e "ISPF_NATS_ENABLED=false" `
    -e "ISPF_REDIS_ENABLED=false" `
    -e "ISPF_SECURITY_STRICT_STARTUP=false" `
    -e "ISPF_BOOTSTRAP_FIXTURES_ENABLED=false" `
    -e "ISPF_LICENSE_ENFORCE=false" `
    $ImageJre `
    java -jar /app/ispf-server.jar | Out-Null
if ($LASTEXITCODE -ne 0) { throw "failed to start $NameApp (is :$AppPort free?)" }

Write-Host "== wait health :$AppPort =="
for ($i = 1; $i -le 120; $i++) {
    try {
        $h = Invoke-RestMethod -Uri "http://127.0.0.1:${AppPort}/actuator/health" -TimeoutSec 3
        if ($h.status -eq "UP") {
            Write-Host "ispf_up attempt=$i"
            Invoke-RestMethod -Uri "http://127.0.0.1:${AppPort}/api/v1/info" | ConvertTo-Json -Compress
            exit 0
        }
    } catch {}
    $alive = docker ps --format "{{.Names}}" | Select-String -Pattern "^$NameApp$"
    if (-not $alive) {
        docker logs $NameApp 2>&1 | Select-Object -Last 80
        throw "ISPF B2 container exited"
    }
    Start-Sleep 5
}
docker logs $NameApp 2>&1 | Select-Object -Last 80
throw "ISPF B2 not healthy"
