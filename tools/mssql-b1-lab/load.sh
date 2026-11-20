#!/usr/bin/env bash
# Cert-bundle import + concurrent by-path soak (MSSQL B1 or any BASE).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROUNDS=${ROUNDS:-30}
CONCURRENCY=${CONCURRENCY:-8}
BUNDLE=${BUNDLE:-"$SCRIPT_DIR/mssql-b1-cert-bundle.json"}
BASE=${BASE:-http://127.0.0.1:18080}

TOKEN=$(curl -sf -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
echo "token_ok rounds=$ROUNDS concurrency=$CONCURRENCY base=$BASE"

AUTH="Authorization: Bearer $TOKEN"
IMPORT_URL="$BASE/api/v1/platform/packages/import?packageId=mssql-b1-cert"

code=$(curl -s -o /tmp/lab_import.json -w '%{http_code}' -X POST "$IMPORT_URL" \
  -H "$AUTH" -H 'Content-Type: application/json' --data-binary @"$BUNDLE")
echo "seed_import=$code"
test "$code" = "200"

paths=(
  root.platform.applications.mssql-b1-cert
  root.platform.devices.mssql-b1-cert-sensor-01
  root.platform.dashboards.mssql-b1-cert-overview
  root.platform.data-sources.mssql-b1-cert
)

fail=0
ok=0
start=$(date +%s)
for ((i=1; i<=ROUNDS; i++)); do
  pids=()
  for ((c=0; c<CONCURRENCY; c++)); do
    (
      for p in "${paths[@]}"; do
        hc=$(curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" \
          "$BASE/api/v1/objects/by-path?path=$p")
        if [[ "$hc" != "200" ]]; then
          echo "FAIL round=$i path=$p code=$hc" >&2
          exit 1
        fi
      done
    ) &
    pids+=($!)
  done
  for pid in "${pids[@]}"; do
    if wait "$pid"; then
      ok=$((ok + 1))
    else
      fail=$((fail + 1))
    fi
  done
  if (( i % 5 == 0 )); then
    ic=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$IMPORT_URL" \
      -H "$AUTH" -H 'Content-Type: application/json' --data-binary @"$BUNDLE")
    echo "reimport_round=$i code=$ic"
    [[ "$ic" == "200" ]] || fail=$((fail + 1))
  fi
done
elapsed=$(( $(date +%s) - start ))
echo "done ok_batches=$ok fail_batches=$fail elapsed_s=$elapsed"
test "$fail" -eq 0
