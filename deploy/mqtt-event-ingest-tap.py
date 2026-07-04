#!/usr/bin/env python3
"""
MQTT subscriber → POST /api/v1/events/fire per message (HTTP event journal load test).

External tap: each broker message becomes one event in the journal (ClickHouse on prod).
Requires: pip install paho-mqtt requests
"""

from __future__ import annotations

import argparse
import queue
import sys
import threading
import time
from dataclasses import dataclass, field
from urllib.parse import quote

import requests

try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("Install dependency: pip install paho-mqtt", file=sys.stderr)
    raise SystemExit(1)

DEFAULT_EVENT = "messageReceived"
MESSAGE_RECEIVED_EVENT = {
    "name": DEFAULT_EVENT,
    "description": "MQTT payload received (load test tap)",
    "level": "INFO",
    "payloadSchema": {
        "name": "mqttPayload",
        "fields": [{"name": "raw", "type": "STRING"}],
    },
}


@dataclass
class Stats:
    received: int = 0
    enqueued: int = 0
    dropped: int = 0
    fired: int = 0
    failed: int = 0
    lock: threading.Lock = field(default_factory=threading.Lock, repr=False)

    def inc(self, name: str, n: int = 1) -> None:
        with self.lock:
            setattr(self, name, getattr(self, name) + n)


class ApiClient:
    def __init__(self, base_url: str, timeout: float):
        self.base = base_url.rstrip("/")
        self.timeout = timeout
        self.session = requests.Session()
        self.session.headers.update({"Accept": "application/json", "Content-Type": "application/json"})

    def login(self, username: str, password: str) -> None:
        r = self.session.post(
            f"{self.base}/api/v1/auth/login",
            json={"username": username, "password": password},
            timeout=self.timeout,
        )
        r.raise_for_status()
        self.session.headers["Authorization"] = f"Bearer {r.json()['token']}"

    def ensure_event(self, object_path: str, event_body: dict) -> None:
        r = self.session.put(
            f"{self.base}/api/v1/objects/by-path/events?path={quote(object_path, safe='')}",
            json=event_body,
            timeout=self.timeout,
        )
        if r.status_code >= 400:
            raise RuntimeError(f"ensure event: HTTP {r.status_code} {r.text[:200]}")

    def fire(self, object_path: str, event_name: str, raw: str, include_raw: bool) -> bool:
        body = None
        if include_raw:
            body = {"rows": [{"raw": raw[:128]}]}
        url = (
            f"{self.base}/api/v1/events/fire"
            f"?objectPath={quote(object_path, safe='')}"
            f"&eventName={quote(event_name, safe='')}"
        )
        r = self.session.post(url, json=body, timeout=self.timeout)
        return r.status_code < 400


def worker_loop(
    work: queue.Queue,
    stats: Stats,
    factory: callable,
    object_path: str,
    event_name: str,
    include_raw: bool,
    stop: threading.Event,
) -> None:
    client = factory()
    while not stop.is_set() or not work.empty():
        try:
            raw = work.get(timeout=0.05)
        except queue.Empty:
            continue
        try:
            if client.fire(object_path, event_name, raw, include_raw):
                stats.inc("fired")
            else:
                stats.inc("failed")
        except Exception:
            stats.inc("failed")
        finally:
            work.task_done()


