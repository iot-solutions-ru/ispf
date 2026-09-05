#!/usr/bin/env bash
# Analytics + historian scale gate (BL-161 / BL-210). Tracked replacement for
# gitignored deploy/tools/analytics-*-gate.sh and deploy/local/tools copies.
#
# Catalog gate counts **history-enabled variables** under TAG_PREFIX — not
# GET /platform/analytics/tags (those are binding-rule analytics tags).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

OUT_DIR="${ISPF_ANALYTICS_SCALE_OUT:-${ISPF_ANALYTICS_BENCH_DIR:-$ROOT/build/analytics-scale}}"
mkdir -p "$OUT_DIR"
REPORT="$OUT_DIR/analytics-scale-gate.md"
LOG="$OUT_DIR/analytics-scale-gate.log"

P95_ANALYTICS="${ISPF_ANALYTICS_LOAD_P95_CEILING_MS:-3000}"
export ISPF_ANALYTICS_LOAD_P95_CEILING_MS="$P95_ANALYTICS"

OVERALL="PASS"
declare -a ROWS=()

run_jvm() {
  local name="$1"
  local fqcn="$2"
  local log="$OUT_DIR/${name}.log"
  echo "==> JVM gate: $name ($fqcn)"
  set +e
  set -o pipefail
  ./gradlew \
    :packages:ispf-server:test \
    --tests "$fqcn" \
    --no-daemon \
    -Dorg.gradle.workers.max=1 \
    2>&1 | tee "$log"
  local rc=${PIPESTATUS[0]}
  set +o pipefail
  set -e
  if [[ $rc -eq 0 ]]; then
    ROWS+=("| ${name} | PASS | \`${log#$ROOT/}\` |")
  else
    ROWS+=("| ${name} | FAIL | \`${log#$ROOT/}\` |")
    OVERALL="FAIL"
  fi
}

echo "==> analytics-scale-gate (tracked tools/historian-scale)" | tee "$LOG"

run_jvm "historian-aggregate-1M" "com.ispf.server.history.HistorianAggregateQueryLoadTest"
run_jvm "analytics-multi-tag" "com.ispf.server.platform.analytics.AnalyticsMultiTagQueryLoadTest"

# Optional live lab probes (Enterprise L) — SKIP when unset / skip flags true
CATALOG_STATUS="SKIP"
CH_STATUS="SKIP"
BASE_URL="${ISPF_ANALYTICS_BENCH_BASE_URL:-${ISPF_BENCH_BASE_URL:-}}"
CH_URL="${ISPF_ANALYTICS_BENCH_CH_URL:-${ISPF_VARIABLE_HISTORY_CLICKHOUSE_URL:-}}"
CATALOG_MIN="${ISPF_ANALYTICS_BENCH_CATALOG_MIN:-${ISPF_ANALYTICS_BENCH_CATALOG_MIN_TAGS:-50000}}"
CH_MIN="${ISPF_ANALYTICS_BENCH_CH_MIN_SAMPLES:-1000000000}"
SKIP_CH="${ISPF_ANALYTICS_BENCH_SKIP_CH_GATE:-true}"
SKIP_CATALOG="${ISPF_ANALYTICS_BENCH_SKIP_CATALOG_GATE:-true}"
TAG_PREFIX="${ISPF_ANALYTICS_BENCH_TAG_PREFIX:-root.platform.devices.analytics-scale-lab}"
CATALOG_MODE="${ISPF_ANALYTICS_BENCH_CATALOG_MODE:-history-enabled}"
BENCH_USER="${ISPF_ANALYTICS_BENCH_USERNAME:-${ISPF_BENCH_USER:-admin}}"
BENCH_PASSWORD="${ISPF_ANALYTICS_BENCH_PASSWORD:-${ISPF_BENCH_PASSWORD:-admin}}"
BENCH_TOKEN="${ISPF_ANALYTICS_BENCH_TOKEN:-${ISPF_BENCH_TOKEN:-}}"

count_history_enabled() {
  local count=""
  local compose="${ISPF_ANALYTICS_BENCH_CATALOG_PG_COMPOSE:-}"
  if [[ -z "$compose" && -f "$ROOT/lab-cluster-compose.yml" ]]; then
    compose="$ROOT/lab-cluster-compose.yml"
  fi
  if [[ -n "$compose" && -f "$compose" ]]; then
    count="$(docker compose -f "$compose" exec -T postgres psql -U ispf -d ispf -tAc \
      "SELECT COUNT(*) FROM object_variables WHERE object_path LIKE '${TAG_PREFIX}.%' AND history_enabled = true;" \
      2>/dev/null | tr -d '[:space:]' || true)"
    if [[ -n "${count:-}" && "$count" =~ ^[0-9]+$ ]]; then
      echo "$count"
      return 0
    fi
  fi

  if [[ -z "$BASE_URL" ]]; then
    return 1
  fi

  local py_args=(
    --base-url "${BASE_URL%/}"
    --path-prefix "$TAG_PREFIX"
  )
  if [[ -n "$BENCH_TOKEN" ]]; then
    py_args+=(--token "$BENCH_TOKEN")
  else
    py_args+=(--username "$BENCH_USER" --password "$BENCH_PASSWORD")
  fi
  python3 "$ROOT/tools/historian-scale/count-history-enabled.py" "${py_args[@]}" 2>>"$LOG" \
    | tr -d '[:space:]'
}

