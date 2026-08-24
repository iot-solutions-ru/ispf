#!/usr/bin/env bash
# Post-S33: BL-180 field soak on remote ISPF stand (VPS or named site).
# Wraps vps-generator-oneshot.ps1 when pwsh is available; falls back to curl instructions.
#
# Usage:
#   ISPF_VPS_URL=https://ispf.iot-solutions.ru \
#   ISPF_VPS_USER=admin ISPF_VPS_PASSWORD=… \
#   bash tools/agent-regression/run-vps-field-soak.sh hvac
#
# Evidence: build/agent-regression/live-generator-results.json
# Archive: docs/evidence/ai-generator/YYYY-MM-DD-<site>-<domain>.json
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

DOMAIN="${1:-${AGENT_LIVE_GENERATOR_DOMAIN:-hvac}}"
BASE_URL="${ISPF_VPS_URL:-${ISPF_BASE_URL:-https://ispf.iot-solutions.ru}}"
USER="${ISPF_VPS_USER:-${ISPF_DEPLOY_USER:-admin}}"
PASS="${ISPF_VPS_PASSWORD:-${ISPF_DEPLOY_PASSWORD:-}}"
OUT="${AGENT_LIVE_GENERATOR_RESULTS:-build/agent-regression/live-generator-results.json}"
SITE="${ISPF_FIELD_SITE:-vps}"

case "$DOMAIN" in
  hvac|mes|scada) ;;
  *)
    echo "FAIL: domain must be hvac|mes|scada (got: $DOMAIN)" >&2
    exit 1
    ;;
esac

mkdir -p "$(dirname "$OUT")"

if command -v pwsh >/dev/null 2>&1; then
  if [ -z "$PASS" ]; then
    PASS="${ISPF_DEPLOY_PASSWORD:-admin}"
  fi
  pwsh "$ROOT/tools/agent-regression/vps-generator-oneshot.ps1" \
    -BaseUrl "$BASE_URL" \
    -Domain "$DOMAIN" \
    -Username "$USER" \
    -Password "$PASS" \
    -Out "$OUT"
else
  export ISPF_VPS_URL="$BASE_URL" ISPF_VPS_USER="$USER" AGENT_LIVE_GENERATOR_RESULTS="$OUT"
  export ISPF_VPS_PASSWORD="${PASS:-${ISPF_DEPLOY_PASSWORD:-admin}}"
  bash "$ROOT/tools/agent-regression/vps-generator-oneshot.sh" "$DOMAIN"
fi

node "$ROOT/tools/agent-regression/validate-generator-evidence.mjs" --results "$OUT"

ARCHIVE="$ROOT/docs/evidence/ai-generator/$(date -u +%Y-%m-%d)-${SITE}-${DOMAIN}.json"
mkdir -p "$(dirname "$ARCHIVE")"
cp "$OUT" "$ARCHIVE"
echo "Archived → $ARCHIVE"
echo "Fill journal: docs/evidence/ai-generator-soak-journal.template.md"
