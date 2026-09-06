#!/usr/bin/env python3
"""Minimal ISPF DLMS TCP WRAPPER lab peer for OT Trust BL-141 (lab only).

Wire subset aligned with packages/ispf-driver-dlms Java loopback
(DlmsTcpWrapperCodec / DlmsLoopbackServer):
  WRAPPER v1 (8-byte BE header)
  Associate 0x60/0x61, GET 0xC0/0xC4, SET 0xC1/0xC5
  Seed REGISTER 1.0.1.8.0.255 attr 2 = 42.0

Writes persist so smoke can do SET → GET read-back. Stdlib only —
not a production DLMS/COSEM stack (no HDLC, no Gurux).
"""
from __future__ import annotations

import argparse
import socket
import struct
import threading
from typing import Dict, Optional, Tuple

VERSION = 1
CMD_ASSOCIATE_REQUEST = 0x60
CMD_ASSOCIATE_RESPONSE = 0x61
CMD_GET_REQUEST = 0xC0
CMD_GET_RESPONSE = 0xC4
CMD_SET_REQUEST = 0xC1
CMD_SET_RESPONSE = 0xC5

TAG_NULL = 0
TAG_BOOLEAN = 3
TAG_OCTETS = 9
TAG_STRING = 10
TAG_DOUBLE = 17

CLASS_DATA = 1
CLASS_REGISTER = 3

DEFAULT_PORT = 4059
DEFAULT_CLIENT_SAP = 16
DEFAULT_LOGICAL_DEVICE = 1

ENERGY_OBIS = "1.0.1.8.0.255"
DEVICE_NAME_OBIS = "0.0.42.0.0.255"
SEED_ENERGY = 42.0
SEED_DEVICE_NAME = "ISPF-TEST"


def store_key(class_id: int, obis: str, attr: int) -> str:
    return f"{class_id}:{obis}:{attr}"


class MeterStore:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.values: Dict[str, object] = {
            store_key(CLASS_REGISTER, ENERGY_OBIS, 2): float(SEED_ENERGY),
            store_key(CLASS_DATA, DEVICE_NAME_OBIS, 2): SEED_DEVICE_NAME,
        }

    def get(self, class_id: int, obis: str, attr: int) -> Optional[object]:
        with self.lock:
            return self.values.get(store_key(class_id, obis, attr))

    def set(self, class_id: int, obis: str, attr: int, value: object) -> None:
        with self.lock:
            self.values[store_key(class_id, obis, attr)] = value


def encode_obis(obis: str) -> bytes:
    parts = obis.split(".")
    if len(parts) != 6:
        raise ValueError(f"invalid OBIS: {obis}")
    return bytes(int(p) & 0xFF for p in parts)


def decode_obis(data: bytes) -> str:
    if len(data) != 6:
        raise ValueError("OBIS must be 6 bytes")
    return ".".join(str(b) for b in data)


def encode_value(value: object) -> bytes:
    if value is None:
        return bytes([TAG_NULL])
    if isinstance(value, bool):
        return bytes([TAG_BOOLEAN, 1 if value else 0])
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return bytes([TAG_DOUBLE]) + struct.pack(">d", float(value))
    if isinstance(value, (bytes, bytearray)):
        raw = bytes(value)
        return bytes([TAG_OCTETS]) + struct.pack(">H", len(raw)) + raw
    raw = str(value).encode("utf-8")
    return bytes([TAG_STRING]) + struct.pack(">H", len(raw)) + raw


def decode_value(buf: bytes, offset: int = 0) -> Tuple[object, int]:
    if offset >= len(buf):
        raise ValueError("truncated value")
    tag = buf[offset]
    offset += 1
    if tag == TAG_NULL:
        return None, offset
    if tag == TAG_BOOLEAN:
        return buf[offset] != 0, offset + 1
    if tag == TAG_DOUBLE:
        return struct.unpack_from(">d", buf, offset)[0], offset + 8
    if tag in (TAG_STRING, TAG_OCTETS):
        length = struct.unpack_from(">H", buf, offset)[0]
        offset += 2
        raw = buf[offset : offset + length]
        offset += length
        if tag == TAG_STRING:
            return raw.decode("utf-8"), offset
        return raw, offset
    raise ValueError(f"unsupported tag {tag}")


