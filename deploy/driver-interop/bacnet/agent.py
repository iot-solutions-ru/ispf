#!/usr/bin/env python3
"""Minimal BACnet/IP UDP lab agent for OT Trust BL-141 (lab only).

Wire subset aligned with packages/ispf-driver-bacnet BacnetLoopbackServer:
  Who-Is → I-Am (device instance 1001)
  ReadProperty / WriteProperty on analog-value:1 present-value (REAL)

Stdlib only — not a production BACnet stack.
"""
from __future__ import annotations

import argparse
import struct
import socket
import threading
from typing import Optional, Tuple

BVLC_TYPE = 0x81
BVLC_ORIGINAL_UNICAST = 0x0A
NPDU_VERSION = 0x01
NPDU_EXPECTING_REPLY = 0x04

PDU_CONFIRMED = 0x00
PDU_UNCONFIRMED = 0x10
PDU_SIMPLE_ACK = 0x20
PDU_COMPLEX_ACK = 0x30
MAX_APDU_1476 = 0x05

SERVICE_I_AM = 0
SERVICE_WHO_IS = 8
SERVICE_READ_PROPERTY = 12
SERVICE_WRITE_PROPERTY = 15

OBJECT_ANALOG_VALUE = 2
OBJECT_DEVICE = 8
PROP_PRESENT_VALUE = 85
PROP_UNITS = 117

DEVICE_ID = 1001
AV_INSTANCE = 1
DEFAULT_VALUE = 18.5


def encode_object_id(object_type: int, instance: int) -> int:
    return ((object_type & 0x3FF) << 22) | (instance & 0x3FFFFF)


def decode_object_id(encoded: int) -> Tuple[int, int]:
    return (encoded >> 22) & 0x3FF, encoded & 0x3FFFFF


def wrap_npdu(apdu: bytes, expecting_reply: bool) -> bytes:
    length = 6 + len(apdu)
    return bytes(
        [
            BVLC_TYPE,
            BVLC_ORIGINAL_UNICAST,
            (length >> 8) & 0xFF,
            length & 0xFF,
            NPDU_VERSION,
            NPDU_EXPECTING_REPLY if expecting_reply else 0,
        ]
    ) + apdu


def write_u32(value: int) -> bytes:
    return struct.pack(">I", value & 0xFFFFFFFF)


