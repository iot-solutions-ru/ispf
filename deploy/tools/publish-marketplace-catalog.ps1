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
$UiPackPatchScript = Join-Path $PSScriptRoot "patch-marketplace-ui-pack-download-remote.sh"
$RemoteScript = Join-Path $PSScriptRoot "publish-marketplace-catalog-remote.sh"

if (-not (Test-Path $CatalogDir)) {
    throw "Missing catalog: $CatalogDir"
}
if (-not (Test-Path $UiPackPatchScript)) {
    throw "Missing ui-pack patch: $UiPackPatchScript"
}

Write-Host "Uploading marketplace catalog ($CatalogDir) -> $Remote ..."
ssh -o BatchMode=yes $Remote "mkdir -p $RemoteSeed"
# Upload each listing entry explicitly. Do NOT use `scp -r "$CatalogDir\*"`:
# PowerShell passes the unexpanded glob to scp.exe, whose own wildcard handling
# silently skips directories it cannot expand — new listings then never arrive.
Get-ChildItem -LiteralPath $CatalogDir -Directory | ForEach-Object {
    scp -r $_.FullName "${Remote}:${RemoteSeed}/"
}
Get-ChildItem -LiteralPath $CatalogDir -File | ForEach-Object {
    scp $_.FullName "${Remote}:${RemoteSeed}/"
}

# Upload helper scripts into a private mktemp dir (shared /tmp with fixed names is hijackable).
$RemoteWork = (ssh -o BatchMode=yes $Remote "mktemp -d /tmp/ispf-marketplace-publish.XXXXXXXX").Trim()
if (-not $RemoteWork) { throw "Failed to create remote work directory on $Remote" }
try {
    scp $PatchScript "${Remote}:${RemoteWork}/patch-marketplace-free-download-signing.sh"
    scp $UiPackPatchScript "${Remote}:${RemoteWork}/patch-marketplace-ui-pack-download-remote.sh"
    scp $RemoteScript "${Remote}:${RemoteWork}/publish-marketplace-catalog.sh"

    Write-Host "Patching download signing + ui-pack ZIP support, then seeding catalog (incl. oil-control ui-pack)..."
    ssh -o BatchMode=yes $Remote @"
sed -i 's/\r$//' \
  $RemoteWork/patch-marketplace-free-download-signing.sh \
  $RemoteWork/patch-marketplace-ui-pack-download-remote.sh \
  $RemoteWork/publish-marketplace-catalog.sh && \
chmod +x \
  $RemoteWork/patch-marketplace-free-download-signing.sh \
  $RemoteWork/patch-marketplace-ui-pack-download-remote.sh \
  $RemoteWork/publish-marketplace-catalog.sh && \
bash $RemoteWork/publish-marketplace-catalog.sh $RemoteSeed
"@
}
finally {
    ssh -o BatchMode=yes $Remote "rm -rf '$RemoteWork'" | Out-Null
}

Write-Host "Done. Verify: https://marketplace.ispf.ai/api/v1/catalog"
Write-Host "Expect oil-control (uiPackSlug=oil-control-ui) and oil-control-ui (artifactKind=ui-pack)."
