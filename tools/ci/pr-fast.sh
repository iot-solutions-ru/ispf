#!/usr/bin/env bash
# PR-fast CI equivalent (see .github/workflows/ci.yml). Issue #65 fast pre-push path.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "==> Backend (pr-fast modules, skip load/federation, dev driver packs)"
./gradlew testPrFast \
  --no-daemon \
  -Dorg.gradle.workers.max=1 \
  -Dispf.test.skipLoad=true \
  -Dispf.test.skipFederation=true \
  -Dispf.driver.packs=dev

echo "==> Web console (unit, i18n, build)"
(
  cd apps/web-console
  npm ci
  npm test
  npm run i18n:check
  npm run build
)

echo "==> Agent regression schema gate"
./gradlew \
  :packages:ispf-server:test \
  --tests com.ispf.server.ai.agent.AgentRegressionCiTest \
  --no-daemon \
  -Dorg.gradle.workers.max=1 \
  -Dispf.test.skipLoad=true \
  -Dispf.test.skipFederation=true \
  -Dispf.driver.packs=dev

echo "pr-fast OK"
