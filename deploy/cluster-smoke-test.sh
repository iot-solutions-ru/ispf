#!/usr/bin/env bash
# Multi-replica cluster smoke: round-robin, REST failover, driver ownership reclaim (BL-134…136).
# Optional: --config-sync (ADR-0030), --live-var-lag (ADR-0029 API/config value path).
# Wave 6: reclaim SLO + health asserts. See docs/en/cluster-chaos-soak-runbook.md.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="${ISPF_CLUSTER_COMPOSE_FILE:-${ROOT}/deploy/docker-compose.cluster.yml}"
PORT="${ISPF_CLUSTER_PORT:-8088}"
CONFIG_SYNC=0
LIVE_VAR_LAG=0
for arg in "$@"; do
  case "$arg" in
    --config-sync) CONFIG_SYNC=1 ;;
    --live-var-lag) LIVE_VAR_LAG=1 ;;
  esac
done
COMPOSE=(docker compose -f "${COMPOSE_FILE}")
BASE="http://127.0.0.1:${PORT}"
CURL=(curl -sf --no-keepalive -H "Connection: close")
RECLAIM_SLO_SEC="${ISPF_CLUSTER_RECLAIM_SLO_SEC:-45}"
LIVE_VAR_LAG_SLO_MS="${ISPF_CLUSTER_LIVE_VAR_LAG_SLO_MS:-5000}"
REQUIRE_DRIVER_LOCKS="${ISPF_CLUSTER_REQUIRE_DRIVER_LOCKS:-0}"
LIVE_VAR_CONSECUTIVE="${ISPF_CLUSTER_LIVE_VAR_CONSECUTIVE:-8}"

echo "==> Cluster smoke (compose=${COMPOSE_FILE}, port=${PORT})"
echo "    flags: config-sync=${CONFIG_SYNC} live-var-lag=${LIVE_VAR_LAG} require-locks=${REQUIRE_DRIVER_LOCKS}"
echo "    SLO: reclaim<=${RECLAIM_SLO_SEC}s live-var-lag<=${LIVE_VAR_LAG_SLO_MS}ms"
"${COMPOSE[@]}" ps

echo "==> Wait for nginx /api/v1/info (>=2 replicas in LB pool)"
for i in $(seq 1 90); do
  if "${CURL[@]}" "${BASE}/api/v1/info" >/dev/null 2>&1; then
    SEEN=$(
      for _ in $(seq 1 15); do
        "${CURL[@]}" "${BASE}/api/v1/info" \
          | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaId',''))" 2>/dev/null || true
      done | sort -u | grep -c . || true
    )
    if [[ "${SEEN:-0}" -ge 2 ]]; then
      echo "  ready after ${i} attempts (${SEEN} replicas in pool)"
      break
    fi
  fi
  if [[ "$i" -eq 90 ]]; then
    echo "ERROR: cluster LB not ready (need >=2 unique replicaId)" >&2
    "${COMPOSE[@]}" logs --tail 40 ispf-server-1 ispf-server-2 ispf-server-3 nginx
    exit 1
  fi
  sleep 3
done

