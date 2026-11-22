# Shared helpers for the ADR-0080 Oracle B1 lab scripts (dot-sourced).

$ImageJre = "eclipse-temurin:25-jre-jammy"

function Resolve-LabBootJar([string]$RepoRoot, [string]$Jar) {
    if (-not $Jar) {
        $candidates = Get-ChildItem -Path (Join-Path $RepoRoot "packages\ispf-server\build\libs") `
            -Filter "ispf-server-*.jar" -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notlike "*-plain.jar" } |
            Sort-Object LastWriteTime -Descending
        if (-not $candidates) { throw "No bootJar under packages/ispf-server/build/libs (build first or pass -Jar)" }
        $Jar = $candidates[0].FullName
    }
    if (-not (Test-Path $Jar)) { throw "Jar not found: $Jar" }
    return $Jar
}

# ISPF does not ship ojdbc. The lab copy comes from: gradlew :packages:ispf-server:copyOracleLabJdbc
function Resolve-LabOjdbc([string]$RepoRoot, [string]$OjdbcJar) {
    if (-not $OjdbcJar) {
        $candidates = Get-ChildItem -Path (Join-Path $RepoRoot "packages\ispf-server\build\oracle-lab-jdbc") `
            -Filter "ojdbc*.jar" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending
        if (-not $candidates) {
            throw "No ojdbc JAR (run gradlew :packages:ispf-server:copyOracleLabJdbc or pass -OjdbcJar)"
        }
        $OjdbcJar = $candidates[0].FullName
    }
    if (-not (Test-Path $OjdbcJar)) { throw "ojdbc JAR not found: $OjdbcJar" }
    return $OjdbcJar
}

function Get-OracleJdbcUrl([string]$NameSql) {
    return "jdbc:oracle:thin:@//${NameSql}:1521/FREEPDB1"
}

function Get-OracleB1Env {
    return @{
        "ISPF_TENANT_DB_ROW_ISOLATION" = "false"
        "ISPF_TENANT_ISOLATION_MODE" = "logical"
        "ISPF_VARIABLE_HISTORY_ENABLED" = "false"
        "ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED" = "false"
        "ISPF_EVENT_JOURNAL_ENABLED" = "false"
        "ISPF_CLUSTER_ENABLED" = "false"
    }
}

# Runs one SQL*Plus script inside the Oracle container and returns its trimmed output.
function Invoke-OracleSql([string]$NameSql, [string]$User, [string]$Password, [string]$Sql) {
    $script = "SET HEADING OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 400 TRIMSPOOL ON VERIFY OFF`nSET DEFINE OFF`n$Sql`nEXIT`n"
    $tmp = [System.IO.Path]::GetTempFileName()
    [System.IO.File]::WriteAllText($tmp, $script, (New-Object System.Text.UTF8Encoding($false)))
    docker cp $tmp "${NameSql}:/tmp/ispf-lab.sql" 2>$null | Out-Null
    Remove-Item $tmp -ErrorAction SilentlyContinue
    $out = docker exec $NameSql bash -lc "sqlplus -S -L '$User/$Password@//localhost:1521/FREEPDB1' @/tmp/ispf-lab.sql" 2>&1
    return (($out | Where-Object { $_ -and $_.Trim() }) -join "`n").Trim()
}

# Starts ISPF from the bootJar with the driver on loader.path (PropertiesLauncher), like an operator would.
# -NoDriver starts it without the lib mount to prove the clear refusal.
function Start-OracleIspf {
    param(
        [string]$Name,
        [string]$Net,
        [string]$Jar,
        [string]$OjdbcJar,
        [string]$Password,
        [string]$Jdbc,
        [hashtable]$EnvExtra,
        [string]$Publish = "",
        [switch]$NoDriver
    )
    docker rm -f $Name 2>$null | Out-Null
    $envBase = [ordered]@{
        "SPRING_PROFILES_ACTIVE" = "local"
        "ISPF_METADATA_DB_KIND" = "oracle"
        "SPRING_DATASOURCE_URL" = $Jdbc
        "SPRING_DATASOURCE_USERNAME" = "ispf"
        "SPRING_DATASOURCE_PASSWORD" = $Password
        "SPRING_DATASOURCE_DRIVER_CLASS_NAME" = "oracle.jdbc.OracleDriver"
        "ISPF_NATS_ENABLED" = "false"
        "ISPF_REDIS_ENABLED" = "false"
        "ISPF_SECURITY_STRICT_STARTUP" = "false"
        "ISPF_BOOTSTRAP_FIXTURES_ENABLED" = "false"
        "ISPF_LICENSE_ENFORCE" = "false"
    }
    foreach ($k in $EnvExtra.Keys) { $envBase[$k] = $EnvExtra[$k] }
    $dockerArgs = @("run", "-d", "--name", $Name, "--network", $Net, "-v", "${Jar}:/app/ispf-server.jar:ro")
    if (-not $NoDriver) { $dockerArgs += @("-v", "${OjdbcJar}:/app/lib/ojdbc17.jar:ro") }
    if ($Publish) { $dockerArgs += @("-p", $Publish) }
    foreach ($k in $envBase.Keys) { $dockerArgs += @("-e", "$k=$($envBase[$k])") }
    $dockerArgs += @($ImageJre, "java", "-Dloader.path=/app/lib", "-cp", "/app/ispf-server.jar",
        "org.springframework.boot.loader.launch.PropertiesLauncher")
    docker @dockerArgs | Out-Null
    return $LASTEXITCODE -eq 0
}
