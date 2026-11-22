# Local B1 lab stand: Oracle Database Free + branch bootJar on ports 11521 / 18092 (ADR-0080).
# The Oracle JDBC driver is mounted from outside the JAR (loader.path), the operator path ISPF documents.
param(
    [string]$Jar = "",
    [string]$OjdbcJar = "",
    [string]$Password = $env:ORACLE_LAB_PASSWORD,
    [string]$Net = "ispf-oracle-b1-net",
    [string]$NameSql = "ispf-oracle-b1-stand",
    [string]$NameApp = "ispf-oracle-b1-app",
    [string]$BindHost = $(if ($env:BIND_HOST) { $env:BIND_HOST } else { "127.0.0.1" }),
    [string]$ImageSql = $(if ($env:ORACLE_IMAGE) { $env:ORACLE_IMAGE } else { "gvenzl/oracle-free:23-slim-faststart" })
)

$ErrorActionPreference = "Continue"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
. (Join-Path $ScriptDir "lab-common.ps1")

if (-not $Password) {
    throw "Set ORACLE_LAB_PASSWORD (lab throwaway; SYSTEM and the ispf user) or pass -Password"
}
$Jar = Resolve-LabBootJar $RepoRoot $Jar
$OjdbcJar = Resolve-LabOjdbc $RepoRoot $OjdbcJar
Write-Host "jar=$Jar"
Write-Host "ojdbc=$OjdbcJar"

docker network inspect $Net 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    docker network create $Net | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "failed to create docker network $Net" }
}

$running = docker ps --format "{{.Names}}" | Select-String -Pattern "^$NameSql$"
if (-not $running) {
    docker rm -f $NameSql 2>$null | Out-Null
    # APP_USER creates the ispf schema user in FREEPDB1 (an Oracle schema is a user).
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

Write-Host "== engine =="
Invoke-OracleSql $NameSql "system" $Password "SELECT banner_full FROM v`$version;"
Invoke-OracleSql $NameSql "system" $Password "SELECT parameter || '=' || value FROM nls_database_parameters WHERE parameter IN ('NLS_CHARACTERSET', 'NLS_LENGTH_SEMANTICS');"
Invoke-OracleSql $NameSql "system" $Password "SELECT name || '=' || value FROM v`$parameter WHERE name IN ('max_string_size', 'compatible', 'db_block_size');"

docker rm -f $NameApp 2>$null | Out-Null
Write-Host "== start ISPF =="
if (-not (Start-OracleIspf -Name $NameApp -Net $Net -Jar $Jar -OjdbcJar $OjdbcJar -Password $Password `
        -Jdbc (Get-OracleJdbcUrl $NameSql) -EnvExtra (Get-OracleB1Env) -Publish "${BindHost}:18092:8080")) {
    throw "docker run failed for $NameApp"
}

Write-Host "== wait health =="
for ($i = 1; $i -le 120; $i++) {
    try {
        $h = Invoke-RestMethod -Uri "http://127.0.0.1:18092/actuator/health" -TimeoutSec 3
        if ($h.status -eq "UP") {
            Write-Host "ispf_up attempt=$i"
            Invoke-RestMethod -Uri "http://127.0.0.1:18092/api/v1/info" | ConvertTo-Json -Compress
            exit 0
        }
    } catch {}
    $alive = docker ps --format "{{.Names}}" | Select-String -Pattern "^$NameApp$"
    if (-not $alive) {
        docker logs $NameApp 2>&1 | Select-Object -Last 80
        throw "ISPF container exited"
    }
    Start-Sleep 5
}
docker logs $NameApp 2>&1 | Select-Object -Last 80
throw "ISPF not healthy"
