#!/usr/bin/env bash
# BL-170: one-command MES production bundle deploy + smoke verification.
# Usage: bash deploy/tools/mes-platform-production-deploy.sh [bundle.json]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

API="${API:-http://127.0.0.1:8080}"
BUNDLE="${1:-$ROOT/examples/mes-platform-production/bundle.json}"
APP_ID="mes-platform-production"
HUB="root.platform.devices.mes-platform-production-hub"
SEED_SHIFT_ID="dddddddd-dddd-dddd-dddd-dddddddddddd"
SEED_BATCH_PATH="root.platform.mes.lots.batch-line-a01-001"

log() { echo "==> $*"; }

login() {
  curl -sf -X POST "${API}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' \
    | python3 -c 'import json,sys; print(json.load(sys.stdin).get("token",""))' 2>/dev/null || true
}

auth_header() {
  local token="$1"
  if [ -n "$token" ]; then
    printf 'Authorization: Bearer %s' "$token"
  fi
}

bff_invoke() {
  local token="$1"
  local body="$2"
  local hdr
  hdr="$(auth_header "$token")"
  if [ -n "$hdr" ]; then
    curl -sf -X POST "${API}/api/v1/bff/invoke" \
      -H 'Content-Type: application/json' \
      -H "$hdr" \
      -d "$body"
  else
    curl -sf -X POST "${API}/api/v1/bff/invoke" \
      -H 'Content-Type: application/json' \
      -d "$body"
  fi
}

wait_for_api() {
  for _ in $(seq 1 60); do
    if curl -sf "${API}/api/v1/info" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "API not reachable at ${API}" >&2
  exit 1
}

if [ ! -f "$BUNDLE" ]; then
  echo "Bundle not found: $BUNDLE" >&2
  exit 1
fi

wait_for_api
TOKEN="$(login)"

log "Deploying ${APP_ID} from ${BUNDLE}"
if [ -n "$TOKEN" ]; then
  curl -sf -X POST "${API}/api/v1/applications/${APP_ID}/deploy" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H 'Content-Type: application/json' \
    --data-binary @"$BUNDLE" \
    | python3 -m json.tool
else
  curl -sf -X POST "${API}/api/v1/applications/${APP_ID}/deploy" \
    -H 'Content-Type: application/json' \
    --data-binary @"$BUNDLE" \
    | python3 -m json.tool
fi

log "Smoke: OEE KPI (seed shift)"
OEE_JSON="$(bff_invoke "$TOKEN" "$(cat <<EOF
{
  "objectPath": "${HUB}",
  "functionName": "mes_oee_getKpi",
  "input": {
    "schema": { "name": "in", "fields": [{ "name": "shiftId", "type": "STRING" }] },
    "rows": [{ "shiftId": "${SEED_SHIFT_ID}" }]
  }
}
EOF
)")"
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("error_code")=="OK", d; assert float(d["result"]["oeePct"])>80, d["result"]["oeePct"]' <<<"$OEE_JSON"

log "Smoke: SPC samples"
SPC_JSON="$(bff_invoke "$TOKEN" "$(cat <<EOF
{
  "objectPath": "${HUB}",
  "functionName": "mes_quality_listSpcSamples",
  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
}
EOF
)")"
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("error_code")=="OK", d; assert len(d["result"]["rows"])>=3, len(d["result"]["rows"])' <<<"$SPC_JSON"

log "Smoke: batch phase advance"
BATCH_JSON="$(bff_invoke "$TOKEN" "$(cat <<EOF
{
  "objectPath": "${HUB}",
  "functionName": "mes_batch_runPhase",
  "input": {
    "schema": {
      "name": "in",
      "fields": [
        { "name": "batchPath", "type": "STRING" },
        { "name": "batchId", "type": "STRING" },
        { "name": "recipe", "type": "STRING" },
        { "name": "phase", "type": "STRING" }
      ]
    },
    "rows": [{
      "batchPath": "${SEED_BATCH_PATH}",
      "batchId": "BATCH-LINE-A01-001",
      "recipe": "recipe-standard-a",
      "phase": "react"
    }]
  }
}
EOF
)")"
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("error_code")=="OK", d; assert d["result"]["phase"]=="react", d["result"]' <<<"$BATCH_JSON"

log "Smoke: ERP outbox round-trip"
bff_invoke "$TOKEN" "$(cat <<EOF
{
  "objectPath": "${HUB}",
  "functionName": "mes_erp_enqueueOutbox",
  "input": {
    "schema": {
      "name": "in",
      "fields": [
        { "name": "entityType", "type": "STRING" },
        { "name": "entityId", "type": "STRING" },
        { "name": "payloadJson", "type": "STRING" }
      ]
    },
    "rows": [{
      "entityType": "WORK_ORDER",
      "entityId": "WO-LINE-A01-001",
      "payloadJson": "{\\"status\\":\\"dispatched\\"}"
    }]
  }
}
EOF
)" >/dev/null
OUTBOX_JSON="$(bff_invoke "$TOKEN" "$(cat <<EOF
{
  "objectPath": "${HUB}",
  "functionName": "mes_erp_pollOutbox",
  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
}
EOF
)")"
python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("error_code")=="OK", d; rows=d["result"]["rows"]; assert rows and rows[0].get("status")=="sent", rows' <<<"$OUTBOX_JSON"

log "MES production deploy + smoke OK"
log "Operator UI: ${API%/}/?mode=operator&app=${APP_ID}"
