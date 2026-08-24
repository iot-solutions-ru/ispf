#!/usr/bin/env bash
# Deploy mes-platform-production bundle + quick BFF smoke (BL-170 field/lab).
# Usage:
#   bash deploy/tools/mes-platform-production-deploy.sh
#   ISPF_BASE_URL=https://ispf.example.invalid bash deploy/tools/mes-platform-production-deploy.sh /path/bundle.json
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BUNDLE="${1:-$ROOT/examples/mes-platform-production/bundle.json}"
BASE_URL="${ISPF_BASE_URL:-http://127.0.0.1:8080}"
USER="${ISPF_DEPLOY_USER:-admin}"
PASS="${ISPF_DEPLOY_PASSWORD:-admin}"

if [ ! -f "$BUNDLE" ]; then
  echo "Missing bundle: $BUNDLE" >&2
  exit 1
fi

echo "==> Login $BASE_URL as $USER"
TOKEN=$(curl -fsS -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")

echo "==> Deploy mes-platform-production"
curl -fsS -X POST "$BASE_URL/api/v1/applications/mes-platform-production/deploy" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data-binary @"$BUNDLE" | python3 -m json.tool | head -20

HUB="root.platform.devices.mes-platform-production-hub"
echo "==> OEE smoke @ $HUB"
curl -fsS -X POST "$BASE_URL/api/v1/bff/invoke" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"functionName\":\"mes_oee_getKpi\",\"contextPath\":\"$HUB\"}" \
  | python3 -m json.tool | head -15

echo
echo "OK: operator UI → ${BASE_URL%/}/?mode=operator&app=mes-platform-production"
echo "Hub: $HUB"
