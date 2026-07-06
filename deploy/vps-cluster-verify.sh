#!/usr/bin/env bash
# VPS full ADR-0032 cluster health (edge-api, hmi-read, io, compute).
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
  echo "ERROR: cluster not ready" >&2
  docker ps --filter name=ispf-vps
  docker ps --filter name=ispf-nats
  docker logs ispf-vps-replica-1 2>&1 | tail -20
  exit 1
}

verify_cluster() {
  echo "==> Full cluster verification"
  python3 <<'PY'
import json, urllib.request

def fetch(url):
    return json.loads(urllib.request.urlopen(url).read())

info = fetch("http://127.0.0.1:8080/api/v1/info")
assert info.get("clusterEnabled") is True, info
assert info.get("replicaProfile") in ("edge-api", "hmi-read"), info
print("nginx tier:", info.get("replicaId"), info.get("replicaProfile"))

r3 = fetch("http://127.0.0.1:8083/api/v1/info")
assert r3.get("replicaId") == "replica-3", r3
assert r3.get("replicaProfile") == "io", r3
print("io tier:", r3.get("replicaId"), r3.get("replicaProfile"))

w1 = fetch("http://127.0.0.1:8084/api/v1/info")
assert w1.get("replicaId") == "worker-1", w1
assert w1.get("replicaProfile") == "compute", w1
print("compute tier:", w1.get("replicaId"), w1.get("replicaProfile"))

seen = set()
for _ in range(20):
    i = fetch("http://127.0.0.1:8080/api/v1/info")
    seen.add(i.get("replicaId"))
# ip_hash LB: single client may stick to one upstream; verify both tiers directly
r1 = fetch("http://127.0.0.1:8081/api/v1/info")
r2 = fetch("http://127.0.0.1:8082/api/v1/info")
assert r1.get("replicaProfile") == "edge-api", r1
assert r2.get("replicaProfile") == "hmi-read", r2
print("api tiers direct:", r1.get("replicaId"), r2.get("replicaId"))
print("nginx sample:", sorted(seen))
print("cluster PASSED")
PY
}

wait_ready
verify_cluster

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