def write_frame(sock: socket.socket, source: int, destination: int, payload: bytes) -> None:
    header = struct.pack(
        ">HHHH",
        VERSION,
        source & 0xFFFF,
        destination & 0xFFFF,
        len(payload) & 0xFFFF,
    )
    sock.sendall(header + payload)


def read_exact(sock: socket.socket, n: int) -> Optional[bytes]:
    buf = bytearray()
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            return None
        buf.extend(chunk)
    return bytes(buf)


def read_frame(sock: socket.socket) -> Optional[Tuple[int, int, bytes]]:
    header = read_exact(sock, 8)
    if header is None:
        return None
    version, source, destination, length = struct.unpack(">HHHH", header)
    if version != VERSION:
        raise RuntimeError(f"unsupported WRAPPER version {version}")
    payload = b""
    if length:
        payload = read_exact(sock, length)
        if payload is None:
            return None
    return source, destination, payload


def handle_apdu(
    store: MeterStore,
    associated: bool,
    source_wport: int,
    client_sap: int,
    payload: bytes,
) -> Tuple[bool, bytes]:
    if not payload:
        return associated, bytes([CMD_ASSOCIATE_RESPONSE, 1])
    command = payload[0]
    if command == CMD_ASSOCIATE_REQUEST:
        ok = source_wport == client_sap
        return ok, bytes([CMD_ASSOCIATE_RESPONSE, 0 if ok else 1])
    if not associated:
        return False, bytes([CMD_ASSOCIATE_RESPONSE, 1])
    if command == CMD_GET_REQUEST:
        if len(payload) < 1 + 2 + 6 + 1:
            return associated, bytes([CMD_GET_RESPONSE, 1]) + encode_value(None)
        class_id = struct.unpack_from(">H", payload, 1)[0]
        obis = decode_obis(payload[3:9])
        attr = payload[9]
        value = store.get(class_id, obis, attr)
        if value is None:
            return associated, bytes([CMD_GET_RESPONSE, 1]) + encode_value(None)
        return associated, bytes([CMD_GET_RESPONSE, 0]) + encode_value(value)
    if command == CMD_SET_REQUEST:
        if len(payload) < 1 + 2 + 6 + 1 + 1:
            return associated, bytes([CMD_SET_RESPONSE, 1])
        class_id = struct.unpack_from(">H", payload, 1)[0]
        obis = decode_obis(payload[3:9])
        attr = payload[9]
        value, _ = decode_value(payload, 10)
        store.set(class_id, obis, attr, value)
        return associated, bytes([CMD_SET_RESPONSE, 0])
    return associated, bytes([CMD_SET_RESPONSE, 1])


def handle_client(conn: socket.socket, store: MeterStore, client_sap: int) -> None:
    conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    conn.settimeout(30)
    associated = False
    try:
        while True:
            frame = read_frame(conn)
            if frame is None:
                return
            source, destination, payload = frame
            associated, response = handle_apdu(store, associated, source, client_sap, payload)
            # reply with swapped wPorts (meter → client)
            write_frame(conn, destination, source, response)
    except (OSError, ConnectionError, TimeoutError, RuntimeError, ValueError):
        return
    finally:
        try:
            conn.close()
        except OSError:
            pass


def serve(host: str, port: int, client_sap: int = DEFAULT_CLIENT_SAP) -> None:
    store = MeterStore()
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((host, port))
    sock.listen(8)
    print(
        f"dlms lab peer listening tcp://{host}:{port} "
        f"energy={ENERGY_OBIS} client_sap={client_sap}",
        flush=True,
    )
    while True:
        conn, _addr = sock.accept()
        threading.Thread(
            target=handle_client,
            args=(conn, store, client_sap),
            daemon=True,
        ).start()


