#!/usr/bin/env python3
"""
Compare nginx LB throughput: 1 replica vs 3 replicas (BL-138).

Usage:
  python deploy/cluster-scale-load-test.py --base-url http://127.0.0.1:8088
  python deploy/cluster-scale-load-test.py --compose-file deploy/docker-compose.cluster.yml --scale-factor-floor 1.8
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path


def http_get(url: str, timeout: float = 30.0) -> tuple[int, float]:
    req = urllib.request.Request(url, headers={"Connection": "close"})
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            resp.read()
            return resp.status, time.perf_counter() - t0
    except urllib.error.HTTPError as exc:
        return exc.code, time.perf_counter() - t0


def benchmark(base_url: str, path: str, duration_sec: float, concurrency: int) -> dict:
    url = base_url.rstrip("/") + path
    deadline = time.perf_counter() + duration_sec
    latencies: list[float] = []
    ok = 0
    errors = 0

    def worker() -> None:
        nonlocal ok, errors
        while time.perf_counter() < deadline:
            code, elapsed = http_get(url)
            if code == 200:
                ok += 1
                latencies.append(elapsed)
            else:
                errors += 1

    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(worker) for _ in range(concurrency)]
        for future in as_completed(futures):
            future.result()

    latencies.sort()
    p99 = latencies[int(len(latencies) * 0.99) - 1] if latencies else 0.0
    elapsed = duration_sec
    rps = ok / elapsed if elapsed > 0 else 0.0
    return {
        "requests_ok": ok,
        "errors": errors,
        "rps": round(rps, 1),
        "p99_ms": round(p99 * 1000, 1),
    }


def compose_cmd(compose_file: Path, *args: str) -> list[str]:
    return ["docker", "compose", "-f", str(compose_file), *args]


def set_replica_count(compose_file: Path, count: int) -> None:
    services = ["ispf-server-1", "ispf-server-2", "ispf-server-3"]
    for idx, svc in enumerate(services, start=1):
        cid = subprocess.check_output(compose_cmd(compose_file, "ps", "-q", svc), text=True).strip()
        if not cid:
            continue
        if idx <= count:
            subprocess.run(["docker", "start", cid], check=False, capture_output=True)
        else:
            subprocess.run(["docker", "stop", cid], check=False, capture_output=True)
    time.sleep(10)


def wait_info(base_url: str, attempts: int = 30) -> None:
    for _ in range(attempts):
        code, _ = http_get(base_url + "/api/v1/info")
        if code == 200:
            return
        time.sleep(2)
    raise RuntimeError(f"API not ready at {base_url}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Cluster scale-out throughput gate (BL-138)")
    parser.add_argument("--base-url", default="http://127.0.0.1:8088")
    parser.add_argument(
        "--path",
        default="/api/v1/objects?parent=root.platform.devices&lite=true",
    )
    parser.add_argument("--duration", type=float, default=20.0)
    parser.add_argument("--concurrency", type=int, default=40)
    parser.add_argument("--scale-factor-floor", type=float, default=1.8)
    parser.add_argument("--compose-file", type=Path, default=Path("deploy/docker-compose.cluster.yml"))
    parser.add_argument("--skip-compose", action="store_true", help="Only benchmark current stack as-is")
    args = parser.parse_args()

    report: dict = {"base_url": args.base_url, "floor": args.scale_factor_floor}

    if not args.skip_compose:
        print("==> Scale down to 1 replica")
        set_replica_count(args.compose_file, 1)
    wait_info(args.base_url)
    print("==> Benchmark 1 replica")
    single = benchmark(args.base_url, args.path, args.duration, args.concurrency)
    report["single"] = single
    print(json.dumps(single, indent=2))

    if not args.skip_compose:
        print("==> Scale up to 3 replicas")
        set_replica_count(args.compose_file, 3)
    wait_info(args.base_url)
    print("==> Benchmark 3 replicas")
    triple = benchmark(args.base_url, args.path, args.duration, args.concurrency)
    report["triple"] = triple
    print(json.dumps(triple, indent=2))

    if single["rps"] <= 0:
        print("ERROR: single-replica throughput is zero", file=sys.stderr)
        return 1
    factor = triple["rps"] / single["rps"]
    report["scale_factor"] = round(factor, 2)
    print(f"==> Scale factor: {factor:.2f} (floor {args.scale_factor_floor})")

    out = Path("deploy/cluster-scale-report.json")
    out.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"==> Report: {out}")

    if factor < args.scale_factor_floor:
        print(
            f"ERROR: scale factor {factor:.2f} < floor {args.scale_factor_floor}",
            file=sys.stderr,
        )
        return 1
    print("==> Scale gate PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
