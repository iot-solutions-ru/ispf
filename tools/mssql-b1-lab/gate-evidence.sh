#!/usr/bin/env bash
# ADR-0078 §8 gate evidence collector against a live B1 stand (default :18080).
# Records results to OUT. Does not claim Prod Yes by itself.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE=${BASE:-http://127.0.0.1:18080}
JAR=${JAR:-/home/iot-solutions/ispf-mssql-b1/ispf-server.jar}
PASS=${MSSQL_SA_PASSWORD:?Set MSSQL_SA_PASSWORD}
NET=${NET:-ispf-mssql-b1-net}
NAME_SQL=${NAME_SQL:-ispf-mssql-b1-stand}
BUNDLE=${BUNDLE:-"$SCRIPT_DIR/mssql-b1-cert-bundle.json"}
OUT=${OUT:-/tmp/mssql-b1-gate-evidence.json}
IMAGE_JRE=${IMAGE_JRE:-eclipse-temurin:25-jre-jammy}
ENGINE_NOTE=${ENGINE_NOTE:-"named-SKU cell (set ENGINE_NOTE)"}

results=()
note() { echo "== $* =="; }
# Windows Git Bash + CPython need a Win32 path; Linux python reads the path as-is.
hostpath() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$1"
  else
    printf '%s' "$1"
  fi
}

pass() {
  local id="$1" msg="$2"
  results+=("{\"id\":\"$id\",\"status\":\"PASS\",\"detail\":$(python3 -c 'import json,sys; print(json.dumps(sys.argv[1]))' "$msg")}")
  echo "PASS $id — $msg"
}
fail() {
  local id="$1" msg="$2"
  results+=("{\"id\":\"$id\",\"status\":\"FAIL\",\"detail\":$(python3 -c 'import json,sys; print(json.dumps(sys.argv[1]))' "$msg")}")
  echo "FAIL $id — $msg" >&2
}

http() {
  local method="$1" url="$2" token="${3:-}" body="${4:-}"
  local args=(-s -o /tmp/gate_body.json -w '%{http_code}' -X "$method" "$url")
  if [[ -n "$token" ]]; then
    args+=(-H "Authorization: Bearer $token")
  fi
  if [[ -n "$body" ]]; then
    args+=(-H 'Content-Type: application/json' --data-binary "$body")
  fi
  args+=(--max-time 60 --connect-timeout 10)
  curl "${args[@]}"
}

# --- 0. identify engine ---
note "engine identity"
ENGINE_LINE="unknown"
if docker exec "$NAME_SQL" bash -c 'true' 2>/dev/null; then
  ENGINE_LINE=$(docker inspect --format '{{.Config.Image}}' "$NAME_SQL" 2>/dev/null || echo unknown)
fi
echo "engine_image=$ENGINE_LINE"

sqlq() {
  local db="$1" query="$2" out="$3"
  # Git Bash rewrites /opt/... into a host path; keep container paths intact.
  if MSYS_NO_PATHCONV=1 docker exec "$NAME_SQL" test -x /opt/mssql-tools18/bin/sqlcmd >/dev/null 2>&1; then
    if [[ -n "$db" ]]; then
      MSYS_NO_PATHCONV=1 docker exec "$NAME_SQL" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$PASS" -C -d "$db" -Q "$query" -h -1 -W >"$out"
    else
      MSYS_NO_PATHCONV=1 docker exec "$NAME_SQL" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$PASS" -C -Q "$query" -h -1 -W >"$out"
    fi
  else
    local dbarg=()
    [[ -n "$db" ]] && dbarg=(-d "$db")
    MSYS_NO_PATHCONV=1 docker run --rm --network host --platform linux/amd64 \
      --entrypoint /opt/mssql-tools18/bin/sqlcmd \
      mcr.microsoft.com/mssql/server:2022-latest \
      -S 127.0.0.1,14333 -U sa -P "$PASS" -C "${dbarg[@]}" -Q "$query" -h -1 -W >"$out"
  fi
}

sqlq "" "SET NOCOUNT ON; SELECT CAST(SERVERPROPERTY('Edition') AS nvarchar(128)), CAST(SERVERPROPERTY('ProductVersion') AS nvarchar(64)), CAST(SERVERPROPERTY('ProductLevel') AS nvarchar(64))" /tmp/gate_edition.txt || true
echo "edition=$(tr '\n' ' ' </tmp/gate_edition.txt 2>/dev/null)"

# --- 1. health + login + flyway already applied ---
note "1 health/login"
HC=$(http GET "$BASE/actuator/health")
if [[ "$HC" == "200" ]] && grep -q '"status":"UP"' /tmp/gate_body.json; then
  pass g1_health "actuator health UP"
