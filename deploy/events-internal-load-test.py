#!/usr/bin/env python3
"""
Server-side event generation load test on VDS.

Pipeline: virtual driver poll → intValue update → alert rule → EventService.fire (internal).
Observed via GET /api/v1/platform/metrics (eventHistoryRecords delta).
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import sys
import time
from pathlib import Path
from urllib.parse import quote

import requests

from loadtest_cleanup_lib import cleanup_for_internal_poll_test, delete_loadtest_alert_rules, format_cleanup_stats

PREFIX = "loadtest"
SETUP_SCRIPT = Path(__file__).with_name("setup-platform-metrics-monitor.py")


def load_setup_module():
    spec = importlib.util.spec_from_file_location("setup_platform_metrics", SETUP_SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Cannot load {SETUP_SCRIPT}")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


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


def list_loadtest_devices(client: Client) -> list[str]:
    paths: list[str] = []
    items = client.request("GET", "/api/v1/objects?parent=root.platform.devices&lite=true").json()
    for item in items:
        path = item.get("path", "")
        if path.startswith("root.platform.devices.loadtest-dev-"):
            paths.append(path)
    return sorted(paths)


def automation_metrics(client: Client) -> dict:
    r = client.request("GET", "/api/v1/platform/metrics")
    r.raise_for_status()
    for section in r.json().get("sections", []):
        if section.get("id") == "automation":
            return section.get("values") or {}
    return {}


def event_history_count(client: Client) -> int:
    return int(automation_metrics(client).get("eventHistoryRecords") or 0)


def alert_fires_count(client: Client) -> int:
    return int(automation_metrics(client).get("alertFiresTotal") or 0)


def ensure_alert_rules(client: Client, device_paths: list[str], condition_expr: str) -> int:
    existing_items = client.request("GET", "/api/v1/alert-rules").json()
    if not isinstance(existing_items, list):
        existing_items = []
    by_name = {item.get("name", ""): item.get("id", "") for item in existing_items}
    updated = 0
    for i, device in enumerate(device_paths, start=1):
        name = f"loadtest internal alert {i:04d}"
        body = {
            "objectPath": device,
            "watchVariable": "sineWave",
            "conditionExpr": condition_expr,
            "eventName": "event1",
            "payloadVariable": "sineWave",
            "enabled": True,
            "edgeTrigger": False,
        }
        if name in by_name and by_name[name]:
            for attempt in range(3):
                r = client.request(
                    "PUT",
                    f"/api/v1/alert-rules/by-path?path={quote(by_name[name], safe='')}",
                    json=body,
                )
                if r.status_code in (200, 201, 409):
                    break
                if r.status_code in (403, 502, 503) and attempt < 2:
                    time.sleep(1.0)
                    continue
                break
        else:
            r = client.request("POST", "/api/v1/alert-rules", json={"name": name, **body})
        if r.status_code in (200, 201, 409):
            updated += 1
        else:
            print(f"  WARN alert {name}: HTTP {r.status_code} {r.text[:100]}")
    return updated


def configure_driver(
    client: Client,
    device_path: str,
    poll_ms: int,
    telemetry_publish_mode: str | None = None,
    verbose: bool = False,
) -> bool:
    body = {
        "driverId": "virtual",
        "pollIntervalMs": poll_ms,
        "configuration": {"profile": "lab"},
        "pointMappings": {},
        "autoStart": True,
    }
    if telemetry_publish_mode:
        body["telemetryPublishMode"] = telemetry_publish_mode
    url = f"/api/v1/drivers/runtime/configure?devicePath={quote(device_path, safe='')}"
    for attempt in range(3):
        r = client.request("PUT", url, json=body)
        if r.status_code < 400:
            return True
        if r.status_code in (502, 503, 504) and attempt < 2:
            time.sleep(1.0)
            continue
        if verbose:
            print(f"  WARN driver {device_path}: HTTP {r.status_code} {r.text[:160]}")
        return False
    return False


def ensure_drivers(
    client: Client,
    device_paths: list[str],
    poll_ms: int,
    telemetry_mix_ratio: float = 0.0,
    verbose: bool = False,
) -> dict:
    """Configure drivers. telemetry_mix_ratio: fraction of devices set to TELEMETRY_ONLY (0=all FULL)."""
    ok = 0
    telemetry_only = 0
    for index, path in enumerate(device_paths):
        mode = None
        if telemetry_mix_ratio > 0 and index < int(len(device_paths) * telemetry_mix_ratio):
            mode = "TELEMETRY_ONLY"
            telemetry_only += 1
        if configure_driver(client, path, poll_ms, telemetry_publish_mode=mode, verbose=verbose):
            ok += 1
    return {"configured": ok, "telemetryOnly": telemetry_only, "full": ok - telemetry_only}


def measure_phase(client: Client, duration_sec: float) -> dict:
    t0 = time.perf_counter()
    start_count = event_history_count(client)
    start_alert_fires = alert_fires_count(client)
    time.sleep(duration_sec)
    elapsed = time.perf_counter() - t0
    end_count = event_history_count(client)
    end_alert_fires = alert_fires_count(client)
    delta = max(0, end_count - start_count)
    alert_delta = max(0, end_alert_fires - start_alert_fires)
    eps = delta / max(elapsed, 0.001)
    alert_eps = alert_delta / max(elapsed, 0.001)
    return {
        "durationSec": round(elapsed, 1),
        "startEvents": start_count,
        "endEvents": end_count,
        "eventsGenerated": delta,
        "eventsPerSecond": round(eps, 2),
        "alertFiresGenerated": alert_delta,
        "alertFiresPerSecond": round(alert_eps, 2),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="ISPF internal event generation load test")
    parser.add_argument("--base-url", default="https://ispf.iot-solutions.ru")
    parser.add_argument("--host-header", default="")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--phase-seconds", type=int, default=60)
    parser.add_argument("--warmup-seconds", type=int, default=15, help="Wait after driver configure before measuring")
    parser.add_argument(
        "--condition-expr",
        default="true",
        help='Alert conditionExpr (default "true" for max driver throughput; use self.sineWave["value"] > -1000 for realistic)',
    )
    parser.add_argument(
        "--condition-expr-file",
        default="",
        help="Read conditionExpr from file (avoids shell quoting issues with bracket syntax)",
    )
    parser.add_argument("--poll-ms", default="3000,1000,500")
    parser.add_argument(
        "--telemetry-mix-ratio",
        type=float,
        default=0.0,
        help="Fraction of devices with TELEMETRY_ONLY (0=all FULL, 0.5=half skip automation)",
    )
    parser.add_argument("--max-devices", type=int, default=0, help="Limit loadtest devices (0=all)")
    parser.add_argument("--skip-monitor-setup", action="store_true")
    parser.add_argument("--skip-cleanup", action="store_true", help="Do not stop mqtt loadtest fleet before run")
    parser.add_argument("--skip-alert-seed", action="store_true")
    parser.add_argument("--metrics-interval", type=float, default=5.0)
    parser.add_argument("--timeout", type=float, default=60.0)
    parser.add_argument("--verbose", action="store_true", help="Log driver configure failures")
    args = parser.parse_args()

    condition_expr = args.condition_expr
    if args.condition_expr_file:
        condition_expr = Path(args.condition_expr_file).read_text(encoding="utf-8").strip()

    client = Client(args.base_url, args.host_header or None, args.timeout)
    client.login(args.username, args.password)

    if not args.skip_cleanup:
        print("Cleaning environment (stop demo/lab drivers, disable automation, stop loadtest fleet)...")
        stats = cleanup_for_internal_poll_test(client)
        print(f"  {format_cleanup_stats(stats)}")

    setup_mod = load_setup_module()
    syncer = setup_mod.MetricsSyncer(client, args.metrics_interval)

    if not args.skip_monitor_setup:
        print("Setting up platform metrics probe + dashboard...")
        setup_mod.setup(client)

    device_paths = list_loadtest_devices(client)
    if args.max_devices > 0:
        device_paths = device_paths[: args.max_devices]
    if not device_paths:
        print("No loadtest-dev-* devices found. Run: python deploy/vps-load-test.py --seed-only --devices 60", file=sys.stderr)
        return 1

    print(f"Loadtest devices: {len(device_paths)}")

    if not args.skip_alert_seed:
        removed = delete_loadtest_alert_rules(client)
        if removed:
            print(f"Removed {removed} stale loadtest alert rules")
        n = ensure_alert_rules(client, device_paths, condition_expr)
        print(f"Alert rules ensured/updated: {n} (condition={condition_expr!r})")

    syncer.sync_once()
    syncer.start()
    print(f"Metrics sync -> {setup_mod.DASHBOARD_PATH}")

    poll_levels = [int(x.strip()) for x in args.poll_ms.split(",") if x.strip()]
    results: list[dict] = []

    print(f"\nInternal event generation phases ({args.phase_seconds}s each)...")
    print(f"{'Poll ms':>8} {'Drivers':>8} {'Events':>10} {'Events/s':>10} {'Alert/s':>8} {'Journal':>12}")
    print("-" * 64)

    for poll_ms in poll_levels:
        driver_stats = ensure_drivers(
            client,
            device_paths,
            poll_ms,
            telemetry_mix_ratio=args.telemetry_mix_ratio,
            verbose=args.verbose,
        )
        drivers_ok = driver_stats["configured"]
        mix_note = ""
        if args.telemetry_mix_ratio > 0:
            mix_note = (
                f" (FULL={driver_stats['full']}, TELEMETRY_ONLY={driver_stats['telemetryOnly']})"
            )
        print(f"  configured {drivers_ok}/{len(device_paths)} drivers @ {poll_ms}ms poll{mix_note}")
        warmup = max(0, args.warmup_seconds)
        if warmup:
            print(f"  warming up {warmup}s (telemetry coalesce + async journal)...")
            time.sleep(warmup)
        phase = measure_phase(client, args.phase_seconds)
        phase["pollIntervalMs"] = poll_ms
        phase["devices"] = len(device_paths)
        phase["driversConfigured"] = drivers_ok
        phase["telemetryMixRatio"] = args.telemetry_mix_ratio
        phase["telemetryOnlyDevices"] = driver_stats.get("telemetryOnly", 0)
        results.append(phase)
        print(
            f"{poll_ms:8d} {drivers_ok:8d} {phase['eventsGenerated']:10d} "
            f"{phase['eventsPerSecond']:10.1f} {phase.get('alertFiresPerSecond', 0):8.1f} "
            f"{phase['endEvents']:12d}"
        )
        syncer.sync_once()

    syncer.stop()

    report_path = Path(__file__).with_name(f"events-internal-load-test-report-{int(time.time())}.json")
    with open(report_path, "w", encoding="utf-8") as fh:
        json.dump(
            {
                "devices": len(device_paths),
                "telemetryMixRatio": args.telemetry_mix_ratio,
                "dashboard": setup_mod.DASHBOARD_PATH,
                "probeDevice": setup_mod.DEVICE_PATH,
                "phases": results,
            },
            fh,
            indent=2,
        )

    best = max(results, key=lambda r: r["eventsPerSecond"])
    print("-" * 54)
    print(f"Peak internal generation: {best['eventsPerSecond']} events/s @ poll={best['pollIntervalMs']}ms")
    print(f"Dashboard: {setup_mod.DASHBOARD_PATH}")
    print(f"Report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
