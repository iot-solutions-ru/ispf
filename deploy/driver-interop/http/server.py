#!/usr/bin/env python3
"""Minimal writable HTTP JSON fixture for OT Trust BL-141 (lab only).

Endpoints:
  GET  /health          -> {"ok": true}
  GET  /points/gauge    -> {"value": <int>}
  PUT  /points/gauge    -> body {"value": <int>}; updates store; echoes value
  POST /points/gauge    -> same as PUT

Stdlib only. Lab use — not a production API.
"""
from __future__ import annotations

import argparse
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Dict
from urllib import error, request


class Store:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.gauge = 7

    def get(self) -> int:
        with self.lock:
            return self.gauge

    def set(self, value: int) -> int:
        with self.lock:
            self.gauge = value
            return self.gauge


def make_handler(store: Store):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, fmt: str, *args: Any) -> None:  # noqa: A003
            print(f"http-lab: {self.address_string()} {fmt % args}", flush=True)

        def _send_json(self, code: int, payload: Dict[str, Any]) -> None:
            body = json.dumps(payload).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self) -> None:  # noqa: N802
            if self.path.rstrip("/") == "/health":
                self._send_json(200, {"ok": True})
                return
            if self.path.rstrip("/") == "/points/gauge":
                self._send_json(200, {"value": store.get()})
                return
            self._send_json(404, {"error": "not found"})

        def do_PUT(self) -> None:  # noqa: N802
            self._write_gauge()

        def do_POST(self) -> None:  # noqa: N802
            self._write_gauge()

        def _write_gauge(self) -> None:
            if self.path.rstrip("/") != "/points/gauge":
                self._send_json(404, {"error": "not found"})
                return
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length) if length > 0 else b"{}"
            try:
                payload = json.loads(raw.decode("utf-8") or "{}")
                value = int(payload["value"])
            except (ValueError, KeyError, json.JSONDecodeError, TypeError):
                self._send_json(400, {"error": "expected JSON {\"value\": <int>}"})
                return
            self._send_json(200, {"value": store.set(value)})

    return Handler


def serve(host: str, port: int) -> None:
    store = Store()
    httpd = ThreadingHTTPServer((host, port), make_handler(store))
    print(f"http lab fixture listening http://{host}:{port}", flush=True)
    httpd.serve_forever()


def self_test() -> None:
    store = Store()
    httpd = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(store))
    port = httpd.server_address[1]
    t = threading.Thread(target=httpd.serve_forever, daemon=True)
    t.start()
    try:
        with request.urlopen(f"http://127.0.0.1:{port}/health", timeout=3) as resp:
            assert json.load(resp)["ok"] is True
        with request.urlopen(f"http://127.0.0.1:{port}/points/gauge", timeout=3) as resp:
            assert json.load(resp)["value"] == 7
        req = request.Request(
            f"http://127.0.0.1:{port}/points/gauge",
            data=json.dumps({"value": 99}).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="PUT",
        )
        with request.urlopen(req, timeout=3) as resp:
            assert json.load(resp)["value"] == 99
        with request.urlopen(f"http://127.0.0.1:{port}/points/gauge", timeout=3) as resp:
            assert json.load(resp)["value"] == 99
    except error.URLError as exc:
        raise AssertionError(exc) from exc
    finally:
        httpd.shutdown()
    print("http self-test ok")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8089)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    serve(args.host, args.port)


if __name__ == "__main__":
    main()
