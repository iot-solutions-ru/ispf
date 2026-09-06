#!/usr/bin/env python3
"""Minimal SNMPv2c UDP agent for OT Trust BL-141 (lab only).

Supports GET/SET for a writable Integer32 lab OID and GET for sysDescr.
Stdlib only — not a production snmpd. Community: public (read+write in lab).
"""
from __future__ import annotations

import argparse
import socket
import threading
from typing import List, Optional, Tuple

OID_SYSDESCR = (1, 3, 6, 1, 2, 1, 1, 1, 0)
OID_LAB_GAUGE = (1, 3, 6, 1, 4, 1, 99999, 1, 1, 0)
COMMUNITY = b"public"


class BerReader:
    def __init__(self, data: bytes) -> None:
        self.data = data
        self.i = 0

    def remaining(self) -> int:
        return len(self.data) - self.i

    def read_byte(self) -> int:
        if self.i >= len(self.data):
            raise ValueError("truncated")
        b = self.data[self.i]
        self.i += 1
        return b

    def read_length(self) -> int:
        first = self.read_byte()
        if first & 0x80 == 0:
            return first
        n = first & 0x7F
        if n == 0 or n > 4:
            raise ValueError("bad length")
        val = 0
        for _ in range(n):
            val = (val << 8) | self.read_byte()
        return val

    def read_tlv(self) -> Tuple[int, bytes]:
        tag = self.read_byte()
        length = self.read_length()
        if self.remaining() < length:
            raise ValueError("truncated value")
        value = self.data[self.i : self.i + length]
        self.i += length
        return tag, value


def encode_length(n: int) -> bytes:
    if n < 0x80:
        return bytes([n])
    if n < 0x100:
        return bytes([0x81, n])
    if n < 0x10000:
        return bytes([0x82, (n >> 8) & 0xFF, n & 0xFF])
    raise ValueError("length too large")


def encode_tlv(tag: int, value: bytes) -> bytes:
    return bytes([tag]) + encode_length(len(value)) + value


def encode_null() -> bytes:
    return encode_tlv(0x05, b"")


