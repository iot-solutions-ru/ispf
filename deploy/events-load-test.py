#!/usr/bin/env python3
"""Focused load test for ISPF events API: fire and list."""

from __future__ import annotations

import argparse
import json
import random
import statistics
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from typing import Callable
from urllib.parse import quote

import requests


EVENT1_PAYLOAD = {"rows": [{"int": 7, "string": "loadtest"}]}


@dataclass
class Sample:
    ok: bool
    ms: float
    op: str
    status: int = 0


@dataclass
class PhaseResult:
    concurrency: int
    duration_sec: float
    samples: list[Sample] = field(default_factory=list)

    def summary(self) -> dict:
        ok = [s for s in self.samples if s.ok]
        fail = [s for s in self.samples if not s.ok]
        lat = [s.ms for s in ok]
        elapsed = max(self.duration_sec, 0.001)
        by_op: dict[str, list[float]] = {}
        fail_by_op: dict[str, int] = {}
        for s in ok:
            by_op.setdefault(s.op, []).append(s.ms)
        for s in fail:
            fail_by_op[s.op] = fail_by_op.get(s.op, 0) + 1
        return {
            "concurrency": self.concurrency,
            "total": len(self.samples),
            "ok": len(ok),
            "fail": len(fail),
            "rps": round(len(ok) / elapsed, 1),
            "fail_rate_pct": round(100 * len(fail) / max(len(self.samples), 1), 2),
            "latency_ms": percentile(lat),
            "by_op": {op: percentile(vals) for op, vals in sorted(by_op.items())},
            "fail_by_op": dict(sorted(fail_by_op.items())),
        }


def percentile(values: list[float]) -> dict:
    if not values:
        return {"p50": 0, "p95": 0, "p99": 0, "avg": 0, "max": 0, "n": 0}
    values = sorted(values)
    n = len(values)

    def pct(p: float) -> float:
        idx = min(n - 1, max(0, int(round(p * (n - 1)))))
        return round(values[idx], 1)

    return {
        "p50": pct(0.50),
        "p95": pct(0.95),
        "p99": pct(0.99),
        "avg": round(statistics.mean(values), 1),
        "max": round(max(values), 1),
        "n": n,
    }


class Client:
    def __init__(self, base_url: str, host_header: str | None, timeout: float):
        self.base = base_url.rstrip("/")
        self.timeout = timeout
        self.session = requests.Session()
        self.session.headers.update({"Accept": "application/json"})
        if host_header:
            self.session.headers["Host"] = host_header
        self.token: str | None = None

    def login(self, username: str, password: str) -> None:
        r = self.session.post(
            f"{self.base}/api/v1/auth/login",
            json={"username": username, "password": password},
            timeout=self.timeout,
        )
        r.raise_for_status()
        self.token = r.json()["token"]
        self.session.headers["Authorization"] = f"Bearer {self.token}"

    def request(self, method: str, path: str, **kwargs) -> requests.Response:
        kwargs.setdefault("timeout", self.timeout)
        return self.session.request(method, f"{self.base}{path}", **kwargs)


def resolve_device_paths(client: Client, explicit: list[str]) -> tuple[list[str], str]:
    if explicit:
        return explicit, "thresholdExceeded"
    demo = "root.platform.devices.demo-sensor-01"
    lab_paths: list[str] = []
    try:
        items = client.request("GET", "/api/v1/objects?parent=root.platform.devices&lite=true").json()
        for item in items:
            path = item.get("path", "")
            if path.startswith("root.platform.devices.loadtest-dev-"):
                lab_paths.append(path)
    except Exception:
        pass
    if lab_paths:
        return lab_paths, "event1"
    return [demo], "thresholdExceeded"


def make_ops(device_paths: list[str], event_name: str) -> list[tuple[str, float, Callable[[Client], None]]]:
    def pick_device() -> str:
        return random.choice(device_paths)

    ops: list[tuple[str, float, Callable[[Client], None]]] = []
    ops.append((
        "fire_event",
        0.65,
        lambda c: c.request(
            "POST",
            f"/api/v1/events/fire?objectPath={quote(pick_device(), safe='')}&eventName={quote(event_name, safe='')}",
            json=EVENT1_PAYLOAD if event_name == "event1" else None,
        ).raise_for_status(),
    ))
    ops.append((
        "list_events_global",
        0.20,
        lambda c: c.request("GET", "/api/v1/events?limit=50").raise_for_status(),
    ))
    ops.append((
        "list_events_device",
        0.15,
        lambda c: c.request(
            "GET", f"/api/v1/events?objectPath={quote(pick_device(), safe='')}&limit=30"
        ).raise_for_status(),
    ))
    return ops


