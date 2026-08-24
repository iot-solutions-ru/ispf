#!/usr/bin/env bash
# Deploy mes-platform-production bundle + quick BFF smoke (BL-170 field/lab).
# Usage:
#   bash deploy/tools/mes-platform-production-deploy.sh
#   ISPF_BASE_URL=https://ispf.example.invalid bash deploy/tools/mes-platform-production-deploy.sh /path/bundle.json
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/../.." && pwd)"
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
DEPLOY_HTTP=$(curl -sS -w '%{http_code}' -o /tmp/mes-deploy.json -X POST "$BASE_URL/api/v1/applications/mes-platform-production/deploy" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data-binary @"$BUNDLE")
if [ "$DEPLOY_HTTP" != "200" ]; then
  echo "Deploy failed HTTP $DEPLOY_HTTP:" >&2
  cat /tmp/mes-deploy.json >&2
  echo >&2
  if grep -q 'require-signed-bundles' /tmp/mes-deploy.json 2>/dev/null; then
    echo "Hint (prod VPS): set ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=false in ispf-server.env and recreate container," >&2
    echo "  or sign bundle: tools/license-builder/sign-bundle.py --installation-id \$(curl -s .../installation-id)" >&2
  fi
  exit 1
fi
python3 -m json.tool /tmp/mes-deploy.json | head -20

HUB="root.platform.devices.mes-platform-production-hub"
SEED_SHIFT_ID="${MES_OEE_SEED_SHIFT_ID:-dddddddd-dddd-dddd-dddd-dddddddddddd}"

echo "==> listLines smoke @ $HUB"
LINES_HTTP=$(curl -sS -w '%{http_code}' -o /tmp/mes-lines.json -X POST "$BASE_URL/api/v1/bff/invoke" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"objectPath\":\"$HUB\",\"functionName\":\"mes_platform_listLines\",\"input\":{\"schema\":{\"name\":\"in\",\"fields\":[]},\"rows\":[{}]}}")
if [ "$LINES_HTTP" != "200" ]; then
  echo "listLines smoke failed HTTP $LINES_HTTP:" >&2
  cat /tmp/mes-lines.json >&2
  exit 1
fi
python3 -m json.tool /tmp/mes-lines.json | head -10

echo "==> OEE smoke @ $HUB (shift $SEED_SHIFT_ID)"
OEE_HTTP=$(curl -sS -w '%{http_code}' -o /tmp/mes-oee.json -X POST "$BASE_URL/api/v1/bff/invoke" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"objectPath\":\"$HUB\",\"functionName\":\"mes_oee_getKpi\",\"input\":{\"schema\":{\"name\":\"in\",\"fields\":[{\"name\":\"shiftId\",\"type\":\"STRING\"}]},\"rows\":[{\"shiftId\":\"$SEED_SHIFT_ID\"}]}}")
if [ "$OEE_HTTP" != "200" ]; then
  echo "OEE smoke failed HTTP $OEE_HTTP:" >&2
  cat /tmp/mes-oee.json >&2
  exit 1
fi
python3 -m json.tool /tmp/mes-oee.json | head -15

echo
echo "OK: operator UI → ${BASE_URL%/}/?mode=operator&app=mes-platform-production"
echo "Hub: $HUB"