def main() -> int:
    parser = argparse.ArgumentParser(description="MQTT → events/fire tap (1 message = 1 journal event)")
    parser.add_argument("--broker", default="tcp://127.0.0.1:1883")
    parser.add_argument("--topic", default="ispf/mqtt-device-01/temperature")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--object-path", default="root.platform.devices.mqtt-device-01")
    parser.add_argument("--event-name", default=DEFAULT_EVENT)
    parser.add_argument("--workers", type=int, default=48)
    parser.add_argument("--queue-size", type=int, default=50000)
    parser.add_argument("--duration-seconds", type=float, default=80.0)
    parser.add_argument("--warmup-seconds", type=float, default=0.0)
    parser.add_argument("--include-raw", action="store_true", help="Include MQTT payload in event rows")
    parser.add_argument("--skip-ensure-event", action="store_true")
    parser.add_argument("--client-id", default="ispf-mqtt-event-ingest-tap")
    parser.add_argument("--timeout", type=float, default=10.0)
    args = parser.parse_args()

    host_port = args.broker.replace("tcp://", "")
    host, port_s = host_port.rsplit(":", 1) if ":" in host_port else (host_port, "1883")
    port = int(port_s)

    admin = ApiClient(args.base_url, args.timeout)
    admin.login(args.username, args.password)
    if not args.skip_ensure_event and args.event_name == DEFAULT_EVENT:
        admin.ensure_event(args.object_path, MESSAGE_RECEIVED_EVENT)
        print(f"Ensured event {DEFAULT_EVENT} on {args.object_path}")

    work: queue.Queue[str] = queue.Queue(maxsize=max(args.queue_size, args.workers * 4))
    stats = Stats()
    stop = threading.Event()
    auth = (args.username, args.password)

    def factory() -> ApiClient:
        c = ApiClient(args.base_url, args.timeout)
        c.login(*auth)
        return c

    workers: list[threading.Thread] = []
    for _ in range(max(1, args.workers)):
        t = threading.Thread(
            target=worker_loop,
            args=(work, stats, factory, args.object_path, args.event_name, args.include_raw, stop),
            daemon=True,
        )
        t.start()
        workers.append(t)

    def on_connect(client, _userdata, _flags, rc):
        if rc != 0:
            print(f"MQTT connect failed: rc={rc}", file=sys.stderr)
            return
        client.subscribe(args.topic, qos=0)
        print(f"Subscribed: {args.topic}")

    def on_message(_client, _userdata, msg):
        stats.inc("received")
        raw = msg.payload.decode("utf-8", errors="replace")
        try:
            work.put_nowait(raw)
            stats.inc("enqueued")
        except queue.Full:
            stats.inc("dropped")

    mqtt_client = mqtt.Client(client_id=args.client_id, protocol=mqtt.MQTTv311)
    mqtt_client.on_connect = on_connect
    mqtt_client.on_message = on_message
    mqtt_client.connect(host, port, keepalive=30)
    mqtt_client.loop_start()

    if args.warmup_seconds > 0:
        print(f"Warmup {args.warmup_seconds:.0f}s (MQTT only, still firing events)...")
        time.sleep(args.warmup_seconds)

    print(
        f"Tap running {args.duration_seconds:.0f}s: {args.workers} workers, "
        f"queue={args.queue_size}, event={args.event_name}"
    )
    t0 = time.perf_counter()
    measure_start = time.perf_counter()
    last_print = measure_start

    while time.perf_counter() - measure_start < args.duration_seconds:
        time.sleep(1.0)
        now = time.perf_counter()
        if now - last_print >= 5.0:
            elapsed = now - t0
            with stats.lock:
                r, f, fail, drop, q = (
                    stats.received,
                    stats.fired,
                    stats.failed,
                    stats.dropped,
                    work.qsize(),
                )
            print(
                f"  {elapsed:5.0f}s: mqtt={r} fired={f} fail={fail} drop={drop} queue={q} "
                f"fire/s={f / max(elapsed, 0.001):.0f}",
                flush=True,
            )
            last_print = now

    stop.set()
    mqtt_client.loop_stop()
    mqtt_client.disconnect()

    for t in workers:
        t.join(timeout=2.0)

    elapsed = time.perf_counter() - t0
    with stats.lock:
        summary = {
            "received": stats.received,
            "enqueued": stats.enqueued,
            "fired": stats.fired,
            "failed": stats.failed,
            "dropped": stats.dropped,
            "queueRemaining": work.qsize(),
        }

    fire_rate = summary["fired"] / max(elapsed, 0.001)
    recv_rate = summary["received"] / max(elapsed, 0.001)
    print("")
    print(f"Duration: {elapsed:.1f}s")
    print(f"MQTT received: {summary['received']} ({recv_rate:.1f} msg/s)")
    print(f"Events fired (HTTP ok): {summary['fired']} ({fire_rate:.1f} events/s)")
    print(f"Failed: {summary['failed']}  Dropped (queue full): {summary['dropped']}")
    if summary["queueRemaining"]:
        print(f"Queue remaining: {summary['queueRemaining']}")
    return 0 if summary["fired"] > 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
