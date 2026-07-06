#!/usr/bin/env bash
set -euo pipefail
TOKEN=$(curl -sf -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")

echo "==> Cluster health"
curl -sf -H "Authorization: Bearer ${TOKEN}" http://127.0.0.1:8080/api/v1/platform/cluster/health | python3 -m json.tool

echo "==> Replica profiles"
for spec in "8081:replica-1:edge-api" "8082:replica-2:hmi-read" "8083:replica-3:io" "8084:worker-1:compute"; do
  port="${spec%%:*}"
  rest="${spec#*:}"
  rid="${rest%%:*}"
  profile="${rest#*:}"
  got=$(curl -sf "http://127.0.0.1:${port}/api/v1/info" \
    | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['replicaId'], d['replicaProfile'])")
  echo "  :${port} => ${got} (expected ${rid} ${profile})"
done

bash /opt/ispf/bin/vps-cluster-verify.sh
