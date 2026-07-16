#!/usr/bin/env bash
# Thin wrapper for CI / Unix shells. Prefer the .py on Windows.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
exec python3 "$ROOT/deploy/tools/golden-path-alarm-smoke.py" "$@"
