#!/usr/bin/env python3
"""Minimal ISPF DNP3 TCP outstation for OT Trust BL-141 (lab / poll-only).

Wire subset aligned with packages/ispf-driver-dnp3 Java loopback
(Dnp3TcpCodec / Dnp3LoopbackOutstation):
  05 64 | LEN | BODY | CRC16_LE(body)
  BODY = ctrl | dest_le16 | src_le16 | transport | application
  Integrity poll READ g60v1–v3 → static BI/BO/CTR/AI/AO objects

Seed (flags=0x01):
  AI0=12.34, BI0=True, CTR0=999, AO0=55.5, BO0=False

Master=1, outstation=1024, port=20000. Stdlib only — not a full IEEE 1815 stack.
ADR-0057: DNP3 is PRODUCTION + POLL_ONLY (no write path).
"""
from __future__ import annotations

import argparse
import socket
import struct
import threading
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple

START = b"\x05\x64"
LINK_CONTROL = 0x44
TRANSPORT_FIN_FIR = 0xC0
APP_REQUEST = 0xC0
APP_RESPONSE = 0xC0
FUNCTION_READ = 0x01
FUNCTION_RESPONSE = 0x81
QUALIFIER_16BIT_INDEXES = 0x28

DEFAULT_PORT = 20000
DEFAULT_MASTER = 1
DEFAULT_OUTSTATION = 1024

SEED_AI0 = 12.34
SEED_BI0 = True
SEED_CTR0 = 999
SEED_AO0 = 55.5
SEED_BO0 = False
SEED_FLAGS = 0x01


def crc16(data: bytes) -> int:
    """Modbus/DNP poly 0xA001, init 0xFFFF — matches Dnp3TcpCodec.crc16."""
    crc = 0xFFFF
    for byte in data:
        crc ^= byte
        for _ in range(8):
            if crc & 1:
                crc = (crc >> 1) ^ 0xA001
            else:
                crc >>= 1
    return crc & 0xFFFF


def build_frame(source: int, destination: int, application: bytes, sequence: int) -> bytes:
    body = bytearray()
    body.append(LINK_CONTROL)
    body.extend(struct.pack("<H", destination & 0xFFFF))
    body.extend(struct.pack("<H", source & 0xFFFF))
    body.append(TRANSPORT_FIN_FIR | (sequence & 0x3F))
    body.extend(application)
    body_bytes = bytes(body)
    frame = bytearray(START)
    frame.append(len(body_bytes) & 0xFF)
    frame.extend(body_bytes)
    frame.extend(struct.pack("<H", crc16(body_bytes)))
    return bytes(frame)


def integrity_poll_request(master: int, outstation: int, sequence: int) -> bytes:
    app = bytearray()
    app.append(APP_REQUEST | (sequence & 0x0F))
    app.append(FUNCTION_READ)
    for variation in (1, 2, 3):
        app.extend(bytes([0x3C, variation, 0x06]))
    return build_frame(master, outstation, bytes(app), sequence)


@dataclass
class Measurement:
    type: str  # BINARY_INPUT|BINARY_OUTPUT|COUNTER|ANALOG_INPUT|ANALOG_OUTPUT
    index: int
    value: object
    flags: int = SEED_FLAGS


def seed_measurements() -> List[Measurement]:
    return [
        Measurement("ANALOG_INPUT", 0, SEED_AI0),
        Measurement("BINARY_INPUT", 0, SEED_BI0),
        Measurement("COUNTER", 0, SEED_CTR0),
        Measurement("ANALOG_OUTPUT", 0, SEED_AO0),
        Measurement("BINARY_OUTPUT", 0, SEED_BO0),
    ]


_GROUP_SPEC = (
    ("BINARY_INPUT", 1, 2),
    ("BINARY_OUTPUT", 10, 2),
    ("COUNTER", 20, 1),
    ("ANALOG_INPUT", 30, 5),
    ("ANALOG_OUTPUT", 40, 1),
)


