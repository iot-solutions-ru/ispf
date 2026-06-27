# Direct VPS deploy: build locally, SCP to staging, apply on server.
# Does NOT use GitHub Releases. See .cursor/rules/vps-deploy.mdc
#
# Optional: -VerifyClickHouse runs deploy/vps-clickhouse-verify.sh after rollout.
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [string]$RemoteHost = "root@ispf.iot-solutions.ru",
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [switch]$SkipBuild,
    [switch]$SkipTests,
    [switch]$VerifyClickHouse
)

$ErrorActionPreference = "Stop"

$staging = "/opt/ispf/staging/$Version"
$jarOut = Join-Path $RepoRoot "packages\ispf-server\build\libs\ispf-server-$Version.jar"
$packsDir = Join-Path $RepoRoot "build\driver-packs"
$packsTar = Join-Path $RepoRoot "build\driver-packs.tar.gz"
$webRoot = Join-Path $RepoRoot "apps\web-console"
$distDir = Join-Path $webRoot "dist"
$zipPath = Join-Path $webRoot "web-console.zip"

function Invoke-Remote([string]$Command) {
    ssh -o BatchMode=yes $RemoteHost $Command
    if ($LASTEXITCODE -ne 0) { throw "Remote command failed: $Command" }
}

if (-not $SkipBuild) {
    Write-Host "==> Building server jar $Version"
    Push-Location $RepoRoot
    try {
        if ($SkipTests) {
            & .\gradlew ":packages:ispf-server:bootJar" "syncAllDriverPacks" "-Pversion=$Version"
        } else {
            & .\gradlew ":packages:ispf-server:test" ":packages:ispf-server:bootJar" "syncAllDriverPacks" "-Pversion=$Version"
        }
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }
    } finally {
        Pop-Location
    }

    if (-not (Test-Path $jarOut)) {
        throw "Missing jar: $jarOut"
    }

    Write-Host "==> Building web console"
    Push-Location $webRoot
    try {
        if (-not (Test-Path "node_modules")) {
            npm ci
            if ($LASTEXITCODE -ne 0) { throw "npm ci failed" }
        }
        npm run build
        if ($LASTEXITCODE -ne 0) { throw "npm run build failed" }
    } finally {
        Pop-Location
    }

    if (-not (Test-Path $distDir)) {
        throw "Missing dist: $distDir"
    }

    if (-not (Test-Path $packsDir)) {
        throw "Missing driver packs: $packsDir (run syncAllDriverPacks)"
    }

    Write-Host "==> Packaging driver-packs.tar.gz"
    if (Test-Path $packsTar) { Remove-Item $packsTar -Force }
    Push-Location $packsDir
    try {
        tar -c -z -f $packsTar *
        if ($LASTEXITCODE -ne 0) { throw "tar driver-packs failed" }
    } finally {
        Pop-Location
    }

    Write-Host "==> Packaging web-console.zip (tar, Unix paths)"
    Push-Location $distDir
    try {
        if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
        tar -a -c -f $zipPath *
        if ($LASTEXITCODE -ne 0) { throw "tar packaging failed" }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $jarOut)) { throw "Jar not found: $jarOut" }
if (-not (Test-Path $zipPath)) { throw "Zip not found: $zipPath" }
if (-not (Test-Path $packsTar)) { throw "Driver packs tar not found: $packsTar" }

Write-Host "==> Uploading to ${RemoteHost}:$staging"
Invoke-Remote "mkdir -p $staging"
scp -o BatchMode=yes $jarOut "${RemoteHost}:${staging}/ispf-server.jar"
if ($LASTEXITCODE -ne 0) { throw "scp jar failed" }
scp -o BatchMode=yes $zipPath "${RemoteHost}:${staging}/web-console.zip"
if ($LASTEXITCODE -ne 0) { throw "scp zip failed" }
scp -o BatchMode=yes $packsTar "${RemoteHost}:${staging}/driver-packs.tar.gz"
if ($LASTEXITCODE -ne 0) { throw "scp driver-packs failed" }

Write-Host "==> Ensuring ISPF_BOOTSTRAP_FIXTURES_ENABLED=false on VPS"
Invoke-Remote "grep -q '^ISPF_BOOTSTRAP_FIXTURES_ENABLED=' /opt/ispf/ispf-server.env 2>/dev/null && sed -i 's/^ISPF_BOOTSTRAP_FIXTURES_ENABLED=.*/ISPF_BOOTSTRAP_FIXTURES_ENABLED=false/' /opt/ispf/ispf-server.env || echo 'ISPF_BOOTSTRAP_FIXTURES_ENABLED=false' >> /opt/ispf/ispf-server.env"

Write-Host "==> Applying update on VPS"
Invoke-Remote "bash /opt/ispf/bin/apply-platform-update.sh $staging"

Write-Host "==> Verifying"
$info = Invoke-Remote "curl -sf http://127.0.0.1:8080/api/v1/info"
Write-Host $info
if ($info -notmatch "`"version`":`"$Version`"") {
    Write-Warning "Version mismatch in /api/v1/info (expected $Version)"
}

try {
    $public = curl.exe -sf "https://ispf.iot-solutions.ru/api/v1/info"
    Write-Host "Public: $public"
} catch {
    Write-Warning "Public HTTPS check skipped: $_"
}

Write-Host "Deploy complete: $Version -> $RemoteHost"

if ($VerifyClickHouse) {
    Write-Host "==> ClickHouse verification"
    $verifyScript = Join-Path $PSScriptRoot "vps-clickhouse-verify.sh"
    if (-not (Test-Path $verifyScript)) { throw "Missing $verifyScript" }
    scp -o BatchMode=yes $verifyScript "${RemoteHost}:/opt/ispf/vps-clickhouse-verify.sh"
    if ($LASTEXITCODE -ne 0) { throw "scp verify script failed" }
    Invoke-Remote "chmod +x /opt/ispf/vps-clickhouse-verify.sh && sed -i 's/\r$//' /opt/ispf/vps-clickhouse-verify.sh && bash /opt/ispf/vps-clickhouse-verify.sh $Version"
}
