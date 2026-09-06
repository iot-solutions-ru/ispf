#!/usr/bin/env python3
"""Minimal IEC 60870-5-104 TCP outstation for OT Trust BL-141 (lab only).

Wire subset aligned with packages/ispf-driver-iec104:
  STARTDT_ACT → STARTDT_CON
  TESTFR_ACT → TESTFR_CON
  C_RD_NA_1 (102) → measurement reply for seeded IOAs
  C_SE_NC_1 (50) / C_SC_NA_1 (45) update lab store

Default CA=1, seed IOA 3001 = 42.5 (FLOAT / M_ME_NC_1).
Stdlib only — not a production IEC 104 stack.
"""
from __future__ import annotations

import argparse
import socket
import struct
import threading
import time
from typing import Dict, Optional, Tuple

START = 0x68
STARTDT_ACT = bytes([0x07, 0x00, 0x00, 0x00])
STARTDT_CON = bytes([0x0B, 0x00, 0x00, 0x00])
TESTFR_ACT = bytes([0x43, 0x00, 0x00, 0x00])
TESTFR_CON = bytes([0x83, 0x00, 0x00, 0x00])

M_SP_NA_1 = 1
M_ME_NC_1 = 13
C_SC_NA_1 = 45
C_SE_NC_1 = 50
C_RD_NA_1 = 102

CAUSE_REQUEST = 5
CAUSE_ACTIVATION = 6

COMMON_ADDRESS = 1
IOA_FLOAT = 3001
IOA_BOOL = 2001
DEFAULT_FLOAT = 42.5


def write_le16(value: int) -> bytes:
    return bytes([value & 0xFF, (value >> 8) & 0xFF])


def write_le24(value: int) -> bytes:
    return bytes([value & 0xFF, (value >> 8) & 0xFF, (value >> 16) & 0xFF])


def read_le16(data: bytes, offset: int) -> int:
    return data[offset] | (data[offset + 1] << 8)


def read_le24(data: bytes, offset: int) -> int:
    return data[offset] | (data[offset + 1] << 8) | (data[offset + 2] << 16)


def write_f32(value: float) -> bytes:
    return struct.pack("<f", float(value))


def read_f32(data: bytes, offset: int) -> float:
    return struct.unpack("<f", data[offset : offset + 4])[0]


def encode_asdu(type_id: int, cause: int, ca: int, ioa: int, payload: bytes) -> bytes:
    return (
        bytes([type_id & 0xFF, 0x01])
        + write_le16(cause & 0x3F)
        + write_le16(ca)
        + write_le24(ioa)
        + payload
    )


def encode_u_frame(control: bytes) -> bytes:
    return bytes([START, 4]) + control


def encode_i_frame(send_seq: int, recv_seq: int, asdu: bytes) -> bytes:
    body_len = 4 + len(asdu)
    send = (send_seq << 1) & 0xFFFE
    recv = (recv_seq << 1) & 0xFFFE
    return bytes([START, body_len & 0xFF]) + write_le16(send) + write_le16(recv) + asdu


class LabStore:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.values: Dict[int, Tuple[int, float]] = {
            IOA_FLOAT: (M_ME_NC_1, float(DEFAULT_FLOAT)),
            IOA_BOOL: (M_SP_NA_1, 1.0),
        }

    def get(self, ioa: int) -> Optional[Tuple[int, float]]:
        with self.lock:
            return self.values.get(ioa)

    def set_float(self, ioa: int, value: float) -> None:
        with self.lock:
            self.values[ioa] = (M_ME_NC_1, float(value))

    def set_bool(self, ioa: int, value: bool) -> None:
        with self.lock:
            self.values[ioa] = (M_SP_NA_1, 1.0 if value else 0.0)


class Session:
    def __init__(self, conn: socket.socket, store: LabStore) -> None:
        self.conn = conn
        self.store = store
        self.send_seq = 0
        self.recv_seq = 0
        self.buf = bytearray()

    def send_all(self, data: bytes) -> None:
        self.conn.sendall(data)

    def send_u(self, control: bytes) -> None:
        self.send_all(encode_u_frame(control))

    def send_i(self, asdu: bytes) -> None:
        self.send_all(encode_i_frame(self.send_seq, self.recv_seq, asdu))
        self.send_seq = (self.send_seq + 1) & 0x7FFF

    def handle_asdu(self, asdu: bytes) -> None:
        if len(asdu) < 9:
            return
        type_id = asdu[0]
        ca = read_le16(asdu, 4)
        ioa = read_le24(asdu, 6)
        if ca != COMMON_ADDRESS:
            return
        if type_id == C_RD_NA_1:
            entry = self.store.get(ioa)
            if entry is None:
                return
            meas_type, value = entry
            if meas_type == M_ME_NC_1:
                payload = write_f32(value) + bytes([0])
            elif meas_type == M_SP_NA_1:
                payload = bytes([1 if value else 0])
            else:
                return
            self.send_i(encode_asdu(meas_type, CAUSE_REQUEST, ca, ioa, payload))
            return
        if type_id == C_SE_NC_1 and len(asdu) >= 13:
            self.store.set_float(ioa, read_f32(asdu, 9))
            return
        if type_id == C_SC_NA_1 and len(asdu) >= 10:
            self.store.set_bool(ioa, (asdu[9] & 0x01) != 0)

    def handle_body(self, body: bytes) -> None:
        if len(body) < 4:
            return
        control0 = body[0]
        if (control0 & 0x01) == 0:
            self.recv_seq = (self.recv_seq + 1) & 0x7FFF
            asdu = body[4:]
            if asdu:
                self.handle_asdu(asdu)
            return
        if (control0 & 0x03) == 0x03:
            ctrl = body[:4]
            if ctrl == STARTDT_ACT:
                self.send_u(STARTDT_CON)
            elif ctrl == TESTFR_ACT:
                self.send_u(TESTFR_CON)

    def feed(self, data: bytes) -> None:
        self.buf.extend(data)
        while True:
            if len(self.buf) < 2:
                return
            if self.buf[0] != START:
                del self.buf[0]
                continue
            length = self.buf[1]
            if length < 4:
                del self.buf[0]
                continue
            if len(self.buf) < 2 + length:
                return
            body = bytes(self.buf[2 : 2 + length])
            del self.buf[: 2 + length]
            self.handle_body(body)


