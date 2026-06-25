#!/usr/bin/env python3
"""
MQTT ingress load test — ISPF mqtt driver **subscribes** to broker topics (pull/subscribe).

Default: historian path (TELEMETRY_ONLY → variable_samples, no alert/automation).
Use --automation for legacy alert → event journal benchmarking.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import subprocess
import sys
import time
from pathlib import Path

from loadtest_cleanup_lib import cleanup_for_mqtt_subscribe_test, format_cleanup_stats
from mqtt_loadtest_lib import (
    Client,
    alert_fires_count,
    device_path,
    driver_status,
    event_history_count,
    list_mqtt_loadtest_devices,
    read_temperature_raw,
    seed_mqtt_devices,
    variable_history_metrics,
    variable_history_sample_count,
)

SETUP_SCRIPT = Path(__file__).with_name("setup-platform-metrics-monitor.py")
PUBLISHER_SCRIPT = Path(__file__).with_name("mqtt-loadtest-publisher.py")
TAP_SCRIPT = Path(__file__).with_name("mqtt-loadtest-tap.py")
REAL_TOPICS_FILE = Path(__file__).with_name("mqtt-real-topics.json")


def load_setup_module():
    spec = importlib.util.spec_from_file_location("setup_platform_metrics", SETUP_SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Cannot load {SETUP_SCRIPT}")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def load_topics_file(path: Path) -> tuple[str, list[str]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    broker = data.get("brokerUrl") or data.get("broker_url") or ""
    topics = data.get("topics") or []
    if not broker or not topics:
        raise ValueError(f"Topics file must contain brokerUrl and topics: {path}")
    return broker, [str(topic) for topic in topics]


def run_publisher_local(broker_url: str, devices: int, messages_per_second: float, duration_seconds: float):
    cmd = [
        sys.executable,
        str(PUBLISHER_SCRIPT),
        "--broker",
        broker_url,
        "--devices",
        str(devices),
        "--messages-per-second",
        str(messages_per_second),
        "--duration-seconds",
        str(duration_seconds),
    ]
    return subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)


def run_publisher_ssh(
    remote_host: str,
    broker_url: str,
    devices: int,
    messages_per_second: float,
    duration_seconds: float,
    remote_dir: str,
):
    remote_cmd = (
        f"cd {remote_dir} && {remote_dir}/venv/bin/python mqtt-loadtest-publisher.py "
        f"--broker {broker_url} --devices {devices} "
        f"--messages-per-second {messages_per_second} "
        f"--duration-seconds {duration_seconds}"
    )
    return subprocess.Popen(
        ["ssh", "-o", "BatchMode=yes", remote_host, remote_cmd],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )


def wait_for_ingress(client: Client, path: str, timeout_sec: float = 60.0) -> bool:
    deadline = time.perf_counter() + timeout_sec
    baseline_raw: str | None = read_temperature_raw(client, path)
    while time.perf_counter() < deadline:
        status = driver_status(client, path)
        if not status or status.get("status") != "RUNNING" or not status.get("connected"):
            time.sleep(1.0)
            continue
        raw = read_temperature_raw(client, path)
        if raw is not None and raw.strip() and raw != baseline_raw:
            return True
        time.sleep(1.0)
    return False


def measure_phase(client: Client, duration_sec: float, historian_only: bool) -> dict:
    t0 = time.perf_counter()
    start_samples = variable_history_sample_count(client)
    start_events = event_history_count(client)
    start_alerts = alert_fires_count(client)
    time.sleep(duration_sec)
    elapsed = time.perf_counter() - t0
    end_samples = variable_history_sample_count(client)
    end_events = event_history_count(client)
    end_alerts = alert_fires_count(client)
    delta_samples = max(0, end_samples - start_samples)
    delta_events = max(0, end_events - start_events)
    delta_alerts = max(0, end_alerts - start_alerts)
    result = {
        "durationSec": round(elapsed, 1),
        "historianSamplesGenerated": delta_samples,
        "historianSamplesPerSecond": round(delta_samples / max(elapsed, 0.001), 2),
        "endHistorianSamples": end_samples,
        "eventsGenerated": delta_events,
        "eventsPerSecond": round(delta_events / max(elapsed, 0.001), 2),
        "alertFiresGenerated": delta_alerts,
        "alertFiresPerSecond": round(delta_alerts / max(elapsed, 0.001), 2),
        "endEvents": end_events,
        "historianMinIntervalMs": variable_history_metrics(client).get("minIntervalMs"),
    }
    if historian_only:
        result["eventsPerSecond"] = result["historianSamplesPerSecond"]
        result["eventsGenerated"] = delta_samples
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description="ISPF MQTT subscribe ingress load test")
    parser.add_argument("--base-url", default="https://ispf.iot-solutions.ru")
    parser.add_argument("--host-header", default="")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument(
        "--mode",
        choices=("subscribe", "push"),
        default="subscribe",
        help="subscribe: ISPF mqtt driver only (real broker topics). push: synthetic publisher (local lab)",
    )
    parser.add_argument(
        "--topics-file",
        default=str(REAL_TOPICS_FILE),
        help="JSON with brokerUrl + topics for subscribe mode",
    )
    parser.add_argument("--broker-url", default="", help="Override broker URL from topics file")
    parser.add_argument("--devices", type=int, default=4)
    parser.add_argument("--messages-per-second", type=float, default=200.0, help="push mode only")
    parser.add_argument("--phase-seconds", type=int, default=60)
    parser.add_argument("--warmup-seconds", type=int, default=30)
    parser.add_argument("--seed-only", action="store_true")
    parser.add_argument("--skip-seed", action="store_true")
    parser.add_argument("--skip-cleanup", action="store_true")
    parser.add_argument("--skip-monitor-setup", action="store_true")
    parser.add_argument(
        "--condition-expr-file",
        default=str(Path(__file__).with_name("loadtest-sinewave-condition.txt")),
    )
    parser.add_argument(
        "--condition-expr",
        default='self.temperature["value"] > -1000.0',
    )
    parser.add_argument("--telemetry-mix-ratio", type=float, default=0.0)
    parser.add_argument(
        "--telemetry-coalesce-ms",
        type=int,
        default=50,
        help="Per-device telemetryCoalesceMs (historian ingest rate; 0 = server default)",
    )
    parser.add_argument(
        "--automation",
        action="store_true",
        help="Legacy: FULL telemetry + alert rules → event journal (not historian benchmark)",
    )
    parser.add_argument("--publish-via-ssh", default="", help="push mode: run publisher on VPS")
    parser.add_argument("--remote-deploy-dir", default="/opt/ispf/loadtest")
    parser.add_argument("--timeout", type=float, default=60.0)
    args = parser.parse_args()

    condition_expr = args.condition_expr
    if args.condition_expr_file and Path(args.condition_expr_file).is_file():
        condition_expr = Path(args.condition_expr_file).read_text(encoding="utf-8").strip()
        condition_expr = condition_expr.replace("sineWave", "temperature")

    broker_url = args.broker_url
    subscribe_topics: list[str] | None = None
    if args.mode == "subscribe":
        topics_path = Path(args.topics_file)
        if not topics_path.is_file():
            print(f"Missing topics file: {topics_path}", file=sys.stderr)
            return 1
        file_broker, subscribe_topics = load_topics_file(topics_path)
        broker_url = broker_url or file_broker
    else:
        broker_url = broker_url or "tcp://127.0.0.1:1883"
        subscribe_topics = None

    client = Client(args.base_url, args.host_header or None, args.timeout)
    client.login(args.username, args.password)

    if not args.skip_cleanup:
        print("Cleaning environment (stop demo/lab drivers, disable automation, stop virtual poll fleet)...")
        stats = cleanup_for_mqtt_subscribe_test(client, purge_mqtt=not args.skip_seed)
        print(f"  {format_cleanup_stats(stats)}")

    historian_only = not args.automation

    if not args.skip_seed:
        coalesce = args.telemetry_coalesce_ms if args.telemetry_coalesce_ms > 0 else None
        mode_label = "historian TELEMETRY_ONLY" if historian_only else "automation FULL"
        print(f"Seeding {args.devices} MQTT devices ({mode_label}, broker={broker_url})...")
        seed_mqtt_devices(
            client,
            args.devices,
            broker_url,
            condition_expr,
            telemetry_mix_ratio=args.telemetry_mix_ratio,
            topics=subscribe_topics,
            telemetry_coalesce_ms=coalesce,
            historian_only=historian_only,
        )
    else:
        existing = list_mqtt_loadtest_devices(client)
        if len(existing) < args.devices:
            print(f"Only {len(existing)} mqtt devices; run without --skip-seed", file=sys.stderr)
            return 1

    if args.seed_only:
        sample = device_path(1)
        print(f"Seed complete. Sample: {sample} driver={driver_status(client, sample)}")
        return 0

    setup_mod = None
    syncer = None
    if not args.skip_monitor_setup:
        setup_mod = load_setup_module()
        syncer = setup_mod.MetricsSyncer(client, 5.0)
        setup_mod.setup(client)
        syncer.sync_once()
        syncer.start()

    sample_path = device_path(1)
    status = driver_status(client, sample_path)
    print(f"Sample device {sample_path}: {status}")

    pub_proc = None
    if args.mode == "push":
        print(
            f"\nPhase [push]: synthetic publisher ~{args.messages_per_second} msg/s, "
            f"measure {args.phase_seconds}s"
        )
        duration = args.warmup_seconds + args.phase_seconds + 5
        if args.publish_via_ssh:
            pub_proc = run_publisher_ssh(
                args.publish_via_ssh,
                broker_url,
                args.devices,
                args.messages_per_second,
                duration,
                args.remote_deploy_dir,
            )
        else:
            pub_proc = run_publisher_local(
                broker_url,
                args.devices,
                args.messages_per_second,
                duration,
            )
    else:
        topic_hint = subscribe_topics[0] if subscribe_topics else "?"
        print(
            f"\nPhase [subscribe]: mqtt driver pull from {broker_url}, "
            f"topics like {topic_hint!r}, measure {args.phase_seconds}s"
        )

    if args.warmup_seconds > 0:
        print(f"  warming up {args.warmup_seconds}s ...")
        time.sleep(args.warmup_seconds)

    if not wait_for_ingress(client, sample_path, timeout_sec=max(60, args.warmup_seconds + 30)):
        print(
            f"  WARN: no live MQTT ingress on {sample_path} "
            f"(driver must be RUNNING/connected; broker reachable from ISPF server?)",
            file=sys.stderr,
        )
    else:
        print(f"  ingress OK: {sample_path} temperature.raw={read_temperature_raw(client, sample_path)!r}")

    phase = measure_phase(client, args.phase_seconds, historian_only)
    phase.update(
        {
            "mode": args.mode,
            "historianOnly": historian_only,
            "devices": args.devices,
            "brokerUrl": broker_url,
            "topics": subscribe_topics,
            "telemetryMixRatio": args.telemetry_mix_ratio,
            "telemetryCoalesceMs": args.telemetry_coalesce_ms if args.telemetry_coalesce_ms > 0 else None,
            "conditionExpr": condition_expr if not historian_only else None,
        }
    )

    if pub_proc is not None:
        pub_out, _ = pub_proc.communicate(timeout=args.phase_seconds + args.warmup_seconds + 30)
        if pub_out:
            for line in pub_out.strip().splitlines()[-3:]:
                print(f"  publisher: {line}")

    if syncer is not None:
        syncer.sync_once()
        syncer.stop()

    label = "MQTT subscribe" if args.mode == "subscribe" else "MQTT push msg/s"
    rate_label = "live feed" if args.mode == "subscribe" else f"{args.messages_per_second:.1f}"

    if historian_only:
        print(
            f"\n{'MQTT historian':>22} {'Samples/s':>10} {'Alert/s':>10} {'Samples':>12}\n"
            f"{rate_label:>22} {phase['historianSamplesPerSecond']:10.1f} "
            f"{phase['alertFiresPerSecond']:10.1f} {phase['endHistorianSamples']:12d}"
        )
        if phase.get("historianMinIntervalMs"):
            print(f"  historian min-interval-ms: {phase['historianMinIntervalMs']}")
    else:
        print(
            f"\n{label:>22} {'Events/s':>10} {'Alert/s':>10} {'Journal':>12}\n"
            f"{rate_label:>22} {phase['eventsPerSecond']:10.1f} "
            f"{phase['alertFiresPerSecond']:10.1f} {phase['endEvents']:12d}"
        )

    report_path = Path(__file__).with_name(f"mqtt-ingress-load-test-report-{int(time.time())}.json")
    report_path.write_text(
        json.dumps({"phase": phase, "sampleDevice": sample_path, "driverStatus": status}, indent=2),
        encoding="utf-8",
    )
    print(f"Report: {report_path}")
    if setup_mod is not None:
        print(f"Dashboard: {setup_mod.DASHBOARD_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
