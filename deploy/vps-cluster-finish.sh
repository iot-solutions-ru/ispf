#!/usr/bin/env bash
set -euo pipefail
COMPOSE_FILE=/opt/ispf/docker-compose.vps-cluster.yml
if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose -f "$COMPOSE_FILE")
else
  COMPOSE=(docker-compose -f "$COMPOSE_FILE")
fi

sed -i 's/\r$//' /opt/ispf/bin/vps-cluster-prune-stale.sh /opt/ispf/bin/vps-cluster-verify.sh 2>/dev/null || true
chmod +x /opt/ispf/bin/vps-cluster-prune-stale.sh
bash /opt/ispf/bin/vps-cluster-prune-stale.sh 2>/dev/null || true

"${COMPOSE[@]}" up -d ispf-server-2 ispf-server-3 ispf-server-worker-1 nginx

for i in $(seq 1 60); do
  R1=$(curl -sf "http://127.0.0.1:8081/api/v1/info" \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaProfile',''))" 2>/dev/null || true)
  R2=$(curl -sf "http://127.0.0.1:8082/api/v1/info" \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaProfile',''))" 2>/dev/null || true)
  R3=$(curl -sf "http://127.0.0.1:8083/api/v1/info" \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaProfile',''))" 2>/dev/null || true)
  W1=$(curl -sf "http://127.0.0.1:8084/api/v1/info" \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('replicaProfile',''))" 2>/dev/null || true)
  echo "attempt $i edge=$R1 hmi=$R2 io=$R3 compute=$W1"
  if [[ "$R1" == "edge-api" && "$R2" == "hmi-read" && "$R3" == "io" && "$W1" == "compute" ]]; then
    break
  fi
  [[ "$i" -eq 60 ]] && { docker ps; docker logs ispf-vps-replica-2 --tail 20; docker logs ispf-vps-worker-1 --tail 20; exit 1; }
  sleep 5
done

docker ps --format 'table {{.Names}}\t{{.Status}}'
bash /opt/ispf/bin/vps-cluster-verify.sh
