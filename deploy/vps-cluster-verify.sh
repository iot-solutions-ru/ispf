#!/usr/bin/env bash
# VPS single unified node health + optional config-sync smoke.
set -euo pipefail
BASE="http://127.0.0.1:8080"
CONFIG_SYNC=0
for arg in "$@"; do
  case "$arg" in
    --config-sync) CONFIG_SYNC=1 ;;
  esac
done

wait_ready() {
  for i in $(seq 1 24); do
    code=$(curl -s -o /tmp/vps-info.json -w '%{http_code}' "${BASE}/api/v1/info" || echo 000)
    echo "attempt ${i}: HTTP ${code}"
    if [[ "$code" == "200" ]]; then
      python3 -m json.tool /tmp/vps-info.json | head -16
      return 0
    fi
    sleep 10
  done
  echo "ERROR: node not ready" >&2
  docker ps --filter name=ispf-vps
  docker logs ispf-vps-replica-1 2>&1 | tail -20
  exit 1
}

verify_single_node() {
  echo "==> Single unified node verification"
  python3 -c "
import json, urllib.request
info = json.loads(urllib.request.urlopen('${BASE}/api/v1/info').read())
assert info.get('replicaId') == 'replica-1', info
assert info.get('clusterEnabled') is False, info
assert info.get('replicaRole') == 'all', info
assert info.get('replicaProfile') == 'unified', info
assert info.get('jobConsumerActive') is True, info
print('unified node OK:', info.get('version'), info.get('replicaRole'))
"
  echo "single node PASSED"
}

wait_ready
verify_single_node

if [[ "$CONFIG_SYNC" -eq 1 ]]; then
  echo "==> Config-sync smoke"
  TOKEN=$(curl -sf -X POST "${BASE}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('token',''))")
  if [[ -z "$TOKEN" ]]; then
    echo "ERROR: admin login failed" >&2
    exit 1
  fi
  SYNC_NAME="vps-verify-sync-$$"
  SYNC_PATH="root.platform.devices.${SYNC_NAME}"
  CREATE_CODE=$(curl -s -o /tmp/vps-sync-create.json -w '%{http_code}' \
    -X POST "${BASE}/api/v1/objects" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"parentPath\":\"root.platform.devices\",\"name\":\"${SYNC_NAME}\",\"type\":\"DEVICE\",\"displayName\":\"VPS verify sync\"}" \
    || echo 000)
  if [[ "$CREATE_CODE" != "201" && "$CREATE_CODE" != "200" ]]; then
    echo "ERROR: create HTTP ${CREATE_CODE}" >&2
    exit 1
  fi
  sleep 2
  for _ in $(seq 1 15); do
    CODE=$(curl -s -o /dev/null -w '%{http_code}' --no-keepalive \
      -H "Authorization: Bearer ${TOKEN}" \
      "${BASE}/api/v1/objects/by-path?path=${SYNC_PATH}" || echo 000)
    [[ "$CODE" == "200" ]] && break
  done
  if [[ "$CODE" != "200" ]]; then
    echo "ERROR: object not visible after create" >&2
    exit 1
  fi
  curl -sf -X DELETE "${BASE}/api/v1/objects/by-path?path=${SYNC_PATH}" \
    -H "Authorization: Bearer ${TOKEN}" >/dev/null
  GHOST=1
  for attempt in $(seq 1 30); do
    GHOST=0
    for _ in $(seq 1 15); do
      CODE=$(curl -s -o /dev/null -w '%{http_code}' --no-keepalive \
        -H "Authorization: Bearer ${TOKEN}" \
        "${BASE}/api/v1/objects/by-path?path=${SYNC_PATH}" || echo 000)
      if [[ "$CODE" == "200" ]]; then
        GHOST=1
        break
      fi
    done
    if [[ "$GHOST" -eq 0 ]]; then
      break
    fi
    sleep 1
  done
  if [[ "$GHOST" -eq 1 ]]; then
    echo "ERROR: ghost object after delete" >&2
    exit 1
  fi
  echo "config-sync PASSED"
fi