def _write_group(app: bytearray, measurements: List[Measurement], mtype: str, group: int, variation: int) -> None:
    filtered = [m for m in measurements if m.type == mtype]
    if not filtered:
        return
    app.append(group & 0xFF)
    app.append(variation & 0xFF)
    app.append(QUALIFIER_16BIT_INDEXES)
    app.extend(struct.pack("<H", len(filtered)))
    for measurement in filtered:
        app.extend(struct.pack("<H", measurement.index & 0xFFFF))
        app.append(measurement.flags & 0xFF)
        if mtype in ("BINARY_INPUT", "BINARY_OUTPUT"):
            app.append(1 if measurement.value else 0)
        elif mtype == "COUNTER":
            app.extend(struct.pack("<I", int(measurement.value) & 0xFFFFFFFF))
        else:
            app.extend(struct.pack("<d", float(measurement.value)))


def integrity_poll_response(
    outstation: int,
    master: int,
    measurements: List[Measurement],
    sequence: int,
) -> bytes:
    app = bytearray()
    app.append(APP_RESPONSE | (sequence & 0x0F))
    app.append(FUNCTION_RESPONSE)
    app.extend(struct.pack("<H", 0))  # IIN
    for mtype, group, variation in _GROUP_SPEC:
        _write_group(app, measurements, mtype, group, variation)
    return build_frame(outstation, master, bytes(app), sequence)


def _recv_exact(conn: socket.socket, n: int) -> Optional[bytes]:
    buf = bytearray()
    while len(buf) < n:
        chunk = conn.recv(n - len(buf))
        if not chunk:
            return None
        buf.extend(chunk)
    return bytes(buf)


def read_frame(conn: socket.socket) -> Tuple[int, int, int, int, bytes]:
    header = _recv_exact(conn, 3)
    if header is None:
        raise ConnectionError("peer closed")
    if header[0] != 0x05 or header[1] != 0x64:
        raise RuntimeError(f"bad start bytes {header[:2]!r}")
    length = header[2]
    if length < 5 or length > 4096:
        raise RuntimeError(f"bad frame length {length}")
    body = _recv_exact(conn, length)
    crc_bytes = _recv_exact(conn, 2)
    if body is None or crc_bytes is None:
        raise ConnectionError("peer closed mid-frame")
    sent_crc = struct.unpack("<H", crc_bytes)[0]
    if sent_crc != crc16(body):
        raise RuntimeError("CRC mismatch")
    control = body[0]
    destination, source = struct.unpack_from("<HH", body, 1)
    transport = body[5]
    application = body[6:]
    return control, source, destination, transport, application


def request_sequence(application: bytes) -> int:
    if len(application) < 2 or application[1] != FUNCTION_READ:
        raise RuntimeError("unsupported DNP3 request")
    return application[0] & 0x0F


def apply_response(application: bytes) -> Dict[str, object]:
    if len(application) < 4:
        raise RuntimeError("short application response")
    control = application[0]
    function = application[1]
    if (control & 0xC0) != APP_RESPONSE or function != FUNCTION_RESPONSE:
        raise RuntimeError("unexpected DNP3 response")
    offset = 4  # skip control, function, IIN
    values: Dict[str, object] = {}
    while offset < len(application):
        if offset + 5 > len(application):
            raise RuntimeError("truncated object header")
        group = application[offset]
        variation = application[offset + 1]
        qualifier = application[offset + 2]
        count = struct.unpack_from("<H", application, offset + 3)[0]
        offset += 5
        if qualifier != QUALIFIER_16BIT_INDEXES:
            raise RuntimeError(f"unsupported qualifier 0x{qualifier:x}")
        for _ in range(count):
            if offset + 3 > len(application):
                raise RuntimeError("truncated object")
            index = struct.unpack_from("<H", application, offset)[0]
            flags = application[offset + 2]
            offset += 3
            _ = flags
            if group in (1, 10):
                if offset >= len(application):
                    raise RuntimeError("truncated binary value")
                value = application[offset] != 0
                offset += 1
                key = ("BINARY_INPUT" if group == 1 else "BINARY_OUTPUT", index)
                values[f"{key[0]}:{key[1]}"] = value
            elif group == 20:
                if offset + 4 > len(application):
                    raise RuntimeError("truncated counter")
                value = struct.unpack_from("<I", application, offset)[0]
                offset += 4
                values[f"COUNTER:{index}"] = value
            elif group in (30, 40):
                if offset + 8 > len(application):
                    raise RuntimeError("truncated analog")
                value = struct.unpack_from("<d", application, offset)[0]
                offset += 8
                key = "ANALOG_INPUT" if group == 30 else "ANALOG_OUTPUT"
                values[f"{key}:{index}"] = value
            else:
                raise RuntimeError(f"unsupported group {group} var {variation}")
    return values


