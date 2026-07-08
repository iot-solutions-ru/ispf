#!/usr/bin/env python3
"""
Collect acceleration baseline KPIs (S19-01).

Writes tools/acceleration/baseline-report.json and prints a markdown summary.
Uses `gh` CLI when available for CI stats; other KPIs from documented baselines.
"""

from __future__ import annotations

import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / "baseline-report.json"


def gh_json(args: list[str]) -> list[dict] | None:
    try:
        proc = subprocess.run(
            ["gh", *args],
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=60,
            check=False,
        )
        if proc.returncode != 0:
            return None
        return json.loads(proc.stdout or "[]")
    except (FileNotFoundError, json.JSONDecodeError, subprocess.TimeoutExpired):
        return None


def ci_stats(days: int = 14) -> dict:
    runs = gh_json(
        [
            "run",
            "list",
            "--workflow=ci.yml",
            "--limit",
            "50",
            "--json",
            "conclusion,createdAt,updatedAt,status",
        ]
    )
    if not runs:
        return {"source": "gh unavailable", "sample_size": 0}

    completed = [r for r in runs if r.get("conclusion")]
    successes = [r for r in completed if r.get("conclusion") == "success"]
    durations_min: list[float] = []
    for run in completed:
        try:
            start = datetime.fromisoformat(run["createdAt"].replace("Z", "+00:00"))
            end = datetime.fromisoformat(run["updatedAt"].replace("Z", "+00:00"))
            durations_min.append((end - start).total_seconds() / 60.0)
        except (KeyError, ValueError):
            continue

    avg_min = round(sum(durations_min) / len(durations_min), 1) if durations_min else None
    success_rate = round(len(successes) / len(completed) * 100, 1) if completed else None
    return {
        "source": "github actions ci.yml",
        "sample_size": len(completed),
        "success_rate_pct": success_rate,
        "avg_wall_min": avg_min,
        "note": "Parallel jobs: backend + web-console; avg is workflow wall time",
    }


def build_report() -> dict:
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    return {
        "generated_at": now,
        "program": "acceleration S19-S23",
        "baseline_date": "2026-07-05",
        "kpi": {
            "ci": ci_stats(),
            "jvm_load": {
                "list_devices_p99_ceiling_ms": 2500,
                "list_devices_p99_ci_ceiling_ms": 5000,
                "events_fire_p99_ceiling_ms": 3000,
                "source": "ListDevicesLoadTest, EventFireLoadTest",
            },
            "mqtt_ingress": {
                "sustained_events_per_s": 1878,
                "version": "0.9.87",
                "source": "docs/en/load-testing.md, HF01",
            },
            "cluster": {
                "scale_factor_floor": 1.8,
                "source": "deploy/cluster-scale-load-test.py",
            },
            "hmi": {
                "lighthouse_performance": None,
                "lighthouse_accessibility": None,
                "mimic_fps": None,
                "note": "Not gated yet — S21 targets",
            },
            "federation": {
                "selective_sync_pct": 55,
                "chaos_automated": False,
                "bl": "BL-119, BL-120",
            },
            "semantic": {
                "time_to_first_dashboard_min": None,
                "note": "S23 target <= 5 min",
            },
        },
    }


def print_markdown(report: dict) -> None:
    ci = report["kpi"]["ci"]
    jvm = report["kpi"]["jvm_load"]
    mqtt = report["kpi"]["mqtt_ingress"]
    cluster = report["kpi"]["cluster"]
    print("# Acceleration baseline snapshot\n")
    print(f"Generated: {report['generated_at']}\n")
    print("| KPI | Value |")
    print("| --- | ----- |")
    if ci.get("avg_wall_min") is not None:
        print(f"| CI avg wall (min) | {ci['avg_wall_min']} |")
    if ci.get("success_rate_pct") is not None:
        print(f"| CI success rate | {ci['success_rate_pct']}% ({ci['sample_size']} runs) |")
    print(f"| list_devices p99 ceiling | {jvm['list_devices_p99_ceiling_ms']} ms |")
    print(f"| MQTT ingress sustained | ~{mqtt['sustained_events_per_s']} events/s |")
    print(f"| Cluster scale floor | {cluster['scale_factor_floor']}x |")
    print("\nFull report: tools/acceleration/baseline-report.json")


def main() -> int:
    report = build_report()
    OUT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {OUT}")
    print_markdown(report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
