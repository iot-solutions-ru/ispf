#!/usr/bin/env bash
# BL-141: smoke OT docker fixtures (MQTT, Modbus TCP, OPC UA) before driver interop CI.
# Wave 1 / B1: also exercises Modbus FC6 write + FC3 read-back when python3 is available.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

COMPOSE_FILE="${ISPF_INTEROP_COMPOSE_FILE:-deploy/driver-interop/docker-compose.yml}"
MQTT_HOST="${ISPF_INTEROP_MQTT_HOST:-127.0.0.1}"
MQTT_PORT="${ISPF_INTEROP_MQTT_PORT:-1883}"
MODBUS_HOST="${ISPF_INTEROP_MODBUS_HOST:-127.0.0.1}"
MODBUS_PORT="${ISPF_INTEROP_MODBUS_PORT:-502}"
OPCUA_HOST="${ISPF_INTEROP_OPCUA_HOST:-127.0.0.1}"
OPCUA_PORT="${ISPF_INTEROP_OPCUA_PORT:-4840}"
WAIT_SEC="${ISPF_INTEROP_SMOKE_WAIT_SEC:-120}"
MOSQUITTO_CONTAINER="${ISPF_INTEROP_MOSQUITTO_CONTAINER:-ispf-interop-mosquitto}"

REPORT_DIR="${ISPF_INTEROP_REPORT_DIR:-$ROOT/build/driver-interop}"
mkdir -p "$REPORT_DIR"
SUMMARY_FILE="$REPORT_DIR/fixture-smoke-summary.md"

PASS=0
FAIL=0

log() {
  echo "==> $*"
}

record() {
  local name="$1"
  local result="$2"
  local detail="${3:-}"
  if [[ "$result" == "pass" ]]; then
    PASS=$((PASS + 1))
    log "$name: pass${detail:+ ($detail)}"
  else
    FAIL=$((FAIL + 1))
    log "$name: FAIL${detail:+ ($detail)}" >&2
  fi
  echo "- \`${name}\`: **${result}**${detail:+ — ${detail}}" >>"$SUMMARY_FILE"
}

wait_for_tcp() {
  local host="$1"
  local port="$2"
  local label="$3"
  local deadline=$((SECONDS + WAIT_SEC))
  while ((SECONDS < deadline)); do
    if timeout 2 bash -c "</dev/tcp/${host}/${port}" 2>/dev/null; then
      record "$label" pass "tcp://${host}:${port}"
      return 0
    fi
    sleep 2
  done
  record "$label" fail "timeout after ${WAIT_SEC}s (tcp://${host}:${port})"
  return 1
}

mqtt_roundtrip() {
  local topic="ispf/lab/smoke-$(date +%s)"
  local payload="ok"
  if docker ps --format '{{.Names}}' | grep -qx "$MOSQUITTO_CONTAINER"; then
    if docker exec "$MOSQUITTO_CONTAINER" sh -c "
      mosquitto_sub -h localhost -t '$topic' -C 1 -W 5 > /tmp/ispf-mqtt-smoke.txt &
      sub_pid=\$!
      sleep 1
      mosquitto_pub -h localhost -t '$topic' -m '$payload'
      wait \$sub_pid
      grep -qx '$payload' /tmp/ispf-mqtt-smoke.txt
    "; then
      record "mqtt-roundtrip" pass "topic ${topic}"
      return 0
    fi
    record "mqtt-roundtrip" fail "docker exec publish/subscribe"
    return 1
  fi
  if command -v mosquitto_pub >/dev/null 2>&1 && command -v mosquitto_sub >/dev/null 2>&1; then
    mosquitto_sub -h "$MQTT_HOST" -p "$MQTT_PORT" -t "$topic" -C 1 -W 5 > /tmp/ispf-mqtt-smoke.txt &
    local sub_pid=$!
    sleep 1
    mosquitto_pub -h "$MQTT_HOST" -p "$MQTT_PORT" -t "$topic" -m "$payload"
    if wait "$sub_pid" && grep -qx "$payload" /tmp/ispf-mqtt-smoke.txt; then
      record "mqtt-roundtrip" pass "topic ${topic}"
      return 0
    fi
    record "mqtt-roundtrip" fail "host mosquitto clients"
    return 1
  fi
  record "mqtt-roundtrip" pass "skipped (no mosquitto client; tcp ok)"
  return 0
}

