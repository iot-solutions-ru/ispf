#!/usr/bin/env python3
"""Minimal Modbus TCP fixture for OT Trust Wave 1 (BL-141).

Supports FC3 (read holding), FC6 (write single), FC16 (write multiple).
Stdlib only — no pymodbus. Lab use; not a production slave.
"""
from __future__ import annotations

import argparse
import socket
import struct
import threading
from typing import List


class HoldingMap:
    def __init__(self, size: int = 256) -> None:
        self.regs: List[int] = [0] * size

    def read(self, addr: int, count: int) -> List[int]:
        if addr < 0 or count < 1 or addr + count > len(self.regs):
            raise ValueError("illegal data address")
        return self.regs[addr : addr + count]

    def write(self, addr: int, values: List[int]) -> None:
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


def _recv_exact(conn: socket.socket, n: int) -> bytes | None:
    buf = b""
    while len(buf) < n:
        chunk = conn.recv(n - len(buf))
        if not chunk:
            return None
        buf += chunk
    return buf


def serve(host: str, port: int) -> None:
    holding = HoldingMap()
    # Seed a recognizable pattern for read-only smoke.
    holding.write(0, [0x1111, 0x2222, 0x3333])
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind((host, port))
        sock.listen(32)
        print(f"ispf-modbus-fixture listening on {host}:{port}", flush=True)
        while True:
            conn, _ = sock.accept()
            threading.Thread(target=handle_client, args=(conn, holding), daemon=True).start()


def self_test() -> None:
    """In-process FC6 + FC16 + FC3 without binding a port (unit check)."""
    h = HoldingMap()
    # FC6
    r = handle_pdu(h, struct.pack(">BHH", 6, 0, 0xA5A5))
    assert r == struct.pack(">BHH", 6, 0, 0xA5A5), r
    r = handle_pdu(h, struct.pack(">BHH", 3, 0, 1))
    assert r == struct.pack(">BBH", 3, 2, 0xA5A5), r
    # FC16
    r = handle_pdu(h, struct.pack(">BHHBHH", 16, 1, 2, 4, 0x10, 0x20))
    assert r == struct.pack(">BHH", 16, 1, 2), r
    r = handle_pdu(h, struct.pack(">BHH", 3, 1, 2))
    assert r == struct.pack(">BBHH", 3, 4, 0x10, 0x20), r
    print("modbus fixture self-test ok", flush=True)


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--host", default="0.0.0.0")
    p.add_argument("--port", type=int, default=502)
    p.add_argument("--self-test", action="store_true")
    args = p.parse_args()
    if args.self_test:
        self_test()
        return
    serve(args.host, args.port)


if __name__ == "__main__":
    main()