def unsigned_bytes(value: int) -> bytes:
    if value < 0:
        raise ValueError("unsigned must be non-negative")
    if value <= 0xFF:
        return bytes([value])
    if value <= 0xFFFF:
        return bytes([(value >> 8) & 0xFF, value & 0xFF])
    if value <= 0xFFFFFF:
        return bytes([(value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF])
    return write_u32(value)


def context_unsigned(tag: int, value: int) -> bytes:
    enc = unsigned_bytes(value)
    return bytes([(tag << 4) | 0x08 | len(enc)]) + enc


def encode_i_am(device_id: int) -> bytes:
    apdu = bytearray([PDU_UNCONFIRMED, SERVICE_I_AM])
    apdu.append(0xC4)
    apdu.extend(write_u32(encode_object_id(OBJECT_DEVICE, device_id)))
    for tag, value in ((2, 1476), (9, 3), (2, 999)):
        enc = unsigned_bytes(value)
        apdu.append((tag << 4) | len(enc))
        apdu.extend(enc)
    return wrap_npdu(bytes(apdu), False)


def encode_read_property(invoke_id: int, object_type: int, instance: int, prop: int) -> bytes:
    apdu = bytearray([PDU_CONFIRMED, MAX_APDU_1476, invoke_id & 0xFF, SERVICE_READ_PROPERTY])
    apdu.append(0x0C)
    apdu.extend(write_u32(encode_object_id(object_type, instance)))
    apdu.extend(context_unsigned(1, prop))
    return wrap_npdu(bytes(apdu), True)


def encode_write_property(
    invoke_id: int, object_type: int, instance: int, prop: int, real_value: float
) -> bytes:
    apdu = bytearray([PDU_CONFIRMED, MAX_APDU_1476, invoke_id & 0xFF, SERVICE_WRITE_PROPERTY])
    apdu.append(0x0C)
    apdu.extend(write_u32(encode_object_id(object_type, instance)))
    apdu.extend(context_unsigned(1, prop))
    apdu.append(0x3E)
    apdu.append(0x44)
    apdu.extend(struct.pack(">f", float(real_value)))
    apdu.append(0x3F)
    return wrap_npdu(bytes(apdu), True)


def encode_read_ack(
    invoke_id: int, object_type: int, instance: int, prop: int, real_value: float
) -> bytes:
    apdu = bytearray([PDU_COMPLEX_ACK, invoke_id & 0xFF, SERVICE_READ_PROPERTY])
    apdu.append(0x0C)
    apdu.extend(write_u32(encode_object_id(object_type, instance)))
    apdu.extend(context_unsigned(1, prop))
    apdu.append(0x3E)
    if prop == PROP_PRESENT_VALUE:
        apdu.append(0x44)
        apdu.extend(struct.pack(">f", float(real_value)))
    else:
        enc = unsigned_bytes(int(real_value))
        apdu.append(0x20 | len(enc))
        apdu.extend(enc)
    apdu.append(0x3F)
    return wrap_npdu(bytes(apdu), False)


def encode_simple_ack(invoke_id: int, service: int) -> bytes:
    return wrap_npdu(bytes([PDU_SIMPLE_ACK, invoke_id & 0xFF, service & 0xFF]), False)


class Cursor:
    def __init__(self, data: bytes, offset: int = 0, end: Optional[int] = None) -> None:
        self.data = data
        self.i = offset
        self.end = len(data) if end is None else end

    def remaining(self) -> int:
        return self.end - self.i

    def read_byte(self) -> int:
        if self.i >= self.end:
            raise ValueError("truncated")
        b = self.data[self.i]
        self.i += 1
        return b

    def read_int(self) -> int:
        if self.remaining() < 4:
            raise ValueError("truncated int")
        value = struct.unpack(">I", self.data[self.i : self.i + 4])[0]
        self.i += 4
        return value

    def read_unsigned(self, length: int) -> int:
        if length < 1 or length > 4 or self.remaining() < length:
            raise ValueError("bad unsigned")
        value = 0
        for _ in range(length):
            value = (value << 8) | self.read_byte()
        return value


def parse_bvlc_apdu(packet: bytes) -> bytes:
    if len(packet) < 6:
        raise ValueError("too short")
    if packet[0] != BVLC_TYPE or packet[1] != BVLC_ORIGINAL_UNICAST:
        raise ValueError("unsupported BVLC")
    declared = (packet[2] << 8) | packet[3]
    if declared > len(packet):
        raise ValueError("incomplete")
    if packet[4] != NPDU_VERSION:
        raise ValueError("bad NPDU version")
    return packet[6:declared]


class LabStore:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.av1 = float(DEFAULT_VALUE)
        self.units = 95  # NO_UNITS

    def get_av1(self) -> float:
        with self.lock:
            return self.av1

    def set_av1(self, value: float) -> float:
        with self.lock:
            self.av1 = float(value)
            return self.av1


def _read_context_unsigned(cur: Cursor, expected_tag: int) -> int:
    tag = cur.read_byte()
    tag_num = (tag >> 4) & 0x0F
    length = tag & 0x07
    context = (tag & 0x08) != 0
    if tag_num != expected_tag or not context:
        raise ValueError("bad context tag")
    if length == 5:
        length = cur.read_byte()
    return cur.read_unsigned(length)


def handle_apdu(store: LabStore, apdu: bytes) -> Optional[bytes]:
    if not apdu:
        return None
    pdu_type = apdu[0] & 0xF0
    if pdu_type == PDU_UNCONFIRMED:
        if len(apdu) >= 2 and apdu[1] == SERVICE_WHO_IS:
            return encode_i_am(DEVICE_ID)
        return None
    if pdu_type != PDU_CONFIRMED or len(apdu) < 4:
        return None
    invoke_id = apdu[2]
    service = apdu[3]
    cur = Cursor(apdu, 4)
    if cur.read_byte() != 0x0C:
        return None
    obj_type, instance = decode_object_id(cur.read_int())
    prop = _read_context_unsigned(cur, 1)
    if service == SERVICE_READ_PROPERTY:
        if obj_type == OBJECT_ANALOG_VALUE and instance == AV_INSTANCE:
            if prop == PROP_PRESENT_VALUE:
                return encode_read_ack(invoke_id, obj_type, instance, prop, store.get_av1())
            if prop == PROP_UNITS:
                return encode_read_ack(invoke_id, obj_type, instance, prop, float(store.units))
        return None
    if service == SERVICE_WRITE_PROPERTY:
        if cur.read_byte() != 0x3E:
            return None
        app = cur.read_byte()
        if ((app >> 4) & 0x0F) != 4 or (app & 0x07) != 4:
            return None
        bits = cur.read_int()
        if cur.read_byte() != 0x3F:
            return None
        value = struct.unpack(">f", struct.pack(">I", bits))[0]
        if (
            obj_type == OBJECT_ANALOG_VALUE
            and instance == AV_INSTANCE
            and prop == PROP_PRESENT_VALUE
        ):
            store.set_av1(value)
            return encode_simple_ack(invoke_id, SERVICE_WRITE_PROPERTY)
        return None
    return None


def handle_message(store: LabStore, data: bytes) -> Optional[bytes]:
    try:
        return handle_apdu(store, parse_bvlc_apdu(data))
    except ValueError:
        return None


def serve(host: str, port: int) -> None:
    store = LabStore()
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((host, port))
    print(f"bacnet lab agent listening udp://{host}:{port} device={DEVICE_ID}", flush=True)
    while True:
        data, addr = sock.recvfrom(2048)
        resp = handle_message(store, data)
        if resp:
            sock.sendto(resp, addr)


def _exchange(host: str, port: int, msg: bytes, timeout: float = 3.0) -> bytes:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(timeout)
    try:
        sock.sendto(msg, (host, port))
        data, _ = sock.recvfrom(2048)
        return data
    finally:
        sock.close()


def bacnet_read(host: str, port: int, invoke_id: int = 1) -> float:
    msg = encode_read_property(invoke_id, OBJECT_ANALOG_VALUE, AV_INSTANCE, PROP_PRESENT_VALUE)
    data = _exchange(host, port, msg)
    apdu = parse_bvlc_apdu(data)
    if (apdu[0] & 0xF0) != PDU_COMPLEX_ACK or apdu[2] != SERVICE_READ_PROPERTY:
        raise RuntimeError(f"unexpected read ack {apdu[:8]!r}")
    cur = Cursor(apdu, 3)
    if cur.read_byte() != 0x0C:
        raise RuntimeError("bad object tag")
    cur.read_int()
    _read_context_unsigned(cur, 1)
    if cur.read_byte() != 0x3E:
        raise RuntimeError("missing opening")
    app = cur.read_byte()
    if ((app >> 4) & 0x0F) != 4:
        raise RuntimeError("expected REAL")
    bits = cur.read_int()
    return struct.unpack(">f", struct.pack(">I", bits))[0]


def bacnet_write(host: str, port: int, value: float, invoke_id: int = 2) -> None:
    msg = encode_write_property(
        invoke_id, OBJECT_ANALOG_VALUE, AV_INSTANCE, PROP_PRESENT_VALUE, value
    )
    data = _exchange(host, port, msg)
    apdu = parse_bvlc_apdu(data)
    if (apdu[0] & 0xF0) != PDU_SIMPLE_ACK or apdu[2] != SERVICE_WRITE_PROPERTY:
        raise RuntimeError(f"unexpected write ack {apdu!r}")


def self_test() -> None:
    store = LabStore()
    host, port = "127.0.0.1", 47809
    done = threading.Event()

    def run() -> None:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.bind((host, port))
        sock.settimeout(5)
        try:
            for _ in range(3):
                data, addr = sock.recvfrom(2048)
                resp = handle_message(store, data)
                assert resp is not None
                sock.sendto(resp, addr)
        finally:
            sock.close()
            done.set()

    threading.Thread(target=run, daemon=True).start()
    before = bacnet_read(host, port)
    assert abs(before - DEFAULT_VALUE) < 1e-3, before
    bacnet_write(host, port, 27.25)
    after = bacnet_read(host, port, invoke_id=3)
    assert abs(after - 27.25) < 1e-3, after
    done.wait(timeout=2)
    print("bacnet self-test ok")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=47808)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    serve(args.host, args.port)


if __name__ == "__main__":
    main()
