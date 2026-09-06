#!/usr/bin/env python3
"""Minimal Modbus TCP/UDP/RTU fixture for OT Trust Wave 1 (BL-141).

Supports FC3 (read holding), FC6 (write single), FC16 (write multiple).
- TCP/UDP: MBAP+PDU (j2mod ModbusTCPMaster / ModbusUDPMaster compatible)
- RTU: unit+PDU+CRC16 ADUs on a TCP stream (lab bridge — not RS-485 PHY)

Stdlib only — no pymodbus. Lab use; not a production slave.
"""
from __future__ import annotations

import argparse
import socket
import struct
import threading
from typing import List, Optional, Tuple


class HoldingMap:
    def __init__(self, size: int = 256) -> None:
        self.lock = threading.Lock()
        self.regs: List[int] = [0] * size

    def read(self, addr: int, count: int) -> List[int]:
        with self.lock:
            if addr < 0 or count < 1 or addr + count > len(self.regs):
                raise ValueError("illegal data address")
            return self.regs[addr : addr + count]

    def write(self, addr: int, values: List[int]) -> None:
        with self.lock:
            if addr < 0 or not values or addr + len(values) > len(self.regs):
                raise ValueError("illegal data address")
            for i, v in enumerate(values):
                self.regs[addr + i] = v & 0xFFFF


def handle_pdu(holding: HoldingMap, pdu: bytes) -> bytes:
    if not pdu:
        return struct.pack(">BB", 0x80, 0x01)
    func = pdu[0]
    try:
        if func == 3 and len(pdu) >= 5:
            addr, count = struct.unpack(">HH", pdu[1:5])
            vals = holding.read(addr, count)
            payload = struct.pack(">BB", 3, len(vals) * 2)
            for v in vals:
                payload += struct.pack(">H", v)
            return payload
        if func == 6 and len(pdu) >= 5:
            addr, value = struct.unpack(">HH", pdu[1:5])
            holding.write(addr, [value])
            return struct.pack(">BHH", 6, addr, value)
        if func == 16 and len(pdu) >= 6:
            addr, count, byte_count = struct.unpack(">HHB", pdu[1:6])
            if byte_count != count * 2 or len(pdu) < 6 + byte_count:
                return struct.pack(">BB", 16 + 0x80, 0x03)
            values = list(struct.unpack(">" + "H" * count, pdu[6 : 6 + byte_count]))
            holding.write(addr, values)
            return struct.pack(">BHH", 16, addr, count)
        return struct.pack(">BB", func + 0x80, 0x01)
    except ValueError:
        return struct.pack(">BB", func + 0x80, 0x02)


def _recv_exact(conn: socket.socket, n: int) -> Optional[bytes]:
    buf = b""
    while len(buf) < n:
        chunk = conn.recv(n - len(buf))
        if not chunk:
            return None
        buf += chunk
    return buf


def handle_client(conn: socket.socket, holding: HoldingMap) -> None:
    with conn:
        conn.settimeout(30)
        while True:
            try:
                hdr = _recv_exact(conn, 7)
            except (OSError, ConnectionError, TimeoutError):
                return
            if hdr is None:
                return
            tid, pid, length, unit = struct.unpack(">HHHB", hdr)
            if pid != 0 or length < 1:
                return
            pdu = _recv_exact(conn, length - 1)
            if pdu is None:
                return
            resp = handle_pdu(holding, pdu)
            mbap = struct.pack(">HHHB", tid, 0, len(resp) + 1, unit)
            try:
                conn.sendall(mbap + resp)
            except OSError:
                return


def serve_tcp(host: str, port: int) -> None:
    holding = HoldingMap()
    holding.write(0, [0x1111, 0x2222, 0x3333])
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind((host, port))
        sock.listen(32)
        print(f"ispf-modbus-tcp-fixture listening on {host}:{port}", flush=True)
        while True:
            conn, _ = sock.accept()
            threading.Thread(target=handle_client, args=(conn, holding), daemon=True).start()


def _parse_mbap_datagram(data: bytes) -> Optional[Tuple[int, int, bytes]]:
    if len(data) < 8:
        return None
    tid, pid, length, unit = struct.unpack(">HHHB", data[:7])
    if pid != 0 or length < 1:
        return None
    if len(data) < 6 + length:
        return None
    pdu = data[7 : 6 + length]
    return tid, unit, pdu


