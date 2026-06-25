#!/usr/bin/env python3
"""
Sweep runtime-telemetry.coalesce-ms on prod and measure internal event throughput.

Requires SSH access to VPS for service restart between phases.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

DEFAULT_COALESCE_MS = [1000, 500, 250, 100, 50, 25, 10]
ENV_KEY = "ISPF_RUNTIME_TELEMETRY_COALESCE_MS"
ENV_FILE = "/opt/ispf/ispf-server.env"
LOAD_TEST = Path(__file__).with_name("events-internal-load-test.py")


def ssh(host: str, command: str) -> None:
    result = subprocess.run(
        ["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=30", host, command],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(f"SSH failed: {result.stderr or result.stdout}")


def set_coalesce_ms(host: str, coalesce_ms: int | None) -> None:
    """Set or remove coalesce env var and restart ispf-server."""
    if coalesce_ms is None:
        ssh(
            host,
            f"sed -i '/^{ENV_KEY}=/d' {ENV_FILE} && systemctl restart ispf-server",
        )
    else:
        ssh(
            host,
            f"grep -q '^{ENV_KEY}=' {ENV_FILE} && "
            f"sed -i 's/^{ENV_KEY}=.*/{ENV_KEY}={coalesce_ms}/' {ENV_FILE} || "
            f"echo '{ENV_KEY}={coalesce_ms}' >> {ENV_FILE}; "
            f"systemctl restart ispf-server",
        )


def wait_healthy(base_url: str, timeout_sec: float = 120.0) -> None:
    deadline = time.time() + timeout_sec
    url = base_url.rstrip("/") + "/api/v1/info"
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=10) as resp:
                if resp.status == 200:
                    return
        except Exception:
            pass
        time.sleep(3)
    raise TimeoutError(f"Server not healthy after {timeout_sec}s: {url}")


def run_load_test(
    base_url: str,
    username: str,
    password: str,
    phase_seconds: int,
    warmup_seconds: int,
    poll_ms: int,
) -> dict:
    cmd = [
        sys.executable,
        str(LOAD_TEST),
        "--base-url",
        base_url,
        "--username",
        username,
        "--password",
        password,
        "--phase-seconds",
        str(phase_seconds),
        "--warmup-seconds",
        str(warmup_seconds),
        "--poll-ms",
        str(poll_ms),
        "--condition-expr",
        "true",
        "--skip-monitor-setup",
    ]
    env = os.environ.copy()
    env["PYTHONUNBUFFERED"] = "1"
    result = subprocess.run(cmd, capture_output=True, text=True, env=env)
    print(result.stdout)
    if result.returncode != 0:
        print(result.stderr, file=sys.stderr)
        raise RuntimeError(f"Load test failed (exit {result.returncode})")
    # Parse last JSON report path from stdout
    report_path = None
    for line in result.stdout.splitlines():
        if line.startswith("Report:"):
            report_path = line.split(":", 1)[1].strip()
    if report_path and Path(report_path).exists():
        data = json.loads(Path(report_path).read_text(encoding="utf-8"))
        phases = data.get("phases") or []
        if phases:
            return phases[-1]
    raise RuntimeError("Could not parse load test report")


def main() -> int:
    parser = argparse.ArgumentParser(description="Coalesce-ms sweep internal load test")
    parser.add_argument("--base-url", default="https://ispf.iot-solutions.ru")
    parser.add_argument("--ssh-host", default="root@ispf.iot-solutions.ru")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--phase-seconds", type=int, default=45)
    parser.add_argument("--warmup-seconds", type=int, default=20)
    parser.add_argument("--poll-ms", type=int, default=1000)
    parser.add_argument(
        "--coalesce-ms",
        default=",".join(str(v) for v in DEFAULT_COALESCE_MS),
        help="Comma-separated coalesce values (high to low)",
    )
    parser.add_argument("--restore-default", type=int, default=1000)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    values = [int(x.strip()) for x in args.coalesce_ms.split(",") if x.strip()]
    results: list[dict] = []
    report_path = f"deploy/coalesce-sweep-report-{int(time.time())}.json"

    def save_report() -> None:
        with open(report_path, "w", encoding="utf-8") as fh:
            json.dump(
                {
                    "baseUrl": args.base_url,
                    "pollMs": args.poll_ms,
                    "phaseSeconds": args.phase_seconds,
                    "warmupSeconds": args.warmup_seconds,
                    "conditionExpr": "true",
                    "results": results,
                },
                fh,
                indent=2,
            )

    print(f"Coalesce sweep: {values}")
    print(f"Poll={args.poll_ms}ms, phase={args.phase_seconds}s, warmup={args.warmup_seconds}s")
    print(f"{'Coalesce ms':>12} {'Events/s':>10} {'Alert/s':>10} {'Journal d':>10}")
    print("-" * 48)

    try:
        for coalesce_ms in values:
            print(f"\n>>> Setting {ENV_KEY}={coalesce_ms} on {args.ssh_host} ...", flush=True)
            if not args.dry_run:
                set_coalesce_ms(args.ssh_host, coalesce_ms)
                print("    Waiting for server health...")
                wait_healthy(args.base_url)
                time.sleep(5)

            if args.dry_run:
                phase = {"eventsPerSecond": 0, "alertFiresPerSecond": 0, "eventsGenerated": 0}
            else:
                phase = run_load_test(
                    args.base_url,
                    args.username,
                    args.password,
                    args.phase_seconds,
                    args.warmup_seconds,
                    args.poll_ms,
                )

            row = {
                "coalesceMs": coalesce_ms,
                "pollMs": args.poll_ms,
                **phase,
            }
            results.append(row)
            print(
                f"{coalesce_ms:12d} {phase.get('eventsPerSecond', 0):10.1f} "
                f"{phase.get('alertFiresPerSecond', 0):10.1f} "
                f"{phase.get('eventsGenerated', 0):10d}",
                flush=True,
            )
            save_report()
    finally:
        print(f"\n>>> Restoring {ENV_KEY}={args.restore_default} ...")
        if not args.dry_run:
            set_coalesce_ms(args.ssh_host, args.restore_default)
            wait_healthy(args.base_url)

    print(f"\nReport: {report_path}")
    if results:
        best = max(results, key=lambda r: r.get("eventsPerSecond", 0))
        print(
            f"Peak: {best.get('eventsPerSecond')} events/s @ coalesce-ms={best.get('coalesceMs')}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
