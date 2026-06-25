#!/usr/bin/env python3
"""
Sweep per-device telemetryCoalesceMs on MQTT historian ingress (TELEMETRY_ONLY).

Measures variable_samples write rate (dashboard/history path), not event journal.
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
    automation_queue_metrics,
    device_path,
    list_mqtt_loadtest_devices,
    mqtt_topic,
    reapply_mqtt_coalesce,
    seed_mqtt_devices,
)

SWEEP_SCRIPT = Path(__file__).with_name("mqtt-ingress-load-test.py")
LOCAL_TOPICS = Path(__file__).with_name("mqtt-local-topics.json")
DEFAULT_COALESCE_MS = [250, 100, 50, 25, 10, 5, 1]


def load_ingress_module():
    spec = importlib.util.spec_from_file_location("mqtt_ingress_load_test", SWEEP_SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Cannot load {SWEEP_SCRIPT}")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def load_local_topics() -> tuple[str, list[str]]:
    data = json.loads(LOCAL_TOPICS.read_text(encoding="utf-8"))
    return data["brokerUrl"], [str(t) for t in data["topics"]]


def main() -> int:
    parser = argparse.ArgumentParser(description="MQTT coalesce-ms sweep (per-device override)")
    parser.add_argument("--base-url", default="https://ispf.iot-solutions.ru")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--devices", type=int, default=4)
    parser.add_argument("--messages-per-second", type=float, default=2000.0)
    parser.add_argument("--phase-seconds", type=int, default=45)
    parser.add_argument("--warmup-seconds", type=int, default=20)
    parser.add_argument("--publish-via-ssh", default="root@ispf.iot-solutions.ru")
    parser.add_argument("--remote-deploy-dir", default="/opt/ispf/loadtest")
    parser.add_argument(
        "--coalesce-ms",
        default=",".join(str(v) for v in DEFAULT_COALESCE_MS),
        help="Comma-separated per-device coalesce values (high to low)",
    )
    parser.add_argument("--skip-cleanup", action="store_true")
    args = parser.parse_args()

    ingress = load_ingress_module()
    broker_url, topics = load_local_topics()
    condition_expr = 'self.temperature["value"] > -1000.0'
    values = [int(x.strip()) for x in args.coalesce_ms.split(",") if x.strip()]

    client = Client(args.base_url, None, 60.0)
    client.login(args.username, args.password)

    if not args.skip_cleanup:
        print("Cleaning environment...")
        stats = cleanup_for_mqtt_subscribe_test(client, purge_mqtt=True)
        print(f"  {format_cleanup_stats(stats)}")

    print(f"Seeding {args.devices} mqtt devices (broker={broker_url})...")
    seed_mqtt_devices(
        client,
        args.devices,
        broker_url,
        condition_expr,
        topics=topics[: args.devices],
        telemetry_coalesce_ms=values[0],
        historian_only=True,
    )
    device_paths = list_mqtt_loadtest_devices(client)[: args.devices]

    results: list[dict] = []
    report_path = Path(__file__).with_name(f"mqtt-coalesce-sweep-report-{int(time.time())}.json")

    def save_report() -> None:
        report_path.write_text(
            json.dumps(
                {
                    "baseUrl": args.base_url,
                    "mode": "push",
                    "brokerUrl": broker_url,
                    "devices": args.devices,
                    "messagesPerSecond": args.messages_per_second,
                    "phaseSeconds": args.phase_seconds,
                    "warmupSeconds": args.warmup_seconds,
                    "results": results,
                },
                indent=2,
            ),
            encoding="utf-8",
        )

    print(f"\nMQTT historian coalesce sweep @ ~{args.messages_per_second} msg/s publisher")
    print(f"{'Coalesce':>10} {'Samples/s':>10} {'Alert/s':>10} {'TelQ':>8} {'minInt':>8}")
    print("-" * 52)

    for coalesce_ms in values:
        print(f"\n>>> telemetryCoalesceMs={coalesce_ms} (TELEMETRY_ONLY) ...", flush=True)
        started = reapply_mqtt_coalesce(
            client,
            device_paths,
            broker_url,
            coalesce_ms,
            topics=topics[: args.devices],
            historian_only=True,
        )
        if started < len(device_paths):
            print(f"  WARN: only {started}/{len(device_paths)} drivers started", flush=True)
        time.sleep(3)

        duration = args.warmup_seconds + args.phase_seconds + 5
        pub_proc = ingress.run_publisher_ssh(
            args.publish_via_ssh,
            broker_url,
            args.devices,
            args.messages_per_second,
            duration,
            args.remote_deploy_dir,
        )
        if args.warmup_seconds > 0:
            time.sleep(args.warmup_seconds)

        sample = device_path(1)
        if not ingress.wait_for_ingress(client, sample, timeout_sec=30):
            print(f"  WARN: no ingress on {sample}", flush=True)

        phase = ingress.measure_phase(client, args.phase_seconds, historian_only=True)
        queues = automation_queue_metrics(client)
        pub_out, _ = pub_proc.communicate(timeout=duration + 30)
        pub_rate = None
        if pub_out:
            for line in pub_out.strip().splitlines():
                if "msg/s)" in line:
                    pub_rate = line.strip()

        row = {
            "telemetryCoalesceMs": coalesce_ms,
            "historianOnly": True,
            "messagesPerSecondTarget": args.messages_per_second,
            "publisherSummary": pub_rate,
            **phase,
            **queues,
        }
        results.append(row)
        print(
            f"{coalesce_ms:10d} {phase.get('historianSamplesPerSecond', 0):10.1f} "
            f"{phase.get('alertFiresPerSecond', 0):10.1f} "
            f"{queues.get('objectChangeTelemetryQueueSize', 0):8d} "
            f"{int(phase.get('historianMinIntervalMs') or 0):8d}",
            flush=True,
        )
        if pub_rate:
            print(f"  publisher: {pub_rate}", flush=True)
        save_report()
        time.sleep(5)

    print(f"\nReport: {report_path}")
    if results:
        best = max(results, key=lambda r: r.get("historianSamplesPerSecond", 0))
        saturated = next(
            (
                r
                for r in sorted(results, key=lambda x: x["telemetryCoalesceMs"], reverse=True)
                if r.get("historianSamplesPerSecond", 0) < best.get("historianSamplesPerSecond", 0) * 0.95
                and r["telemetryCoalesceMs"] > best["telemetryCoalesceMs"]
            ),
            None,
        )
        print(
            f"Peak: {best.get('historianSamplesPerSecond')} samples/s "
            f"@ telemetryCoalesceMs={best.get('telemetryCoalesceMs')}"
        )
        if saturated:
            print(
                f"Coalesce-limited below peak from {saturated.get('telemetryCoalesceMs')}ms "
                f"({saturated.get('historianSamplesPerSecond')} samples/s)"
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
