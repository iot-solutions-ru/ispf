# Publish ISPF analytics KPI demo pack to IoT Solutions marketplace VPS.
# Usage: .\deploy\tools\publish-marketplace-analytics-pack.ps1

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$DemoDir = Join-Path $RepoRoot "examples\marketplace-analytics-pack-demo"
$ZipPath = Join-Path $DemoDir "analytics-pack-demo-1.0.0.zip"
$Remote = "root@ispf-marketplace.iot-solutions.ru"
$RemoteApp = "/opt/ispf-marketplace"
$RemoteScript = Join-Path $PSScriptRoot "publish-marketplace-analytics-pack-remote.sh"

Write-Host "Building analytics pack zip..."
Push-Location $RepoRoot
try {
    .\gradlew :packages:ispf-analytics-marketplace-demo:assembleAnalyticsMarketplaceZip -q
} finally {
    Pop-Location
}

if (-not (Test-Path $ZipPath)) {
    throw "Missing zip: $ZipPath"
}

Write-Host "Uploading artifacts..."
scp $ZipPath "${Remote}:${RemoteApp}/seed/ispf-analytics-kpi-demo__1.0.0.zip"
scp $RemoteScript "${Remote}:/tmp/publish-analytics-pack.sh"

Write-Host "Applying patches and seeding on marketplace VPS..."
ssh -o BatchMode=yes $Remote "sed -i 's/\r$//' /tmp/publish-analytics-pack.sh && chmod +x /tmp/publish-analytics-pack.sh && bash /tmp/publish-analytics-pack.sh"

Write-Host "Done. Verify: https://marketplace.ispf.ai/api/v1/catalog/ispf-analytics-kpi-demo"