def serve_udp(host: str, port: int) -> None:
    holding = HoldingMap()
    holding.write(0, [0x1111, 0x2222, 0x3333])
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind((host, port))
        print(f"ispf-modbus-udp-fixture listening on udp://{host}:{port}", flush=True)
        while True:
            try:
                data, addr = sock.recvfrom(260)
            except OSError:
                return
            parsed = _parse_mbap_datagram(data)
            if parsed is None:
                continue
            tid, unit, pdu = parsed
            resp = handle_pdu(holding, pdu)
            mbap = struct.pack(">HHHB", tid, 0, len(resp) + 1, unit)
            try:
                sock.sendto(mbap + resp, addr)
            except OSError:
                continue


def udp_exchange(
    host: str,
    port: int,
    unit: int,
    pdu: bytes,
    tid: int = 1,
    timeout: float = 2.0,
) -> bytes:
    """Send one Modbus UDP request (MBAP+PDU) and return response PDU."""
    req = struct.pack(">HHHB", tid & 0xFFFF, 0, len(pdu) + 1, unit & 0xFF) + pdu
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.settimeout(timeout)
        sock.sendto(req, (host, port))
        data, _ = sock.recvfrom(260)
    parsed = _parse_mbap_datagram(data)
    if parsed is None:
        raise RuntimeError(f"short/invalid udp response {data!r}")
    _tid, _unit, resp_pdu = parsed
    if not resp_pdu or resp_pdu[0] & 0x80:
        raise RuntimeError(f"modbus exception pdu={resp_pdu!r}")
    return resp_pdu


def udp_probe(host: str, port: int, timeout: float = 2.0) -> None:
    """FC3 read of seed register 0 — used by compose healthcheck / smoke wait."""
    pdu = udp_exchange(host, port, unit=1, pdu=struct.pack(">BHH", 3, 0, 1), timeout=timeout)
    if len(pdu) < 4 or pdu[0] != 3:
        raise RuntimeError(f"unexpected probe pdu {pdu!r}")
    value = struct.unpack(">H", pdu[2:4])[0]
    if value != 0x1111:
        raise RuntimeError(f"seed mismatch {value:#x}")


def self_test() -> None:
    """In-process FC6 + FC16 + FC3 without binding a port (unit check)."""
    h = HoldingMap()
    r = handle_pdu(h, struct.pack(">BHH", 6, 0, 0xA5A5))
    assert r == struct.pack(">BHH", 6, 0, 0xA5A5), r
    r = handle_pdu(h, struct.pack(">BHH", 3, 0, 1))
    assert r == struct.pack(">BBH", 3, 2, 0xA5A5), r
    r = handle_pdu(h, struct.pack(">BHHBHH", 16, 1, 2, 4, 0x10, 0x20))
    assert r == struct.pack(">BHH", 16, 1, 2), r
    r = handle_pdu(h, struct.pack(">BHH", 3, 1, 2))
    assert r == struct.pack(">BBHH", 3, 4, 0x10, 0x20), r
    print("modbus fixture self-test ok", flush=True)


def self_test_udp() -> None:
    """Bind ephemeral UDP port and verify FC6/FC16/FC3 round-trip."""
    holding = HoldingMap()
    holding.write(0, [0x1111, 0x2222, 0x3333])
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("127.0.0.1", 0))
    host, port = sock.getsockname()
    sock.settimeout(2)

    def loop() -> None:
        try:
            for _ in range(8):
                data, addr = sock.recvfrom(260)
                parsed = _parse_mbap_datagram(data)
                if parsed is None:
                    continue
                tid, unit, pdu = parsed
                resp = handle_pdu(holding, pdu)
                sock.sendto(struct.pack(">HHHB", tid, 0, len(resp) + 1, unit) + resp, addr)
        except OSError:
            pass

    threading.Thread(target=loop, daemon=True).start()
    udp_probe(host, port)
    udp_exchange(host, port, 1, struct.pack(">BHH", 6, 0, 0xA5A5), tid=2)
    pdu = udp_exchange(host, port, 1, struct.pack(">BHH", 3, 0, 1), tid=3)
    got = struct.unpack(">H", pdu[2:4])[0]
    assert got == 0xA5A5, got
    udp_exchange(host, port, 1, struct.pack(">BHHBHH", 16, 2, 2, 4, 0x1111, 0x2222), tid=4)
    pdu = udp_exchange(host, port, 1, struct.pack(">BHH", 3, 2, 2), tid=5)
    a, b = struct.unpack(">HH", pdu[2:6])
    assert (a, b) == (0x1111, 0x2222), (a, b)
    sock.close()
    print("modbus-udp fixture self-test ok", flush=True)


