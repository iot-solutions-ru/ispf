#!/usr/bin/env bash
# Steady B1 metadata load wrapper. Keep import serial (Edge/Hikari).
set -euo pipefail
export PYTHONUNBUFFERED=1
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE=${BASE:-http://127.0.0.1:18080}
OUT=${OUT:-"$SCRIPT_DIR/loadtest-result.json"}
ENGINE=${ENGINE:-mssql-b1}

echo "== health =="
curl -sf "$BASE/actuator/health"
echo

(
  while true; do
    date -Is
    docker stats --no-stream --format '{{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}}' \
      ispf-mssql-b1-app ispf-mssql-b1-stand 2>/dev/null || true
    sleep 15
  done
) > /tmp/mssql-b1-loadtest-stats.txt &
STATS_PID=$!
trap 'kill "$STATS_PID" 2>/dev/null || true' EXIT

set +e
python3 "$SCRIPT_DIR/loadtest.py" \
  --base "$BASE" \
  --engine "$ENGINE" \
  --out "$OUT" \
  --bundle "$SCRIPT_DIR/mssql-b1-cert-bundle.json" \
  --read-workers 20 --read-seconds 120 \
  --import-workers 1 --import-seconds 60 \
  --write-workers 12 --write-seconds 90 \
  --mixed-workers 16 --mixed-seconds 120
RC=$?
set -e

echo "EXIT=$RC"
echo "--- stats ---"
tail -20 /tmp/mssql-b1-loadtest-stats.txt 2>/dev/null || true
echo "--- result ---"
cat "$OUT" 2>/dev/null || true
exit "$RC"
