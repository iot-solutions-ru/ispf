#!/usr/bin/env bash
# Stop orphaned emqtt-bench containers left after interrupted load tests.
set -euo pipefail
IMAGE="${EMQTT_BENCH_IMAGE:-emqx/emqtt-bench:latest}"
LABEL="${EMQTT_BENCH_LABEL:-ispf.emqtt-bench}"

ids=""
ids=$(docker ps -q --filter "label=${LABEL}=1" 2>/dev/null || true)
if [[ -z "$ids" ]]; then
  ids=$(docker ps -q --filter "ancestor=${IMAGE}" 2>/dev/null || true)
fi
if [[ -z "$ids" ]]; then
  ids=$(docker ps --format '{{.ID}} {{.Command}}' 2>/dev/null | grep -F 'emqtt_bench' | awk '{print $1}' | tr '\n' ' ' || true)
fi

if [[ -z "${ids// /}" ]]; then
  echo "No stale emqtt-bench containers."
  exit 0
fi

count=$(echo "$ids" | wc -w | tr -d ' ')
echo "Stopping ${count} emqtt-bench container(s)..."
# shellcheck disable=SC2086
docker stop -t 3 $ids >/dev/null 2>&1 || true
# shellcheck disable=SC2086
docker rm -f $ids >/dev/null 2>&1 || true
echo "Done."
