#!/usr/bin/env bash
# BL-141 / OT Trust Wave 1 (B1) + post-merge fixture depth:
# - MQTT pub/sub round-trip
# - Modbus TCP FC6 + FC16 write with FC3 read-back (writable lab fixture)
# - Modbus UDP FC6 + FC16 write with FC3 read-back (same MBAP over datagrams)
# - Optional OPC UA write when asyncua is installed (ISPF_INTEROP_OPCUA_WRITE=1 default on)
# - SNMP GET/SET round-trip (lab Integer32 OID)
# - HTTP GET/PUT JSON gauge round-trip
# - BACnet ReadProperty/WriteProperty round-trip (analog-value:1 present-value)
# - IEC 104 C_SE_NC_1 / C_RD_NA_1 float round-trip (IOA 3001)
# - EtherNet/IP CIP Write Tag / Read Tag DINT round-trip (Program:MainProgram.Counter)
# - DLMS WRAPPER SET/GET double round-trip (REGISTER 1.0.1.8.0.255 attr 2)
# - DNP3 integrity poll (read-only AI0/BI0/CTR0 — ADR-0057 poll-only)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

COMPOSE_FILE="${ISPF_INTEROP_COMPOSE_FILE:-deploy/driver-interop/docker-compose.yml}"
MQTT_HOST="${ISPF_INTEROP_MQTT_HOST:-127.0.0.1}"
MQTT_PORT="${ISPF_INTEROP_MQTT_PORT:-1883}"
MODBUS_HOST="${ISPF_INTEROP_MODBUS_HOST:-127.0.0.1}"
MODBUS_PORT="${ISPF_INTEROP_MODBUS_PORT:-502}"
MODBUS_UDP_HOST="${ISPF_INTEROP_MODBUS_UDP_HOST:-127.0.0.1}"
MODBUS_UDP_PORT="${ISPF_INTEROP_MODBUS_UDP_PORT:-502}"
OPCUA_HOST="${ISPF_INTEROP_OPCUA_HOST:-127.0.0.1}"
OPCUA_PORT="${ISPF_INTEROP_OPCUA_PORT:-4840}"
SNMP_HOST="${ISPF_INTEROP_SNMP_HOST:-127.0.0.1}"
SNMP_PORT="${ISPF_INTEROP_SNMP_PORT:-161}"
HTTP_HOST="${ISPF_INTEROP_HTTP_HOST:-127.0.0.1}"
HTTP_PORT="${ISPF_INTEROP_HTTP_PORT:-8089}"
BACNET_HOST="${ISPF_INTEROP_BACNET_HOST:-127.0.0.1}"
BACNET_PORT="${ISPF_INTEROP_BACNET_PORT:-47808}"
IEC104_HOST="${ISPF_INTEROP_IEC104_HOST:-127.0.0.1}"
IEC104_PORT="${ISPF_INTEROP_IEC104_PORT:-2404}"
EIP_HOST="${ISPF_INTEROP_EIP_HOST:-127.0.0.1}"
EIP_PORT="${ISPF_INTEROP_EIP_PORT:-44818}"
DLMS_HOST="${ISPF_INTEROP_DLMS_HOST:-127.0.0.1}"
DLMS_PORT="${ISPF_INTEROP_DLMS_PORT:-4059}"
DNP3_HOST="${ISPF_INTEROP_DNP3_HOST:-127.0.0.1}"
DNP3_PORT="${ISPF_INTEROP_DNP3_PORT:-20000}"
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

wait_for_snmp() {
  local host="$1"
  local port="$2"
  local label="$3"
  local deadline=$((SECONDS + WAIT_SEC))
  if ! command -v python3 >/dev/null 2>&1; then
    record "$label" pass "skipped wait (no python3)"
    return 0
  fi
  while ((SECONDS < deadline)); do
    if SNMP_HOST="$host" SNMP_PORT="$port" python3 - <<'PY'
import os, sys
sys.path.insert(0, "deploy/driver-interop/snmp")
from agent import OID_LAB_GAUGE, snmp_get
host = os.environ.get("SNMP_HOST", "127.0.0.1")
port = int(os.environ.get("SNMP_PORT", "161"))
assert snmp_get(host, port, OID_LAB_GAUGE) == 42
PY
    then
      record "$label" pass "udp://${host}:${port} GET lab OID"
      return 0
    fi
    sleep 2
  done
  record "$label" fail "timeout after ${WAIT_SEC}s (udp://${host}:${port})"
  return 1
}

