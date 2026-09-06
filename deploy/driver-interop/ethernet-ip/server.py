#!/usr/bin/env python3
"""Minimal EtherNet/IP (CIP UCMM) TCP lab peer for OT Trust BL-141 (lab only).

Wire subset aligned with packages/ispf-driver-ethernet-ip Java loopback:
  RegisterSession (0x0065)
  SendRRData (0x006F) → UCMM Read Tag (0x4C) / Write Tag (0x4D)
  Seed tag: Program:MainProgram.Counter (DINT)

Unlike the Java test emulator (fixed read value), this peer persists writes
so smoke can do write → read-back. Stdlib only — not a production EIP stack.
"""
from __future__ import annotations

import argparse
import socket
import struct
import threading
from typing import Dict, Optional, Tuple

ENCAP_REGISTER_SESSION = 0x0065
ENCAP_SEND_RR_DATA = 0x006F
CPF_NULL_ADDRESS = 0x0000
CPF_UNCONNECTED_DATA = 0x00B2
CIP_READ_TAG = 0x4C
CIP_WRITE_TAG = 0x4D
CIP_DINT = 0xC4

DEFAULT_PORT = 44818
SESSION_HANDLE = 42
SEED_TAG = "Program:MainProgram.Counter"
SEED_VALUE = 12345678


def u16(n: int) -> bytes:
    return struct.pack("<H", n & 0xFFFF)


def u32(n: int) -> bytes:
    return struct.pack("<I", n & 0xFFFFFFFF)


def encode_symbolic_path(tag_path: str) -> bytes:
    parts = tag_path.split(".")
    out = bytearray()
    for part in parts:
        raw = part.encode("ascii")
        out.append(0x91)
        out.append(len(raw))
        out.extend(raw)
        if len(raw) % 2 == 1:
            out.append(0x00)
    return bytes(out)


def decode_symbolic_path(path: bytes) -> str:
    parts = []
    i = 0
    while i + 1 < len(path):
        if path[i] != 0x91:
            break
        length = path[i + 1]
        start = i + 2
        end = start + length
        if end > len(path):
            break
        parts.append(path[start:end].decode("ascii"))
        i = end + (1 if length % 2 == 1 else 0)
    return ".".join(parts)


class TagStore:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.values: Dict[str, Tuple[int, int]] = {SEED_TAG: (CIP_DINT, SEED_VALUE)}

    def read_dint(self, tag: str) -> Optional[int]:
        with self.lock:
            entry = self.values.get(tag)
            return None if entry is None else int(entry[1])

    def write_dint(self, tag: str, value: int) -> None:
        with self.lock:
            self.values[tag] = (CIP_DINT, int(value))


def build_encap(command: int, session: int, payload: bytes) -> bytes:
    header = bytearray(24)
    struct.pack_into("<H", header, 0, command)
    struct.pack_into("<H", header, 2, len(payload))
    struct.pack_into("<I", header, 4, session)
    # status=0, sender context=0, options=0
    return bytes(header) + payload


def build_send_rr_reply(cip_reply: bytes) -> bytes:
    body = bytearray()
    body.extend(u32(0))  # interface handle
    body.extend(u16(0))  # timeout
    body.extend(u16(2))  # item count
    body.extend(u16(CPF_NULL_ADDRESS))
    body.extend(u16(0))
    body.extend(u16(CPF_UNCONNECTED_DATA))
    body.extend(u16(len(cip_reply)))
    body.extend(cip_reply)
    return bytes(body)


def extract_cip_request(payload: bytes) -> bytes:
    if len(payload) < 8:
        return b""
    offset = 4 + 2  # interface + timeout
    item_count = struct.unpack_from("<H", payload, offset)[0]
    offset += 2
    for _ in range(item_count):
        if offset + 4 > len(payload):
            break
        type_id, length = struct.unpack_from("<HH", payload, offset)
        offset += 4
        if type_id == CPF_UNCONNECTED_DATA:
            return payload[offset : offset + length]
        offset += length
    return b""


def handle_cip(store: TagStore, cip: bytes) -> bytes:
    if len(cip) < 2:
        return bytes([CIP_READ_TAG | 0x80, 0, 0x08, 0])
    service = cip[0]
    path_words = cip[1]
    path_len = path_words * 2
    if len(cip) < 2 + path_len:
        return bytes([service | 0x80, 0, 0x04, 0])
    path = cip[2 : 2 + path_len]
    tag = decode_symbolic_path(path)
    rest = cip[2 + path_len :]

    if service == CIP_READ_TAG:
        value = store.read_dint(tag)
        if value is None:
            return bytes([CIP_READ_TAG | 0x80, 0, 0x04, 0])
        return bytes([CIP_READ_TAG | 0x80, 0, 0, 0]) + u16(CIP_DINT) + u32(value)

    if service == CIP_WRITE_TAG:
        if len(rest) < 8:
            return bytes([CIP_WRITE_TAG | 0x80, 0, 0x04, 0])
        cip_type, _count, value = struct.unpack_from("<HHi", rest, 0)
        if cip_type != CIP_DINT:
            return bytes([CIP_WRITE_TAG | 0x80, 0, 0x08, 0])
        store.write_dint(tag, value)
        return bytes([CIP_WRITE_TAG | 0x80, 0, 0, 0])

    return bytes([service | 0x80, 0, 0x08, 0])


