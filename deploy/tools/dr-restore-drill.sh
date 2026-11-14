#!/usr/bin/env bash
# Lab / CI DR restore drill — ADR-0068 follow-up (T2 dump→restore).
#
# Claims: Postgres dump round-trip into an empty database with matching
# flyway_schema_history (+ optional users) row counts.
# Non-claims: not site DR, not RPO/RTO proof, not T3 historian / T4 secrets-key,
# and not a full ISPF login smoke unless ISPF_BASE_URL is set.
#
# Usage:
#   bash deploy/tools/dr-restore-drill.sh
#
# Env:
#   POSTGRES_IMAGE   default timescale/timescaledb:latest-pg16
#   DRILL_OUT_DIR    default ./build/dr-restore-drill
#   ISPF_BASE_URL    optional — if set, POST /api/v1/auth/login (body discarded)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/../.." && pwd)"
OUT="${DRILL_OUT_DIR:-$ROOT/build/dr-restore-drill}"
IMAGE="${POSTGRES_IMAGE:-timescale/timescaledb:latest-pg16}"
SRC_NAME="ispf-dr-src-$$"
DST_NAME="ispf-dr-dst-$$"
NET_NAME="ispf-dr-net-$$"
PASS="${POSTGRES_PASSWORD:-ispf}"
USER="${POSTGRES_USER:-ispf}"
DB="${POSTGRES_DB:-ispf}"
RESULT="PASS"
LOGIN_NOTE="skipped"

cleanup() {
  docker rm -f "$SRC_NAME" "$DST_NAME" >/dev/null 2>&1 || true
  docker network rm "$NET_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

mkdir -p "$OUT"
echo "==> DR restore drill (T2 lab) → $OUT"

docker network create "$NET_NAME" >/dev/null

echo "==> Start source Postgres"
docker run -d --name "$SRC_NAME" --network "$NET_NAME" \
  -e POSTGRES_PASSWORD="$PASS" -e POSTGRES_USER="$USER" -e POSTGRES_DB="$DB" \
  "$IMAGE" >/dev/null

echo "==> Wait for source ready"
for _ in $(seq 1 60); do
  if docker exec "$SRC_NAME" pg_isready -U "$USER" -d "$DB" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$SRC_NAME" pg_isready -U "$USER" -d "$DB"

echo "==> Seed minimal schema"
docker exec -i "$SRC_NAME" psql -U "$USER" -d "$DB" <<'SQL'
CREATE TABLE IF NOT EXISTS flyway_schema_history (
  installed_rank INT PRIMARY KEY,
  version VARCHAR(50),
  description VARCHAR(200) NOT NULL,
  type VARCHAR(20) NOT NULL,
  script VARCHAR(1000) NOT NULL,
  checksum INT,
  installed_by VARCHAR(100) NOT NULL,
  installed_on TIMESTAMP NOT NULL DEFAULT now(),
  execution_time INT NOT NULL,
  success BOOLEAN NOT NULL
);
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, installed_by, execution_time, success)
VALUES (1, '1', 'seed', 'SQL', 'V1__seed.sql', 'drill', 1, true);
CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE
);
INSERT INTO users (username) VALUES ('admin');
SQL

echo "==> pg_dump source"
docker exec "$SRC_NAME" pg_dump -U "$USER" -d "$DB" -Fc -f /tmp/ispf.dump
docker cp "$SRC_NAME:/tmp/ispf.dump" "$OUT/ispf.dump"

echo "==> Start empty destination + restore"
docker run -d --name "$DST_NAME" --network "$NET_NAME" \
  -e POSTGRES_PASSWORD="$PASS" -e POSTGRES_USER="$USER" -e POSTGRES_DB="$DB" \
  "$IMAGE" >/dev/null
for _ in $(seq 1 60); do
  if docker exec "$DST_NAME" pg_isready -U "$USER" -d "$DB" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker cp "$OUT/ispf.dump" "$DST_NAME:/tmp/ispf.dump"
docker exec "$DST_NAME" pg_restore -U "$USER" -d "$DB" --clean --if-exists /tmp/ispf.dump

SRC_FLYWAY=$(docker exec "$SRC_NAME" psql -U "$USER" -d "$DB" -Atc 'SELECT count(*) FROM flyway_schema_history')
DST_FLYWAY=$(docker exec "$DST_NAME" psql -U "$USER" -d "$DB" -Atc 'SELECT count(*) FROM flyway_schema_history')
SRC_USERS=$(docker exec "$SRC_NAME" psql -U "$USER" -d "$DB" -Atc 'SELECT count(*) FROM users')
DST_USERS=$(docker exec "$DST_NAME" psql -U "$USER" -d "$DB" -Atc 'SELECT count(*) FROM users')

if [[ "$SRC_FLYWAY" != "$DST_FLYWAY" || "$SRC_USERS" != "$DST_USERS" ]]; then
  echo "Row count mismatch src_flyway=$SRC_FLYWAY dst_flyway=$DST_FLYWAY src_users=$SRC_USERS dst_users=$DST_USERS" >&2
  RESULT="FAIL"
fi

if [[ -n "${ISPF_BASE_URL:-}" ]]; then
  echo "==> Optional login smoke against $ISPF_BASE_URL (response body discarded)"
  LOGIN_PAYLOAD=$(python3 - <<'PY'
import json, os
print(json.dumps({
    "username": os.environ.get("ISPF_DEPLOY_USER", "admin"),
    "password": os.environ.get("ISPF_DEPLOY_PASSWORD", "admin"),
}))
PY
)
  if printf '%s' "$LOGIN_PAYLOAD" | curl -fsS -o /dev/null -X POST "$ISPF_BASE_URL/api/v1/auth/login" \
      -H "Content-Type: application/json" \
      --data-binary @-; then
    LOGIN_NOTE="PASS"
  else
    LOGIN_NOTE="FAIL"
    RESULT="FAIL"
  fi
fi

{
  echo "# DR restore drill transcript"
  echo
  echo "- date: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "- image: $IMAGE"
  echo "- tiers restored: **T2** (Postgres dump→restore lab)"
  echo "- tiers NOT restored: T1 tree import, T3 historian, T4 secrets-key"
  echo "- flyway_schema_history: src=$SRC_FLYWAY dst=$DST_FLYWAY"
  echo "- users: src=$SRC_USERS dst=$DST_USERS"
  echo "- optional login: $LOGIN_NOTE"
  echo "- result: $RESULT"
} | tee "$OUT/transcript.md"

if [[ "$RESULT" != "PASS" ]]; then
  exit 1
fi

echo "OK — transcript $OUT/transcript.md"
echo "Archive a redacted copy under docs/evidence/dr-restore/ when refreshing the claim board."
