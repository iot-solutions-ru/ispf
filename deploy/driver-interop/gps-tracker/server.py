#!/usr/bin/env python3
"""GPS tracker lab fixture for OT Trust BL-141 (lab only).

Mirrors the Java gps-tracker driver listen path: accept TCP clients and keep the
last non-blank UTF-8 line (NMEA-style). Adds a tiny HTTP status API so smoke can
verify the feed without embedding the JVM.

Honesty: this is a lab stand-in for the driver accept/read-line path — not a real
GPS device, GNSS modem, or field tracker. Java GpsTrackerDeviceDriverTest remains
the accept-loop codec proof. Lab ≠ field Done.

Default NMEA seed (self-test): classic GGA sentence from the driver unit test.
"""
from __future__ import annotations

import argparse
import json
import socket
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Optional

DEFAULT_FEED_PORT = 5005
DEFAULT_API_PORT = 5006
SEED_NMEA = "$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47"


class GpsFeedState:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.last_line = ""
        self.clients = 0
        self.lines_total = 0

    def on_connect(self) -> None:
        with self.lock:
            self.clients += 1

    def on_disconnect(self) -> None:
        with self.lock:
            self.clients = max(0, self.clients - 1)

    def on_line(self, line: str) -> None:
        text = line.strip()
        if not text:
            return
        with self.lock:
            self.last_line = text
            self.lines_total += 1

    def snapshot(self) -> dict:
        with self.lock:
            return {
                "ok": True,
                "lastLine": self.last_line,
                "connectedClients": self.clients,
                "linesTotal": self.lines_total,
            }


def handle_client(conn: socket.socket, state: GpsFeedState) -> None:
    state.on_connect()
    try:
        with conn:
            conn.settimeout(60)
            buf = b""
            while True:
                try:
                    chunk = conn.recv(4096)
                except TimeoutError:
                    continue
                if not chunk:
                    break
                buf += chunk
                while b"\n" in buf:
                    raw, buf = buf.split(b"\n", 1)
                    line = raw.decode("utf-8", errors="replace").rstrip("\r")
                    state.on_line(line)
    except OSError:
        pass
    finally:
        state.on_disconnect()


def serve_feed(host: str, port: int, state: GpsFeedState, stop: threading.Event) -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind((host, port))
        sock.listen(32)
        sock.settimeout(0.5)
        print(f"ispf-gps-tracker-fixture listening on tcp://{host}:{port} (NMEA lines)", flush=True)
        while not stop.is_set():
            try:
                conn, _ = sock.accept()
            except TimeoutError:
                continue
            except OSError:
                break
            threading.Thread(target=handle_client, args=(conn, state), daemon=True).start()


def make_api_handler(state: GpsFeedState):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, fmt: str, *args) -> None:  # noqa: A003
            return

        def do_GET(self) -> None:  # noqa: N802
            path = self.path.split("?", 1)[0].rstrip("/") or "/"
            if path in ("/health", "/"):
                body = b'{"ok":true}'
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                return
            if path == "/last":
                payload = json.dumps(state.snapshot()).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
                return
            self.send_response(404)
            self.end_headers()

    return Handler


def serve_api(host: str, port: int, state: GpsFeedState, stop: threading.Event) -> ThreadingHTTPServer:
    handler = make_api_handler(state)
    server = ThreadingHTTPServer((host, port), handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    print(f"ispf-gps-tracker-fixture api on http://{host}:{port}/last", flush=True)

    def _watch() -> None:
        while not stop.is_set():
            time.sleep(0.2)
        server.shutdown()

    threading.Thread(target=_watch, daemon=True).start()
    return server


def feed_nmea(host: str, port: int, line: str = SEED_NMEA, timeout: float = 5.0) -> None:
    """Device-side helper: connect and send one NMEA line (CRLF)."""
    payload = (line.rstrip("\r\n") + "\r\n").encode("utf-8")
    with socket.create_connection((host, port), timeout=timeout) as sock:
        sock.sendall(payload)
        try:
            sock.shutdown(socket.SHUT_WR)
        except OSError:
            pass


def probe_api(api_base: str, timeout: float = 2.0) -> dict:
    import urllib.request

    url = api_base.rstrip("/") + "/last"
    with urllib.request.urlopen(url, timeout=timeout) as resp:
        return json.load(resp)


def wait_for_api(api_base: str, timeout: float = 30.0) -> None:
    deadline = time.time() + timeout
    last_err: Optional[BaseException] = None
    while time.time() < deadline:
        try:
            probe_api(api_base, timeout=2.0)
            return
        except BaseException as exc:  # noqa: BLE001 — wait loop
            last_err = exc
            time.sleep(0.5)
    raise RuntimeError(f"gps api not ready: {last_err!r}")


def self_test() -> None:
    state = GpsFeedState()
    stop = threading.Event()
    feed_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    feed_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    feed_sock.bind(("127.0.0.1", 0))
    feed_host, feed_port = feed_sock.getsockname()
    feed_sock.close()

    api_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    api_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    api_sock.bind(("127.0.0.1", 0))
    api_host, api_port = api_sock.getsockname()
    api_sock.close()

    threading.Thread(
        target=serve_feed, args=(feed_host, feed_port, state, stop), daemon=True
    ).start()
    serve_api(api_host, api_port, state, stop)
    api = f"http://{api_host}:{api_port}"
    # wait for feed port
    deadline = time.time() + 5
    while time.time() < deadline:
        try:
            with socket.create_connection((feed_host, feed_port), timeout=0.5):
                break
        except OSError:
            time.sleep(0.05)
    wait_for_api(api, timeout=5.0)

    feed_nmea(feed_host, feed_port, SEED_NMEA)
    deadline = time.time() + 5
    snap = {}
    while time.time() < deadline:
        snap = probe_api(api)
        if snap.get("lastLine") == SEED_NMEA:
            break
        time.sleep(0.05)
    assert snap.get("lastLine") == SEED_NMEA, snap
    assert int(snap.get("linesTotal", 0)) >= 1, snap

    # second sentence overwrites last line
    other = "$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"
    feed_nmea(feed_host, feed_port, other)
    deadline = time.time() + 5
    while time.time() < deadline:
        snap = probe_api(api)
        if snap.get("lastLine") == other:
            break
        time.sleep(0.05)
    assert snap.get("lastLine") == other, snap
    stop.set()
    print("gps-tracker fixture self-test ok", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=DEFAULT_FEED_PORT, help="NMEA TCP listen port")
    parser.add_argument("--api-port", type=int, default=DEFAULT_API_PORT, help="HTTP status port")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--feed", metavar="HOST", help="device mode: send SEED_NMEA to HOST:port")
    parser.add_argument("--line", default=SEED_NMEA, help="NMEA line for --feed")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    if args.feed:
        feed_nmea(args.feed, args.port, args.line)
        print(f"fed NMEA to {args.feed}:{args.port}", flush=True)
        return
    state = GpsFeedState()
    stop = threading.Event()
    serve_api(args.host, args.api_port, state, stop)
    try:
        serve_feed(args.host, args.port, state, stop)
    except KeyboardInterrupt:
        stop.set()


if __name__ == "__main__":
    main()
