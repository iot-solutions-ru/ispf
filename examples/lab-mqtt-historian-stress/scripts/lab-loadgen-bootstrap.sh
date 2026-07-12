#!/usr/bin/env bash
set -euo pipefail

ROOT="${ISPF_LAB_LOADGEN_ROOT:-$HOME/ispf}"
COMPOSE=(docker compose -f "${ROOT}/lab-loadgen-compose.yml")

echo "==> lab-loadgen-bootstrap $(date -Iseconds)"
echo "ROOT=${ROOT}"

if [[ ! -f "${ROOT}/lab-loadgen-compose.yml" ]]; then
  echo "ERROR: missing ${ROOT}/lab-loadgen-compose.yml" >&2
  exit 1
fi

echo "==> Start Mosquitto (bind ${ISPF_LAB_LOADGEN_HOST:-198.51.100.10}:1883)"
"${COMPOSE[@]}" up -d mqtt

echo "==> Wait broker"
for i in $(seq 1 30); do
  if docker exec "$("${COMPOSE[@]}" ps -q mqtt)" mosquitto_sub -h localhost -t '$SYS/broker/version' -C 1 -W 2 >/dev/null 2>&1; then
    echo "  mosquitto ready (${i} attempts)"
    exit 0
  fi
  sleep 2
done
echo "ERROR: mosquitto timeout on loadgen" >&2
"${COMPOSE[@]}" logs --tail 20 mqtt 2>&1
exit 1
