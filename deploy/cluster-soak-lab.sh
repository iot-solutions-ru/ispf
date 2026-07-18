#!/usr/bin/env bash
# Lab-only cluster soak: sustained LB traffic + mid-window chaos (kill owner / smoke).
# Not for CI — see docs/en/cluster-chaos-soak-runbook.md § Soak window.
#
# Usage:
#   bash deploy/cluster-soak-lab.sh                     # 30 minutes
#   bash deploy/cluster-soak-lab.sh --duration-min 60
#   bash deploy/cluster-soak-lab.sh --duration-min 2     # dry-run / short lab
#   bash deploy/cluster-soak-lab.sh --no-chaos          # load only + final smoke
#   OPERATOR=alice bash deploy/cluster-soak-lab.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# Repo layout: deploy/cluster-soak-lab.sh → ROOT=repo. Lab host: ~/ispf/cluster-soak-lab.sh → ROOT=that dir.
if [[ -f "${SCRIPT_DIR}/cluster-smoke-test.sh" ]]; then
  ROOT="${SCRIPT_DIR}"
  SMOKE_SCRIPT="${SCRIPT_DIR}/cluster-smoke-test.sh"
  DEFAULT_COMPOSE="${SCRIPT_DIR}/lab-cluster-compose.yml"
  if [[ ! -f "${DEFAULT_COMPOSE}" ]]; then
    DEFAULT_COMPOSE="${SCRIPT_DIR}/docker-compose.cluster.yml"
  fi
  DEFAULT_JOURNAL_DIR="${SCRIPT_DIR}/journals"
elif [[ -f "${SCRIPT_DIR}/../deploy/cluster-smoke-test.sh" ]]; then
  ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
  SMOKE_SCRIPT="${ROOT}/deploy/cluster-smoke-test.sh"
  DEFAULT_COMPOSE="${ROOT}/deploy/docker-compose.cluster.yml"
  DEFAULT_JOURNAL_DIR="${ROOT}/deploy/journals"
else
  echo "ERROR: cluster-smoke-test.sh not found next to this script or under deploy/" >&2
  exit 1
fi

COMPOSE_FILE="${ISPF_CLUSTER_COMPOSE_FILE:-${DEFAULT_COMPOSE}}"
PORT="${ISPF_CLUSTER_PORT:-8088}"
DURATION_MIN="${ISPF_CLUSTER_SOAK_MINUTES:-30}"
RUN_CHAOS=1
OPERATOR="${OPERATOR:-$(whoami 2>/dev/null || echo unknown)}"
JOURNAL_DIR="${ISPF_CLUSTER_SOAK_JOURNAL_DIR:-${DEFAULT_JOURNAL_DIR}}"
BASE="http://127.0.0.1:${PORT}"
CURL=(curl -sf --no-keepalive -H "Connection: close")

args=("$@")
i=0
while [[ $i -lt ${#args[@]} ]]; do
  case "${args[$i]}" in
    --duration-min=*) DURATION_MIN="${args[$i]#*=}" ;;
    --duration-min)
      i=$((i + 1))
      DURATION_MIN="${args[$i]:-}"
      ;;
    --no-chaos) RUN_CHAOS=0 ;;
    --help|-h)
      sed -n '2,12p' "$0"
      exit 0
      ;;
  esac
  i=$((i + 1))
done

if ! [[ "$DURATION_MIN" =~ ^[0-9]+$ ]] || [[ "$DURATION_MIN" -lt 1 ]]; then
  echo "ERROR: --duration-min must be a positive integer (got ${DURATION_MIN})" >&2
  exit 1
fi

DURATION_SEC=$((DURATION_MIN * 60))
MID_SEC=$((DURATION_SEC / 2))
[[ "$MID_SEC" -lt 15 ]] && MID_SEC=15
[[ "$MID_SEC" -ge "$DURATION_SEC" ]] && MID_SEC=$((DURATION_SEC / 2))
if [[ "$DURATION_SEC" -le 30 ]]; then
  MID_SEC=10
fi

mkdir -p "${JOURNAL_DIR}"
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
JOURNAL_FILE="${JOURNAL_DIR}/cluster-soak-${STAMP}.md"
LOAD_LOG="${JOURNAL_DIR}/cluster-soak-${STAMP}-load.log"
LOAD_PID=""