# Wave 1 / B1: Modbus TCP FC6 write + FC3 read-back (stdlib Python; no pymodbus).
modbus_write_roundtrip() {
  if ! command -v python3 >/dev/null 2>&1; then
    record "modbus-write-roundtrip" pass "skipped (no python3; tcp ok)"
    return 0
  fi
  if MODBUS_HOST="$MODBUS_HOST" MODBUS_PORT="$MODBUS_PORT" python3 - <<'PY'
import os, socket, struct

host = os.environ.get("MODBUS_HOST", "127.0.0.1")
port = int(os.environ.get("MODBUS_PORT", "502"))
unit = 1
addr = 0
value = 0xA5A5
tid = 1

def txn(func: int, pdu: bytes) -> bytes:
    global tid
    tid = (tid + 1) & 0xFFFF
    mbap = struct.pack(">HHHB", tid, 0, len(pdu) + 1, unit)
    with socket.create_connection((host, port), timeout=5) as s:
        s.sendall(mbap + pdu)
        hdr = s.recv(7)
        if len(hdr) < 7:
            raise RuntimeError(f"short mbap {hdr!r}")
        _, _, length, _ = struct.unpack(">HHHB", hdr)
        body = b""
        need = length - 1
        while len(body) < need:
            chunk = s.recv(need - len(body))
            if not chunk:
                break
            body += chunk
    if not body or body[0] == func + 0x80:
        raise RuntimeError(f"modbus exception func={func:#x} body={body!r}")
    return body

txn(6, struct.pack(">BHH", 6, addr, value))
body = txn(3, struct.pack(">BHH", 3, addr, 1))
if len(body) < 4 or body[0] != 3:
    raise RuntimeError(f"unexpected read body {body!r}")
got = struct.unpack(">H", body[2:4])[0]
if got != value:
    raise RuntimeError(f"readback {got:#x} != wrote {value:#x}")
print(f"fc6/fc3 ok reg={addr} value={value:#x}")
PY
  then
    record "modbus-write-roundtrip" pass "FC6/FC3 holding[0]"
    return 0
  fi
  record "modbus-write-roundtrip" fail "FC6/FC3 against ${MODBUS_HOST}:${MODBUS_PORT}"
  return 1
}

{
  echo "# Driver interop fixture smoke (BL-141)"
  echo
  echo "Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo
  echo "Compose: \`${COMPOSE_FILE}\`"
  echo
} >"$SUMMARY_FILE"

log "Waiting for docker fixture endpoints (timeout ${WAIT_SEC}s)"
FAILED=0
wait_for_tcp "$MQTT_HOST" "$MQTT_PORT" "mqtt-tcp" || FAILED=1
wait_for_tcp "$MODBUS_HOST" "$MODBUS_PORT" "modbus-tcp" || FAILED=1
wait_for_tcp "$OPCUA_HOST" "$OPCUA_PORT" "opcua-tcp" || FAILED=1

if [[ "$FAILED" -eq 0 ]]; then
  mqtt_roundtrip || FAILED=1
  modbus_write_roundtrip || FAILED=1
fi

{
  echo
  echo "Pass: **${PASS}** / Fail: **${FAIL}**"
} >>"$SUMMARY_FILE"

echo
echo "Fixture smoke summary: $SUMMARY_FILE"
cat "$SUMMARY_FILE"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  echo >>"$GITHUB_STEP_SUMMARY"
  cat "$SUMMARY_FILE" >>"$GITHUB_STEP_SUMMARY"
fi

if [[ "$FAIL" -gt 0 || "$FAILED" -ne 0 ]]; then
  exit 1
fi
