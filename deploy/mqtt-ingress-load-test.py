#!/usr/bin/env python3
"""
MQTT ingress load test — ISPF mqtt driver **subscribes** to broker topics (pull/subscribe).

Default: historian path (TELEMETRY_ONLY → variable_samples, no alert/automation).
Use --ingress-history-only to benchmark lastIngress.raw on gateway only (no child dispatch).
Use --automation for legacy alert → event journal benchmarking.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

from loadtest_cleanup_lib import cleanup_for_mqtt_subscribe_test, format_cleanup_stats
from mqtt_loadtest_lib import (
    Client,
    INGRESS_HISTORY_FIELD,
    INGRESS_VARIABLE,
    alert_fires_count,
    device_path,
    driver_status,
    event_history_count,
    gateway_path,
    list_mqtt_loadtest_devices,
    read_last_ingress_raw,
    read_temperature_raw,
    seed_mqtt_devices,
    seed_mqtt_gateway_devices,
    seed_mqtt_gateway_ingress_history,
    sensor_path,
    variable_history_field_sample_count,
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


def parse_broker_host_port(broker_url: str) -> tuple[str, int]:
    host_port = broker_url.replace("tcp://", "").replace("ssl://", "")
    if ":" in host_port:
        host, port_s = host_port.rsplit(":", 1)
        return host, int(port_s)
    return host_port, 1883


def run_publisher_ssh(
    remote_host: str,
    broker_url: str,
    devices: int,
    messages_per_second: float,
    duration_seconds: float,
    remote_dir: str,
    publisher: str = "python",
    emqtt_interval_ms: int = 10,
):
    if publisher == "emqtt-bench":
        host, port = parse_broker_host_port(broker_url)
        remote_cmd = (
            f"bash {remote_dir}/mqtt-emqtt-bench.sh "
            f"--host {host} --port {port} --devices {devices} "
            f"--messages-per-second {messages_per_second} "
            f"--duration-seconds {duration_seconds} "
            f"--interval-ms {emqtt_interval_ms}"
        )
    else:
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


def run_emqtt_bench_local(
    broker_url: str,
    devices: int,
    messages_per_second: float,
    duration_seconds: float,
    emqtt_interval_ms: int,
):
    script = Path(__file__).with_name("mqtt-emqtt-bench.sh")
    host, port = parse_broker_host_port(broker_url)
    cmd = [
        "bash",
        str(script),
        "--host",
        host,
        "--port",
        str(port),
        "--devices",
        str(devices),
        "--messages-per-second",
        str(messages_per_second),
        "--duration-seconds",
        str(duration_seconds),
        "--interval-ms",
        str(emqtt_interval_ms),
    ]
    return subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)


def wait_for_ingress(
    client: Client,
    ingress_path: str,
    timeout_sec: float = 60.0,
    driver_path: str | None = None,
    *,
    ingress_history_only: bool = False,
) -> bool:
    """Wait until MQTT telemetry reaches ingress_path (driver_path defaults to ingress_path)."""
    driver_path = driver_path or ingress_path
    deadline = time.perf_counter() + timeout_sec
    if ingress_history_only:
        baseline_raw: str | None = read_last_ingress_raw(client, ingress_path)
        read_fn = read_last_ingress_raw
    else:
        baseline_raw = read_temperature_raw(client, ingress_path)
        read_fn = read_temperature_raw
    while time.perf_counter() < deadline:
        status = driver_status(client, driver_path)
        if not status or status.get("status") != "RUNNING" or not status.get("connected"):
            time.sleep(1.0)
            continue
        raw = read_fn(client, ingress_path)
        if raw is not None and raw.strip() and raw != baseline_raw:
            return True
        time.sleep(1.0)
    return False


def measure_phase(
    client: Client,
    duration_sec: float,
    historian_only: bool,
    *,
    ingress_history_path: str | None = None,
    ingress_history_variable: str | None = None,
    ingress_history_field: str | None = None,
) -> dict:
    t0 = time.perf_counter()
    phase_start = datetime.now(timezone.utc)
    if ingress_history_path and ingress_history_variable and ingress_history_field:
        start_samples = variable_history_field_sample_count(
            client,
            ingress_history_path,
            ingress_history_variable,
            ingress_history_field,
            to_instant=phase_start,
        )
    else:
        start_samples = variable_history_sample_count(client)
    start_events = event_history_count(client)
    start_alerts = alert_fires_count(client)
    time.sleep(duration_sec)
    if ingress_history_path:
        time.sleep(3.0)
    elapsed = time.perf_counter() - t0
    phase_end = datetime.now(timezone.utc)
    if ingress_history_path and ingress_history_variable and ingress_history_field:
        end_samples = variable_history_field_sample_count(
            client,
            ingress_history_path,
            ingress_history_variable,
            ingress_history_field,
            to_instant=phase_end,
        )
        delta_samples = max(0, end_samples - start_samples)
        total_samples = end_samples
    else:
        end_samples = variable_history_sample_count(client)
        delta_samples = max(0, end_samples - start_samples)
        total_samples = end_samples
    end_events = event_history_count(client)
    end_alerts = alert_fires_count(client)
    delta_events = max(0, end_events - start_events)
    delta_alerts = max(0, end_alerts - start_alerts)
    result = {
        "durationSec": round(elapsed, 1),
        "historianSamplesGenerated": delta_samples,
        "historianSamplesPerSecond": round(delta_samples / max(elapsed, 0.001), 2),
        "endHistorianSamples": total_samples,
        "eventsGenerated": delta_events,
        "eventsPerSecond": round(delta_events / max(elapsed, 0.001), 2),
        "alertFiresGenerated": delta_alerts,
        "alertFiresPerSecond": round(delta_alerts / max(elapsed, 0.001), 2),
        "endEvents": end_events,
        "historianMinIntervalMs": variable_history_metrics(client).get("minIntervalMs"),
    }
    if ingress_history_path:
        result["historianTarget"] = {
            "objectPath": ingress_history_path,
            "variableName": ingress_history_variable,
            "fieldName": ingress_history_field,
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
    parser.add_argument(
        "--gateway",
        action="store_true",
        help="Orchestrator mode: 1× mqtt-gateway + N child sensors (dispatchTelemetry)",
    )
    parser.add_argument(
        "--ingress-history-only",
        action="store_true",
        help="Gateway-only: historian on lastIngress.raw, no child sensors/dispatch",
    )
    parser.add_argument("--publish-via-ssh", default="", help="push mode: run publisher on VPS")
    parser.add_argument(
        "--publisher",
        choices=("python", "emqtt-bench"),
        default="python",
        help="push mode publisher: python (paho, ~1.5k msg/s) or emqtt-bench (Docker, high rate)",
    )
    parser.add_argument(
        "--emqtt-interval-ms",
        type=int,
        default=10,
        help="emqtt-bench -I per client (default 10 → 100 msg/s per client)",
    )
    parser.add_argument("--remote-deploy-dir", default="/opt/ispf/loadtest")
    parser.add_argument("--timeout", type=float, default=60.0)
    args = parser.parse_args()

    if args.ingress_history_only and args.gateway:
        print("Note: --ingress-history-only replaces --gateway (no child dispatch)", file=sys.stderr)
    if args.ingress_history_only and args.automation:
        print("--ingress-history-only requires historian mode (omit --automation)", file=sys.stderr)
        return 2

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
    ingress_history_only = args.ingress_history_only

    if not args.skip_seed:
        coalesce = args.telemetry_coalesce_ms if args.telemetry_coalesce_ms > 0 else None
        mode_label = "historian TELEMETRY_ONLY" if historian_only else "automation FULL"
        if ingress_history_only:
            print(
                f"Seeding mqtt-gateway ingress historian ({mode_label}, "
                f"lastIngress.{INGRESS_HISTORY_FIELD}, broker={broker_url})..."
            )
            seed_mqtt_gateway_ingress_history(
                client,
                args.devices,
                broker_url,
                topics=subscribe_topics,
                telemetry_coalesce_ms=coalesce,
            )
        elif args.gateway:
            print(
                f"Seeding mqtt-gateway orchestrator + {args.devices} child sensors "
                f"({mode_label}, broker={broker_url})..."
            )
            seed_mqtt_gateway_devices(
                client,
                args.devices,
                broker_url,
                topics=subscribe_topics,
                telemetry_coalesce_ms=coalesce,
            )
        else:
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
        sample = gateway_path() if ingress_history_only or args.gateway else device_path(1)
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

    if ingress_history_only:
        ingress_path = gateway_path()
        driver_check_path = gateway_path()
    else:
        ingress_path = sensor_path(1) if args.gateway else device_path(1)
        driver_check_path = gateway_path() if args.gateway else ingress_path
    status = driver_status(client, driver_check_path)
    if ingress_history_only:
        print(f"Gateway ingress historian {gateway_path()}: {status}")
    elif args.gateway:
        print(f"Gateway {gateway_path()}: {status}")
    else:
        print(f"Sample device {ingress_path}: {status}")

    pub_proc = None
    if args.mode == "push":
        pub_label = args.publisher if args.publish_via_ssh or args.publisher == "emqtt-bench" else "python-local"
        print(
            f"\nPhase [push]: {pub_label} publisher ~{args.messages_per_second} msg/s, "
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
                publisher=args.publisher,
                emqtt_interval_ms=args.emqtt_interval_ms,
            )
        elif args.publisher == "emqtt-bench":
            pub_proc = run_emqtt_bench_local(
                broker_url,
                args.devices,
                args.messages_per_second,
                duration,
                args.emqtt_interval_ms,
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

    if not wait_for_ingress(
        client,
        ingress_path,
        timeout_sec=max(60, args.warmup_seconds + 30),
        driver_path=driver_check_path,
        ingress_history_only=ingress_history_only,
    ):
        label = f"{INGRESS_VARIABLE}.{INGRESS_HISTORY_FIELD}" if ingress_history_only else "temperature"
        print(
            f"  WARN: no live MQTT ingress on {ingress_path} ({label}) "
            f"(driver must be RUNNING/connected; broker reachable from ISPF server?)",
            file=sys.stderr,
        )
    else:
        if ingress_history_only:
            raw = read_last_ingress_raw(client, ingress_path)
            print(f"  ingress OK: {ingress_path} {INGRESS_VARIABLE}.raw={raw!r}")
        else:
            print(f"  ingress OK: {ingress_path} temperature={read_temperature_raw(client, ingress_path)!r}")

    historian_target = None
    if ingress_history_only:
        historian_target = (gateway_path(), INGRESS_VARIABLE, INGRESS_HISTORY_FIELD)
    phase = measure_phase(
        client,
        args.phase_seconds,
        historian_only,
        ingress_history_path=historian_target[0] if historian_target else None,
        ingress_history_variable=historian_target[1] if historian_target else None,
        ingress_history_field=historian_target[2] if historian_target else None,
    )
    phase.update(
        {
            "mode": args.mode,
            "gateway": args.gateway or ingress_history_only,
            "ingressHistoryOnly": ingress_history_only,
            "historianOnly": historian_only,
            "devices": args.devices,
            "brokerUrl": broker_url,
            "topics": subscribe_topics,
            "telemetryMixRatio": args.telemetry_mix_ratio,
            "telemetryCoalesceMs": args.telemetry_coalesce_ms if args.telemetry_coalesce_ms > 0 else None,
            "conditionExpr": condition_expr if not historian_only else None,
            "publisher": args.publisher if args.mode == "push" else None,
            "emqttIntervalMs": args.emqtt_interval_ms if args.publisher == "emqtt-bench" else None,
        }
    )

    if pub_proc is not None:
        pub_timeout = args.phase_seconds + args.warmup_seconds + 120
        if args.publisher == "emqtt-bench":
            pub_timeout += args.warmup_seconds + args.phase_seconds + 10
        pub_out, _ = pub_proc.communicate(timeout=pub_timeout)
        if pub_out:
            for line in pub_out.strip().splitlines()[-5:]:
                print(f"  publisher: {line}")

    if syncer is not None:
        syncer.sync_once()
        syncer.stop()

    label = "MQTT subscribe" if args.mode == "subscribe" else "MQTT push msg/s"
    rate_label = "live feed" if args.mode == "subscribe" else f"{args.messages_per_second:.1f}"

    if historian_only:
        title = "lastIngress historian" if ingress_history_only else "MQTT historian"
        print(
            f"\n{title:>22} {'Samples/s':>10} {'Alert/s':>10} {'Samples':>12}\n"
            f"{rate_label:>22} {phase['historianSamplesPerSecond']:10.1f} "
            f"{phase['alertFiresPerSecond']:10.1f} {phase['endHistorianSamples']:12d}"
        )
        if ingress_history_only and phase.get("historianTarget"):
            target = phase["historianTarget"]
            print(
                f"  target: {target['objectPath']}.{target['variableName']}.{target['fieldName']}"
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
        json.dumps({"phase": phase, "sampleDevice": ingress_path, "driverStatus": status}, indent=2),
        encoding="utf-8",
    )
    print(f"Report: {report_path}")
    if setup_mod is not None:
        print(f"Dashboard: {setup_mod.DASHBOARD_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
