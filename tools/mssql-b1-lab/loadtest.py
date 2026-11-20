#!/usr/bin/env python3
"""B1 metadata load test (ADR-0078). Read / serial import / write / mixed.

Does not enable JDBC history/journal or cluster. Keep --import-workers=1 on Azure SQL Edge.
"""
from __future__ import annotations

import argparse
import json
import os
import random
import statistics
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_BUNDLE = SCRIPT_DIR / "mssql-b1-cert-bundle.json"

PATHS = [
    "root.platform.applications.mssql-b1-cert",
    "root.platform.devices.mssql-b1-cert-sensor-01",
    "root.platform.dashboards.mssql-b1-cert-overview",
    "root.platform.data-sources.mssql-b1-cert",
]
SENSOR = "root.platform.devices.mssql-b1-cert-sensor-01"
VAR_NAME = "loadValue"
VAR_SCHEMA = {
    "name": "loadValue",
    "fields": [{"name": "value", "type": "DOUBLE"}],
}


@dataclass
class Stats:
    name: str
    lat_ms: list[float] = field(default_factory=list)
    ok: int = 0
    fail: int = 0
    errors: list[str] = field(default_factory=list)
    lock: threading.Lock = field(default_factory=threading.Lock)

    def add(self, ms: float, success: bool, err: str | None = None) -> None:
        with self.lock:
            self.lat_ms.append(ms)
            if success:
                self.ok += 1
            else:
                self.fail += 1
                if err and len(self.errors) < 8:
                    self.errors.append(err)

    def report(self, elapsed_s: float) -> dict:
        xs = sorted(self.lat_ms)
        n = len(xs)
        total = self.ok + self.fail

        def pct(p: float) -> float:
            if not xs:
                return 0.0
            i = min(n - 1, max(0, int(round((p / 100.0) * (n - 1)))))
            return xs[i]

        out = {
            "name": self.name,
            "ok": self.ok,
            "fail": self.fail,
            "total": total,
            "rps": round(total / elapsed_s, 1) if elapsed_s > 0 else 0.0,
            "p50_ms": round(pct(50), 1),
            "p95_ms": round(pct(95), 1),
            "p99_ms": round(pct(99), 1),
            "max_ms": round(xs[-1], 1) if xs else 0.0,
            "mean_ms": round(statistics.fmean(xs), 1) if xs else 0.0,
        }
        if self.errors:
            out["sample_errors"] = self.errors
        return out


def http_json(
    method: str,
    url: str,
    token: str | None = None,
    body: bytes | None = None,
) -> tuple[int, bytes, float, str | None]:
    headers: dict[str, str] = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = resp.read()
            return resp.status, data, (time.perf_counter() - t0) * 1000, None
    except urllib.error.HTTPError as e:
        raw = e.read() if e.fp else b""
        return e.code, raw, (time.perf_counter() - t0) * 1000, f"HTTP {e.code}"
    except Exception as ex:  # noqa: BLE001
        return 0, b"", (time.perf_counter() - t0) * 1000, str(ex)


def login(base: str, user: str, password: str) -> str:
    code, data, _, err = http_json(
        "POST",
        f"{base}/api/v1/auth/login",
        body=json.dumps({"username": user, "password": password}).encode(),
    )
    if code != 200:
        raise SystemExit(f"login failed code={code} err={err} body={data[:200]!r}")
    return json.loads(data)["token"]


def health_ok(base: str) -> bool:
    code, data, _, _ = http_json("GET", f"{base}/actuator/health")
    if code != 200:
        return False
    try:
        return json.loads(data).get("status") == "UP"
    except Exception:
        return False


def ensure_bundle(base: str, token: str, bundle: Path) -> None:
    raw = bundle.read_bytes()
    code, data, ms, err = http_json(
        "POST",
        f"{base}/api/v1/platform/packages/import?packageId=mssql-b1-cert",
        token=token,
        body=raw,
    )
    print(f"seed_import code={code} ms={ms:.0f}", flush=True)
    if code != 200:
        raise SystemExit(f"seed import failed: {err} {data[:300]!r}")


def ensure_load_variable(base: str, token: str) -> None:
    code, _, _, _ = http_json(
        "GET",
        f"{base}/api/v1/objects/by-path/variables/detail?path={SENSOR}&name={VAR_NAME}",
        token=token,
    )
    if code == 200:
        print(f"variable {VAR_NAME} exists", flush=True)
        return
    body = json.dumps(
        {
            "name": VAR_NAME,
            "schema": VAR_SCHEMA,
            "readable": True,
            "writable": True,
            "initialValue": {"schema": VAR_SCHEMA, "rows": [{"value": 0.0}]},
            "historyEnabled": False,
        }
    ).encode()
    code, data, ms, err = http_json(
        "POST",
        f"{base}/api/v1/objects/by-path/variables?path={SENSOR}",
        token=token,
        body=body,
    )
    print(f"create_variable code={code} ms={ms:.0f}", flush=True)
    if code not in (200, 201):
        code2, _, _, _ = http_json(
            "GET",
            f"{base}/api/v1/objects/by-path/variables/detail?path={SENSOR}&name={VAR_NAME}",
            token=token,
        )
        if code2 != 200:
            raise SystemExit(f"create variable failed: {err} {data[:300]!r}")