def crc16_modbus(data: bytes) -> int:
    """Modbus RTU CRC-16 (poly 0xA001, init 0xFFFF)."""
    crc = 0xFFFF
    for byte in data:
        crc ^= byte
        for _ in range(8):
            if crc & 0x0001:
                crc = (crc >> 1) ^ 0xA001
            else:
                crc >>= 1
    return crc & 0xFFFF


def build_rtu_adu(unit: int, pdu: bytes) -> bytes:
    body = bytes([unit & 0xFF]) + pdu
    crc = crc16_modbus(body)
    return body + struct.pack("<H", crc)


def verify_rtu_adu(adu: bytes) -> Tuple[int, bytes]:
    if len(adu) < 4:
        raise RuntimeError(f"rtu adu too short: {adu!r}")
    body, crc_bytes = adu[:-2], adu[-2:]
    got = struct.unpack("<H", crc_bytes)[0]
    expect = crc16_modbus(body)
    if got != expect:
        raise RuntimeError(f"rtu crc mismatch got={got:#x} expect={expect:#x} adu={adu!r}")
    return body[0], body[1:]


def _read_rtu_request(conn: socket.socket) -> Optional[Tuple[int, bytes]]:
    """Length-aware RTU ADU reader (TCP bridge — no inter-frame silence)."""
    hdr = _recv_exact(conn, 2)
    if hdr is None:
        return None
    _unit, func = hdr[0], hdr[1]
    if func in (1, 2, 3, 4, 5, 6):
        rest = _recv_exact(conn, 4 + 2)
        if rest is None:
            return None
        return verify_rtu_adu(hdr + rest)
    if func == 16:
        mid = _recv_exact(conn, 5)  # addr(2)+qty(2)+bc(1)
        if mid is None:
            return None
        byte_count = mid[4]
        data = _recv_exact(conn, byte_count + 2)
        if data is None:
            return None
        return verify_rtu_adu(hdr + mid + data)
    rest = _recv_exact(conn, 1 + 2)
    if rest is None:
        return None
    return verify_rtu_adu(hdr + rest)


def _read_rtu_response(conn: socket.socket, func: int) -> bytes:
    """Read one RTU response ADU for the given request function code; return PDU."""
    hdr = _recv_exact(conn, 2)
    if hdr is None:
        raise RuntimeError("rtu response eof")
    _unit, resp_func = hdr[0], hdr[1]
    if resp_func & 0x80:
        rest = _recv_exact(conn, 1 + 2)
        if rest is None:
            raise RuntimeError("rtu exception truncated")
        _u, pdu = verify_rtu_adu(hdr + rest)
        raise RuntimeError(f"modbus exception pdu={pdu!r}")
    if resp_func != func:
        raise RuntimeError(f"rtu unexpected func {resp_func} != {func}")
    if func in (5, 6, 15, 16):
        rest = _recv_exact(conn, 4 + 2)
        if rest is None:
            raise RuntimeError("rtu response truncated")
        _u, pdu = verify_rtu_adu(hdr + rest)
        return pdu
    if func in (1, 2, 3, 4):
        bc = _recv_exact(conn, 1)
        if bc is None:
            raise RuntimeError("rtu response truncated (byte count)")
        data = _recv_exact(conn, bc[0] + 2)
        if data is None:
            raise RuntimeError("rtu response truncated (data)")
        _u, pdu = verify_rtu_adu(hdr + bc + data)
        return pdu
    raise RuntimeError(f"unsupported rtu response func {func}")


def handle_rtu_client(conn: socket.socket, holding: HoldingMap) -> None:
    with conn:
        conn.settimeout(30)
        while True:
            try:
                parsed = _read_rtu_request(conn)
            except (OSError, ConnectionError, TimeoutError, RuntimeError):
                return
            if parsed is None:
                return
            unit, pdu = parsed
            resp = handle_pdu(holding, pdu)
            try:
                conn.sendall(build_rtu_adu(unit, resp))
            except OSError:
                return