else
  fail g1_health "health code=$HC body=$(head -c 200 /tmp/gate_body.json)"
fi

LOGIN_CODE=$(http POST "$BASE/api/v1/auth/login" "" '{"username":"admin","password":"admin"}')
TOKEN=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8")).get("token",""))' "$(hostpath /tmp/gate_body.json)" 2>/dev/null || true)
if [[ "$LOGIN_CODE" == "200" && -n "$TOKEN" ]]; then
  pass g1_login "admin login ok"
else
  fail g1_login "login code=$LOGIN_CODE"
  TOKEN=""
fi

# RCSI / collation
note "1b RCSI/collation"
if sqlq "" "SELECT name, collation_name, is_read_committed_snapshot_on FROM sys.databases WHERE name=N'ispf'" /tmp/gate_db.txt; then
  cat /tmp/gate_db.txt
  if grep -qi 'Latin1_General_CS_AS' /tmp/gate_db.txt && grep -q '1$' /tmp/gate_db.txt; then
    pass g1_rcsi "ispf DB CS collation + RCSI on"
  else
    fail g1_rcsi "unexpected: $(tr '\n' ' ' </tmp/gate_db.txt)"
  fi
  sqlq ispf "SET NOCOUNT ON; SELECT version, success FROM flyway_schema_history WHERE version=N'1' AND success=1" /tmp/gate_flyway.txt || true
  if grep -q '1' /tmp/gate_flyway.txt; then
    pass g1_flyway "flyway V1 success=1"
  else
    fail g1_flyway "no baseline row: $(tr '\n' ' ' </tmp/gate_flyway.txt)"
  fi
else
  fail g1_rcsi "sqlcmd probe failed"
  fail g1_flyway "sqlcmd probe failed"
fi

# --- 2. bundle import + paths ---
note "2 bundle import"
if [[ -n "$TOKEN" ]]; then
  IMP=$(curl -s --max-time 120 --connect-timeout 10 -o /tmp/gate_import.json -w '%{http_code}' -X POST \
    "$BASE/api/v1/platform/packages/import?packageId=mssql-b1-cert" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    --data-binary @"$BUNDLE")
  if [[ "$IMP" == "200" ]]; then
    pass g2_import "cert bundle import 200"
  else
    fail g2_import "import code=$IMP body=$(head -c 200 /tmp/gate_import.json)"
  fi
  path_ok=1
  for p in \
    root.platform.applications.mssql-b1-cert \
    root.platform.devices.mssql-b1-cert-sensor-01 \
    root.platform.dashboards.mssql-b1-cert-overview \
    root.platform.data-sources.mssql-b1-cert
  do
    c=$(curl -s --max-time 30 --connect-timeout 10 -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
      "$BASE/api/v1/objects/by-path?path=$p")
    [[ "$c" == "200" ]] || path_ok=0
    echo "path $p -> $c"
  done
  if [[ "$path_ok" -eq 1 ]]; then
    pass g2_paths "all cert paths 200"
  else
    fail g2_paths "one or more paths not 200"
  fi
else
  fail g2_import "no token"
  fail g2_paths "no token"
fi

# --- 3. two-JVM lease failover (same DB) ---
note "3 dual-JVM lease"
JDBC="jdbc:sqlserver://${NAME_SQL}:1433;databaseName=ispf;encrypt=false;trustServerCertificate=true"
docker rm -f ispf-mssql-b1-lease2 2>/dev/null || true
if [[ -f "$JAR" ]]; then
  MSYS_NO_PATHCONV=1 docker run -d --name ispf-mssql-b1-lease2 --network "$NET" \
    -p 127.0.0.1:18081:8080 \
    -v "$JAR:/app/ispf-server.jar:ro" \
    -e SPRING_PROFILES_ACTIVE=local \
    -e ISPF_METADATA_DB_KIND=mssql \
    -e SPRING_DATASOURCE_URL="$JDBC" \
    -e SPRING_DATASOURCE_USERNAME=sa \
    -e SPRING_DATASOURCE_PASSWORD="$PASS" \
    -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.microsoft.sqlserver.jdbc.SQLServerDriver \
    -e ISPF_TENANT_DB_ROW_ISOLATION=false \
    -e ISPF_VARIABLE_HISTORY_ENABLED=false \
    -e ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=false \
    -e ISPF_EVENT_JOURNAL_ENABLED=false \
    -e ISPF_CLUSTER_ENABLED=false \
    -e ISPF_NATS_ENABLED=false \
    -e ISPF_REDIS_ENABLED=false \
    -e ISPF_BOOTSTRAP_FIXTURES_ENABLED=false \
    -e ISPF_LICENSE_ENFORCE=false \
    -e ISPF_NATS_REPLICA_ID=lease-replica-2 \
    "$IMAGE_JRE" java -jar /app/ispf-server.jar >/dev/null
  lease_up=0
  for i in $(seq 1 60); do
    if curl -sf http://127.0.0.1:18081/actuator/health | grep -q UP; then
      lease_up=1
      break
    fi
    sleep 3
  done
  if [[ "$lease_up" -eq 1 ]]; then
    pass g3_dual_jvm "second non-clustered JVM health UP on :18081 against shared ispf; live lease reclaim not asserted (cluster off). Lease failover is CI: MssqlFlywayBaselineTckTest"
  else
    fail g3_dual_jvm "second JVM did not become healthy; logs follow"
    docker logs ispf-mssql-b1-lease2 2>&1 | tail -40 || true
  fi
  # free Edge RAM/CPU before fence boots (lease already evidenced)
  docker rm -f ispf-mssql-b1-lease2 2>/dev/null || true
