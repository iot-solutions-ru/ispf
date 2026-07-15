# Publish full marketplace catalog to IoT Solutions marketplace VPS (SSH key auth).
# Usage: .\deploy\tools\publish-marketplace-catalog.ps1
# Override host: $env:ISPF_MARKETPLACE_SSH = "ispf-marketplace"

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$CatalogDir = Join-Path $RepoRoot "examples\marketplace-catalog"
# Prefer local SSH config Host alias (key auth). Placeholder kept for anonymized clones.
$Remote = if ($env:ISPF_MARKETPLACE_SSH) { $env:ISPF_MARKETPLACE_SSH } else { "ispf-marketplace" }
$RemoteApp = "/opt/ispf-marketplace"
$RemoteSeed = "$RemoteApp/seed/catalog"
$PatchScript = Join-Path $PSScriptRoot "patch-marketplace-free-download-signing.sh"
$RemoteScript = Join-Path $PSScriptRoot "publish-marketplace-catalog-remote.sh"

if (-not (Test-Path $CatalogDir)) {
    throw "Missing catalog: $CatalogDir"
}

Write-Host "Uploading marketplace catalog ($CatalogDir) -> $Remote ..."
ssh -o BatchMode=yes $Remote "mkdir -p $RemoteSeed"
scp -r "$CatalogDir\*" "${Remote}:${RemoteSeed}/"
scp $PatchScript "${Remote}:/tmp/patch-marketplace-free-download-signing.sh"
scp $RemoteScript "${Remote}:/tmp/publish-marketplace-catalog.sh"

Write-Host "Patching download signing (noop if already present) + seeding catalog..."
ssh -o BatchMode=yes $Remote "sed -i 's/\r$//' /tmp/patch-marketplace-free-download-signing.sh /tmp/publish-marketplace-catalog.sh && chmod +x /tmp/patch-marketplace-free-download-signing.sh /tmp/publish-marketplace-catalog.sh && bash /tmp/publish-marketplace-catalog.sh $RemoteSeed"

Write-Host "Done. Verify: https://marketplace.ispf.ai/api/v1/catalog"