echo "==> Round-robin replicaId (15 requests, Connection: close)"
declare -A SEEN=()
for _ in $(seq 1 15); do
  RID=$("${CURL[@]}" "${BASE}/api/v1/info" | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaId',''))")
  echo "  replicaId=${RID}"
  SEEN["$RID"]=1
done
UNIQUE=${#SEEN[@]}
echo "  unique replicas: ${UNIQUE}"
if [[ "$UNIQUE" -lt 2 ]]; then
  echo "ERROR: round-robin expected >=2 unique replicaId (got ${UNIQUE})" >&2
  exit 1
fi

echo "==> Cluster health API"
TOKEN=$(
  "${CURL[@]}" -X POST "${BASE}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('token',''))"
)
if [[ -z "$TOKEN" ]]; then
  echo "ERROR: admin login failed" >&2
  exit 1
fi
HEALTH=$("${CURL[@]}" -H "Authorization: Bearer ${TOKEN}" "${BASE}/api/v1/platform/cluster/health")
echo "$HEALTH" | python3 -m json.tool
echo "$HEALTH" | python3 -c "
import json, sys
h = json.load(sys.stdin)
errors = []
if not h.get('clusterEnabled'):
    errors.append('clusterEnabled is not true')
if not h.get('liveVariableSyncEnabled'):
    errors.append('liveVariableSyncEnabled is not true')
if int(h.get('nodesUp') or 0) < 2:
    errors.append('nodesUp < 2 (got %s)' % h.get('nodesUp'))
if int(h.get('nodesTotal') or 0) < 2:
    errors.append('nodesTotal < 2 (got %s)' % h.get('nodesTotal'))
coalesce = h.get('liveVariableSyncCoalesceMs')
if coalesce is None:
    errors.append('liveVariableSyncCoalesceMs missing')
if errors:
    print('ERROR: health asserts failed: ' + '; '.join(errors), file=sys.stderr)
    sys.exit(1)
print('  health OK (nodesUp=%s/%s, coalesceMs=%s)' % (h.get('nodesUp'), h.get('nodesTotal'), coalesce))
"

echo "==> Driver ownership: wait for locks, kill owner, verify reclaim within SLO"
PG_USER="${ISPF_CLUSTER_PG_USER:-ispf}"
PG_PASS="${ISPF_CLUSTER_PG_PASS:-ispf}"
LOCK_HOLDER=""
for i in $(seq 1 60); do
  LOCK_HOLDER=$("${COMPOSE[@]}" exec -T postgres psql -U "${PG_USER}" -d ispf -tAc \
    "SELECT holder_id FROM platform_driver_locks WHERE expires_at > NOW() GROUP BY holder_id ORDER BY COUNT(*) DESC LIMIT 1;" 2>/dev/null \
    | tr -d '[:space:]' || true)
  if [[ -n "$LOCK_HOLDER" ]]; then
    echo "  driver lock holder: ${LOCK_HOLDER} (${i} attempts)"
    break
  fi
  sleep 3
done
if [[ -z "$LOCK_HOLDER" ]]; then
  if [[ "$REQUIRE_DRIVER_LOCKS" == "1" ]]; then
    echo "ERROR: no active driver locks (ISPF_CLUSTER_REQUIRE_DRIVER_LOCKS=1)" >&2
    exit 1
  fi
  echo "WARN: no active driver locks — skipping ownership failover (fixtures may lack running drivers)" >&2
else
  case "$LOCK_HOLDER" in
    replica-1) OWNER_SVC=ispf-server-1 ;;
    replica-2) OWNER_SVC=ispf-server-2 ;;
    replica-3) OWNER_SVC=ispf-server-3 ;;
    replica-4) OWNER_SVC=ispf-server-4 ;;
    replica-5) OWNER_SVC=ispf-server-5 ;;
    replica-6) OWNER_SVC=ispf-server-6 ;;
    *)
      if [[ "$REQUIRE_DRIVER_LOCKS" == "1" ]]; then
        echo "ERROR: unknown holder ${LOCK_HOLDER} (cannot map to compose service)" >&2
        exit 1
      fi
      echo "WARN: unknown holder ${LOCK_HOLDER}, skipping kill-owner test" >&2
      OWNER_SVC=""
      ;;
  esac
  if [[ -n "${OWNER_SVC:-}" ]]; then
    OWNER_CID=$("${COMPOSE[@]}" ps -q "${OWNER_SVC}")
    echo "==> Stop ${OWNER_SVC} (owner ${LOCK_HOLDER})"
    RECLAIM_START=$(date +%s)
    docker stop "$OWNER_CID"
    echo "==> Wait for lock reclaim (SLO ${RECLAIM_SLO_SEC}s)"
    RECLAIMED=""
    RECLAIM_ELAPSED=0
    # Poll budget: SLO + small grace for docker/pg latency
    MAX_ATTEMPTS=$(( (RECLAIM_SLO_SEC + 15) / 2 ))
    [[ "$MAX_ATTEMPTS" -lt 15 ]] && MAX_ATTEMPTS=15
    for i in $(seq 1 "$MAX_ATTEMPTS"); do
      NEW_HOLDER=$("${COMPOSE[@]}" exec -T postgres psql -U "${PG_USER}" -d ispf -tAc \
        "SELECT holder_id FROM platform_driver_locks WHERE expires_at > NOW() GROUP BY holder_id ORDER BY COUNT(*) DESC LIMIT 1;" 2>/dev/null \
        | tr -d '[:space:]' || true)
      RECLAIM_ELAPSED=$(( $(date +%s) - RECLAIM_START ))
      if [[ -n "$NEW_HOLDER" && "$NEW_HOLDER" != "$LOCK_HOLDER" ]]; then
        RECLAIMED="$NEW_HOLDER"
        echo "  lock reclaimed by ${RECLAIMED} in ${RECLAIM_ELAPSED}s (attempt ${i})"
        break
      fi
      if [[ "$RECLAIM_ELAPSED" -ge "$RECLAIM_SLO_SEC" ]]; then
        break
      fi
      sleep 2
    done
    docker start "$OWNER_CID" || true
    sleep 15
    if [[ -z "$RECLAIMED" ]]; then
      echo "ERROR: driver lock not reclaimed within ${RECLAIM_SLO_SEC}s after killing ${OWNER_SVC}" >&2
      exit 1
    fi
    if [[ "$RECLAIM_ELAPSED" -gt "$RECLAIM_SLO_SEC" ]]; then
      echo "ERROR: reclaim took ${RECLAIM_ELAPSED}s > SLO ${RECLAIM_SLO_SEC}s" >&2
      exit 1
    fi
    echo "  reclaim SLO PASSED (${RECLAIM_ELAPSED}s <= ${RECLAIM_SLO_SEC}s)"
  fi