def handle_client(
    conn: socket.socket,
    master: int,
    outstation: int,
    measurements: List[Measurement],
) -> None:
    conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    conn.settimeout(30)
    try:
        while True:
            _control, source, destination, _transport, application = read_frame(conn)
            if source != master or destination != outstation:
                continue
            sequence = request_sequence(application)
            response = integrity_poll_response(outstation, master, measurements, sequence)
            conn.sendall(response)
    except (OSError, ConnectionError, TimeoutError, RuntimeError):
        return
    finally:
        try:
            conn.close()
        except OSError:
            pass


def serve(host: str, port: int, master: int = DEFAULT_MASTER, outstation: int = DEFAULT_OUTSTATION) -> None:
    measurements = seed_measurements()
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((host, port))
    sock.listen(8)
    print(
        f"dnp3 lab outstation listening tcp://{host}:{port} "
        f"master={master} outstation={outstation} AI0={SEED_AI0}",
        flush=True,
    )
    while True:
        conn, _addr = sock.accept()
        threading.Thread(
            target=handle_client,
            args=(conn, master, outstation, measurements),
            daemon=True,
        ).start()


def dnp3_integrity_poll(
    host: str,
    port: int = DEFAULT_PORT,
    master: int = DEFAULT_MASTER,
    outstation: int = DEFAULT_OUTSTATION,
    sequence: int = 0,
    timeout: float = 5.0,
) -> Dict[str, object]:
    sock = socket.create_connection((host, port), timeout=timeout)
    sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    sock.settimeout(timeout)
    try:
        sock.sendall(integrity_poll_request(master, outstation, sequence))
        _control, source, destination, _transport, application = read_frame(sock)
        if source != outstation or destination != master:
            raise RuntimeError(f"address mismatch src={source} dest={destination}")
        return apply_response(application)
    finally:
        try:
            sock.close()
        except OSError:
            pass


def self_test() -> None:
    host, port = "127.0.0.1", 12000
    measurements = seed_measurements()
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((host, port))
    sock.listen(1)
    sock.settimeout(5)

    def accept_loop() -> None:
        try:
            for _ in range(2):
                conn, _ = sock.accept()
                handle_client(conn, DEFAULT_MASTER, DEFAULT_OUTSTATION, measurements)
        except OSError:
            pass

    threading.Thread(target=accept_loop, daemon=True).start()
    values = dnp3_integrity_poll(host, port)
    assert abs(float(values["ANALOG_INPUT:0"]) - SEED_AI0) < 1e-9, values
    assert values["BINARY_INPUT:0"] is True, values
    assert int(values["COUNTER:0"]) == SEED_CTR0, values
    assert abs(float(values["ANALOG_OUTPUT:0"]) - SEED_AO0) < 1e-9, values
    assert values["BINARY_OUTPUT:0"] is False, values
    sock.close()
    print("dnp3 self-test ok", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--master", type=int, default=DEFAULT_MASTER)
    parser.add_argument("--outstation", type=int, default=DEFAULT_OUTSTATION)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    serve(args.host, args.port, args.master, args.outstation)


if __name__ == "__main__":
    main()