wait_for_bacnet() {
  local host="$1"
  local port="$2"
  local label="$3"
  local deadline=$((SECONDS + WAIT_SEC))
  if ! command -v python3 >/dev/null 2>&1; then
    record "$label" pass "skipped wait (no python3)"
    return 0
  fi
  while ((SECONDS < deadline)); do
    if BACNET_HOST="$host" BACNET_PORT="$port" python3 - <<'PY'
import os, sys
sys.path.insert(0, "deploy/driver-interop/bacnet")
from agent import DEFAULT_VALUE, bacnet_read
host = os.environ.get("BACNET_HOST", "127.0.0.1")
port = int(os.environ.get("BACNET_PORT", "47808"))
got = bacnet_read(host, port)
assert abs(got - DEFAULT_VALUE) < 0.01, got
PY
    then
      record "$label" pass "udp://${host}:${port} ReadProperty AV:1"
      return 0
    fi
    sleep 2
  done
  record "$label" fail "timeout after ${WAIT_SEC}s (udp://${host}:${port})"
  return 1
}

wait_for_modbus_udp() {
  local host="$1"
  local port="$2"
  local label="$3"
  local deadline=$((SECONDS + WAIT_SEC))
  if ! command -v python3 >/dev/null 2>&1; then
    record "$label" pass "skipped wait (no python3)"
    return 0
  fi
  while ((SECONDS < deadline)); do
    if MODBUS_UDP_HOST="$host" MODBUS_UDP_PORT="$port" python3 - <<'PY'
import os, sys
sys.path.insert(0, "deploy/driver-interop/modbus")
from server import udp_probe
host = os.environ.get("MODBUS_UDP_HOST", "127.0.0.1")
port = int(os.environ.get("MODBUS_UDP_PORT", "502"))
udp_probe(host, port)
PY
    then
      record "$label" pass "udp://${host}:${port} FC3 seed"
      return 0
    fi
    sleep 2
  done
  record "$label" fail "timeout after ${WAIT_SEC}s (udp://${host}:${port})"
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

modbus_udp_write_roundtrip() {
  if ! command -v python3 >/dev/null 2>&1; then
    record "modbus-udp-write-roundtrip" pass "skipped (no python3)"
    return 0
  fi
  if MODBUS_UDP_HOST="$MODBUS_UDP_HOST" MODBUS_UDP_PORT="$MODBUS_UDP_PORT" python3 - <<'PY'
import os, struct, sys
sys.path.insert(0, "deploy/driver-interop/modbus")
from server import udp_exchange

host = os.environ.get("MODBUS_UDP_HOST", "127.0.0.1")
port = int(os.environ.get("MODBUS_UDP_PORT", "502"))
unit = 1

# FC6 single
udp_exchange(host, port, unit, struct.pack(">BHH", 6, 0, 0xA5A5), tid=10)
pdu = udp_exchange(host, port, unit, struct.pack(">BHH", 3, 0, 1), tid=11)
got = struct.unpack(">H", pdu[2:4])[0]
if got != 0xA5A5:
    raise RuntimeError(f"fc6 readback {got:#x}")

# FC16 multi
udp_exchange(host, port, unit, struct.pack(">BHHBHH", 16, 2, 2, 4, 0x1111, 0x2222), tid=12)
pdu = udp_exchange(host, port, unit, struct.pack(">BHH", 3, 2, 2), tid=13)
a, b = struct.unpack(">HH", pdu[2:6])
if (a, b) != (0x1111, 0x2222):
    raise RuntimeError(f"fc16 readback {(a, b)}")
print("udp fc6+fc16/fc3 ok")
PY
  then
    record "modbus-udp-write-roundtrip" pass "FC6+FC16/FC3 over UDP"
    return 0
  fi
  record "modbus-udp-write-roundtrip" fail "against udp://${MODBUS_UDP_HOST}:${MODBUS_UDP_PORT}"
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

snmp_write_roundtrip() {
  if ! command -v python3 >/dev/null 2>&1; then
    record "snmp-write-roundtrip" pass "skipped (no python3)"
    return 0
  fi
  if SNMP_HOST="$SNMP_HOST" SNMP_PORT="$SNMP_PORT" python3 - <<'PY'
import os, random, sys
sys.path.insert(0, "deploy/driver-interop/snmp")
from agent import OID_LAB_GAUGE, snmp_get, snmp_set

host = os.environ.get("SNMP_HOST", "127.0.0.1")
port = int(os.environ.get("SNMP_PORT", "161"))
value = random.randint(1, 60000)
snmp_set(host, port, OID_LAB_GAUGE, value)
got = snmp_get(host, port, OID_LAB_GAUGE)
if got != value:
    raise RuntimeError(f"snmp readback {got} != {value}")
print(f"snmp set/get ok value={value}")
PY
  then
    record "snmp-write-roundtrip" pass "GET/SET lab OID"
    return 0
  fi
  record "snmp-write-roundtrip" fail "against udp://${SNMP_HOST}:${SNMP_PORT}"
  return 1
}

http_write_roundtrip() {
  if ! command -v python3 >/dev/null 2>&1; then
    record "http-write-roundtrip" pass "skipped (no python3)"
    return 0
  fi
  if HTTP_HOST="$HTTP_HOST" HTTP_PORT="$HTTP_PORT" python3 - <<'PY'
import json, os, random, urllib.request

host = os.environ.get("HTTP_HOST", "127.0.0.1")
port = int(os.environ.get("HTTP_PORT", "8089"))
base = f"http://{host}:{port}"
value = random.randint(1, 60000)

with urllib.request.urlopen(f"{base}/health", timeout=5) as resp:
    assert json.load(resp)["ok"] is True

req = urllib.request.Request(
    f"{base}/points/gauge",
    data=json.dumps({"value": value}).encode("utf-8"),
    headers={"Content-Type": "application/json"},
    method="PUT",
)
with urllib.request.urlopen(req, timeout=5) as resp:
    assert json.load(resp)["value"] == value

with urllib.request.urlopen(f"{base}/points/gauge", timeout=5) as resp:
    got = json.load(resp)["value"]
if got != value:
    raise RuntimeError(f"http readback {got} != {value}")
print(f"http put/get ok value={value}")
PY
  then
    record "http-write-roundtrip" pass "PUT/GET /points/gauge"
    return 0
  fi
  record "http-write-roundtrip" fail "against http://${HTTP_HOST}:${HTTP_PORT}"
  return 1
}

bacnet_write_roundtrip() {
  if ! command -v python3 >/dev/null 2>&1; then
    record "bacnet-write-roundtrip" pass "skipped (no python3)"
    return 0
  fi
  if BACNET_HOST="$BACNET_HOST" BACNET_PORT="$BACNET_PORT" python3 - <<'PY'
import os, random, sys
sys.path.insert(0, "deploy/driver-interop/bacnet")
from agent import bacnet_read, bacnet_write

host = os.environ.get("BACNET_HOST", "127.0.0.1")
port = int(os.environ.get("BACNET_PORT", "47808"))
value = round(random.uniform(1.0, 90.0), 2)
bacnet_write(host, port, value)
got = bacnet_read(host, port, invoke_id=3)
if abs(got - value) > 0.01:
    raise RuntimeError(f"bacnet readback {got} != {value}")
print(f"bacnet write/read ok value={value}")
PY
  then
    record "bacnet-write-roundtrip" pass "WriteProperty/ReadProperty AV:1"
    return 0
  fi
  record "bacnet-write-roundtrip" fail "against udp://${BACNET_HOST}:${BACNET_PORT}"
  return 1
}

iec104_write_roundtrip() {
  if ! command -v python3 >/dev/null 2>&1; then
    record "iec104-write-roundtrip" pass "skipped (no python3)"
    return 0
  fi
  if IEC104_HOST="$IEC104_HOST" IEC104_PORT="$IEC104_PORT" python3 - <<'PY'
import os, random, sys
sys.path.insert(0, "deploy/driver-interop/iec104")
from server import iec104_read_float, iec104_write_float

host = os.environ.get("IEC104_HOST", "127.0.0.1")
port = int(os.environ.get("IEC104_PORT", "2404"))
value = round(random.uniform(1.0, 90.0), 2)
iec104_write_float(host, port, value)
got = iec104_read_float(host, port)
if abs(got - value) > 0.01:
    raise RuntimeError(f"iec104 readback {got} != {value}")
print(f"iec104 write/read ok value={value}")
PY
  then
    record "iec104-write-roundtrip" pass "C_SE_NC_1/C_RD_NA_1 IOA 3001"
    return 0
  fi
  record "iec104-write-roundtrip" fail "against tcp://${IEC104_HOST}:${IEC104_PORT}"
  return 1
}

eip_write_roundtrip() {
  if ! command -v python3 >/dev/null 2>&1; then
    record "eip-write-roundtrip" pass "skipped (no python3)"
    return 0
  fi
  if EIP_HOST="$EIP_HOST" EIP_PORT="$EIP_PORT" python3 - <<'PY'
import os, random, sys
sys.path.insert(0, "deploy/driver-interop/ethernet-ip")
from server import eip_read_tag, eip_write_tag

host = os.environ.get("EIP_HOST", "127.0.0.1")
port = int(os.environ.get("EIP_PORT", "44818"))
value = random.randint(1, 2_000_000_000)
eip_write_tag(host, port, value)
got = eip_read_tag(host, port)
if got != value:
    raise RuntimeError(f"eip readback {got} != {value}")
print(f"eip write/read ok value={value}")
PY
  then
    record "eip-write-roundtrip" pass "CIP Write/Read Tag DINT"
    return 0
  fi
  record "eip-write-roundtrip" fail "against tcp://${EIP_HOST}:${EIP_PORT}"
  return 1
}

dlms_write_roundtrip() {
  if ! command -v python3 >/dev/null 2>&1; then
    record "dlms-write-roundtrip" pass "skipped (no python3)"
    return 0
  fi
  if DLMS_HOST="$DLMS_HOST" DLMS_PORT="$DLMS_PORT" python3 - <<'PY'
import os, random, sys
sys.path.insert(0, "deploy/driver-interop/dlms")
from server import dlms_read, dlms_write

host = os.environ.get("DLMS_HOST", "127.0.0.1")
port = int(os.environ.get("DLMS_PORT", "4059"))
value = round(random.uniform(1.0, 90.0), 2)
dlms_write(host, port, value)
got = dlms_read(host, port)
if abs(got - value) > 1e-9:
    raise RuntimeError(f"dlms readback {got} != {value}")
print(f"dlms write/read ok value={value}")
PY
  then
    record "dlms-write-roundtrip" pass "WRAPPER SET/GET REGISTER energy"
    return 0
  fi
  record "dlms-write-roundtrip" fail "against tcp://${DLMS_HOST}:${DLMS_PORT}"
  return 1
}

dnp3_integrity_poll() {
  if ! command -v python3 >/dev/null 2>&1; then
    record "dnp3-integrity-poll" pass "skipped (no python3)"
    return 0
  fi
  if DNP3_HOST="$DNP3_HOST" DNP3_PORT="$DNP3_PORT" python3 - <<'PY'
import os, sys
sys.path.insert(0, "deploy/driver-interop/dnp3")
from server import SEED_AI0, SEED_BI0, SEED_CTR0, dnp3_integrity_poll

host = os.environ.get("DNP3_HOST", "127.0.0.1")
port = int(os.environ.get("DNP3_PORT", "20000"))
values = dnp3_integrity_poll(host, port)
ai = float(values["ANALOG_INPUT:0"])
bi = values["BINARY_INPUT:0"]
ctr = int(values["COUNTER:0"])
if abs(ai - SEED_AI0) > 1e-9:
    raise RuntimeError(f"AI0 {ai} != {SEED_AI0}")
if bi is not True:
    raise RuntimeError(f"BI0 {bi!r} != True")
if ctr != SEED_CTR0:
    raise RuntimeError(f"CTR0 {ctr} != {SEED_CTR0}")
print(f"dnp3 integrity poll ok AI0={ai} BI0={bi} CTR0={ctr}")
PY
  then
    record "dnp3-integrity-poll" pass "Class 0/1/2/3 static points (poll-only)"
    return 0
  fi
  record "dnp3-integrity-poll" fail "against tcp://${DNP3_HOST}:${DNP3_PORT}"
  return 1
}

if [[ "${1:-}" == "--self-test-modbus" ]]; then
  python3 "$ROOT/deploy/driver-interop/modbus/server.py" --self-test
  exit $?
fi

if [[ "${1:-}" == "--self-test-modbus-udp" ]]; then
  python3 "$ROOT/deploy/driver-interop/modbus/server.py" --self-test-udp
  exit $?
fi

if [[ "${1:-}" == "--self-test-snmp" ]]; then
  python3 "$ROOT/deploy/driver-interop/snmp/agent.py" --self-test
  exit $?
fi

if [[ "${1:-}" == "--self-test-http" ]]; then
  python3 "$ROOT/deploy/driver-interop/http/server.py" --self-test
  exit $?
fi

if [[ "${1:-}" == "--self-test-bacnet" ]]; then
  python3 "$ROOT/deploy/driver-interop/bacnet/agent.py" --self-test
  exit $?
fi

if [[ "${1:-}" == "--self-test-iec104" ]]; then
  python3 "$ROOT/deploy/driver-interop/iec104/server.py" --self-test
  exit $?
fi

if [[ "${1:-}" == "--self-test-ethernet-ip" ]]; then
  python3 "$ROOT/deploy/driver-interop/ethernet-ip/server.py" --self-test
  exit $?
fi

if [[ "${1:-}" == "--self-test-dlms" ]]; then
  python3 "$ROOT/deploy/driver-interop/dlms/server.py" --self-test
  exit $?
fi

if [[ "${1:-}" == "--self-test-dnp3" ]]; then
  python3 "$ROOT/deploy/driver-interop/dnp3/server.py" --self-test
  exit $?
fi

{
  echo "# Driver interop fixture smoke (BL-141 / OT Trust)"
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
wait_for_modbus_udp "$MODBUS_UDP_HOST" "$MODBUS_UDP_PORT" "modbus-udp" || FAILED=1
wait_for_tcp "$OPCUA_HOST" "$OPCUA_PORT" "opcua-tcp" || FAILED=1
wait_for_snmp "$SNMP_HOST" "$SNMP_PORT" "snmp-udp" || FAILED=1
wait_for_tcp "$HTTP_HOST" "$HTTP_PORT" "http-tcp" || FAILED=1
wait_for_bacnet "$BACNET_HOST" "$BACNET_PORT" "bacnet-udp" || FAILED=1
wait_for_tcp "$IEC104_HOST" "$IEC104_PORT" "iec104-tcp" || FAILED=1
wait_for_tcp "$EIP_HOST" "$EIP_PORT" "ethernet-ip-tcp" || FAILED=1
wait_for_tcp "$DLMS_HOST" "$DLMS_PORT" "dlms-tcp" || FAILED=1
wait_for_tcp "$DNP3_HOST" "$DNP3_PORT" "dnp3-tcp" || FAILED=1

if [[ "$FAILED" -eq 0 ]]; then
  mqtt_roundtrip || FAILED=1
  modbus_write_roundtrip || FAILED=1
  modbus_udp_write_roundtrip || FAILED=1
  opcua_write_roundtrip || FAILED=1
  snmp_write_roundtrip || FAILED=1
  http_write_roundtrip || FAILED=1
  bacnet_write_roundtrip || FAILED=1
  iec104_write_roundtrip || FAILED=1
  eip_write_roundtrip || FAILED=1
  dlms_write_roundtrip || FAILED=1
  dnp3_integrity_poll || FAILED=1
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
