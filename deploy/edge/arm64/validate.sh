#!/usr/bin/env bash
# BL-187: validate ARM64 edge gateway compose profile (static + optional smoke).
set -euo pipefail

EDGE_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${EDGE_DIR}/../.." && pwd)"
COMPOSE_FILE="${EDGE_DIR}/docker-compose.yml"
NGINX_CONF="${ROOT}/deploy/nginx-edge.conf"
API_BASE="${ISPF_EDGE_API_BASE:-http://127.0.0.1:8080}"
UI_BASE="${ISPF_EDGE_UI_BASE:-http://127.0.0.1:8081}"
SKIP_SMOKE="${ISPF_EDGE_VALIDATE_SKIP_SMOKE:-0}"

PASS=0
FAIL=0

log() {
  echo "==> $*"
}

pass() {
  PASS=$((PASS + 1))
  log "PASS: $*"
}

fail() {
  FAIL=$((FAIL + 1))
  echo "FAIL: $*" >&2
}

require_file() {
  local path="$1"
  local label="$2"
  if [[ -f "$path" ]]; then
    pass "${label} exists (${path})"
  else
    fail "${label} missing (${path})"
  fi
}

require_compose_value() {
  local key="$1"
  local expected="$2"
  if grep -q "${key}: ${expected}" "$COMPOSE_FILE" || grep -q "${key}: \"${expected}\"" "$COMPOSE_FILE"; then
    pass "compose ${key}=${expected}"
  else
    fail "compose ${key} expected ${expected}"
  fi
}

log "ARM64 edge profile validation (BL-187)"
log "compose=${COMPOSE_FILE}"

if command -v docker >/dev/null 2>&1; then
  pass "docker CLI available"
else
  fail "docker CLI not found"
fi

if docker compose version >/dev/null 2>&1; then
  pass "docker compose available"
else
  fail "docker compose not available"
fi

require_file "$COMPOSE_FILE" "edge compose"
require_file "${EDGE_DIR}/README.md" "edge README"
require_file "$NGINX_CONF" "nginx edge config"

if grep -q 'platform: linux/arm64' "$COMPOSE_FILE"; then
  pass "compose pins linux/arm64 platform"
else
  fail "compose missing linux/arm64 platform pins"
fi

require_compose_value "ISPF_CLUSTER_ENABLED" "false"
require_compose_value "ISPF_BOOTSTRAP_FIXTURES_ENABLED" "false"
require_compose_value "ISPF_FEDERATION_OUTBOUND_ENABLED" "true"
require_compose_value "ISPF_NATS_ENABLED" "false"

if docker compose -f "$COMPOSE_FILE" config >/dev/null 2>&1; then
  pass "docker compose config validates"
else
  fail "docker compose config failed"
fi

ARTIFACT_JAR="${EDGE_DIR}/artifacts/ispf-server.jar"
ARTIFACT_UI="${EDGE_DIR}/artifacts/web-console/index.html"
if [[ -f "$ARTIFACT_JAR" && -f "$ARTIFACT_UI" ]]; then
  pass "staged artifacts present"
else
  log "WARN: artifacts not staged (skip runtime smoke unless ISPF_EDGE_VALIDATE_SKIP_SMOKE=0 and you build first)"
  SKIP_SMOKE=1
fi

if [[ "$SKIP_SMOKE" == "1" ]]; then
  log "Runtime smoke skipped (ISPF_EDGE_VALIDATE_SKIP_SMOKE=1 or artifacts missing)"
else
  log "Starting edge stack for smoke test"
  docker compose -f "$COMPOSE_FILE" up -d
  trap 'docker compose -f "'"$COMPOSE_FILE"'" down' EXIT

  for attempt in $(seq 1 60); do
    if curl -sf "${API_BASE}/actuator/health" | grep -q '"status":"UP"'; then
      pass "actuator health UP after ${attempt} attempts"
      break
    fi
    if [[ "$attempt" -eq 60 ]]; then
      fail "actuator health not UP after 60 attempts"
    fi
    sleep 3
  done

  INFO_JSON="$(curl -sf "${API_BASE}/api/v1/info")"
  echo "$INFO_JSON" | head -c 400
  echo

  CLUSTER_ENABLED=$(echo "$INFO_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin).get('clusterEnabled', True))" 2>/dev/null || echo "True")
  if [[ "$CLUSTER_ENABLED" == "False" ]]; then
    pass "api clusterEnabled=false"
  else
    fail "api clusterEnabled expected false (got ${CLUSTER_ENABLED})"
  fi

  if curl -sf "${UI_BASE}/" | head -c 200 | grep -qiE 'html|<!DOCTYPE'; then
    pass "nginx UI responds on ${UI_BASE}"
  else
    fail "nginx UI not reachable on ${UI_BASE}"
  fi
fi

log "Summary: ${PASS} passed, ${FAIL} failed"
if [[ "$FAIL" -gt 0 ]]; then
  exit 1
fi

echo "ARM64 edge profile validation OK"