def run_phase(
    client_factory: Callable[[], Client],
    ops: list[tuple[str, float, Callable[[Client], None]]],
    concurrency: int,
    duration_sec: float,
) -> PhaseResult:
    names = [n for n, _, _ in ops]
    weights = [w for _, w, _ in ops]
    end = time.perf_counter() + duration_sec
    result = PhaseResult(concurrency=concurrency, duration_sec=duration_sec)
    lock_end = end
    sample_lock = threading.Lock()

    def worker() -> None:
        c = client_factory()
        local = random.Random()
        while time.perf_counter() < lock_end:
            op_name = local.choices(names, weights=weights, k=1)[0]
            fn = next(f for n, _, f in ops if n == op_name)
            t0 = time.perf_counter()
            try:
                fn(c)
                ms = (time.perf_counter() - t0) * 1000
                with sample_lock:
                    result.samples.append(Sample(True, ms, op_name))
            except Exception as exc:
                ms = (time.perf_counter() - t0) * 1000
                status = getattr(getattr(exc, "response", None), "status_code", 0) or 0
                with sample_lock:
                    result.samples.append(Sample(False, ms, op_name, status))

    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(worker) for _ in range(concurrency)]
        for f in as_completed(futures):
            f.result()
    result.duration_sec = time.perf_counter() - started
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description="ISPF events API load test")
    parser.add_argument("--base-url", default="https://ispf.iot-solutions.ru")
    parser.add_argument("--host-header", default="")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--event-name", default="", help="Event to fire (auto: event1 on loadtest devices, thresholdExceeded on demo-sensor)")
    parser.add_argument("--device-path", action="append", default=[], help="Repeatable device path override")
    parser.add_argument("--phase-seconds", type=int, default=30)
    parser.add_argument("--concurrency", default="10,20,40,60")
    parser.add_argument("--timeout", type=float, default=30.0)
    args = parser.parse_args()

    admin = Client(args.base_url, args.host_header or None, args.timeout)
    admin.login(args.username, args.password)
    device_paths, default_event = resolve_device_paths(admin, args.device_path)
    if not device_paths:
        print("No device paths available", file=sys.stderr)
        return 1

    event_name = args.event_name.strip() or default_event
    print(f"Devices: {len(device_paths)} (sample: {device_paths[:3]})")
    print(f"Event: {event_name}")

    ops = make_ops(device_paths, event_name)
    levels = [int(x.strip()) for x in args.concurrency.split(",") if x.strip()]

    def client_factory() -> Client:
        c = Client(args.base_url, args.host_header or None, args.timeout)
        c.token = admin.token
        c.session.headers["Authorization"] = f"Bearer {admin.token}"
        return c

    phase_results: list[dict] = []
    print(f"\nRunning events load phases: {levels} x {args.phase_seconds}s each...")
    for conc in levels:
        print(f"\n--- concurrency={conc} ---")
        phase = run_phase(client_factory, ops, conc, args.phase_seconds)
        summary = phase.summary()
        phase_results.append(summary)
        lat = summary["latency_ms"]
        print(
            f"  rps={summary['rps']} ok={summary['ok']} fail={summary['fail']} "
            f"fail%={summary['fail_rate_pct']} p50={lat['p50']} p95={lat['p95']} p99={lat['p99']}"
        )
        for op, op_lat in summary.get("by_op", {}).items():
            print(f"    {op}: p50={op_lat['p50']} p95={op_lat['p95']} p99={op_lat['p99']} n={op_lat['n']}")
        if summary.get("fail_by_op"):
            print(f"  fail_by_op={summary['fail_by_op']}")
        time.sleep(2)

    report_path = f"deploy/events-load-test-report-{int(time.time())}.json"
    with open(report_path, "w", encoding="utf-8") as fh:
        json.dump({"phases": phase_results, "devices": device_paths, "eventName": event_name}, fh, indent=2)
    print(f"\nReport: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
