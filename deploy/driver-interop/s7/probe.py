#!/usr/bin/env python3
"""SoftPlc REST helper for BL-141 S7 lab fixture (lab only).

Compose peer is fbarresi/softplc (Snap7 ISO-on-TCP :102 + REST :8080).
Smoke seeds/verifies DB1 REAL via SoftPlc REST (same memory Snap7 serves).
A full pure-Python S7Comm client is out of stdlib scope; Java s7connector
matrix tests remain the ISO-on-TCP codec proof. Lab ≠ field Done.

Demodata (DATA_PATH=/demodata): float max/min at DB1 offsets 80 / 84.
Smoke uses offset 80 as DB:1:80:REAL (matches ISPF S7 point mapping).
"""
from __future__ import annotations

import argparse
import base64
import json
import struct
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Optional, Union
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

DEFAULT_API = "http://127.0.0.1:8080"
DEFAULT_DB = 1
DEFAULT_REAL_OFFSET = 80  # demodata float max slot
SEED_REAL = 42.5


def pack_real(value: float) -> bytes:
    """Siemens REAL = IEEE-754 float, big-endian."""
    return struct.pack(">f", float(value))


def unpack_real(data: bytes, offset: int = 0) -> float:
    return struct.unpack_from(">f", data, offset)[0]


def _decode_data_field(raw: Union[str, list, bytes, None], size: int) -> bytearray:
    if raw is None:
        return bytearray(size)
    if isinstance(raw, (bytes, bytearray)):
        out = bytearray(size)
        out[: min(size, len(raw))] = raw[:size]
        return out
    if isinstance(raw, str):
        decoded = base64.b64decode(raw)
        out = bytearray(size)
        out[: min(size, len(decoded))] = decoded[:size]
        return out
    if isinstance(raw, list):
        out = bytearray(size)
        for i, value in enumerate(raw[:size]):
            out[i] = int(value) & 0xFF
        return out
    raise TypeError(f"unsupported Data field type {type(raw)!r}")


def get_datablock(api_base: str, db_id: int = DEFAULT_DB, timeout: float = 5.0) -> bytearray:
    url = f"{api_base.rstrip('/')}/api/datablocks/{db_id}"
    with urlopen(url, timeout=timeout) as resp:
        payload = json.load(resp)
    size = int(payload.get("size") or payload.get("Size") or 0)
    data_field = payload.get("data", payload.get("Data"))
    if size <= 0 and isinstance(data_field, list):
        size = len(data_field)
    if size <= 0 and isinstance(data_field, str):
        size = len(base64.b64decode(data_field))
    if size <= 0:
        raise RuntimeError(f"datablock {db_id} has no size: {payload!r}")
    return _decode_data_field(data_field, size)


def put_datablock(api_base: str, db_id: int, data: bytes, timeout: float = 5.0) -> None:
    url = f"{api_base.rstrip('/')}/api/datablocks/{db_id}"
    # SoftPlc [FromBody]byte[] — ASP.NET Core JSON binds base64 string.
    body = json.dumps(base64.b64encode(bytes(data)).decode("ascii")).encode("utf-8")
    req = Request(url, data=body, method="PUT", headers={"Content-Type": "application/json"})
    with urlopen(req, timeout=timeout) as resp:
        resp.read()


def ensure_datablock(api_base: str, db_id: int = DEFAULT_DB, size: int = 256, timeout: float = 5.0) -> None:
    try:
        get_datablock(api_base, db_id, timeout=timeout)
        return
    except HTTPError as exc:
        if exc.code != 404:
            raise
    url = f"{api_base.rstrip('/')}/api/datablocks?id={db_id}&size={size}"
    req = Request(url, data=b"", method="POST")
    try:
        with urlopen(req, timeout=timeout) as resp:
            resp.read()
    except HTTPError as exc:
        # 409 = already exists (race with demodata)
        if exc.code not in (409, 400):
            raise


def read_real(
    api_base: str = DEFAULT_API,
    db_id: int = DEFAULT_DB,
    offset: int = DEFAULT_REAL_OFFSET,
    timeout: float = 5.0,
) -> float:
    data = get_datablock(api_base, db_id, timeout=timeout)
    if offset + 4 > len(data):
        raise RuntimeError(f"DB{db_id} too small for REAL at {offset} (size={len(data)})")
    return unpack_real(data, offset)


