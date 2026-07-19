#!/usr/bin/env bash
# Analytics + historian scale gate (BL-161 / BL-210). Tracked replacement for
# gitignored deploy/tools/analytics-*-gate.sh and deploy/local/tools copies.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

OUT_DIR="${ISPF_ANALYTICS_SCALE_OUT:-$ROOT/build/analytics-scale}"
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

# Optional live lab probes (Enterprise L) — SKIP when unset
CATALOG_STATUS="SKIP"
CH_STATUS="SKIP"
BASE_URL="${ISPF_ANALYTICS_BENCH_BASE_URL:-}"
CH_URL="${ISPF_ANALYTICS_BENCH_CH_URL:-${ISPF_VARIABLE_HISTORY_CLICKHOUSE_URL:-}}"
CATALOG_MIN="${ISPF_ANALYTICS_BENCH_CATALOG_MIN:-${ISPF_ANALYTICS_BENCH_CATALOG_MIN_TAGS:-50000}}"
CH_MIN="${ISPF_ANALYTICS_BENCH_CH_MIN_SAMPLES:-1000000000}"
SKIP_CH="${ISPF_ANALYTICS_BENCH_SKIP_CH_GATE:-true}"
SKIP_CATALOG="${ISPF_ANALYTICS_BENCH_SKIP_CATALOG_GATE:-true}"

if [[ -n "$BASE_URL" && "$SKIP_CATALOG" != "true" ]]; then
  echo "==> Live catalog probe @ $BASE_URL"
  if curl -sf "${BASE_URL%/}/api/v1/info" >/dev/null; then
    CATALOG_STATUS="PASS (reachable; count gate is lab sign-off — target ≥${CATALOG_MIN})"
  else
    CATALOG_STATUS="FAIL"
    OVERALL="FAIL"
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
  echo "| catalog (≥${CATALOG_MIN}) | ${CATALOG_STATUS} | \`ISPF_ANALYTICS_BENCH_BASE_URL\` |"
  echo "| clickhouse samples (≥${CH_MIN}) | ${CH_STATUS} | \`ISPF_ANALYTICS_BENCH_CH_URL\` |"
  echo
  echo "| Field | Value |"
  echo "|-------|-------|"
  echo "| Overall | **${OVERALL}** |"
  echo "| Analytics p95 ceiling | ${P95_ANALYTICS} ms |"
  echo "| When | $(date -u +%Y-%m-%dT%H:%M:%SZ) |"
  echo
  echo "JVM gates are sufficient for Phase 28 **Done** in CI. Catalog/CH rows are Enterprise L lab sign-off (scorecard ≥9.5)."
} > "$REPORT"

echo "Report: $REPORT"
[[ "$OVERALL" == "PASS" ]]
