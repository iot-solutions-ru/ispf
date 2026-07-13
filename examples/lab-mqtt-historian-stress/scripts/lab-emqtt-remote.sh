#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lab-loadgen-common.sh
source "${SCRIPT_DIR}/lab-loadgen-common.sh"

BENCH_SH="${ISPF_LAB_LOADGEN_ROOT}/loadtest/mqtt-emqtt-bench.sh"
if [[ $# -eq 0 ]]; then
  echo "Usage: $0 -- [mqtt-emqtt-bench.sh args...]" >&2
  echo "  e.g. $0 -- --devices 4 --messages-per-second 2000 --duration-seconds 90" >&2
  exit 2
fi
if [[ "${1:-}" == "--" ]]; then
  shift
fi

remote_args=(
  --host "${MQTT_PUBLISH_HOST}"
  --port "${MQTT_PUBLISH_PORT}"
)
while [[ $# -gt 0 ]]; do
  case "$1" in
    --host|--port|--docker-network)
      shift 2
      ;;
    *)
      remote_args+=("$1")
      shift
      ;;
  esac
done

quoted="$(printf '%q ' "${remote_args[@]}")"
env_prefix=""
[[ "${NUMERIC_PAYLOAD:-false}" == "true" ]] && env_prefix="NUMERIC_PAYLOAD=true "
[[ "${GATEWAY_NUMERIC_TIMESTAMP:-false}" == "true" ]] && env_prefix+="GATEWAY_NUMERIC_TIMESTAMP=true "
[[ -n "${METRICS:-}" ]] && env_prefix+="METRICS=${METRICS} "
[[ -n "${EMQTT_CPU_LIMIT:-}" ]] && env_prefix+="EMQTT_CPU_LIMIT=${EMQTT_CPU_LIMIT} "
remote_cmd="${env_prefix}bash ${BENCH_SH} ${quoted}"

echo "==> emqtt on loadgen (${ISPF_LAB_LOADGEN_SSH}): ${env_prefix}${quoted}" >&2
ssh -o BatchMode=yes -o ConnectTimeout=15 "${ISPF_LAB_LOADGEN_SSH}" "${remote_cmd}"
