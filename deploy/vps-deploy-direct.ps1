# Direct VPS deploy: build locally, SCP to staging, apply on server.
# Does NOT use GitHub Releases. See .cursor/rules/vps-deploy.mdc
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [string]$RemoteHost = "root@ispf.iot-solutions.ru",
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [switch]$SkipBuild,
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

$staging = "/opt/ispf/staging/$Version"
$jarOut = Join-Path $RepoRoot "packages\ispf-server\build\libs\ispf-server-$Version.jar"
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
            & .\gradlew ":packages:ispf-server:bootJar" "-Pversion=$Version"
        } else {
            & .\gradlew ":packages:ispf-server:test" ":packages:ispf-server:bootJar" "-Pversion=$Version"
        }
        if ($LASTEXITCODE -ne 0) { throw "Gradle bootJar failed" }
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

Write-Host "==> Uploading to ${RemoteHost}:$staging"
Invoke-Remote "mkdir -p $staging"
scp -o BatchMode=yes $jarOut "${RemoteHost}:${staging}/ispf-server.jar"
if ($LASTEXITCODE -ne 0) { throw "scp jar failed" }
scp -o BatchMode=yes $zipPath "${RemoteHost}:${staging}/web-console.zip"
if ($LASTEXITCODE -ne 0) { throw "scp zip failed" }

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
