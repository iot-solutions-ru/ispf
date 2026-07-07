#!/usr/bin/env bash
# BL-178: validate agent-regression scenario JSON + referenced bundle manifests.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
node tools/agent-regression/validate-scenarios.mjs "$@"