def run_phase(
    name: str,
    duration_s: float,
    workers: int,
    work: Callable[[str, Stats], None],
    token_factory: Callable[[], str],
    base: str,
) -> dict:
    stats = Stats(name)
    stop_at = time.time() + duration_s

    def worker() -> None:
        token = token_factory()
        n = 0
        while time.time() < stop_at:
            n += 1
            if n % 200 == 0:
                token = token_factory()
            try:
                work(token, stats)
            except Exception as ex:  # noqa: BLE001
                stats.add(0.0, False, str(ex))

    t0 = time.perf_counter()
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futs = [pool.submit(worker) for _ in range(workers)]
        for fut in as_completed(futs):
            fut.result()
    elapsed = time.perf_counter() - t0
    report = stats.report(elapsed)
    report["elapsed_s"] = round(elapsed, 2)
    report["workers"] = workers
    report["health_after"] = health_ok(base)
    print(json.dumps(report, ensure_ascii=False), flush=True)
    return report


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default=os.environ.get("BASE", "http://127.0.0.1:18080"))
    ap.add_argument("--bundle", default=os.environ.get("BUNDLE", str(DEFAULT_BUNDLE)))
    ap.add_argument("--engine", default=os.environ.get("ENGINE", "mssql-b1"))
    ap.add_argument("--out", default=os.environ.get("OUT", str(SCRIPT_DIR / "loadtest-result.json")))
    ap.add_argument("--user", default=os.environ.get("ISPF_USER", "admin"))
    ap.add_argument("--password", default=os.environ.get("ISPF_PASSWORD", "admin"))
    ap.add_argument("--read-workers", type=int, default=20)
    ap.add_argument("--read-seconds", type=float, default=90)
    ap.add_argument("--import-workers", type=int, default=1)
    ap.add_argument("--import-seconds", type=float, default=45)
    ap.add_argument("--write-workers", type=int, default=12)
    ap.add_argument("--write-seconds", type=float, default=60)
    ap.add_argument("--mixed-workers", type=int, default=16)
    ap.add_argument("--mixed-seconds", type=float, default=90)
    args = ap.parse_args()

    base = args.base.rstrip("/")
    bundle = Path(args.bundle)
    if not health_ok(base):
        raise SystemExit("stand health not UP")
    if not bundle.is_file():
        raise SystemExit(f"bundle missing: {bundle}")

    token0 = login(base, args.user, args.password)
    ensure_bundle(base, token0, bundle)
    ensure_load_variable(base, token0)
    print(f"base={base} health=UP engine={args.engine}", flush=True)

    token_box = {"t": token0}
    token_lock = threading.Lock()

    def token_factory() -> str:
        with token_lock:
            token_box["t"] = login(base, args.user, args.password)
            return token_box["t"]

    # cheap factory: reuse until rotation in worker
    def token_reuse() -> str:
        with token_lock:
            return token_box["t"]

    bundle_bytes = bundle.read_bytes()

    def do_read_one(token: str, stats: Stats) -> None:
        p = PATHS[random.randrange(len(PATHS))]
        code, _, ms, err = http_json(
            "GET", f"{base}/api/v1/objects/by-path?path={p}", token=token
        )
        stats.add(ms, code == 200, err)

    def do_import(token: str, stats: Stats) -> None:
        code, _, ms, err = http_json(
            "POST",
            f"{base}/api/v1/platform/packages/import?packageId=mssql-b1-cert",
            token=token,
            body=bundle_bytes,
        )
        stats.add(ms, code == 200, err)

    def do_write(token: str, stats: Stats) -> None:
        payload = json.dumps(
            {"schema": VAR_SCHEMA, "rows": [{"value": time.time()}]}
        ).encode()
        code, _, ms, err = http_json(
            "PUT",
            f"{base}/api/v1/objects/by-path/variables?path={SENSOR}&name={VAR_NAME}",
            token=token,
            body=payload,
        )
        stats.add(ms, code == 200, err)

    def do_mixed(token: str, stats: Stats) -> None:
        if random.random() < 0.85:
            do_read_one(token, stats)
        else:
            do_write(token, stats)

    reports: list[dict] = []
    print("\n== phase read ==", flush=True)
    reports.append(
        run_phase("read", args.read_seconds, args.read_workers, do_read_one, token_reuse, base)
    )
    print("\n== phase import ==", flush=True)
    reports.append(
        run_phase(
            "import", args.import_seconds, args.import_workers, do_import, token_reuse, base
        )
    )
    print("\n== phase write ==", flush=True)
    reports.append(
        run_phase("write", args.write_seconds, args.write_workers, do_write, token_reuse, base)
    )
    print("\n== phase mixed ==", flush=True)
    reports.append(
        run_phase("mixed", args.mixed_seconds, args.mixed_workers, do_mixed, token_reuse, base)
    )

    tok = login(base, args.user, args.password)
    path_ok = True
    for p in PATHS:
        code, _, _, _ = http_json(
            "GET", f"{base}/api/v1/objects/by-path?path={p}", token=tok
        )
        if code != 200:
            path_ok = False
            print(f"FINAL_FAIL {p} code={code}", flush=True)

    summary = {
        "stand": base,
        "engine": args.engine,
        "health_final": health_ok(base),
        "paths_final_ok": path_ok,
        "phases": reports,
    }
    out = Path(args.out)
    out.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print("\n== summary ==", flush=True)
    print(json.dumps(summary, indent=2, ensure_ascii=False), flush=True)
    print(f"wrote {out}", flush=True)

    hard_fail = (
        any(r["fail"] > 0 for r in reports)
        or not path_ok
        or not summary["health_final"]
    )
    return 1 if hard_fail else 0


if __name__ == "__main__":
    raise SystemExit(main())
