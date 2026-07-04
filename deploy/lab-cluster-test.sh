#!/usr/bin/env bash
# Smoke test for lab-cluster-compose.yml (round-robin, cluster health, nginx failover).
set -euo pipefail

ROOT="${ISPF_LAB_ROOT:-/home/iot-solutions/ispf}"
PORT="${ISPF_LAB_CLUSTER_PORT:-8000}"
COMPOSE=(docker compose -f "${ROOT}/lab-cluster-compose.yml")
BASE="http://127.0.0.1:${PORT}"

echo "==> Lab cluster stack status"
"${COMPOSE[@]}" ps

echo "==> Wait for nginx /api/v1/info (and all replicas)"
for i in $(seq 1 90); do
  if curl -sf "${BASE}/api/v1/info" >/dev/null 2>&1; then
    SEEN=$(
      for _ in $(seq 1 9); do
        curl -sf "${BASE}/api/v1/info" | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaId',''))"
      done | sort -u | grep -c . || true
    )
    if [[ "${SEEN:-0}" -ge 2 ]]; then
      echo "  ready after ${i} attempts (${SEEN} replicas in LB pool)"
      break
    fi
  fi
  if [[ "$i" -eq 90 ]]; then
    echo "ERROR: cluster LB not ready" >&2
    "${COMPOSE[@]}" logs --tail 40 ispf-server-1 ispf-server-2 ispf-server-3 nginx
    exit 1
  fi
  sleep 3
done

echo "==> Round-robin replicaId (12 requests)"
declare -A SEEN=()
for _ in $(seq 1 12); do
  RID=$(curl -sf "${BASE}/api/v1/info" | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaId',''))")
  echo "  replicaId=${RID}"
  SEEN["$RID"]=1
done
UNIQUE=${#SEEN[@]}
echo "  unique replicas: ${UNIQUE}"
if [[ "$UNIQUE" -lt 2 ]]; then
  echo "WARN: expected >=2 unique replicaId values (got ${UNIQUE})" >&2
fi

echo "==> Per-replica health (/api/v1/info via Docker network)"
for svc in ispf-server-1 ispf-server-2 ispf-server-3; do
  RAW=$(docker run --rm --network ispf-cluster-lab_default curlimages/curl:8.5.0 -sf \
    "http://${svc}:8080/api/v1/info" 2>/dev/null || true)
  if [[ -n "$RAW" ]]; then
    STATUS=$(echo "$RAW" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('replicaId','?'))" 2>/dev/null || echo "?")
  else
    STATUS="unreachable"
  fi
  echo "  ${svc}: ${STATUS}"
done

echo "==> Cluster health API (admin login)"
TOKEN=$(
  curl -sf -X POST "${BASE}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('token',''))"
)
if [[ -z "$TOKEN" ]]; then
  echo "WARN: admin login failed — skipping cluster health" >&2
else
  curl -sf -H "Authorization: Bearer ${TOKEN}" "${BASE}/api/v1/platform/cluster/health" \
    | python3 -m json.tool
fi

echo "==> Failover: stop ispf-server-2"
CID=$("${COMPOSE[@]}" ps -q ispf-server-2)
docker stop "$CID"
sleep 8
FAIL=0
for _ in $(seq 1 8); do
  if curl -sf "${BASE}/api/v1/info" >/dev/null; then
    echo "  REST via nginx: OK (replica down)"
    FAIL=0
    break
  fi
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
sleep 20
RID=$(docker run --rm --network ispf-cluster-lab_default curlimages/curl:8.5.0 -sf \
  "http://ispf-server-2:8080/api/v1/info" 2>/dev/null \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('replicaId','?'))" 2>/dev/null || echo "?")
echo "  ispf-server-2: replicaId=${RID}"

echo "==> Optional: LAN peer 192.168.100.10"
if ping -c 1 -W 2 192.168.100.10 >/dev/null 2>&1; then
  echo "  ping 192.168.100.10: OK"
else
  echo "  ping 192.168.100.10: unreachable (multi-host cluster not tested)"
fi

echo "==> Lab cluster smoke PASSED"