cleanup() {
  if [[ -n "${LOAD_PID}" ]] && kill -0 "${LOAD_PID}" 2>/dev/null; then
    kill "${LOAD_PID}" 2>/dev/null || true
    wait "${LOAD_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo "==> Cluster soak lab"
echo "    duration=${DURATION_MIN}m (${DURATION_SEC}s) mid-chaos@${MID_SEC}s chaos=${RUN_CHAOS}"
echo "    base=${BASE} operator=${OPERATOR}"
echo "    journal=${JOURNAL_FILE}"

echo "==> Prerequisite: LB /api/v1/info"
READY=0
for i in $(seq 1 40); do
  if "${CURL[@]}" "${BASE}/api/v1/info" >/dev/null 2>&1; then
    READY=1
    break
  fi
  sleep 2
done
if [[ "$READY" -ne 1 ]]; then
  echo "ERROR: cluster LB not ready at ${BASE} — start with deploy/cluster-quickstart.sh" >&2
  exit 1
fi

echo "==> Start background LB load (GET /api/v1/info)"
(
  ok=0
  fail=0
  while true; do
    if "${CURL[@]}" "${BASE}/api/v1/info" >/dev/null 2>&1; then
      ok=$((ok + 1))
    else
      fail=$((fail + 1))
    fi
    # light cadence — sustained, not a stress test
    sleep 0.2
    if (( (ok + fail) % 100 == 0 )); then
      echo "$(date -u +%H:%M:%S) ok=${ok} fail=${fail}" >>"${LOAD_LOG}"
    fi
  done
) &
LOAD_PID=$!

START_EPOCH=$(date +%s)
RECLAIM_NOTE="n/a"
LB_ERRORS_NOTE="see load log"
EVIDENCE="PARTIAL"
CHAOS_RC=0

echo "==> Soak until mid-window (${MID_SEC}s)…"
sleep "${MID_SEC}"

if [[ "$RUN_CHAOS" -eq 1 ]]; then
  echo "==> Mid-window chaos: cluster-smoke-test (reclaim + config-sync + live-var-lag) under load"
  echo "    smoke=${SMOKE_SCRIPT} compose=${COMPOSE_FILE}"
  set +e
  ISPF_CLUSTER_COMPOSE_FILE="${COMPOSE_FILE}" \
  ISPF_CLUSTER_PORT="${PORT}" \
  ISPF_CLUSTER_REQUIRE_DRIVER_LOCKS="${ISPF_CLUSTER_REQUIRE_DRIVER_LOCKS:-1}" \
    bash "${SMOKE_SCRIPT}" --config-sync --live-var-lag \
    | tee "${JOURNAL_DIR}/cluster-soak-${STAMP}-chaos.log"
  CHAOS_RC=${PIPESTATUS[0]}
  set -e
  if [[ "$CHAOS_RC" -eq 0 ]]; then
    RECLAIM_NOTE=$(grep -E 'reclaim SLO PASSED|lock reclaimed by|WARN: no active driver locks' "${JOURNAL_DIR}/cluster-soak-${STAMP}-chaos.log" | tail -1 || echo "smoke OK")
    EVIDENCE="REAL"
  else
    RECLAIM_NOTE="chaos FAILED (exit ${CHAOS_RC})"
    EVIDENCE="PARTIAL"
  fi
else
  echo "==> --no-chaos: skipping mid-window smoke"
fi

REMAIN=$((DURATION_SEC - ( $(date +%s) - START_EPOCH )))
if [[ "$REMAIN" -gt 0 ]]; then
  echo "==> Continue soak (${REMAIN}s remaining)…"
  sleep "${REMAIN}"
fi

cleanup
LOAD_PID=""

LOAD_SUMMARY="n/a"
if [[ -f "${LOAD_LOG}" ]]; then
  LOAD_SUMMARY=$(tail -1 "${LOAD_LOG}" || true)
fi

# Final health peek
HEALTH_NOTE="n/a"
TOKEN=$(
  "${CURL[@]}" -X POST "${BASE}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' \
    | python3 -c "import json,sys; print(json.load(sys.stdin).get('token',''))" 2>/dev/null || true
)
if [[ -n "$TOKEN" ]]; then
  HEALTH_NOTE=$(
    "${CURL[@]}" -H "Authorization: Bearer ${TOKEN}" "${BASE}/api/v1/platform/cluster/health" \
      | python3 -c "import json,sys; h=json.load(sys.stdin); print('nodesUp=%s/%s' % (h.get('nodesUp'), h.get('nodesTotal')))" 2>/dev/null || echo "health parse failed"
  )
fi

ELAPSED_MIN=$(( ( $(date +%s) - START_EPOCH + 59 ) / 60 ))
TOPOLOGY="compose ($(basename "${COMPOSE_FILE}")) port=${PORT}"

cat >"${JOURNAL_FILE}" <<EOF
# Cluster soak journal — ${STAMP}

| Field | Value |
| ----- | ----- |
| Date (UTC) | ${STAMP} |
| Duration | ${ELAPSED_MIN}m (requested ${DURATION_MIN}m) |
| Topology | ${TOPOLOGY} |
| Operator | ${OPERATOR} |
| Mid-window chaos | $([[ "$RUN_CHAOS" -eq 1 ]] && echo yes || echo no) |
| Reclaim / chaos note | ${RECLAIM_NOTE} |
| LB load (last sample) | ${LOAD_SUMMARY} |
| LB errors during kill | ${LB_ERRORS_NOTE} |
| End cluster health | ${HEALTH_NOTE} |
| Evidence class | **${EVIDENCE}** |

## Artifacts

- Load log: \`$(basename "${LOAD_LOG}")\`
- Chaos log: \`cluster-soak-${STAMP}-chaos.log\` (if chaos ran)

## Copy into runbook table

| Date | Duration | Topology | Reclaim s | LB errors during kill | Live-var lag note | Evidence class | Operator |
| ---- | -------- | -------- | --------- | --------------------- | ----------------- | -------------- | -------- |
| ${STAMP} | ${ELAPSED_MIN}m | ${TOPOLOGY} | ${RECLAIM_NOTE} | ${LB_ERRORS_NOTE} | see chaos log | ${EVIDENCE} | ${OPERATOR} |

Runbook: [docs/en/cluster-chaos-soak-runbook.md](../../docs/en/cluster-chaos-soak-runbook.md)
EOF

echo
echo "==> Soak finished — evidence ${EVIDENCE}"
echo "    journal: ${JOURNAL_FILE}"
if [[ "$CHAOS_RC" -ne 0 && "$RUN_CHAOS" -eq 1 ]]; then
  exit "$CHAOS_RC"
fi
exit 0
