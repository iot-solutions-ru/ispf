#!/usr/bin/env bash
# Wait for ISPF health endpoints (BL-127).
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
MAX_ATTEMPTS="${2:-60}"
SLEEP_SEC="${3:-5}"

echo "Health check: $BASE_URL (max ${MAX_ATTEMPTS} attempts)"

for ((attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)); do
  if curl -sf "${BASE_URL}/actuator/health" | grep -q '"status":"UP"'; then
    echo "actuator/health: UP"
    curl -sf "${BASE_URL}/api/v1/info" | head -c 500
    echo
    exit 0
  fi
  echo "Attempt ${attempt}/${MAX_ATTEMPTS}: not ready, sleeping ${SLEEP_SEC}s..."
  sleep "$SLEEP_SEC"
done

echo "Health check failed after ${MAX_ATTEMPTS} attempts" >&2
exit 1
