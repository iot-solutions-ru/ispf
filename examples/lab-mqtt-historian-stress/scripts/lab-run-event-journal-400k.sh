#!/usr/bin/env bash
# I-03 target ~400k events/s: 16× fan-out on one MQTT topic (~26k publish/s).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

export PROFILE=peak
export DEVICES="${DEVICES:-16}"
export SHARED_TOPIC="${SHARED_TOPIC:-ispf/loadtest/journal-fanout/temperature}"
export MQTT_PUBLISH_RATE="${MQTT_PUBLISH_RATE:-26000}"
export EMQTT_SHARD_MAX="${EMQTT_SHARD_MAX:-4}"
export SHARED_TOPIC_SHARDS="${SHARED_TOPIC_SHARDS:-4}"
export INTERVAL_MS="${INTERVAL_MS:-1}"
export PHASE="${PHASE:-60}"
export WARMUP="${WARMUP:-20}"
export STABILIZE_SEC="${STABILIZE_SEC:-60}"
export CALLBACK_THREADS="${CALLBACK_THREADS:-128}"
export MIN_RATE_EVENTS="${MIN_RATE_EVENTS:-400000}"
export EMQTT_CPU_LIMIT="${EMQTT_CPU_LIMIT:-3}"
export SKIP_DEVICE_SETUP="${SKIP_DEVICE_SETUP:-false}"
export WAIT_QUEUE_DRAIN_SEC="${WAIT_QUEUE_DRAIN_SEC:-300}"

exec bash "${SCRIPT_DIR}/lab-single-mqtt-event-journal-test.sh" "$@"