fi

echo "==> Failover: stop ispf-server-2 (REST must not 502)"
CID=$("${COMPOSE[@]}" ps -q ispf-server-2)
docker stop "$CID"
sleep 5
FAIL=0
for _ in $(seq 1 12); do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" --no-keepalive -H "Connection: close" "${BASE}/api/v1/info" || echo "000")
  if [[ "$CODE" == "200" ]]; then
    echo "  REST via nginx: HTTP ${CODE} (replica-2 down)"
    FAIL=0
    break
  fi
  echo "  attempt: HTTP ${CODE}"
  FAIL=1
  sleep 2
done
if [[ "$FAIL" -ne 0 ]]; then
  echo "ERROR: nginx returned errors while replica-2 down" >&2
  docker start "$CID" || true
  exit 1
fi

echo "==> Restore ispf-server-2"
docker start "$CID"
sleep 15

# Wait until LB again sees >=2 replicas after restore
for i in $(seq 1 40); do
  SEEN=$(
    for _ in $(seq 1 12); do
      "${CURL[@]}" "${BASE}/api/v1/info" \
        | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaId',''))" 2>/dev/null || true
    done | sort -u | grep -c . || true
  )
  if [[ "${SEEN:-0}" -ge 2 ]]; then
    echo "  LB pool restored (${SEEN} replicas)"
    break
  fi
  if [[ "$i" -eq 40 ]]; then
    echo "ERROR: LB pool did not restore to >=2 replicas after bringing replica-2 back" >&2
    exit 1
  fi
  sleep 2
done

