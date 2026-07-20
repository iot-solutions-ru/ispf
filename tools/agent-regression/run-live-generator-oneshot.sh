#!/usr/bin/env bash
# BL-180: single-domain live solution-generator smoke + soft <15 min evidence JSON.
# Default domain: hvac (field-soak oneshot). Override: AGENT_LIVE_GENERATOR_DOMAIN=hvac|mes|scada
# Requires: ISPF_LLM_SMOKE=true, ISPF_AI_BASE_URL, ISPF_AI_MODEL, ISPF_AI_API_KEY (or OPENAI_API_KEY).
#
# Evidence: build/agent-regression/live-generator-results.json
#   softBudgetMet=true  → soft <15 min met (functional assertions also OK)
#   softBudgetMet=false → durable soft miss (still not invented as pass)
# Optional: AGENT_LIVE_GENERATOR_ENFORCE_SOFT=true fails the script when softBudgetMet is false.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

DOMAIN="${AGENT_LIVE_GENERATOR_DOMAIN:-hvac}"
OUT="${AGENT_LIVE_GENERATOR_RESULTS:-build/agent-regression/live-generator-results.json}"
mkdir -p "$(dirname "$OUT")"

case "$DOMAIN" in
  hvac|mes|scada) ;;
  *)
    echo "FAIL: AGENT_LIVE_GENERATOR_DOMAIN must be hvac|mes|scada (got: $DOMAIN)"
    exit 1
    ;;
esac

if [[ "${ISPF_LLM_SMOKE:-}" != "true" ]]; then
  echo "SKIP: set ISPF_LLM_SMOKE=true to run live generator oneshot"
  exit 0
fi

if [[ -z "${ISPF_AI_API_KEY:-${OPENAI_API_KEY:-}}" ]]; then
  echo "FAIL: ISPF_AI_API_KEY or OPENAI_API_KEY required for live generator oneshot"
  exit 1
fi
if [[ -z "${ISPF_AI_BASE_URL:-}" ]]; then
  echo "FAIL: ISPF_AI_BASE_URL required for live generator oneshot"
  exit 1
fi

export AGENT_LIVE_GENERATOR_DOMAIN="$DOMAIN"
export AGENT_LIVE_GENERATOR_RESULTS="$OUT"

echo "=== BL-180 live generator oneshot domain=${DOMAIN} → ${OUT} ==="

set +e
./gradlew :packages:ispf-ai-agent:test \
  --tests com.ispf.server.ai.generation.AiSolutionGeneratorLiveSmokeTest \
  --no-daemon
STATUS=$?
set -e

if [[ ! -f "$OUT" ]]; then
  echo "FAIL: evidence file missing: $OUT (Gradle status=$STATUS)"
  exit 1
fi

echo "Wrote $OUT"
VALIDATE_ARGS=(--results "$OUT")
if [[ "${AGENT_LIVE_GENERATOR_ENFORCE_SOFT:-}" == "true" ]]; then
  VALIDATE_ARGS+=(--enforce-soft)
fi
set +e
node tools/agent-regression/validate-generator-evidence.mjs "${VALIDATE_ARGS[@]}"
VALIDATE_STATUS=$?
set -e

if [[ "$STATUS" -ne 0 ]]; then
  echo "FAIL: AiSolutionGeneratorLiveSmokeTest exited $STATUS (see evidence for soft/functional status)"
  exit "$STATUS"
fi
if [[ "$VALIDATE_STATUS" -ne 0 ]]; then
  exit "$VALIDATE_STATUS"
fi

echo "OK: generator oneshot domain=${DOMAIN} (see softBudgetMet in $OUT)"
