# Deploy Anima_front operator UI to ISPF VPS (static SPA).
# Usage:
#   .\examples\mes-printing-contour\deploy-anima-front.ps1
#   .\examples\mes-printing-contour\deploy-anima-front.ps1 -BasePath /operator-printing/

param(
    [string]$AnimaFrontDir = "$env:USERPROFILE\Downloads\Anima_front",
    [string]$RemoteHost = "root@ispf.iot-solutions.ru",
    [string]$RemoteDir = "/opt/ispf/operator-printing",
    [string]$ApiBase = "https://ispf.iot-solutions.ru",
    [string]$BasePath = "/operator-printing/",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $AnimaFrontDir)) {
    throw "Anima_front not found: $AnimaFrontDir"
}

$envFile = Join-Path $AnimaFrontDir ".env.production.local"
@"
VITE_API_GATEWAY=ispf
VITE_API_BASE_URL=$ApiBase
VITE_ISPF_HUB_PATH=root.platform.devices.printing-contour-hub
VITE_BASE_PATH=$BasePath
"@ | Set-Content -Encoding utf8 $envFile

Push-Location $AnimaFrontDir
try {
    if (-not $SkipBuild) {
        if (-not (Test-Path "node_modules")) {
            npm ci
        }
        npm run build
    }

    $dist = Join-Path $AnimaFrontDir "dist"
    if (-not (Test-Path $dist)) {
        throw "dist missing - run build first"
    }

    $archive = Join-Path $env:TEMP "anima-operator-printing.zip"
    if (Test-Path $archive) { Remove-Item $archive -Force }

    Push-Location $dist
    try {
        tar -a -c -f $archive *
    } finally {
        Pop-Location
    }

    ssh $RemoteHost "mkdir -p $RemoteDir"
    scp $archive "${RemoteHost}:${RemoteDir}/dist.zip"
    $remoteCmd = "cd $RemoteDir; rm -rf html; mkdir -p html; unzip -o dist.zip -d html; rm -f dist.zip"
    ssh $RemoteHost $remoteCmd

    Write-Host "Deployed to ${RemoteHost}:${RemoteDir}/html (base: $BasePath)"
    Write-Host "Configure nginx location $BasePath pointing to $RemoteDir/html"
} finally {
    Pop-Location
}