def encode_integer(value: int) -> bytes:
    if value < 0:
        raise ValueError("negative not supported in lab fixture")
    if value == 0:
        raw = b"\x00"
    else:
        raw = value.to_bytes((value.bit_length() + 7) // 8, "big")
        if raw[0] & 0x80:
            raw = b"\x00" + raw
    return encode_tlv(0x02, raw)


def encode_octet_string(value: bytes) -> bytes:
    return encode_tlv(0x04, value)


def encode_oid(oid: Tuple[int, ...]) -> bytes:
    if len(oid) < 2:
        raise ValueError("oid too short")
    body = bytes([40 * oid[0] + oid[1]])
    for n in oid[2:]:
        if n < 0:
            raise ValueError("bad oid")
        chunks: List[int] = [n & 0x7F]
        n >>= 7
        while n:
            chunks.append(0x80 | (n & 0x7F))
            n >>= 7
        body += bytes(reversed(chunks))
    return encode_tlv(0x06, body)


def encode_sequence(parts: List[bytes]) -> bytes:
    return encode_tlv(0x30, b"".join(parts))


def decode_integer(value: bytes) -> int:
    if not value:
        raise ValueError("empty integer")
    return int.from_bytes(value, "big", signed=True)


def decode_oid(value: bytes) -> Tuple[int, ...]:
    if not value:
        raise ValueError("empty oid")
    first = value[0]
    oid = [first // 40, first % 40]
    i = 1
    while i < len(value):
        n = 0
        while True:
            b = value[i]
            i += 1
            n = (n << 7) | (b & 0x7F)
            if b & 0x80 == 0:
                break
            if i >= len(value):
                raise ValueError("truncated oid")
        oid.append(n)
    return tuple(oid)


class LabStore:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.sys_descr = b"ISPF OT Trust SNMP lab fixture"
        self.gauge = 42

    def get(self, oid: Tuple[int, ...]) -> Optional[bytes]:
        with self.lock:
            if oid == OID_SYSDESCR:
                return encode_octet_string(self.sys_descr)
            if oid == OID_LAB_GAUGE:
                return encode_integer(self.gauge)
        return None

    def set_integer(self, oid: Tuple[int, ...], value: int) -> bool:
        with self.lock:
            if oid != OID_LAB_GAUGE:
                return False
            self.gauge = value
            return True


def parse_varbind(reader: BerReader) -> Tuple[Tuple[int, ...], int, bytes]:
    tag, seq = reader.read_tlv()
    if tag != 0x30:
        raise ValueError("varbind not sequence")
    inner = BerReader(seq)
    t1, oid_raw = inner.read_tlv()
    if t1 != 0x06:
        raise ValueError("expected oid")
    t2, val = inner.read_tlv()
    return decode_oid(oid_raw), t2, val


def build_response(
    version: int,
    community: bytes,
    req_id: int,
    error_status: int,
    error_index: int,
    varbinds: List[bytes],
) -> bytes:
    pdu = encode_tlv(
        0xA2,
        b"".join(
            [
                encode_integer(req_id),
                encode_integer(error_status),
                encode_integer(error_index),
                encode_sequence(varbinds),
            ]
        ),
    )
    return encode_sequence(
        [
            encode_integer(version),
            encode_octet_string(community),
            pdu,
        ]
    )


def handle_message(store: LabStore, data: bytes) -> Optional[bytes]:
    try:
        outer = BerReader(data)
        tag, body = outer.read_tlv()
        if tag != 0x30:
            return None
        msg = BerReader(body)
        t_ver, ver_raw = msg.read_tlv()
        if t_ver != 0x02:
            return None
        version = decode_integer(ver_raw)
        if version not in (0, 1):  # SNMPv1 / v2c
            return None
        t_com, community = msg.read_tlv()
        if t_com != 0x04 or community != COMMUNITY:
            return None
        pdu_tag, pdu_raw = msg.read_tlv()
        if pdu_tag not in (0xA0, 0xA3):  # GET, SET
            return None
        pdu = BerReader(pdu_raw)
        _, req_raw = pdu.read_tlv()
        req_id = decode_integer(req_raw)
        pdu.read_tlv()  # error-status
        pdu.read_tlv()  # error-index
        t_vbl, vbl_raw = pdu.read_tlv()
        if t_vbl != 0x30:
            return None
        vbl = BerReader(vbl_raw)
        out_binds: List[bytes] = []
        error_status = 0
        error_index = 0
        index = 0
        while vbl.remaining() > 0:
            index += 1
            oid, val_tag, val_raw = parse_varbind(vbl)
            if pdu_tag == 0xA3:  # SET
                if val_tag != 0x02:
                    error_status = 3
                    error_index = index
                    out_binds.append(encode_sequence([encode_oid(oid), encode_tlv(val_tag, val_raw)]))
                    break
                value = decode_integer(val_raw)
                if value < 0 or value > 0x7FFFFFFF or not store.set_integer(oid, value):
                    error_status = 2
                    error_index = index
                    out_binds.append(encode_sequence([encode_oid(oid), encode_tlv(val_tag, val_raw)]))
                    break
                encoded = store.get(oid)
                assert encoded is not None
                out_binds.append(encode_sequence([encode_oid(oid), encoded]))
            else:  # GET
                encoded = store.get(oid)
                if encoded is None:
                    if version == 1:
                        out_binds.append(encode_sequence([encode_oid(oid), encode_tlv(0x80, b"")]))
                    else:
                        error_status = 2
                        error_index = index
                        out_binds.append(encode_sequence([encode_oid(oid), encode_null()]))
                        break
                else:
                    out_binds.append(encode_sequence([encode_oid(oid), encoded]))
        return build_response(version, community, req_id, error_status, error_index, out_binds)
    except ValueError:
        return None


def serve(host: str, port: int) -> None:
    store = LabStore()
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((host, port))
    print(f"snmp lab agent listening udp://{host}:{port}", flush=True)
    while True:
        data, addr = sock.recvfrom(65535)
        resp = handle_message(store, data)
        if resp:
            sock.sendto(resp, addr)


def _exchange(host: str, port: int, msg: bytes) -> bytes:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(3)
    try:
        sock.sendto(msg, (host, port))
        data, _ = sock.recvfrom(65535)
        return data
    finally:
        sock.close()


def snmp_get(host: str, port: int, oid: Tuple[int, ...], version: int = 1) -> int:
    req_id = 1
    vb = encode_sequence([encode_oid(oid), encode_null()])
    pdu = encode_tlv(
        0xA0,
        b"".join(
            [
                encode_integer(req_id),
                encode_integer(0),
                encode_integer(0),
                encode_sequence([vb]),
            ]
        ),
    )
    msg = encode_sequence([encode_integer(version), encode_octet_string(COMMUNITY), pdu])
    data = _exchange(host, port, msg)
    outer = BerReader(data)
    _, body = outer.read_tlv()
    reader = BerReader(body)
    reader.read_tlv()
    reader.read_tlv()
    pdu_tag, pdu_raw = reader.read_tlv()
    if pdu_tag != 0xA2:
        raise RuntimeError(f"unexpected pdu tag {pdu_tag:#x}")
    pdu = BerReader(pdu_raw)
    pdu.read_tlv()
    _, err_raw = pdu.read_tlv()
    if decode_integer(err_raw) != 0:
        raise RuntimeError(f"snmp error-status {decode_integer(err_raw)}")
    pdu.read_tlv()
    _, vbl_raw = pdu.read_tlv()
    vbl = BerReader(vbl_raw)
    got_oid, val_tag, val_raw = parse_varbind(vbl)
    if got_oid != oid or val_tag != 0x02:
        raise RuntimeError(f"unexpected binding {got_oid} tag={val_tag:#x}")
    return decode_integer(val_raw)


def snmp_set(host: str, port: int, oid: Tuple[int, ...], value: int, version: int = 1) -> int:
    req_id = 2
    vb = encode_sequence([encode_oid(oid), encode_integer(value)])
    pdu = encode_tlv(
        0xA3,
        b"".join(
            [
                encode_integer(req_id),
                encode_integer(0),
                encode_integer(0),
                encode_sequence([vb]),
            ]
        ),
    )
    msg = encode_sequence([encode_integer(version), encode_octet_string(COMMUNITY), pdu])
    data = _exchange(host, port, msg)
    outer = BerReader(data)
    _, body = outer.read_tlv()
    reader = BerReader(body)
    reader.read_tlv()
    reader.read_tlv()
    pdu_tag, pdu_raw = reader.read_tlv()
    if pdu_tag != 0xA2:
        raise RuntimeError(f"unexpected pdu tag {pdu_tag:#x}")
    pdu = BerReader(pdu_raw)
    pdu.read_tlv()
    _, err_raw = pdu.read_tlv()
    err = decode_integer(err_raw)
    if err != 0:
        raise RuntimeError(f"snmp set error-status {err}")
    pdu.read_tlv()
    _, vbl_raw = pdu.read_tlv()
    vbl = BerReader(vbl_raw)
    got_oid, val_tag, val_raw = parse_varbind(vbl)
    if got_oid != oid or val_tag != 0x02:
        raise RuntimeError(f"unexpected set binding {got_oid}")
    return decode_integer(val_raw)


def self_test() -> None:
    store = LabStore()
    host, port = "127.0.0.1", 1161
    done = threading.Event()

    def run() -> None:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.bind((host, port))
        sock.settimeout(5)
        try:
            for _ in range(3):
                data, addr = sock.recvfrom(65535)
                resp = handle_message(store, data)
                assert resp is not None
                sock.sendto(resp, addr)
        finally:
            sock.close()
            done.set()

    t = threading.Thread(target=run, daemon=True)
    t.start()
    before = snmp_get(host, port, OID_LAB_GAUGE)
    assert before == 42, before
    snmp_set(host, port, OID_LAB_GAUGE, 0x1234)
    after = snmp_get(host, port, OID_LAB_GAUGE)
    assert after == 0x1234, after
    done.wait(timeout=2)
    print("snmp self-test ok")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=161)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    serve(args.host, args.port)


if __name__ == "__main__":
    main()
