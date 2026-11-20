# AMD64 local Gate B (B2-core) stand: SQL Server 2022 Evaluation + single-engine bootJar on :18082.
# Does not claim 0037 Prod Yes (B2). Dedicated instance in lab = this container (no co-tenant OLTP).
param(
    [string]$Jar = "",
    [string]$SaPassword = $env:MSSQL_SA_PASSWORD,
    [string]$Net = "ispf-mssql-b1-net",
    [string]$NameSql = "ispf-mssql-b1-stand",
    [string]$NameApp = "ispf-mssql-b2-app",
    [string]$DbName = "ispf_b2",
    [string]$BindHost = $(if ($env:BIND_HOST) { $env:BIND_HOST } else { "127.0.0.1" }),
    [int]$AppPort = 18082,
    [string]$MssqlPid = $(if ($env:MSSQL_PID) { $env:MSSQL_PID } else { "Evaluation" })
)

$ErrorActionPreference = "Continue"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..\..")

if (-not $SaPassword) {
    throw "Set MSSQL_SA_PASSWORD (lab throwaway) or pass -SaPassword"
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

$ImageSql = "mcr.microsoft.com/mssql/server:2022-latest"
$ImageJre = "eclipse-temurin:25-jre-jammy"

docker network inspect $Net 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    docker network create $Net | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "failed to create docker network $Net" }
}

$running = docker ps --format "{{.Names}}" | Select-String -Pattern "^$NameSql$"
if (-not $running) {
    docker rm -f $NameSql 2>$null | Out-Null
    $cid = docker run -d --name $NameSql --hostname $NameSql --network $Net `
        -e "ACCEPT_EULA=Y" `
        -e "MSSQL_SA_PASSWORD=$SaPassword" `
        -e "MSSQL_PID=$MssqlPid" `
        -p "${BindHost}:14333:1433" `
        $ImageSql
    if (-not $cid) { throw "failed to start MSSQL container" }
    Write-Host "mssql_cid=$cid"
}

Write-Host "== wait MSSQL =="
$ready = $false
for ($i = 1; $i -le 90; $i++) {
    docker exec $NameSql /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P $SaPassword -C -Q "SELECT 1" 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "sql_ready attempt=$i"
        $ready = $true
        break
    }
    Start-Sleep 5
}
if (-not $ready) { throw "MSSQL not ready" }

function Invoke-Sql([string]$q) {
    docker exec $NameSql /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P $SaPassword -C -Q $q
    if ($LASTEXITCODE -ne 0) { throw "sql failed: $q" }
}

Write-Host "== prepare $DbName DB (dedicated B2 DB on this lab instance) =="
# Stop the previous app first so DROP is not deadlocked against open connections.
docker rm -f $NameApp 2>$null | Out-Null
Start-Sleep 2
$dropOk = $false
for ($d = 1; $d -le 5; $d++) {
    docker exec $NameSql /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P $SaPassword -C -Q `
        "IF DB_ID(N'$DbName') IS NOT NULL BEGIN ALTER DATABASE [$DbName] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$DbName]; END" 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $dropOk = $true; break }
    Write-Host "drop retry $d"
    Start-Sleep 3
}
if (-not $dropOk) { throw "failed to drop $DbName" }
Invoke-Sql "CREATE DATABASE [$DbName] COLLATE Latin1_General_CS_AS"
Invoke-Sql "ALTER DATABASE [$DbName] SET ALLOW_SNAPSHOT_ISOLATION ON"
Invoke-Sql "ALTER DATABASE [$DbName] SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE"
Invoke-Sql "SELECT name, collation_name, is_read_committed_snapshot_on FROM sys.databases WHERE name=N'$DbName'"
Write-Host "== edition =="
Invoke-Sql "SELECT CAST(SERVERPROPERTY('Edition') AS nvarchar(128)) AS edition, CAST(SERVERPROPERTY('ProductVersion') AS nvarchar(64)) AS product_version"

$Jdbc = "jdbc:sqlserver://${NameSql}:1433;databaseName=${DbName};encrypt=false;trustServerCertificate=true"

docker rm -f $NameApp 2>$null | Out-Null
Write-Host "== start ISPF single-engine =="
docker run -d --name $NameApp --network $Net `
    -p "${BindHost}:${AppPort}:8080" `
    -v "${Jar}:/app/ispf-server.jar:ro" `
    -e "SPRING_PROFILES_ACTIVE=local" `
    -e "ISPF_METADATA_DB_KIND=mssql" `
    -e "ISPF_MSSQL_SINGLE_ENGINE=true" `
    -e "ISPF_HISTORIAN_DEPLOY_PROFILE=mssql-single-engine" `
    -e "ISPF_HISTORIAN_TIER_WARM_ENABLED=false" `
    -e "ISPF_HISTORIAN_COLD_ARCHIVE_ENABLED=false" `
    -e "SPRING_DATASOURCE_URL=$Jdbc" `
    -e "SPRING_DATASOURCE_USERNAME=sa" `
    -e "SPRING_DATASOURCE_PASSWORD=$SaPassword" `
    -e "SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.microsoft.sqlserver.jdbc.SQLServerDriver" `
    -e "ISPF_TENANT_DB_ROW_ISOLATION=false" `
    -e "ISPF_TENANT_ISOLATION_MODE=logical" `
    -e "ISPF_VARIABLE_HISTORY_ENABLED=true" `
    -e "ISPF_VARIABLE_HISTORY_STORE=jdbc" `
    -e "ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=false" `
    -e "ISPF_VARIABLE_HISTORY_SPOOL_ENABLED=false" `
    -e "ISPF_MSSQL_JDBC_BUFFER=false" `
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
        Write-Host "app exited"
        docker logs $NameApp --tail 80
        throw "B2 app exited"
    }
    Start-Sleep 3
}
docker logs $NameApp --tail 80
throw "health timeout"
