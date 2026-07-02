#!/usr/bin/env bash
# ADR-0024 before/after load test helper (run from repo root).
set -euo pipefail

BASE_URL="${BASE_URL:-https://ispf.iot-solutions.ru}"
USER="${ISPF_USER:-admin}"
PASS="${ISPF_PASS:-admin}"
PHASE="${PHASE:-45}"
DEVICES="${DEVICES:-20}"
LABEL="${1:-run}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/deploy"

echo "==> [$LABEL] cleanup + seed ${DEVICES} virtual devices"
python3 loadtest-cleanup.py || true
python3 vps-load-test.py --seed-only --devices "$DEVICES" \
  --base-url "$BASE_URL" --username "$USER" --password "$PASS"

echo "==> [$LABEL] internal automation load test (${PHASE}s phase)"
python3 events-internal-load-test.py \
  --base-url "$BASE_URL" --username "$USER" --password "$PASS" \
  --skip-monitor-setup --poll-ms 1000 --phase-seconds "$PHASE" \
  --warmup-seconds 30 --max-devices "$DEVICES"

echo "==> [$LABEL] MQTT historian (TELEMETRY_ONLY, push via VPS — needs SSH)"
if [[ -n "${PUBLISH_VIA_SSH:-}" ]]; then
  python3 mqtt-ingress-load-test.py \
    --base-url "$BASE_URL" --username "$USER" --password "$PASS" \
    --mode push --broker-url tcp://127.0.0.1:1883 \
    --devices 4 --messages-per-second 2000 --telemetry-coalesce-ms 5 \
    --phase-seconds "$PHASE" --warmup-seconds 20 --skip-monitor-setup \
    --publish-via-ssh "$PUBLISH_VIA_SSH"
else
  echo "    skip MQTT push (set PUBLISH_VIA_SSH=root@ispf.iot-solutions.ru)"
fi

echo "==> [$LABEL] done — see deploy/*-report-*.json"
