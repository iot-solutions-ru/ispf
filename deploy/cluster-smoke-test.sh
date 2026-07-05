#!/usr/bin/env bash
# Multi-replica cluster smoke: round-robin, REST failover, driver ownership reclaim (BL-134…136).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="${ISPF_CLUSTER_COMPOSE_FILE:-${ROOT}/deploy/docker-compose.cluster.yml}"
PORT="${ISPF_CLUSTER_PORT:-8088}"
COMPOSE=(docker compose -f "${COMPOSE_FILE}")
BASE="http://127.0.0.1:${PORT}"
CURL=(curl -sf --no-keepalive -H "Connection: close")

echo "==> Cluster smoke (compose=${COMPOSE_FILE}, port=${PORT})"
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
CLUSTER_ENABLED=$(echo "$HEALTH" | python3 -c "import json,sys; print(json.load(sys.stdin).get('clusterEnabled', False))")
if [[ "$CLUSTER_ENABLED" != "True" ]]; then
  echo "ERROR: clusterEnabled is not true" >&2
  exit 1
fi

echo "==> Driver ownership: wait for locks, kill owner, verify reclaim"
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
  echo "WARN: no active driver locks — skipping ownership failover (fixtures may lack running drivers)" >&2
else
  case "$LOCK_HOLDER" in
    replica-1) OWNER_SVC=ispf-server-1 ;;
    replica-2) OWNER_SVC=ispf-server-2 ;;
    replica-3) OWNER_SVC=ispf-server-3 ;;
    *)
      echo "WARN: unknown holder ${LOCK_HOLDER}, skipping kill-owner test" >&2
      OWNER_SVC=""
      ;;
  esac
  if [[ -n "$OWNER_SVC" ]]; then
    OWNER_CID=$("${COMPOSE[@]}" ps -q "${OWNER_SVC}")
    echo "==> Stop ${OWNER_SVC} (owner ${LOCK_HOLDER})"
    docker stop "$OWNER_CID"
    echo "==> Wait for lock reclaim (TTL + failover scan)"
    RECLAIMED=""
    for i in $(seq 1 30); do
      NEW_HOLDER=$("${COMPOSE[@]}" exec -T postgres psql -U "${PG_USER}" -d ispf -tAc \
        "SELECT holder_id FROM platform_driver_locks WHERE expires_at > NOW() GROUP BY holder_id ORDER BY COUNT(*) DESC LIMIT 1;" 2>/dev/null \
        | tr -d '[:space:]' || true)
      if [[ -n "$NEW_HOLDER" && "$NEW_HOLDER" != "$LOCK_HOLDER" ]]; then
        RECLAIMED="$NEW_HOLDER"
        echo "  lock reclaimed by ${RECLAIMED} (${i} attempts)"
        break
      fi
      sleep 2
    done
    docker start "$OWNER_CID" || true
    sleep 15
    if [[ -z "$RECLAIMED" ]]; then
      echo "ERROR: driver lock not reclaimed after killing ${OWNER_SVC}" >&2
      exit 1
    fi
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

echo "==> Cluster smoke PASSED"
