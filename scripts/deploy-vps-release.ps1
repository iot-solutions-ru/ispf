# Build release, publish to GitHub, deploy to VPS with LLM settings.
# WARNING: outdated -- the referenced deploy/vps-*.sh helpers no longer exist
# in this repository; the remote deploy step fails until they are restored.
param(
    [string]$Version = "0.7.6",
    [string]$VpsHost = "root@92.63.104.121",
    [string]$AiApiKey = $env:ISPF_AI_API_KEY
)

$ErrorActionPreference = "Stop"
if ($Version -notmatch '^[0-9A-Za-z][0-9A-Za-z._-]*$') {
    throw "Unsafe version string: $Version"
}
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $AiApiKey) {
    $envFile = Join-Path $root ".env"
    if (Test-Path $envFile) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^ISPF_AI_API_KEY=(.+)$') { $AiApiKey = $Matches[1] }
        }
    }
}
if (-not $AiApiKey) {
    throw "Set ISPF_AI_API_KEY or add it to .env"
}

Write-Host "=== Build server + web console (v$Version) ==="
.\gradlew :packages:ispf-server:bootJar -x test --no-daemon "-Pversion=$Version"
Push-Location apps/web-console
npm ci
npm run build
Pop-Location
Push-Location apps/web-console/dist
if (Test-Path ..\web-console.zip) { Remove-Item ..\web-console.zip -Force }
python -c "import zipfile; from pathlib import Path; dist=Path('.'); out=Path('..')/'web-console.zip';
import zipfile as zf
with zf.ZipFile(out,'w',zf.ZIP_DEFLATED) as z:
 [z.write(f, f.relative_to(dist).as_posix()) for f in dist.rglob('*') if f.is_file()]"
Pop-Location

$jar = Get-ChildItem packages/ispf-server/build/libs/ispf-server-*.jar | Where-Object { $_.Name -notmatch 'plain' } | Select-Object -First 1
Copy-Item $jar.FullName ispf-server.jar -Force

Write-Host "=== Git tag + GitHub release v$Version ==="
git add -A
$status = git status --porcelain
if ($status) {
    git commit -m "Release v${Version}: AI Studio agent chat fixes and LLM key detection."
}
git tag -f "v$Version"
git push origin main
git push -f origin "v$Version"
gh release delete "v$Version" -y 2>$null | Out-Null
gh release create "v$Version" --title "ISPF v$Version" --notes @"
## Summary
- AI Studio: fix agent chat send/session race, agent API availability banner, clearer 403/401 errors
- LLM: provider status reports missing API key; Settings tab hints for .env and run-local-with-ai.ps1

## Deploy
VPS: ispf.example.invalid
"@ ispf-server.jar apps/web-console/web-console.zip

Write-Host "=== Deploy to $VpsHost ==="
scp deploy/vps-configure-ai-env.sh deploy/vps-apply-release.sh deploy/vps-deploy-release-with-ai.sh "${VpsHost}:/tmp/"

# Pass the API key via a temp env file (scp + source + rm) instead of the ssh
# command line, so it stays out of remote ps/history and cannot break quoting.
$escapedKey = $AiApiKey -replace "'", "'\''"
$envFile = New-TemporaryFile
try {
    [IO.File]::WriteAllText($envFile.FullName, "ISPF_AI_API_KEY='$escapedKey'`n")
    scp $envFile.FullName "${VpsHost}:.ispf-ai-env"
    ssh $VpsHost "sed -i 's/\r$//' /tmp/vps-configure-ai-env.sh /tmp/vps-apply-release.sh /tmp/vps-deploy-release-with-ai.sh; chmod 600 ~/.ispf-ai-env; set -a; . ~/.ispf-ai-env; set +a; rm -f ~/.ispf-ai-env; bash /tmp/vps-deploy-release-with-ai.sh '$Version'"
} finally {
    Remove-Item $envFile -Force -ErrorAction SilentlyContinue
}

Write-Host "=== Done. Check https://ispf.example.invalid/api/v1/ai/provider ==="
