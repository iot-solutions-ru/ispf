# Nightly backend slice (see .github/workflows/ci-nightly.yml job backend-full). Issue #65.
$ErrorActionPreference = "Stop"
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $Root

$env:GRADLE_OPTS = "-Dorg.gradle.workers.max=1 -Dispf.driver.packs=dev -Dispf.test.skipLoad=true"

Write-Host "==> Backend module batch (nightly tier)"
& .\gradlew testNightlyBackend --no-daemon

Write-Host "==> Scale gate — list_devices p99"
if (-not $env:ISPF_LOAD_P99_CEILING_MS) { $env:ISPF_LOAD_P99_CEILING_MS = "5000" }
& .\gradlew :packages:ispf-server:test `
  --tests com.ispf.server.api.ListDevicesLoadTest `
  --no-daemon

Write-Host "==> Scale gate — events fire/list p99"
& .\gradlew :packages:ispf-server:test `
  --tests com.ispf.server.api.EventFireLoadTest `
  --no-daemon

Write-Host "==> Federation integration gate (S27)"
$env:CI = "true"
& .\gradlew :packages:ispf-server:test `
  --tests com.ispf.server.federation.FederationChaosIntegrationTest `
  --tests com.ispf.server.federation.FederationTunnelIntegrationTest `
  --tests com.ispf.server.federation.FederationStoreForwardIntegrationTest `
  --tests com.ispf.server.federation.FederationApiTest `
  --no-daemon

Write-Host "nightly backend OK"
