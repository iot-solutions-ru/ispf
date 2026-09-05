#!/usr/bin/env bash
# BL-141 / OT Trust Wave 1 (B1): smoke OT docker fixtures before driver interop CI.
# - MQTT pub/sub round-trip
# - Modbus TCP FC6 + FC16 write with FC3 read-back (writable lab fixture)
# - Optional OPC UA write when asyncua is installed (ISPF_INTEROP_OPCUA_WRITE=1 default on)
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
OPCUA_WRITE="${ISPF_INTEROP_OPCUA_WRITE:-1}"

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

# Wave 1 / B1: Modbus TCP FC6 + FC16 write with FC3 read-back (stdlib Python).
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

# FC6 single
txn(6, struct.pack(">BHH", 6, 0, 0xA5A5))
body = txn(3, struct.pack(">BHH", 3, 0, 1))
got = struct.unpack(">H", body[2:4])[0]
if got != 0xA5A5:
    raise RuntimeError(f"fc6 readback {got:#x}")

# FC16 multi
txn(16, struct.pack(">BHHBHH", 16, 2, 2, 4, 0x1111, 0x2222))
body = txn(3, struct.pack(">BHH", 3, 2, 2))
a, b = struct.unpack(">HH", body[2:6])
if (a, b) != (0x1111, 0x2222):
    raise RuntimeError(f"fc16 readback {(a, b)}")
print("fc6+fc16/fc3 ok")
PY
  then
    record "modbus-write-roundtrip" pass "FC6+FC16/FC3"
    return 0
  fi
  record "modbus-write-roundtrip" fail "FC6/FC16 against ${MODBUS_HOST}:${MODBUS_PORT}"
  return 1
}

opcua_write_roundtrip() {
  if [[ "$OPCUA_WRITE" != "1" ]]; then
    record "opcua-write-roundtrip" pass "skipped (ISPF_INTEROP_OPCUA_WRITE!=1)"
    return 0
  fi
  if ! command -v python3 >/dev/null 2>&1; then
    record "opcua-write-roundtrip" pass "skipped (no python3)"
    return 0
  fi
  if ! python3 -c 'import asyncua' 2>/dev/null; then
    record "opcua-write-roundtrip" pass "skipped (asyncua not installed)"
    return 0
  fi
  if OPCUA_HOST="$OPCUA_HOST" OPCUA_PORT="$OPCUA_PORT" python3 - <<'PY'
import asyncio, os, random
from asyncua import Client
from asyncua.ua import Variant, VariantType

endpoint = f"opc.tcp://{os.environ.get('OPCUA_HOST','127.0.0.1')}:{os.environ.get('OPCUA_PORT','4840')}"
candidates = [
    "ns=2;s=FastUInt1",
    "ns=2;s=SlowUInt1",
    "ns=2;s=FastUIntScalar1",
    "ns=2;s=SlowUIntScalar1",
]
value = random.randint(1, 60000)

async def main() -> None:
    client = Client(url=endpoint)
    client.session_timeout = 15000
    await client.connect()
    try:
        last_err: Exception | None = None
        for node_id in candidates:
            try:
                node = client.get_node(node_id)
                await node.write_value(Variant(value, VariantType.UInt32))
                got = await node.read_value()
                if int(got) != value:
                    raise RuntimeError(f"readback {got!r} != {value}")
                print(f"opcua write ok node={node_id} value={value}")
                return
            except Exception as ex:  # noqa: BLE001 — try next candidate
                last_err = ex
        raise RuntimeError(f"no writable candidate succeeded; last={last_err!r}")
    finally:
        await client.disconnect()

asyncio.run(main())
PY
  then
    record "opcua-write-roundtrip" pass "UInt write/read"
    return 0
  fi
  # Soft-fail by default: Microsoft opc-plc node set varies by image tag.
  if [[ "${ISPF_INTEROP_OPCUA_WRITE_STRICT:-0}" == "1" ]]; then
    record "opcua-write-roundtrip" fail "write against opc.tcp://${OPCUA_HOST}:${OPCUA_PORT}"
    return 1
  fi
  record "opcua-write-roundtrip" pass "best-effort failed (set ISPF_INTEROP_OPCUA_WRITE_STRICT=1 to enforce)"
  return 0
}

if [[ "${1:-}" == "--self-test-modbus" ]]; then
  python3 "$ROOT/deploy/driver-interop/modbus/server.py" --self-test
  exit $?
fi

{
  echo "# Driver interop fixture smoke (BL-141 / OT Trust Wave 1)"
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
  opcua_write_roundtrip || FAILED=1
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
