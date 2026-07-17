#!/usr/bin/env bash
# BL-178: live LLM regression suite → results JSON → pass-rate gate (≥95%).
# Requires: ISPF_LLM_SMOKE=true, ISPF_AI_BASE_URL, ISPF_AI_API_KEY (or OPENAI_API_KEY).
#
# Modes (AGENT_LIVE_SUITE_MODE):
#   platform — kind=platform-primitive only (fast)
#   bundle   — scenarios with bundle refs (+ platform-primitive)
#   full     — all scenarios (default; slow, needs LLM budget)
#
# Optional: AGENT_LIVE_SUITE_MAX, AGENT_LIVE_SUITE_RESULTS, AGENT_LIVE_SUITE_ENFORCE=true
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

MODE="${AGENT_LIVE_SUITE_MODE:-full}"
OUT="${AGENT_LIVE_SUITE_RESULTS:-build/agent-regression/live-suite-results.json}"
mkdir -p "$(dirname "$OUT")"

if [[ "${ISPF_LLM_SMOKE:-}" != "true" ]]; then
  echo "FAIL: set ISPF_LLM_SMOKE=true to run live suite"
  exit 1
fi
if [[ -z "${ISPF_AI_API_KEY:-${OPENAI_API_KEY:-}}" ]]; then
  echo "FAIL: ISPF_AI_API_KEY or OPENAI_API_KEY required"
  exit 1
fi
if [[ -z "${ISPF_AI_BASE_URL:-}" ]]; then
  echo "FAIL: ISPF_AI_BASE_URL required"
  exit 1
fi

export AGENT_LIVE_SUITE_MODE="$MODE"
export AGENT_LIVE_SUITE_RESULTS="$OUT"
export AGENT_LIVE_SUITE_ENFORCE="${AGENT_LIVE_SUITE_ENFORCE:-true}"

echo "=== BL-178 live suite (mode=$MODE) ==="
set +e
./gradlew :packages:ispf-server:test \
  --tests com.ispf.server.ai.agent.AgentLiveRegressionSuiteTest \
  --no-daemon
STATUS=$?
set -e

if [[ ! -f "$OUT" ]]; then
  echo "FAIL: results file not written: $OUT"
  exit 1
fi

echo "=== pass-rate gate on $OUT ==="
if [[ "$MODE" == "full" ]]; then
  node tools/agent-regression/validate-scenarios.mjs --results "$OUT" --enforce-rate
else
  # Subset of scenarios — score only ids present in results
  node tools/agent-regression/validate-scenarios.mjs --results "$OUT" --enforce-rate --oneshot
fi

if [[ "$STATUS" -ne 0 ]]; then
  echo "FAIL: AgentLiveRegressionSuiteTest exit=$STATUS"
  exit "$STATUS"
fi

echo "OK: BL-178 live suite gate passed (mode=$MODE)"
