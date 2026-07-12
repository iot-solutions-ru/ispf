# Publish ISPF energy analytics pack to IoT Solutions marketplace VPS.
# Usage: .\deploy\tools\publish-marketplace-analytics-energy-pack.ps1

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$DemoDir = Join-Path $RepoRoot "examples\marketplace-analytics-energy-pack-demo"
$ZipPath = Join-Path $DemoDir "analytics-pack-energy-1.0.0.zip"
$Remote = "deploy-user@marketplace.example.invalid"
$RemoteApp = "/opt/ispf-marketplace"
$RemoteScript = Join-Path $PSScriptRoot "publish-marketplace-analytics-energy-pack-remote.sh"

Write-Host "Building energy analytics pack zip..."
Push-Location $RepoRoot
try {
    .\gradlew :packages:ispf-analytics-core-ext:assembleAnalyticsMarketplaceZip -q
} finally {
    Pop-Location
}

if (-not (Test-Path $ZipPath)) {
    throw "Missing zip: $ZipPath"
}

Write-Host "Uploading artifacts..."
scp $ZipPath "${Remote}:${RemoteApp}/seed/ispf-analytics-energy-pack__1.0.0.zip"
scp $RemoteScript "${Remote}:/tmp/publish-analytics-energy-pack.sh"

Write-Host "Seeding energy pack on marketplace VPS..."
ssh -o BatchMode=yes $Remote "sed -i 's/\r$//' /tmp/publish-analytics-energy-pack.sh && chmod +x /tmp/publish-analytics-energy-pack.sh && bash /tmp/publish-analytics-energy-pack.sh"

Write-Host "Done. Verify: https://marketplace.ispf.ai/api/v1/catalog/ispf-analytics-energy-pack"
