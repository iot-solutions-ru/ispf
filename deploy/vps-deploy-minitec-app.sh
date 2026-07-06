#!/bin/bash
# Seed mini-TEC reference app on prod VPS (fixtures off by default).
# Usage on VPS: bash /opt/ispf/bin/vps-deploy-minitec-app.sh [/path/to/bundle.json]
set -euo pipefail

API="${API:-http://127.0.0.1:8080}"
ENV_FILE="${ENV_FILE:-/opt/ispf/ispf-server.env}"
SERVICE="${SERVICE:-ispf-server}"
BUNDLE="${1:-/opt/ispf/staging/mini-tec/bundle.json}"
PLANT="root.platform.devices.mini-tec-plant"
HUB="${PLANT}.station-hub"
HMI_DASHBOARD="root.platform.dashboards.mini-tec-hmi"

log() { echo "==> $*"; }

login() {
  curl -sf -X POST "${API}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' \
    | python3 -c 'import json,sys; print(json.load(sys.stdin).get("token",""))'
}

hub_exists() {
  local token="$1"
  curl -sf "${API}/api/v1/objects/by-path?path=${HUB}" \
    -H "Authorization: Bearer ${token}" >/dev/null 2>&1
}

dashboard_exists() {
  local token="$1"
  local path="$2"
  curl -sf "${API}/api/v1/objects/by-path?path=${path}" \
    -H "Authorization: Bearer ${token}" >/dev/null 2>&1
}

run_fixture_bootstrap() {
  log "Enabling fixtures for reference bootstrap sync"
  enable_fixtures
  systemctl restart "$SERVICE"
  for i in $(seq 1 120); do
    if curl -sf "${API}/api/v1/info" >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done
  for i in $(seq 1 120); do
    token="$(login 2>/dev/null || true)"
    if [ -n "$token" ] && mimic_has_shared_symbols "$token"; then
      log "mini-TEC mimic diagrams synced"
      break
    fi
    if [ "$i" -eq 120 ]; then
      echo "WARN: mimic sync wait timed out" >&2
    fi
    sleep 2
  done
  disable_fixtures
  systemctl restart "$SERVICE"
  for i in $(seq 1 60); do
    if curl -sf "${API}/api/v1/info" >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done
  log "Fixtures disabled again (prod policy)"
}

mimic_has_shared_symbols() {
  local token="$1"
  curl -sf "${API}/api/v1/mimics/by-path?path=root.platform.mimics.mini-tec-zone-gas" \
    -H "Authorization: Bearer ${token}" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin).get("diagramJson") or ""; sys.exit(0 if "lib-data-block" in d else 1)' 2>/dev/null
}

wait_for_dashboard() {
  local token="$1"
  local path="$2"
  for i in $(seq 1 90); do
    if dashboard_exists "$token" "$path"; then
      return 0
    fi
    sleep 2
  done
  return 1
}

enable_fixtures() {
  if grep -q '^ISPF_BOOTSTRAP_FIXTURES_ENABLED=' "$ENV_FILE" 2>/dev/null; then
    sed -i 's/^ISPF_BOOTSTRAP_FIXTURES_ENABLED=.*/ISPF_BOOTSTRAP_FIXTURES_ENABLED=true/' "$ENV_FILE"
  else
    echo 'ISPF_BOOTSTRAP_FIXTURES_ENABLED=true' >>"$ENV_FILE"
  fi
  if grep -q '^ISPF_BOOTSTRAP_FIXTURE_PROFILE=' "$ENV_FILE" 2>/dev/null; then
    sed -i 's/^ISPF_BOOTSTRAP_FIXTURE_PROFILE=.*/ISPF_BOOTSTRAP_FIXTURE_PROFILE=mini-tec/' "$ENV_FILE"
  else
    echo 'ISPF_BOOTSTRAP_FIXTURE_PROFILE=mini-tec' >>"$ENV_FILE"
  fi
}

disable_fixtures() {
  if grep -q '^ISPF_BOOTSTRAP_FIXTURES_ENABLED=' "$ENV_FILE" 2>/dev/null; then
    sed -i 's/^ISPF_BOOTSTRAP_FIXTURES_ENABLED=.*/ISPF_BOOTSTRAP_FIXTURES_ENABLED=false/' "$ENV_FILE"
  else
    echo 'ISPF_BOOTSTRAP_FIXTURES_ENABLED=false' >>"$ENV_FILE"
  fi
}

wait_for_hub() {
  local token="$1"
  for i in $(seq 1 90); do
    if hub_exists "$token"; then
      return 0
    fi
    sleep 2
  done
  return 1
}

start_plant_drivers() {
  local token="$1"
  local devices=(gpu-01 gpu-02 gpu-03 grpb rumb-10kv dgu load-module)
  for name in "${devices[@]}"; do
    local path="${PLANT}.${name}"
    curl -sf -X POST "${API}/api/v1/drivers/runtime/start?devicePath=${path}" \
      -H "Authorization: Bearer ${token}" >/dev/null || true
  done
}

TOKEN="$(login)"
if [ -z "$TOKEN" ]; then
  echo "Login failed" >&2
  exit 1
fi

if ! hub_exists "$TOKEN"; then
  log "mini-TEC not found — one-time fixture bootstrap"
  run_fixture_bootstrap
  TOKEN="$(login)"
  if ! wait_for_hub "$TOKEN"; then
    echo "station-hub not created after fixture bootstrap" >&2
    exit 1
  fi
elif ! dashboard_exists "$TOKEN" "$HMI_DASHBOARD"; then
  log "mini-TEC plant present but HMI dashboard missing — fixture sync"
  run_fixture_bootstrap
  TOKEN="$(login)"
  if ! wait_for_dashboard "$TOKEN" "$HMI_DASHBOARD"; then
    echo "mini-tec-hmi dashboard not created after fixture sync" >&2
    exit 1
  fi
else
  log "mini-TEC plant and HMI dashboard present"
fi

if [ -f "$BUNDLE" ]; then
  log "Deploying application bundle: $BUNDLE"
  curl -sf -X POST "${API}/api/v1/applications/mini-tec/deploy" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H 'Content-Type: application/json' \
    --data-binary @"$BUNDLE" >/dev/null
else
  log "Bundle not found at $BUNDLE — skip app deploy"
fi

log "Starting virtual drivers"
start_plant_drivers "$TOKEN"

if [ "${RESYNC_MIMICS:-}" = "1" ]; then
  log "Re-sync mini-TEC mimic diagrams (one restart with fixtures)"
  run_fixture_bootstrap
fi

log "Verify operator UI: https://ispf.iot-solutions.ru/?mode=operator&app=mini-tec"
curl -sf "${API}/api/v1/info" -H "Authorization: Bearer ${TOKEN}" | python3 -m json.tool 2>/dev/null || true