else
  fail g3_dual_jvm "missing JAR=$JAR"
fi

# --- 4. concurrent audit (API-level: parallel config/audit writes via reimport) ---
note "4 audit chain + serial reimport"
if [[ -n "$TOKEN" ]]; then
  # Parallel import saturates Edge Hikari — B1 bar is serial reimport + audit_event_chain present.
  # CI covers true concurrent append (AuditChainConcurrentAppendTest / MssqlFlywayBaselineTckTest).
  SER=$(curl -s --max-time 120 --connect-timeout 10 -o /dev/null -w '%{http_code}' -X POST \
    "$BASE/api/v1/platform/packages/import?packageId=mssql-b1-cert" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    --data-binary @"$BUNDLE" || true)
  echo "serial_import=$SER"
  sqlq ispf "SET NOCOUNT ON; SELECT COUNT(*) FROM audit_event_chain" /tmp/gate_audit.txt || echo 0 > /tmp/gate_audit.txt
  AUDIT_ROWS=$(head -1 /tmp/gate_audit.txt | tr -cd '0-9')
  echo "audit_rows=$AUDIT_ROWS"
  if [[ "$SER" == "200" && -n "$AUDIT_ROWS" && "$AUDIT_ROWS" -gt 0 ]]; then
    pass g4_concurrent "serial reimport 200; audit_event_chain rows=$AUDIT_ROWS. Concurrent append/verify is CI only (AuditChainConcurrentAppendTest, MssqlFlywayBaselineTckTest), not live on this cell"
  else
    fail g4_concurrent "serial=$SER audit_rows=$AUDIT_ROWS"
  fi
else
  fail g4_concurrent "no token"
fi

# --- 5/7 fence boots ---
run_fence() {
  local id="$1" env_extra="$2" expect="$3"
  local name="ispf-mssql-b1-fence-$id"
  local logf="/tmp/gate_fence_${id}.log"
  docker rm -f "$name" 2>/dev/null || true
  # shellcheck disable=SC2086
  if ! MSYS_NO_PATHCONV=1 docker run -d --name "$name" --network "$NET" \
    -v "$JAR:/app/ispf-server.jar:ro" \
    -e SPRING_PROFILES_ACTIVE=local \
    -e ISPF_METADATA_DB_KIND=mssql \
    -e SPRING_DATASOURCE_URL="$JDBC" \
    -e SPRING_DATASOURCE_USERNAME=sa \
    -e SPRING_DATASOURCE_PASSWORD="$PASS" \
    -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.microsoft.sqlserver.jdbc.SQLServerDriver \
    -e ISPF_NATS_ENABLED=false \
    -e ISPF_REDIS_ENABLED=false \
    -e ISPF_BOOTSTRAP_FIXTURES_ENABLED=false \
    -e ISPF_LICENSE_ENFORCE=false \
    $env_extra \
    "$IMAGE_JRE" java -jar /app/ispf-server.jar >/dev/null; then
    fail "$id" "docker run failed"
    return
  fi
  ok=0
  for i in $(seq 1 60); do
    docker logs "$name" >"$logf" 2>&1 || true
    if grep -Fq "$expect" "$logf"; then
      ok=1
      break
    fi
    if grep -Fq 'Started IspfServerApplication' "$logf"; then
      break
    fi
    # boot often dies with APPLICATION FAILED TO START before full message flush
    if grep -Fq 'APPLICATION FAILED TO START' "$logf" && grep -Fq "$expect" "$logf"; then
      ok=1
      break
    fi
    sleep 2
  done
  if [[ "$ok" -ne 1 ]]; then
    echo "--- fence $id last logs ---" >&2
    tail -40 "$logf" >&2 || true
  fi
  docker rm -f "$name" >/dev/null 2>&1 || true
  if [[ "$ok" -eq 1 ]]; then
    pass "$id" "refused with: $expect"
  else
    fail "$id" "did not see expect=$expect"
  fi
}