run_config_sync() {
  echo "==> Config/structure sync (ADR-0030): create + delete temp device"
  SYNC_PATH="root.platform.devices.cluster-smoke-sync-$$"
  SYNC_NAME="cluster-smoke-sync-$$"
  CREATE_BODY=$(cat <<EOF
{"parentPath":"root.platform.devices","name":"${SYNC_NAME}","type":"DEVICE","displayName":"Cluster smoke sync"}
EOF
)
  CREATE_CODE=$(curl -s -o /tmp/cluster-sync-create.json -w "%{http_code}" \
    -X POST "${BASE}/api/v1/objects" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${CREATE_BODY}" || echo "000")
  if [[ "$CREATE_CODE" != "201" && "$CREATE_CODE" != "200" ]]; then
    echo "ERROR: create object HTTP ${CREATE_CODE}" >&2
    cat /tmp/cluster-sync-create.json >&2 || true
    exit 1
  fi
  echo "  created ${SYNC_PATH}"
  sleep 2
  FOUND=0
  for _ in $(seq 1 15); do
    CODE=$(curl -s -o /dev/null -w "%{http_code}" --no-keepalive -H "Connection: close" \
      -H "Authorization: Bearer ${TOKEN}" \
      "${BASE}/api/v1/objects/by-path?path=${SYNC_PATH}" || echo "000")
    if [[ "$CODE" == "200" ]]; then
      FOUND=1
      break
    fi
    sleep 1
  done
  if [[ "$FOUND" -ne 1 ]]; then
    echo "ERROR: created object not visible across LB pool" >&2
    exit 1
  fi
  # Sample pool: require majority of consecutive GETs succeed (round-robin coverage)
  OK_HITS=0
  for _ in $(seq 1 12); do
    CODE=$(curl -s -o /dev/null -w "%{http_code}" --no-keepalive -H "Connection: close" \
      -H "Authorization: Bearer ${TOKEN}" \
      "${BASE}/api/v1/objects/by-path?path=${SYNC_PATH}" || echo "000")
    if [[ "$CODE" == "200" ]]; then
      OK_HITS=$((OK_HITS + 1))
    fi
  done
  if [[ "$OK_HITS" -lt 10 ]]; then
    echo "ERROR: created object not consistently visible (ok=${OK_HITS}/12)" >&2
    exit 1
  fi
  DELETE_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X DELETE "${BASE}/api/v1/objects/by-path?path=${SYNC_PATH}" \
    -H "Authorization: Bearer ${TOKEN}" || echo "000")
  if [[ "$DELETE_CODE" != "204" && "$DELETE_CODE" != "200" ]]; then
    echo "ERROR: delete object HTTP ${DELETE_CODE}" >&2
    exit 1
  fi
  GHOST=1
  for attempt in $(seq 1 30); do
    GHOST=0
    for _ in $(seq 1 15); do
      CODE=$(curl -s -o /dev/null -w "%{http_code}" --no-keepalive -H "Connection: close" \
        -H "Authorization: Bearer ${TOKEN}" \
        "${BASE}/api/v1/objects/by-path?path=${SYNC_PATH}" || echo "000")
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
    echo "ERROR: deleted object still visible on a replica (ghost)" >&2
    exit 1
  fi
  echo "  config-sync PASSED (no ghost after delete; create visibility ${OK_HITS}/12)"
}