def serve_rtu(host: str, port: int) -> None:
    """RTU ADUs on a TCP stream (lab serial-server bridge). Not RS-485."""
    holding = HoldingMap()
    holding.write(0, [0x1111, 0x2222, 0x3333])
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind((host, port))
        sock.listen(32)
        print(f"ispf-modbus-rtu-fixture listening on tcp://{host}:{port} (RTU ADU)", flush=True)
        while True:
            conn, _ = sock.accept()
            threading.Thread(target=handle_rtu_client, args=(conn, holding), daemon=True).start()


def rtu_exchange(
    host: str,
    port: int,
    unit: int,
    pdu: bytes,
    timeout: float = 2.0,
) -> bytes:
    """Send one Modbus RTU ADU over TCP and return response PDU."""
    req = build_rtu_adu(unit, pdu)
    with socket.create_connection((host, port), timeout=timeout) as sock:
        sock.settimeout(timeout)
        sock.sendall(req)
        return _read_rtu_response(sock, pdu[0])


def rtu_probe(host: str, port: int, timeout: float = 2.0) -> None:
    """FC3 read of seed register 0 — compose healthcheck / smoke wait."""
    pdu = rtu_exchange(host, port, unit=1, pdu=struct.pack(">BHH", 3, 0, 1), timeout=timeout)
    if len(pdu) < 4 or pdu[0] != 3:
        raise RuntimeError(f"unexpected probe pdu {pdu!r}")
    value = struct.unpack(">H", pdu[2:4])[0]
    if value != 0x1111:
        raise RuntimeError(f"seed mismatch {value:#x}")


def self_test_rtu() -> None:
    """CRC unit checks + ephemeral TCP RTU FC6/FC16/FC3 round-trip."""
    # CRC known vector: unit=1, FC3 addr=0 qty=1 → body 01 03 00 00 00 01 → CRC 84 0A
    adu = build_rtu_adu(1, struct.pack(">BHH", 3, 0, 1))
    assert adu == bytes.fromhex("010300000001840a"), adu.hex()
    assert verify_rtu_adu(adu) == (1, struct.pack(">BHH", 3, 0, 1))

    holding = HoldingMap()
    holding.write(0, [0x1111, 0x2222, 0x3333])
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", 0))
    host, port = srv.getsockname()
    srv.listen(4)
    stop = threading.Event()

    def accept_loop() -> None:
        srv.settimeout(0.5)
        while not stop.is_set():
            try:
                conn, _ = srv.accept()
            except TimeoutError:
                continue
            except OSError:
                return
            threading.Thread(target=handle_rtu_client, args=(conn, holding), daemon=True).start()

    threading.Thread(target=accept_loop, daemon=True).start()
    try:
        rtu_probe(host, port)
        rtu_exchange(host, port, 1, struct.pack(">BHH", 6, 0, 0xA5A5))
        pdu = rtu_exchange(host, port, 1, struct.pack(">BHH", 3, 0, 1))
        got = struct.unpack(">H", pdu[2:4])[0]
        assert got == 0xA5A5, got
        rtu_exchange(host, port, 1, struct.pack(">BHHBHH", 16, 2, 2, 4, 0x1111, 0x2222))
        pdu = rtu_exchange(host, port, 1, struct.pack(">BHH", 3, 2, 2))
        a, b = struct.unpack(">HH", pdu[2:6])
        assert (a, b) == (0x1111, 0x2222), (a, b)
    finally:
        stop.set()
        srv.close()
    print("modbus-rtu fixture self-test ok", flush=True)


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--host", default="0.0.0.0")
    p.add_argument("--port", type=int, default=502)
    p.add_argument("--udp", action="store_true", help="serve Modbus UDP instead of TCP")
    p.add_argument("--rtu", action="store_true", help="serve Modbus RTU ADUs over TCP (lab bridge)")
    p.add_argument("--self-test", action="store_true")
    p.add_argument("--self-test-udp", action="store_true")
    p.add_argument("--self-test-rtu", action="store_true")
    args = p.parse_args()
    if args.self_test:
        self_test()
        return
    if args.self_test_udp:
        self_test_udp()
        return
    if args.self_test_rtu:
        self_test_rtu()
        return
    if args.udp:
        serve_udp(args.host, args.port)
        return
    if args.rtu:
        serve_rtu(args.host, args.port)
        return
    serve_tcp(args.host, args.port)


if __name__ == "__main__":
    main()