def handle_client(conn: socket.socket, store: LabStore) -> None:
    conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    conn.settimeout(60)
    session = Session(conn, store)
    try:
        while True:
            chunk = conn.recv(4096)
            if not chunk:
                return
            session.feed(chunk)
    except (OSError, ConnectionError, TimeoutError):
        return
    finally:
        try:
            conn.close()
        except OSError:
            pass


def serve(host: str, port: int) -> None:
    store = LabStore()
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((host, port))
    sock.listen(8)
    print(f"iec104 lab outstation listening tcp://{host}:{port} ca={COMMON_ADDRESS}", flush=True)
    while True:
        conn, _addr = sock.accept()
        threading.Thread(target=handle_client, args=(conn, store), daemon=True).start()


class Client:
    def __init__(self, host: str, port: int, timeout: float = 5.0) -> None:
        self.timeout = timeout
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        self.sock.settimeout(timeout)
        self.send_seq = 0
        self.recv_seq = 0
        self.buf = bytearray()
        self._start_dt()

    def close(self) -> None:
        try:
            self.sock.close()
        except OSError:
            pass

    def _send(self, data: bytes) -> None:
        self.sock.sendall(data)

    def _send_u(self, control: bytes) -> None:
        self._send(encode_u_frame(control))

    def _send_i(self, asdu: bytes) -> None:
        self._send(encode_i_frame(self.send_seq, self.recv_seq, asdu))
        self.send_seq = (self.send_seq + 1) & 0x7FFF

    def _recv_frame(self) -> Tuple[str, bytes]:
        deadline = time.time() + self.timeout
        while time.time() < deadline:
            while True:
                if len(self.buf) >= 2 and self.buf[0] == START:
                    length = self.buf[1]
                    if len(self.buf) >= 2 + length:
                        body = bytes(self.buf[2 : 2 + length])
                        del self.buf[: 2 + length]
                        if (body[0] & 0x01) == 0:
                            self.recv_seq = (self.recv_seq + 1) & 0x7FFF
                            return "I", body[4:]
                        if (body[0] & 0x03) == 0x03:
                            return "U", body[:4]
                        return "S", body
                if self.buf and self.buf[0] != START:
                    del self.buf[0]
                    continue
                break
            chunk = self.sock.recv(4096)
            if not chunk:
                raise ConnectionError("peer closed")
            self.buf.extend(chunk)
        raise TimeoutError("IEC104 frame timeout")

    def _start_dt(self) -> None:
        self._send_u(STARTDT_ACT)
        while True:
            kind, payload = self._recv_frame()
            if kind == "U" and payload == STARTDT_CON:
                return

    def read_float(self, ioa: int = IOA_FLOAT, ca: int = COMMON_ADDRESS) -> float:
        self._send_i(encode_asdu(C_RD_NA_1, CAUSE_REQUEST, ca, ioa, b""))
        deadline = time.time() + self.timeout
        while time.time() < deadline:
            kind, payload = self._recv_frame()
            if kind != "I" or len(payload) < 14:
                continue
            if payload[0] != M_ME_NC_1:
                continue
            if read_le24(payload, 6) != ioa:
                continue
            return read_f32(payload, 9)
        raise TimeoutError(f"no M_ME_NC_1 for IOA {ioa}")

    def write_float(self, value: float, ioa: int = IOA_FLOAT, ca: int = COMMON_ADDRESS) -> None:
        self._send_i(
            encode_asdu(C_SE_NC_1, CAUSE_ACTIVATION, ca, ioa, write_f32(value) + bytes([0]))
        )


def iec104_read_float(host: str, port: int, ioa: int = IOA_FLOAT) -> float:
    client = Client(host, port)
    try:
        return client.read_float(ioa=ioa)
    finally:
        client.close()


def iec104_write_float(host: str, port: int, value: float, ioa: int = IOA_FLOAT) -> None:
    client = Client(host, port)
    try:
        client.write_float(value, ioa=ioa)
        time.sleep(0.05)
    finally:
        client.close()


def self_test() -> None:
    store = LabStore()
    host, port = "127.0.0.1", 12404
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((host, port))
    sock.listen(1)
    sock.settimeout(5)

    def accept_loop() -> None:
        try:
            for _ in range(3):
                conn, _ = sock.accept()
                handle_client(conn, store)
        except OSError:
            pass

    threading.Thread(target=accept_loop, daemon=True).start()
    before = iec104_read_float(host, port)
    assert abs(before - DEFAULT_FLOAT) < 1e-3, before
    iec104_write_float(host, port, 27.25)
    after = iec104_read_float(host, port)
    assert abs(after - 27.25) < 1e-3, after
    sock.close()
    print("iec104 self-test ok")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=2404)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    serve(args.host, args.port)


if __name__ == "__main__":
    main()
