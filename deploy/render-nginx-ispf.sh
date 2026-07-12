#!/usr/bin/env bash
# Render deploy/nginx-ispf.conf for a real public host (repo template uses ispf.example.invalid).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE="${ROOT}/deploy/nginx-ispf.conf"
PUBLIC_HOST="${1:-ispf.example.invalid}"
AI_HOST="${2:-ai.example.invalid}"

if [[ ! -f "$TEMPLATE" ]]; then
  echo "Missing template: $TEMPLATE" >&2
  exit 1
fi

sed \
  -e "s/ispf\\.example\\.invalid/${PUBLIC_HOST}/g" \
  -e "s/ai\\.example\\.invalid/${AI_HOST}/g" \
  "$TEMPLATE"
