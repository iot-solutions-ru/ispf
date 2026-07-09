# PR-fast CI equivalent (see .github/workflows/ci.yml). Issue #65 fast pre-push path.
$ErrorActionPreference = "Stop"
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $Root

Write-Host "==> Backend (pr-fast modules, skip load, dev driver packs)"
& .\gradlew `
  :packages:ispf-core:test `
  :packages:ispf-expression:test `
  :packages:ispf-plugin-blueprint:test `
  :packages:ispf-plugin-workflow:test `
  :packages:ispf-server:test `
  --no-daemon `
  -Dorg.gradle.workers.max=1 `
  -Dispf.test.skipLoad=true `
  -Dispf.driver.packs=dev

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
  --no-daemon `
  -Dorg.gradle.workers.max=1 `
  -Dispf.test.skipLoad=true `
  -Dispf.driver.packs=dev

Write-Host "pr-fast OK"