def write_real(
    api_base: str = DEFAULT_API,
    value: float = SEED_REAL,
    db_id: int = DEFAULT_DB,
    offset: int = DEFAULT_REAL_OFFSET,
    timeout: float = 5.0,
) -> None:
    ensure_datablock(api_base, db_id, timeout=timeout)
    data = get_datablock(api_base, db_id, timeout=timeout)
    if offset + 4 > len(data):
        raise RuntimeError(f"DB{db_id} too small for REAL at {offset} (size={len(data)})")
    data[offset : offset + 4] = pack_real(value)
    put_datablock(api_base, db_id, data, timeout=timeout)


def probe_api(api_base: str = DEFAULT_API, timeout: float = 5.0) -> None:
    """Readiness: list datablocks endpoint must respond."""
    url = f"{api_base.rstrip('/')}/api/datablocks"
    with urlopen(url, timeout=timeout) as resp:
        payload = json.load(resp)
    if not isinstance(payload, list):
        raise RuntimeError(f"unexpected /api/datablocks payload {payload!r}")


class _StubHandler(BaseHTTPRequestHandler):
    store: dict[int, bytearray] = {}

    def log_message(self, fmt: str, *args) -> None:  # noqa: A003
        return

    def _send(self, code: int, body: Optional[bytes] = None, content_type: str = "application/json") -> None:
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.end_headers()
        if body is not None:
            self.wfile.write(body)

    def do_GET(self) -> None:  # noqa: N802
        if self.path.rstrip("/") == "/api/datablocks":
            infos = [{"id": db_id, "size": len(data)} for db_id, data in self.store.items()]
            self._send(200, json.dumps(infos).encode("utf-8"))
            return
        if self.path.startswith("/api/datablocks/"):
            db_id = int(self.path.rsplit("/", 1)[-1])
            data = self.store.get(db_id)
            if data is None:
                self._send(404, b"{}")
                return
            payload = {
                "id": db_id,
                "size": len(data),
                "data": base64.b64encode(data).decode("ascii"),
            }
            self._send(200, json.dumps(payload).encode("utf-8"))
            return
        self._send(404, b"{}")

    def do_POST(self) -> None:  # noqa: N802
        if self.path.startswith("/api/datablocks"):
            # SoftPlc: POST api/datablocks?id=&size=
            from urllib.parse import parse_qs, urlparse

            query = parse_qs(urlparse(self.path).query)
            db_id = int(query.get("id", ["1"])[0])
            size = int(query.get("size", ["256"])[0])
            if db_id in self.store:
                self._send(409, b"{}")
                return
            self.store[db_id] = bytearray(size)
            self._send(200, b"")
            return
        self._send(404, b"{}")

    def do_PUT(self) -> None:  # noqa: N802
        if self.path.startswith("/api/datablocks/"):
            db_id = int(self.path.rsplit("/", 1)[-1])
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length)
            payload = json.loads(raw.decode("utf-8"))
            if isinstance(payload, str):
                data = base64.b64decode(payload)
            elif isinstance(payload, list):
                data = bytes(int(x) & 0xFF for x in payload)
            else:
                self._send(400, b"{}")
                return
            current = self.store.get(db_id)
            if current is None:
                self._send(404, b"{}")
                return
            size = len(current)
            current[:] = bytearray(size)
            current[: min(size, len(data))] = data[:size]
            self.store[db_id] = current
            self._send(200, b"")
            return
        self._send(404, b"{}")


def self_test() -> None:
    handler = _StubHandler
    handler.store = {1: bytearray(128)}
    # seed demodata-like float slot
    handler.store[1][DEFAULT_REAL_OFFSET : DEFAULT_REAL_OFFSET + 4] = pack_real(1.0)
    server = ThreadingHTTPServer(("127.0.0.1", 0), handler)
    host, port = server.server_address
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    api = f"http://{host}:{port}"
    try:
        probe_api(api)
        write_real(api, SEED_REAL)
        got = read_real(api)
        assert abs(got - SEED_REAL) < 1e-6, got
        write_real(api, 99.25)
        got = read_real(api)
        assert abs(got - 99.25) < 1e-6, got
    finally:
        server.shutdown()
    print("s7 softplc rest self-test ok", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api", default=DEFAULT_API)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--probe", action="store_true")
    parser.add_argument("--read", action="store_true")
    parser.add_argument("--write", type=float, default=None)
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    if args.probe:
        probe_api(args.api)
        print("s7 api ok", flush=True)
        return
    if args.write is not None:
        write_real(args.api, args.write)
        print(f"wrote REAL {args.write}", flush=True)
    if args.read or args.write is not None:
        value = read_real(args.api)
        print(f"read REAL {value}", flush=True)
        return
    parser.print_help()


if __name__ == "__main__":
    main()
