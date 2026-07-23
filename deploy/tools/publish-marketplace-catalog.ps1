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

# Upload helper scripts into a private mktemp dir (shared /tmp with fixed names is hijackable).
$RemoteWork = (ssh -o BatchMode=yes $Remote "mktemp -d /tmp/ispf-marketplace-publish.XXXXXXXX").Trim()
if (-not $RemoteWork) { throw "Failed to create remote work directory on $Remote" }
try {
    scp $PatchScript "${Remote}:${RemoteWork}/patch-marketplace-free-download-signing.sh"
    scp $RemoteScript "${Remote}:${RemoteWork}/publish-marketplace-catalog.sh"

    Write-Host "Patching download signing (noop if already present) + seeding catalog..."
    ssh -o BatchMode=yes $Remote "sed -i 's/\r$//' $RemoteWork/patch-marketplace-free-download-signing.sh $RemoteWork/publish-marketplace-catalog.sh && chmod +x $RemoteWork/patch-marketplace-free-download-signing.sh $RemoteWork/publish-marketplace-catalog.sh && bash $RemoteWork/publish-marketplace-catalog.sh $RemoteSeed"
}
finally {
    ssh -o BatchMode=yes $Remote "rm -rf '$RemoteWork'" | Out-Null
}

Write-Host "Done. Verify: https://marketplace.ispf.ai/api/v1/catalog"