run_live_var_lag() {
  echo "==> Live-var lag (ADR-0029): API/config value consistency via LB"
  LAG_NAME="cluster-smoke-lag-$$"
  LAG_PATH="root.platform.devices.${LAG_NAME}"
  VAR_NAME="lagProbe"
  NONCE="lag-$(date +%s)-$$"
  CREATE_BODY=$(cat <<EOF
{"parentPath":"root.platform.devices","name":"${LAG_NAME}","type":"DEVICE","displayName":"Cluster smoke live-var lag"}
EOF
)
  CREATE_CODE=$(curl -s -o /tmp/cluster-lag-create.json -w "%{http_code}" \
    -X POST "${BASE}/api/v1/objects" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${CREATE_BODY}" || echo "000")
  if [[ "$CREATE_CODE" != "201" && "$CREATE_CODE" != "200" ]]; then
    echo "ERROR: live-var create object HTTP ${CREATE_CODE}" >&2
    cat /tmp/cluster-lag-create.json >&2 || true
    exit 1
  fi
  VAR_BODY=$(cat <<EOF
{"name":"${VAR_NAME}","readable":true,"writable":true,"historyEnabled":false,"schema":{"name":"${VAR_NAME}","fields":[{"name":"value","type":"STRING"}]},"initialValue":{"schema":{"name":"${VAR_NAME}","fields":[{"name":"value","type":"STRING"}]},"rows":[{"value":"init"}]}}
EOF
)
  VAR_CODE=$(curl -s -o /tmp/cluster-lag-var.json -w "%{http_code}" \
    -X POST "${BASE}/api/v1/objects/by-path/variables?path=${LAG_PATH}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${VAR_BODY}" || echo "000")
  if [[ "$VAR_CODE" != "201" && "$VAR_CODE" != "200" ]]; then
    echo "ERROR: create variable HTTP ${VAR_CODE}" >&2
    cat /tmp/cluster-lag-var.json >&2 || true
    curl -s -X DELETE "${BASE}/api/v1/objects/by-path?path=${LAG_PATH}" \
      -H "Authorization: Bearer ${TOKEN}" >/dev/null || true
    exit 1
  fi
  sleep 1
  PUT_BODY=$(cat <<EOF
{"schema":{"name":"${VAR_NAME}","fields":[{"name":"value","type":"STRING"}]},"rows":[{"value":"${NONCE}"}]}
EOF
)
  PUT_CODE=$(curl -s -o /tmp/cluster-lag-put.json -w "%{http_code}" \
    -X PUT "${BASE}/api/v1/objects/by-path/variables?path=${LAG_PATH}&name=${VAR_NAME}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${PUT_BODY}" || echo "000")
  if [[ "$PUT_CODE" != "200" ]]; then
    echo "ERROR: put variable HTTP ${PUT_CODE}" >&2
    cat /tmp/cluster-lag-put.json >&2 || true
    curl -s -X DELETE "${BASE}/api/v1/objects/by-path?path=${LAG_PATH}" \
      -H "Authorization: Bearer ${TOKEN}" >/dev/null || true
    exit 1
  fi
  LAG_START_MS=$(python3 -c "import time; print(int(time.time()*1000))")
  STREAK=0
  DEADLINE_MS=$((LAG_START_MS + LIVE_VAR_LAG_SLO_MS))
  while true; do
    NOW_MS=$(python3 -c "import time; print(int(time.time()*1000))")
    OBSERVED=$(
      curl -s --no-keepalive -H "Connection: close" \
        -H "Authorization: Bearer ${TOKEN}" \
        "${BASE}/api/v1/objects/by-path/variables/detail?path=${LAG_PATH}&name=${VAR_NAME}" \
        | python3 -c "import json,sys
try:
  d=json.load(sys.stdin)
  rows=(d.get('value') or {}).get('rows') or []
  print(rows[0].get('value','') if rows else '')
except Exception:
  print('')" 2>/dev/null || true
    )
    if [[ "$OBSERVED" == "$NONCE" ]]; then
      STREAK=$((STREAK + 1))
    else
      STREAK=0
    fi
    if [[ "$STREAK" -ge "$LIVE_VAR_CONSECUTIVE" ]]; then
      ELAPSED=$((NOW_MS - LAG_START_MS))
      echo "  live-var-lag PASSED (${ELAPSED}ms, ${STREAK} consecutive LB reads == ${NONCE})"
      break
    fi
    if [[ "$NOW_MS" -ge "$DEADLINE_MS" ]]; then
      echo "ERROR: live-var lag SLO exceeded (${LIVE_VAR_LAG_SLO_MS}ms); last='${OBSERVED}' want='${NONCE}' streak=${STREAK}" >&2
      curl -s -X DELETE "${BASE}/api/v1/objects/by-path?path=${LAG_PATH}" \
        -H "Authorization: Bearer ${TOKEN}" >/dev/null || true
      exit 1
    fi
    sleep 0.2
  done
  curl -s -X DELETE "${BASE}/api/v1/objects/by-path?path=${LAG_PATH}" \
    -H "Authorization: Bearer ${TOKEN}" >/dev/null || true
}

if [[ "$CONFIG_SYNC" -eq 1 ]]; then
  run_config_sync
fi

if [[ "$LIVE_VAR_LAG" -eq 1 ]]; then
  run_live_var_lag
fi

echo "==> Cluster smoke PASSED"
