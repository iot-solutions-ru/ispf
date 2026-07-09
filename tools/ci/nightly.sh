#!/usr/bin/env bash
# Nightly backend slice (see .github/workflows/ci-nightly.yml job backend-full). Issue #65.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

GRADLE_COMMON=(
  --no-daemon
  -Dorg.gradle.workers.max=1
  -Dispf.driver.packs=dev
)

echo "==> Backend module batch (skip load + federation in pr-fast; nightly includes federation)"
./gradlew testNightlyBackend \
  "${GRADLE_COMMON[@]}" \
  -Dispf.test.skipLoad=true

echo "==> Scale gate — list_devices p99"
ISPF_LOAD_P99_CEILING_MS="${ISPF_LOAD_P99_CEILING_MS:-5000}" \
  ./gradlew \
  :packages:ispf-server:test \
  --tests com.ispf.server.api.ListDevicesLoadTest \
  "${GRADLE_COMMON[@]}"

echo "==> Scale gate — events fire/list p99"
ISPF_LOAD_P99_CEILING_MS="${ISPF_LOAD_P99_CEILING_MS:-5000}" \
  ./gradlew \
  :packages:ispf-server:test \
  --tests com.ispf.server.api.EventFireLoadTest \
  "${GRADLE_COMMON[@]}"

echo "==> Federation integration gate (S27)"
CI=true ./gradlew \
  :packages:ispf-server:test \
  --tests com.ispf.server.federation.FederationChaosIntegrationTest \
  --tests com.ispf.server.federation.FederationTunnelIntegrationTest \
  --tests com.ispf.server.federation.FederationStoreForwardIntegrationTest \
  --tests com.ispf.server.federation.FederationApiTest \
  "${GRADLE_COMMON[@]}" \
  -Dispf.test.skipLoad=true

echo "nightly backend OK"
