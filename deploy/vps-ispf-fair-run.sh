#!/usr/bin/env bash
set -euo pipefail
LOG=/opt/ispf/loadtest/ispf-fair-single.log

echo "=== stack prep $(date -Is) ===" | tee "$LOG"
pkill -f vps-ispf-fair-bench 2>/dev/null || true
pkill -f emqtt_bench 2>/dev/null || true
docker rm -f $(docker ps -aq --filter ancestor=emqx/emqtt-bench) 2>/dev/null || true
docker start ispf-scylla ispf-postgres ispf-redis ispf-mqtt-loadtest
for i in $(seq 1 60); do
  docker exec ispf-scylla cqlsh -e "SELECT now() FROM system.local" >/dev/null 2>&1 && break
  sleep 2
done
systemctl restart ispf-server
for i in $(seq 1 90); do
  curl -sf -X POST http://127.0.0.1:8080/api/v1/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' >/tmp/ispf-fair-token.json 2>/dev/null && break
  sleep 3
done
curl -sf http://127.0.0.1:8080/api/v1/info \
  -H "Authorization: Bearer $(python3 -c "import json; print(json.load(open('/tmp/ispf-fair-token.json'))['token'])")" | tee -a "$LOG"

SKIP_STARTUP=1 bash /opt/ispf/loadtest/vps-ispf-fair-bench.sh
