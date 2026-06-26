#!/usr/bin/env python3
"""
Meter bus load test: MQTT sine simulator → ingestMeterPayload → instances + historian.

Single phase:
  python3 /opt/ispf/meter-bus-load-test.py --count 10000 --rounds 30

Interval sweep (decreasing pause, increasing load):
  python3 /opt/ispf/meter-bus-load-test.py --interval-sweep 1,0.5,0.25,0.1,0.05,0
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

API = "http://127.0.0.1:8080"
SIMULATOR = Path("/opt/ispf/meter-sine-simulator.py")


def api(method: str, path: str, body: dict | None = None, token: str | None = None) -> dict:
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f"{API}{path}", data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def login() -> str:
    return api("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin"})["token"]


def platform_metrics(token: str) -> dict[str, dict]:
    payload = api("GET", "/api/v1/platform/metrics", token=token)
    return {s.get("id", ""): (s.get("values") or {}) for s in payload.get("sections", [])}


def psql_scalar(sql: str) -> int | None:
    commands = [
        ["docker", "exec", "ispf-postgres", "psql", "-U", "ispf", "-d", "ispf", "-t", "-A", "-c", sql],
        [
            "docker-compose", "-f", "/opt/ispf/docker-compose.yml", "--env-file", "/opt/ispf/ispf-server.env",
            "exec", "-T", "postgres", "psql", "-U", "ispf", "-d", "ispf", "-t", "-A", "-c", sql,
        ],
    ]
    for cmd in commands:
        try:
            out = subprocess.check_output(cmd, stderr=subprocess.DEVNULL, text=True).strip()
            if out.lstrip("-").isdigit():
                return int(out)
        except (subprocess.CalledProcessError, FileNotFoundError):
            continue
    return None


def count_meter_instances() -> int | None:
    return psql_scalar(
        "SELECT count(*) FROM object_nodes WHERE path LIKE 'root.platform.instances.meter-%';"
    )


def count_meter_samples_since(seconds: int) -> int | None:
    return psql_scalar(
        f"SELECT count(*) FROM variable_samples "
        f"WHERE object_path LIKE 'root.platform.instances.meter-%' "
        f"AND sampled_at > now() - interval '{int(seconds)} seconds';"
    )


def run_simulator(
    *,
    count: int,
    rounds: int,
    interval: float,
    parallel: int,
    simulator: Path,
    id_width: int = 0,
) -> dict:
    width = id_width if id_width > 0 else max(4, len(str(count)))
    cmd = [
        sys.executable,
        str(simulator),
        "--count",
        str(count),
        "--rounds",
        str(rounds),
        "--interval",
        str(interval),
        "--parallel",
        str(parallel),
        "--id-width",
        str(width),
    ]
    t0 = time.perf_counter()
    proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
    elapsed = time.perf_counter() - t0
    if proc.returncode != 0:
        print(proc.stdout, file=sys.stdout)
        print(proc.stderr, file=sys.stderr)
        raise RuntimeError(f"simulator failed with code {proc.returncode}")
    published = rounds * count
    tail = proc.stdout.strip().splitlines()
    return {
        "rounds": rounds,
        "intervalSec": interval,
        "parallel": parallel,
        "sensorsPerRound": count,
        "mqttMessagesPublished": published,
        "publishElapsedSec": round(elapsed, 2),
        "mqttMessagesPerSecond": round(published / max(elapsed, 0.001), 2),
        "simulatorTail": tail[-2:] if tail else [],
    }


def parse_float_list(raw: str) -> list[float]:
    return [float(part.strip()) for part in raw.split(",") if part.strip()]


def parse_int_list(raw: str) -> list[int]:
    return [int(part.strip()) for part in raw.split(",") if part.strip()]


def expand_rounds(base: int, phases: int, step: int) -> list[int]:
    return [base + step * i for i in range(phases)]


def expand_parallel(start: int, phases: int, step: int) -> list[int]:
    return [start + step * i for i in range(phases)]


def measure_historian(token: str, vh_before: dict, seconds: float) -> dict:
    hist_before = int(vh_before.get("sampleCount") or 0)
    time.sleep(seconds)
    vh_after = platform_metrics(token).get("variableHistory", {})
    hist_delta = max(0, int(vh_after.get("sampleCount") or 0) - hist_before)
    meter_samples = count_meter_samples_since(int(seconds) + 5)
    return {
        "measureSec": round(seconds, 1),
        "globalSamplesDelta": hist_delta,
        "globalSamplesPerSecond": round(hist_delta / max(seconds, 0.001), 2),
        "meterSamplesInWindow": meter_samples,
        "meterSamplesPerSecond": (
            None if meter_samples is None else round(meter_samples / max(seconds, 0.001), 2)
        ),
        "queueSize": vh_after.get("queueSize"),
        "droppedDelta": int(vh_after.get("dropped") or 0) - int(vh_before.get("dropped") or 0),
        "minIntervalMs": vh_after.get("minIntervalMs"),
        "vh_after": vh_after,
    }


def run_single_phase(args: argparse.Namespace) -> dict:
    rounds = args.rounds if args.rounds > 0 else max(1, int(args.phase_seconds / max(args.interval, 0.05)))
    token = login()
    metrics_before = platform_metrics(token)
    vh_before = metrics_before.get("variableHistory", {})
    auto_before = metrics_before.get("automation", {})
    instances_before = count_meter_instances()

    publish_stats = run_simulator(
        count=args.count,
        rounds=rounds,
        interval=args.interval,
        parallel=args.parallel,
        simulator=args.simulator,
    )
    time.sleep(args.warmup_seconds)
    hist = measure_historian(token, vh_before, args.phase_seconds)
    metrics_after = platform_metrics(token)
    auto_after = metrics_after.get("automation", {})
    instances_after = count_meter_instances()

    return {
        "mode": "single",
        "config": {
            "sensorsPerRound": args.count,
            "rounds": rounds,
            "intervalSec": args.interval,
            "phaseSeconds": args.phase_seconds,
            "warmupSeconds": args.warmup_seconds,
            "parallel": args.parallel,
        },
        "mqtt": publish_stats,
        "ingest": {
            "meterInstancesBefore": instances_before,
            "meterInstancesAfter": instances_after,
        },
        "historian": {k: v for k, v in hist.items() if k != "vh_after"},
        "automation": {
            "objectChangeQueueDroppedDelta": int(auto_after.get("objectChangeQueueDropped") or 0)
            - int(auto_before.get("objectChangeQueueDropped") or 0),
        },
    }


def run_interval_sweep(args: argparse.Namespace) -> dict:
    intervals = parse_float_list(args.interval_sweep)
    if not intervals:
        raise SystemExit("--interval-sweep must list at least one value")

    if args.rounds_sweep:
        rounds_list = parse_int_list(args.rounds_sweep)
        if len(rounds_list) != len(intervals):
            raise SystemExit("--rounds-sweep length must match --interval-sweep")
    else:
        rounds_list = expand_rounds(args.rounds_per_phase, len(intervals), args.rounds_step)

    if args.parallel_sweep:
        parallel_list = parse_int_list(args.parallel_sweep)
        if len(parallel_list) != len(intervals):
            raise SystemExit("--parallel-sweep length must match --interval-sweep")
    else:
        parallel_list = expand_parallel(args.parallel, len(intervals), args.parallel_step)

    token = login()
    instances_start = count_meter_instances()
    phases: list[dict] = []
    total_mqtt = 0
    total_publish_sec = 0.0

    print(f"==> Interval sweep: {len(intervals)} phases, sensors={args.count}")
    print(f"    intervals={intervals}")
    print(f"    rounds={rounds_list}")
    print(f"    parallel={parallel_list}")

    for idx, interval in enumerate(intervals):
        rounds = rounds_list[idx]
        parallel = parallel_list[idx]
        phase_no = idx + 1
        print(f"\n--- Phase {phase_no}/{len(intervals)}: interval={interval}s rounds={rounds} parallel={parallel} ---")

        vh_before = platform_metrics(token).get("variableHistory", {})
        publish = run_simulator(
            count=args.count,
            rounds=rounds,
            interval=interval,
            parallel=parallel,
            simulator=args.simulator,
        )
        for line in publish.get("simulatorTail", []):
            print(f"    {line}")

        time.sleep(args.warmup_seconds)
        hist = measure_historian(token, vh_before, args.phase_seconds)
        total_mqtt += publish["mqttMessagesPublished"]
        total_publish_sec += publish["publishElapsedSec"]

        phase_result = {
            "phase": phase_no,
            "intervalSec": interval,
            "rounds": rounds,
            "parallel": parallel,
            "mqtt": publish,
            "historian": {k: v for k, v in hist.items() if k != "vh_after"},
        }
        phases.append(phase_result)
        print(
            f"    MQTT {publish['mqttMessagesPerSecond']} msg/s | "
            f"historian {hist['meterSamplesPerSecond']} samples/s | "
            f"queue {hist.get('queueSize')} dropped+{hist.get('droppedDelta', 0)}"
        )

    instances_end = count_meter_instances()
    sweep_publish_sec = sum(p["mqtt"]["publishElapsedSec"] for p in phases)
    sweep_measure_sec = len(phases) * args.phase_seconds

    return {
        "mode": "interval_sweep",
        "config": {
            "sensorsPerRound": args.count,
            "intervals": intervals,
            "roundsPerPhase": rounds_list,
            "parallelPerPhase": parallel_list,
            "phaseSeconds": args.phase_seconds,
            "warmupSeconds": args.warmup_seconds,
        },
        "summary": {
            "totalMqttMessages": total_mqtt,
            "totalPublishSec": round(sweep_publish_sec, 2),
            "avgMqttMessagesPerSecond": round(total_mqtt / max(sweep_publish_sec, 0.001), 2),
            "peakMqttMessagesPerSecond": max(p["mqtt"]["mqttMessagesPerSecond"] for p in phases),
            "peakHistorianSamplesPerSecond": max(
                p["historian"]["meterSamplesPerSecond"] or 0 for p in phases
            ),
            "meterInstancesStart": instances_start,
            "meterInstancesEnd": instances_end,
        },
        "phases": phases,
    }


def print_report(report: dict) -> None:
    print("\n==> Summary")
    if report["mode"] == "single":
        mqtt = report["mqtt"]
        hist = report["historian"]
        print(
            f"MQTT: {mqtt['mqttMessagesPublished']} msgs in {mqtt['publishElapsedSec']}s "
            f"({mqtt['mqttMessagesPerSecond']} msg/s)"
        )
        print(
            f"Historian: {hist['globalSamplesDelta']} samples in {hist['measureSec']}s "
            f"({hist['meterSamplesPerSecond']} meter samples/s)"
        )
        return

    summary = report["summary"]
    print(f"Total MQTT:     {summary['totalMqttMessages']} msgs ({summary['avgMqttMessagesPerSecond']} msg/s avg)")
    print(f"Peak MQTT:      {summary['peakMqttMessagesPerSecond']} msg/s")
    print(f"Peak historian: {summary['peakHistorianSamplesPerSecond']} meter samples/s")
    print(f"Instances:      {summary['meterInstancesEnd']}")
    print("\n| Phase | interval | rounds | parallel | MQTT msg/s | historian samples/s | queue |")
    print("|-------|----------|--------|----------|------------|---------------------|-------|")
    for p in report["phases"]:
        h = p["historian"]
        m = p["mqtt"]
        print(
            f"| {p['phase']:5} | {p['intervalSec']:8} | {p['rounds']:6} | {p['parallel']:8} | "
            f"{m['mqttMessagesPerSecond']:10} | {h.get('meterSamplesPerSecond') or '-':19} | "
            f"{h.get('queueSize') or '-':5} |"
        )


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Meter bus MQTT ingest load test")
    p.add_argument("--count", type=int, default=10000, help="sensors per round")
    p.add_argument("--phase-seconds", type=int, default=30, help="measurement window per phase")
    p.add_argument("--interval", type=float, default=1.0, help="single-phase pause between rounds")
    p.add_argument(
        "--interval-sweep",
        default="",
        help="comma-separated intervals per phase, decreasing (e.g. 1,0.5,0.25,0.1,0.05,0)",
    )
    p.add_argument("--rounds", type=int, default=0, help="single-phase MQTT rounds")
    p.add_argument("--rounds-per-phase", type=int, default=10, help="sweep: rounds in first phase")
    p.add_argument("--rounds-step", type=int, default=5, help="sweep: +rounds each phase")
    p.add_argument("--rounds-sweep", default="", help="explicit rounds list for sweep")
    p.add_argument("--parallel", type=int, default=50, help="parallel mosquitto_pub")
    p.add_argument("--parallel-step", type=int, default=5, help="sweep: +parallel each phase")
    p.add_argument("--parallel-sweep", default="", help="explicit parallel list for sweep")
    p.add_argument("--warmup-seconds", type=int, default=8, help="wait after publish before measure")
    p.add_argument("--simulator", type=Path, default=SIMULATOR)
    p.add_argument("--report", type=Path, default=None)
    return p.parse_args()


def main() -> int:
    args = parse_args()
    report = run_interval_sweep(args) if args.interval_sweep else run_single_phase(args)
    report["timestamp"] = datetime.now(timezone.utc).isoformat()
    print_report(report)

    out = args.report or Path(f"/opt/ispf/meter-bus-load-test-report-{int(time.time())}.json")
    try:
        out.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"\nReport: {out}")
    except OSError:
        print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
