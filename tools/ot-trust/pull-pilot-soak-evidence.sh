#!/usr/bin/env bash
# Pull Pilot #1 (and optional Pilot #2) soak evidence from the OT lab host via jump.
# Requires: sshpass, SSHPASS env (jump password), scp/ssh.
#
# Usage:
#   export SSHPASS='…'   # jump password — do not commit
#   ./tools/ot-trust/pull-pilot-soak-evidence.sh
#   ./tools/ot-trust/pull-pilot-soak-evidence.sh --day 3
set -euo pipefail

JUMP_HOST="${JUMP_HOST:-84.42.21.226}"
JUMP_PORT="${JUMP_PORT:-5031}"
JUMP_USER="${JUMP_USER:-iot-solutions}"
LAB_HOST="${LAB_HOST:-192.168.100.10}"
LAB_USER="${LAB_USER:-iot-solutions}"
DAY="${DAY:-}"
OUT_PILOT1="${OUT_PILOT1:-docs/evidence/ot-trust/pilot1-lab}"
OUT_PILOT2="${OUT_PILOT2:-docs/evidence/ot-trust/pilot2-lab}"
REMOTE_PILOT1="${REMOTE_PILOT1:-/home/iot-solutions/ispf/pilot1-modbus/evidence}"
REMOTE_PILOT2="${REMOTE_PILOT2:-/home/iot-solutions/ispf/pilot2-mqtt/evidence}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --day) DAY="$2"; shift 2 ;;
    --jump) JUMP_HOST="$2"; shift 2 ;;
    --lab) LAB_HOST="$2"; shift 2 ;;
    -h|--help)
      sed -n '1,12p' "$0"
      exit 0
      ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

if [[ -z "${SSHPASS:-}" ]]; then
  echo "FAIL: set SSHPASS to jump password (not committed)" >&2
  exit 2
fi

if ! command -v sshpass >/dev/null; then
  echo "FAIL: sshpass required" >&2
  exit 2
fi

SSH_JUMP=(sshpass -e ssh -o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no -p "$JUMP_PORT" "${JUMP_USER}@${JUMP_HOST}")

echo "Checking jump → lab …"
if ! "${SSH_JUMP[@]}" "ssh -o StrictHostKeyChecking=no -o BatchMode=yes ${LAB_USER}@${LAB_HOST} 'hostname && date -u +%Y-%m-%dT%H:%M:%SZ'"; then
  echo "FAIL: cannot reach lab via jump (auth or network). Refresh credentials out-of-band." >&2
  exit 3
fi

mkdir -p "$OUT_PILOT1" "$OUT_PILOT2"

pull_glob() {
  local remote_dir="$1"
  local local_dir="$2"
  local pattern="$3"
  "${SSH_JUMP[@]}" "ssh -o StrictHostKeyChecking=no ${LAB_USER}@${LAB_HOST} 'ls ${remote_dir}/${pattern} 2>/dev/null || true'" \
    | while read -r f; do
        [[ -z "$f" ]] && continue
        base=$(basename "$f")
        echo "pull $f → ${local_dir}/${base}"
        "${SSH_JUMP[@]}" "ssh -o StrictHostKeyChecking=no ${LAB_USER}@${LAB_HOST} \"cat '${f}'\"" > "${local_dir}/${base}"
      done
}

if [[ -n "$DAY" ]]; then
  pull_glob "$REMOTE_PILOT1" "$OUT_PILOT1" "soak-day${DAY}-*.json"
  pull_glob "$REMOTE_PILOT2" "$OUT_PILOT2" "soak-day${DAY}-*.json"
else
  pull_glob "$REMOTE_PILOT1" "$OUT_PILOT1" "soak-*.json"
  pull_glob "$REMOTE_PILOT2" "$OUT_PILOT2" "soak-*.json"
fi

# Also copy soak-latest if present
pull_glob "$REMOTE_PILOT1" "$OUT_PILOT1" "soak-latest.json"
pull_glob "$REMOTE_PILOT2" "$OUT_PILOT2" "soak-latest.json"

echo "Done. Review ${OUT_PILOT1} / ${OUT_PILOT2} and append journals."