def handle_client(conn: socket.socket, store: TagStore) -> None:
    conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    conn.settimeout(30)
    session = 0
    try:
        while True:
            header = _recv_exact(conn, 24)
            if header is None:
                return
            command, length, req_session = struct.unpack_from("<HHI", header, 0)
            payload = b""
            if length:
                payload = _recv_exact(conn, length)
                if payload is None:
                    return
            if command == ENCAP_REGISTER_SESSION:
                session = SESSION_HANDLE
                body = u16(1) + u16(0)
                conn.sendall(build_encap(ENCAP_REGISTER_SESSION, session, body))
            elif command == ENCAP_SEND_RR_DATA:
                cip = extract_cip_request(payload)
                reply = handle_cip(store, cip)
                conn.sendall(build_encap(ENCAP_SEND_RR_DATA, req_session or session, build_send_rr_reply(reply)))
            else:
                # unsupported command — status 1
                bad = bytearray(build_encap(command, req_session or session, b""))
                struct.pack_into("<I", bad, 8, 1)
                conn.sendall(bytes(bad))
    except (OSError, ConnectionError, TimeoutError):
        return
    finally:
        try:
            conn.close()
        except OSError:
            pass


def _recv_exact(conn: socket.socket, n: int) -> Optional[bytes]:
    buf = bytearray()
    while len(buf) < n:
        chunk = conn.recv(n - len(buf))
        if not chunk:
            return None
        buf.extend(chunk)
    return bytes(buf)


def serve(host: str, port: int) -> None:
    store = TagStore()
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((host, port))
    sock.listen(8)
    print(f"ethernet-ip lab peer listening tcp://{host}:{port} tag={SEED_TAG}", flush=True)
    while True:
        conn, _addr = sock.accept()
        threading.Thread(target=handle_client, args=(conn, store), daemon=True).start()


class Client:
    def __init__(self, host: str, port: int, timeout: float = 5.0) -> None:
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        self.sock.settimeout(timeout)
        self.session = 0
        self._register()

    def close(self) -> None:
        try:
            self.sock.close()
        except OSError:
            pass

    def _exchange(self, command: int, payload: bytes) -> bytes:
        self.sock.sendall(build_encap(command, self.session, payload))
        header = _recv_exact(self.sock, 24)
        if header is None:
            raise ConnectionError("peer closed")
        resp_cmd, length = struct.unpack_from("<HH", header, 0)
        session = struct.unpack_from("<I", header, 4)[0]
        status = struct.unpack_from("<I", header, 8)[0]
        body = _recv_exact(self.sock, length) if length else b""
        if body is None:
            raise ConnectionError("peer closed during payload")
        if resp_cmd != command:
            raise RuntimeError(f"unexpected command 0x{resp_cmd:x}")
        if status != 0:
            raise RuntimeError(f"encap status {status}")
        if command == ENCAP_REGISTER_SESSION:
            self.session = session
        return body

    def _register(self) -> None:
        self._exchange(ENCAP_REGISTER_SESSION, u16(1) + u16(0))
        if self.session == 0:
            raise RuntimeError("RegisterSession returned session handle 0")

    def read_tag(self, tag: str = SEED_TAG) -> int:
        path = encode_symbolic_path(tag)
        cip = bytes([CIP_READ_TAG, len(path) // 2]) + path + u16(1)
        payload = (
            u32(0)
            + u16(10)
            + u16(2)
            + u16(CPF_NULL_ADDRESS)
            + u16(0)
            + u16(CPF_UNCONNECTED_DATA)
            + u16(len(cip))
            + cip
        )
        reply_payload = self._exchange(ENCAP_SEND_RR_DATA, payload)
        cip_reply = extract_cip_request(reply_payload)
        if len(cip_reply) < 8 or cip_reply[2] != 0:
            raise RuntimeError(f"CIP read failed: {cip_reply!r}")
        _type, value = struct.unpack_from("<Hi", cip_reply, 4)
        return int(value)

    def write_tag(self, value: int, tag: str = SEED_TAG) -> None:
        path = encode_symbolic_path(tag)
        data = u16(CIP_DINT) + u16(1) + u32(int(value))
        cip = bytes([CIP_WRITE_TAG, len(path) // 2]) + path + data
        payload = (
            u32(0)
            + u16(10)
            + u16(2)
            + u16(CPF_NULL_ADDRESS)
            + u16(0)
            + u16(CPF_UNCONNECTED_DATA)
            + u16(len(cip))
            + cip
        )
        reply_payload = self._exchange(ENCAP_SEND_RR_DATA, payload)
        cip_reply = extract_cip_request(reply_payload)
        if len(cip_reply) < 4 or cip_reply[2] != 0:
            raise RuntimeError(f"CIP write failed: {cip_reply!r}")


def eip_read_tag(host: str, port: int = DEFAULT_PORT, tag: str = SEED_TAG) -> int:
    client = Client(host, port)
    try:
        return client.read_tag(tag)
    finally:
        client.close()


def eip_write_tag(host: str, port: int, value: int, tag: str = SEED_TAG) -> None:
    client = Client(host, port)
    try:
        client.write_tag(value, tag)
    finally:
        client.close()


def self_test() -> None:
    store = TagStore()
    host, port = "127.0.0.1", 14481
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((host, port))
    sock.listen(1)
    sock.settimeout(5)

    def accept_loop() -> None:
        try:
            for _ in range(4):
                conn, _ = sock.accept()
                handle_client(conn, store)
        except OSError:
            pass

    threading.Thread(target=accept_loop, daemon=True).start()
    before = eip_read_tag(host, port)
    assert before == SEED_VALUE, before
    eip_write_tag(host, port, 555)
    after = eip_read_tag(host, port)
    assert after == 555, after
    sock.close()
    print("ethernet-ip self-test ok")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    serve(args.host, args.port)


if __name__ == "__main__":
    main()
