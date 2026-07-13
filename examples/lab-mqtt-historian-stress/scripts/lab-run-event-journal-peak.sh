#!/usr/bin/env bash
# I-03 peak: EVENT_JOURNAL_ONLY → Scylla (per-topic, 16×32k target).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

export PROFILE=peak
export DEVICES="${DEVICES:-16}"
export RATE_PER_DEVICE="${RATE_PER_DEVICE:-32000}"
export EMQTT_SHARD_MAX="${EMQTT_SHARD_MAX:-8}"
export INTERVAL_MS="${INTERVAL_MS:-1}"
export PHASE="${PHASE:-60}"
export WARMUP="${WARMUP:-20}"
export STABILIZE_SEC="${STABILIZE_SEC:-90}"
export CALLBACK_THREADS="${CALLBACK_THREADS:-64}"
export MIN_RATE_EVENTS="${MIN_RATE_EVENTS:-80000}"
export WAIT_QUEUE_DRAIN_SEC="${WAIT_QUEUE_DRAIN_SEC:-180}"

exec bash "${SCRIPT_DIR}/lab-single-mqtt-event-journal-test.sh" "$@"