if [[ -n "$BASE_URL" || "$SKIP_CATALOG" != "true" ]]; then
  if [[ "$SKIP_CATALOG" != "true" ]]; then
    echo "==> Live catalog probe (mode=${CATALOG_MODE}, prefix=${TAG_PREFIX})"
    if [[ "$CATALOG_MODE" != "history-enabled" ]]; then
      CATALOG_STATUS="FAIL (unsupported CATALOG_MODE=${CATALOG_MODE}; use history-enabled)"
      OVERALL="FAIL"
    else
      COUNT="$(count_history_enabled || true)"
      if [[ -n "$COUNT" && "$COUNT" =~ ^[0-9]+$ && "$COUNT" -ge "$CATALOG_MIN" ]]; then
        CATALOG_STATUS="PASS (${COUNT} ≥ ${CATALOG_MIN})"
      elif [[ -n "$COUNT" && "$COUNT" =~ ^[0-9]+$ ]]; then
        CATALOG_STATUS="FAIL (${COUNT} < ${CATALOG_MIN})"
        OVERALL="FAIL"
      else
        CATALOG_STATUS="FAIL (could not count history-enabled under ${TAG_PREFIX})"
        OVERALL="FAIL"
      fi
    fi
  fi
fi

if [[ -n "$CH_URL" && "$SKIP_CH" != "true" ]]; then
  echo "==> ClickHouse sample count probe @ $CH_URL"
  DB="${ISPF_VARIABLE_HISTORY_CLICKHOUSE_DATABASE:-ispf}"
  TABLE="${ISPF_VARIABLE_HISTORY_CLICKHOUSE_TABLE:-variable_samples}"
  AUTH_USER="${ISPF_VARIABLE_HISTORY_CLICKHOUSE_USERNAME:-default}"
  AUTH_PASS="${ISPF_VARIABLE_HISTORY_CLICKHOUSE_PASSWORD:-}"
  COUNT="$(curl -sf -u "${AUTH_USER}:${AUTH_PASS}" \
    "${CH_URL%/}/?query=SELECT%20count()%20FROM%20${DB}.${TABLE}" 2>/dev/null || echo "")"
  if [[ -n "$COUNT" && "$COUNT" =~ ^[0-9]+$ && "$COUNT" -ge "$CH_MIN" ]]; then
    CH_STATUS="PASS (${COUNT} ≥ ${CH_MIN})"
  elif [[ -n "$COUNT" && "$COUNT" =~ ^[0-9]+$ ]]; then
    CH_STATUS="FAIL (${COUNT} < ${CH_MIN})"
    OVERALL="FAIL"
  else
    CH_STATUS="FAIL (unreachable)"
    OVERALL="FAIL"
  fi
fi

{
  echo "# Analytics scale gate"
  echo
  echo "| Gate | Status | Evidence |"
  echo "|------|--------|----------|"
  for row in "${ROWS[@]+"${ROWS[@]}"}"; do
    echo "$row"
  done
  echo "| catalog history-enabled (≥${CATALOG_MIN}) | ${CATALOG_STATUS} | \`prefix=${TAG_PREFIX}\` |"
  echo "| clickhouse samples (≥${CH_MIN}) | ${CH_STATUS} | \`ISPF_ANALYTICS_BENCH_CH_URL\` |"
  echo
  echo "| Field | Value |"
  echo "|-------|-------|"
  echo "| Overall | **${OVERALL}** |"
  echo "| Analytics p95 ceiling | ${P95_ANALYTICS} ms |"
  echo "| Catalog mode | ${CATALOG_MODE} |"
  echo "| When | $(date -u +%Y-%m-%dT%H:%M:%SZ) |"
  echo
  echo "JVM gates are sufficient for Phase 28 **Done** in CI. Catalog/CH rows are Enterprise L lab sign-off (scorecard ≥9.5)."
  echo
  echo "Catalog counts **history-enabled variables**, not \`/platform/analytics/tags\` binding rules."
} > "$REPORT"

echo "Report: $REPORT"
[[ "$OVERALL" == "PASS" ]]
