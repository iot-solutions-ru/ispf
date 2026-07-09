# PR-fast CI equivalent (see .github/workflows/ci.yml). Issue #65 fast pre-push path.
$ErrorActionPreference = "Stop"
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $Root

Write-Host "==> Backend (pr-fast modules, skip load/federation, dev driver packs)"
$env:GRADLE_OPTS = "-Dorg.gradle.workers.max=1 -Dispf.test.skipLoad=true -Dispf.test.skipFederation=true -Dispf.driver.packs=dev"
& .\gradlew testPrFast --no-daemon

Write-Host "==> Web console (unit, i18n, build)"
Push-Location apps/web-console
npm ci
npm test
npm run i18n:check
npm run build
Pop-Location

Write-Host "==> Agent regression schema gate"
& .\gradlew `
  :packages:ispf-server:test `
  --tests com.ispf.server.ai.agent.AgentRegressionCiTest `
  --no-daemon

Write-Host "pr-fast OK"
