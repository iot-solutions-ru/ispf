#!/usr/bin/env bash
# Staggered cluster bootstrap: replica-1 seeds DB/fixtures/users, then scale out.
set -euo pipefail

ROOT="${ISPF_LAB_ROOT:-/home/iot-solutions/ispf}"
COMPOSE=(docker compose -f "${ROOT}/lab-cluster-compose.yml")
export ISPF_LAB_ROOT="${ROOT}"

if [[ -z "${ISPF_CLUSTER_LAN_BIND:-}" ]]; then
  ISPF_CLUSTER_LAN_BIND=$(ip -4 route get 192.168.100.10 2>/dev/null \
    | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}' || true)
  export ISPF_CLUSTER_LAN_BIND="${ISPF_CLUSTER_LAN_BIND:-127.0.0.1}"
fi
echo "==> ISPF_CLUSTER_LAN_BIND=${ISPF_CLUSTER_LAN_BIND}"

echo "==> Stop previous cluster"
"${COMPOSE[@]}" down -v 2>/dev/null || true

echo "==> Start shared services"
"${COMPOSE[@]}" up -d postgres redis nats

echo "==> Start replica-1 only (bootstrap leader)"
"${COMPOSE[@]}" up -d ispf-server-1

echo "==> Wait replica-1 /api/v1/info"
for i in $(seq 1 90); do
  if docker run --rm --network ispf-cluster-lab_default curlimages/curl:8.5.0 -sf \
      http://ispf-server-1:8080/api/v1/info >/dev/null 2>&1; then
    echo "  replica-1 HTTP ready (${i} attempts)"
    break
  fi
  if [[ "$i" -eq 90 ]]; then
    echo "ERROR: replica-1 timeout" >&2
    docker logs ispf-cluster-lab-ispf-server-1-1 2>&1 | tail -40
    exit 1
  fi
  sleep 3
done

echo "==> Wait admin user seeded (Flyway + PlatformUserBootstrap)"
BASE="http://127.0.0.1:${ISPF_LAB_CLUSTER_PORT:-8000}"
for i in $(seq 1 180); do
  USERS=$("${COMPOSE[@]}" exec -T postgres psql -U ispf -d ispf -tAc "SELECT COUNT(*) FROM platform_users;" 2>/dev/null || echo 0)
  if [[ "${USERS:-0}" -ge 1 ]]; then
    echo "  admin seed ready (${USERS} users, ${i} attempts)"
    break
  fi
  if [[ "$i" -eq 180 ]]; then
    echo "ERROR: platform_users still empty after 180 attempts" >&2
    docker logs ispf-cluster-lab-ispf-server-1-1 2>&1 | tail -50
    exit 1
  fi
  sleep 3
done

echo "==> Verify admin login via nginx"
for i in $(seq 1 30); do
  if curl -sf -X POST "${BASE}/api/v1/auth/login" \
      -H 'Content-Type: application/json' \
      -d '{"username":"admin","password":"admin"}' | grep -q '"token"'; then
    echo "  admin login OK (${i} attempts)"
    break
  fi
  if [[ "$i" -eq 30 ]]; then
    echo "ERROR: admin login failed" >&2
    exit 1
  fi
  sleep 3
done

sleep 2
echo "==> Wait fixture devices seeded on replica-1"
for i in $(seq 1 120); do
  DEVICES=$("${COMPOSE[@]}" exec -T postgres psql -U ispf -d ispf -tAc \
    "SELECT COUNT(*) FROM object_nodes WHERE path LIKE 'root.platform.devices.%';" 2>/dev/null || echo 0)
  DEVICES="${DEVICES// /}"
  if [[ "${DEVICES:-0}" -ge 10 ]]; then
    STABLE=${STABLE:-0}
    if [[ "${DEVICES}" == "${LAST_DEVICES:-}" ]]; then
      STABLE=$((STABLE + 1))
      if [[ "$STABLE" -ge 3 ]]; then
        echo "  fixtures ready (${DEVICES} device nodes, ${i} attempts)"
        break
      fi
    else
      STABLE=0
    fi
    LAST_DEVICES="${DEVICES}"
  fi
  if [[ "$i" -eq 120 ]]; then
    echo "WARN: device fixture count still ${DEVICES:-0} (continuing anyway)" >&2
  fi
  sleep 3
done

echo "==> Bootstrap counts"
"${COMPOSE[@]}" exec -T postgres psql -U ispf -d ispf -c \
  "SELECT 'users' AS what, COUNT(*)::text AS n FROM platform_users UNION ALL
   SELECT 'devices', COUNT(*)::text FROM object_nodes WHERE path LIKE 'root.platform.devices.%';"

echo "==> Start replica-2, replica-3, nginx"
"${COMPOSE[@]}" up -d ispf-server-2 ispf-server-3 nginx

echo "==> Wait LB pool (>=2 replicas)"
BASE="http://127.0.0.1:${ISPF_LAB_CLUSTER_PORT:-8000}"
for i in $(seq 1 90); do
  SEEN=$(
    for _ in $(seq 1 9); do
      curl -sf "${BASE}/api/v1/info" | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaId',''))" 2>/dev/null || true
    done | sort -u | grep -c . || true
  )
  if [[ "${SEEN:-0}" -ge 2 ]]; then
    echo "  nginx pool ready (${SEEN} replicas, ${i} attempts)"
    break
  fi
  sleep 3
done

echo "==> Driver locks"
"${COMPOSE[@]}" exec -T postgres psql -U ispf -d ispf -c \
  "SELECT holder_id, COUNT(*) AS locks FROM platform_driver_locks GROUP BY holder_id ORDER BY 1;"

echo "==> Bootstrap complete"
