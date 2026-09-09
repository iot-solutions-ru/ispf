#!/usr/bin/env bash
# Fail if committed AI context packs still claim live ERP/OGP outbox delivery (`sent`).
# Sources of truth (examples/*/bundle.json) use stub status `simulated`.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PACKS=(
  "$ROOT/ai/context/generated/ispf-context-pack.json"
  "$ROOT/packages/ispf-ai-agent/src/main/resources/ai/context-pack.json"
)
bad=0
for pack in "${PACKS[@]}"; do
  if [[ ! -f "$pack" ]]; then
    echo "MISSING: $pack" >&2
    bad=1
    continue
  fi
  if grep -n "SET status = 'sent'" "$pack" >/dev/null; then
    echo "DISHONEST outbox status 'sent' in $pack — run: python tools/ai-pack/build.py" >&2
    grep -n "SET status = 'sent'" "$pack" | head -5 >&2
    bad=1
  fi
done
if [[ "$bad" -ne 0 ]]; then
  exit 1
fi
echo "context-pack outbox honesty OK (no SET status = 'sent')"