note "5 row-isolation refuse"
run_fence g5_rls \
  "-e ISPF_TENANT_DB_ROW_ISOLATION=true -e ISPF_VARIABLE_HISTORY_ENABLED=false -e ISPF_EVENT_JOURNAL_ENABLED=false -e ISPF_CLUSTER_ENABLED=false" \
  "db-row-isolation=true is unsupported on MSSQL"

note "6 HARD isolation refuse"
run_fence g6_hard \
  "-e ISPF_TENANT_DB_ROW_ISOLATION=false -e ISPF_TENANT_ISOLATION_MODE=HARD -e ISPF_VARIABLE_HISTORY_ENABLED=false -e ISPF_EVENT_JOURNAL_ENABLED=false -e ISPF_CLUSTER_ENABLED=false" \
  "isolation-mode=HARD is unsupported on MSSQL"

note "7 jdbc history refuse"
run_fence g7_hist \
  "-e ISPF_TENANT_DB_ROW_ISOLATION=false -e ISPF_VARIABLE_HISTORY_ENABLED=true -e ISPF_VARIABLE_HISTORY_STORE=jdbc -e ISPF_EVENT_JOURNAL_ENABLED=false -e ISPF_CLUSTER_ENABLED=false" \
  "variable-history"

note "7b dual-write refuse"
run_fence g7_dual \
  "-e ISPF_TENANT_DB_ROW_ISOLATION=false -e ISPF_VARIABLE_HISTORY_ENABLED=true -e ISPF_VARIABLE_HISTORY_STORE=clickhouse -e ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=true -e ISPF_EVENT_JOURNAL_ENABLED=false -e ISPF_CLUSTER_ENABLED=false" \
  "dual-write"

note "7c jdbc journal refuse"
run_fence g7_journal \
  "-e ISPF_TENANT_DB_ROW_ISOLATION=false -e ISPF_VARIABLE_HISTORY_ENABLED=false -e ISPF_EVENT_JOURNAL_ENABLED=true -e ISPF_EVENT_JOURNAL_STORE=jdbc -e ISPF_CLUSTER_ENABLED=false" \
  "event-journal"

note "7d cluster refuse"
run_fence g7_cluster \
  "-e ISPF_TENANT_DB_ROW_ISOLATION=false -e ISPF_VARIABLE_HISTORY_ENABLED=false -e ISPF_EVENT_JOURNAL_ENABLED=false -e ISPF_CLUSTER_ENABLED=true" \
  "not certified on MSSQL"

# write JSON
: > /tmp/gate_items.jsonl
for r in "${results[@]}"; do
  echo "$r" >> /tmp/gate_items.jsonl
done
export GATE_ITEMS_WIN GATE_EDITION_WIN OUT_WIN
GATE_ITEMS_WIN=$(hostpath /tmp/gate_items.jsonl)
GATE_EDITION_WIN=$(hostpath /tmp/gate_edition.txt)
if [[ -z "${OUT:-}" ]]; then OUT=/tmp/mssql-b1-gate-evidence.json; fi
OUT_WIN=$(hostpath "$OUT")
python3 - <<'PY'
import json, datetime, os
items = []
with open(os.environ["GATE_ITEMS_WIN"], encoding="utf-8") as f:
    for line in f:
        line=line.strip()
        if line:
            items.append(json.loads(line))
fails = sum(1 for i in items if i.get("status") == "FAIL")
edition = ""
ep = os.environ.get("GATE_EDITION_WIN", "")
if ep and os.path.exists(ep):
    edition = open(ep, encoding="utf-8", errors="replace").read().strip().replace("\n", " ")
out = {
  "generated_at": datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z"),
  "base": os.environ.get("BASE", "http://127.0.0.1:18080"),
  "engine_note": os.environ.get("ENGINE_NOTE", ""),
  "edition": edition,
  "fail_count": fails,
  "pass_count": sum(1 for i in items if i.get("status") == "PASS"),
  "items": items,
}
path = os.environ.get("OUT_WIN") or os.environ.get("OUT", "/tmp/mssql-b1-gate-evidence.json")
open(path, "w", encoding="utf-8").write(json.dumps(out, indent=2))
print(json.dumps(out, indent=2))
print(f"wrote {path}")
raise SystemExit(1 if fails else 0)
PY
