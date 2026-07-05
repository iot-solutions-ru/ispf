#!/usr/bin/env bash
set -euo pipefail
BASE="http://127.0.0.1:8080"
for i in $(seq 1 24); do
  code=$(curl -s -o /tmp/vps-info.json -w '%{http_code}' "${BASE}/api/v1/info" || echo 000)
  echo "attempt ${i}: HTTP ${code}"
  if [[ "$code" == "200" ]]; then
    python3 -m json.tool /tmp/vps-info.json | head -12
    echo "--- round-robin ---"
    for _ in $(seq 1 15); do
      curl -sf --no-keepalive "${BASE}/api/v1/info" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("replicaId"))'
    done | sort | uniq -c
    exit 0
  fi
  sleep 10
done
echo "ERROR: cluster not ready" >&2
docker ps --filter name=ispf-vps
docker logs ispf-vps-replica-1 2>&1 | tail -20
exit 1