class Client:
    def __init__(
        self,
        host: str,
        port: int,
        client_sap: int = DEFAULT_CLIENT_SAP,
        logical_device: int = DEFAULT_LOGICAL_DEVICE,
        timeout: float = 5.0,
    ) -> None:
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        self.sock.settimeout(timeout)
        self.client_sap = client_sap
        self.logical_device = logical_device
        self._associate()

    def close(self) -> None:
        try:
            self.sock.close()
        except OSError:
            pass

    def _exchange(self, payload: bytes) -> bytes:
        write_frame(self.sock, self.client_sap, self.logical_device, payload)
        frame = read_frame(self.sock)
        if frame is None:
            raise ConnectionError("peer closed")
        _source, _destination, response = frame
        return response

    def _associate(self) -> None:
        body = struct.pack(
            ">BHH",
            CMD_ASSOCIATE_REQUEST,
            self.client_sap,
            self.logical_device,
        )
        response = self._exchange(body)
        if len(response) < 2 or response[0] != CMD_ASSOCIATE_RESPONSE or response[1] != 0:
            raise RuntimeError(f"associate rejected: {response!r}")

    def get(
        self,
        class_id: int = CLASS_REGISTER,
        obis: str = ENERGY_OBIS,
        attr: int = 2,
    ) -> object:
        payload = (
            bytes([CMD_GET_REQUEST])
            + struct.pack(">H", class_id)
            + encode_obis(obis)
            + bytes([attr])
        )
        response = self._exchange(payload)
        if len(response) < 2 or response[0] != CMD_GET_RESPONSE or response[1] != 0:
            raise RuntimeError(f"GET failed: {response!r}")
        value, _ = decode_value(response, 2)
        return value

    def set(
        self,
        value: object,
        class_id: int = CLASS_REGISTER,
        obis: str = ENERGY_OBIS,
        attr: int = 2,
    ) -> None:
        payload = (
            bytes([CMD_SET_REQUEST])
            + struct.pack(">H", class_id)
            + encode_obis(obis)
            + bytes([attr])
            + encode_value(value)
        )
        response = self._exchange(payload)
        if len(response) < 2 or response[0] != CMD_SET_RESPONSE or response[1] != 0:
            raise RuntimeError(f"SET failed: {response!r}")


def dlms_read(
    host: str,
    port: int = DEFAULT_PORT,
    client_sap: int = DEFAULT_CLIENT_SAP,
    logical_device: int = DEFAULT_LOGICAL_DEVICE,
) -> float:
    client = Client(host, port, client_sap, logical_device)
    try:
        return float(client.get())  # type: ignore[arg-type]
    finally:
        client.close()


def dlms_write(
    host: str,
    port: int,
    value: float,
    client_sap: int = DEFAULT_CLIENT_SAP,
    logical_device: int = DEFAULT_LOGICAL_DEVICE,
) -> None:
    client = Client(host, port, client_sap, logical_device)
    try:
        client.set(float(value))
    finally:
        client.close()


def self_test() -> None:
    store = MeterStore()
    host, port = "127.0.0.1", 14059
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((host, port))
    sock.listen(1)
    sock.settimeout(5)

    def accept_loop() -> None:
        try:
            for _ in range(4):
                conn, _ = sock.accept()
                handle_client(conn, store, DEFAULT_CLIENT_SAP)
        except OSError:
            pass

    threading.Thread(target=accept_loop, daemon=True).start()
    before = dlms_read(host, port)
    assert abs(before - SEED_ENERGY) < 1e-9, before
    dlms_write(host, port, 77.5)
    after = dlms_read(host, port)
    assert abs(after - 77.5) < 1e-9, after
    sock.close()
    print("dlms self-test ok")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--client-sap", type=int, default=DEFAULT_CLIENT_SAP)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    serve(args.host, args.port, args.client_sap)


if __name__ == "__main__":
    main()
