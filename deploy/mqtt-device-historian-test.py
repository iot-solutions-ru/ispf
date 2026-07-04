#!/usr/bin/env python3
"""Historian load test for one existing MQTT device (custom topic)."""

from __future__ import annotations

import argparse
import json
import math
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from mqtt_loadtest_lib import (
    Client,
    driver_status,
    read_temperature_raw,
    variable_history_field_sample_count,
    variable_history_metrics,
)


def run_publisher_ssh(
    remote_host: str,
    remote_dir: str,
    broker_url: str,
    topic: str,
    messages_per_second: float,
    duration_seconds: float,
) -> subprocess.Popen[str]:
    remote_cmd = (
        f"cd {remote_dir} && python3 -c "
        f"\"import time,math; import paho.mqtt.client as mqtt; "
        f"host,port='{broker_url.replace('tcp://', '')}'.rsplit(':',1); "
        f"port=int(port) if len('{broker_url}') else 1883; "
        f"c=mqtt.Client(); c.connect(host,port,30); c.loop_start(); "
        f"interval=1.0/max({messages_per_second},0.001); start=time.perf_counter(); sent=0; "
        f"topic='{topic}'; "
        f"exec('while time.perf_counter()-start<{duration_seconds}:\\n "
        f" v=22+5*math.sin(sent*0.15); c.publish(topic,f\\\"{{v:.3f}}\\\",qos=0); sent+=1; time.sleep(interval)'); "
        f"c.loop_stop(); c.disconnect(); print(f'sent={{sent}}')\""
    )
    # Use dedicated publisher script instead of fragile inline Python
    remote_cmd = (
        f"{remote_dir}/venv/bin/python {remote_dir}/mqtt-topic-publisher.py "
        f"--broker {broker_url} --topic {topic} "
        f"--messages-per-second {messages_per_second} "
        f"--duration-seconds {duration_seconds}"
    )
    return subprocess.Popen(
        ["ssh", "-o", "BatchMode=yes", remote_host, remote_cmd],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )


def run_publisher_local(
    broker_url: str,
    topic: str,
    messages_per_second: float,
    duration_seconds: float,
) -> subprocess.Popen[str]:
    script = Path(__file__).with_name("mqtt-topic-publisher.py")
    cmd = [
        sys.executable,
        str(script),
        "--broker",
        broker_url,
        "--topic",
        topic,
        "--messages-per-second",
        str(messages_per_second),
        "--duration-seconds",
        str(duration_seconds),
    ]
    return subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)


def postgres_sample_count_ssh(
    remote_host: str,
    device_path: str,
    field_name: str,
    *,
    since_seconds: int | None = None,
) -> int:
    path_sql = device_path.replace("'", "''")
    where = f"object_path='{path_sql}' AND field_name='{field_name}'"
    if since_seconds is not None:
        where += f" AND sampled_at >= NOW() - INTERVAL '{since_seconds} seconds'"
    sql = f"SELECT COUNT(*) FROM variable_samples WHERE {where};"
    cmd = [
        "ssh",
        "-o",
        "BatchMode=yes",
        remote_host,
        f"docker exec ispf-postgres psql -U ispf -d ispf -t -A -c \"{sql}\"",
    ]
    out = subprocess.check_output(cmd, text=True).strip()
    return int(out or 0)


def measure_historian(
    client: Client,
    device_path: str,
    variable_name: str,
    field_name: str,
    duration_sec: float,
    remote_host: str = "",
) -> dict:
    t0 = time.perf_counter()
    if remote_host:
        baseline = postgres_sample_count_ssh(remote_host, device_path, field_name)
    else:
        baseline = variable_history_field_sample_count(
            client, device_path, variable_name, field_name
        )
    time.sleep(duration_sec)
    time.sleep(3.0)
    elapsed = time.perf_counter() - t0
    if remote_host:
        total = postgres_sample_count_ssh(remote_host, device_path, field_name)
        delta = max(0, total - baseline)
    else:
        total = variable_history_field_sample_count(
            client, device_path, variable_name, field_name
        )
        delta = max(0, total - baseline)
    return {
        "durationSec": round(elapsed, 1),
        "historianSamplesGenerated": delta,
        "historianSamplesPerSecond": round(delta / max(elapsed, 0.001), 2),
        "endHistorianSamples": total,
        "historianMinIntervalMs": variable_history_metrics(client).get("minIntervalMs"),
        "target": {
            "objectPath": device_path,
            "variableName": variable_name,
            "fieldName": field_name,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Single MQTT device historian test")
    parser.add_argument("--base-url", default="https://ispf.iot-solutions.ru")
    parser.add_argument("--host-header", default="")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--device-path", default="root.platform.devices.mqtt-device-01")
    parser.add_argument("--topic", default="ispf/mqtt-device-01/temperature")
    parser.add_argument("--broker-url", default="tcp://127.0.0.1:1883")
    parser.add_argument("--variable", default="temperature")
    parser.add_argument("--field", default="raw", help="Historian field (mqtt driver writes raw)")
    parser.add_argument("--messages-per-second", type=float, default=500.0)
    parser.add_argument("--phase-seconds", type=int, default=60)
    parser.add_argument("--warmup-seconds", type=int, default=15)
    parser.add_argument("--publish-via-ssh", default="root@ispf.iot-solutions.ru")
    parser.add_argument("--remote-deploy-dir", default="/opt/ispf/loadtest")
    parser.add_argument("--timeout", type=float, default=60.0)
    args = parser.parse_args()

    client = Client(args.base_url, args.host_header or None, args.timeout)
    client.login(args.username, args.password)

    status = driver_status(client, args.device_path)
    print(f"Device: {args.device_path}")
    print(f"Driver: {status}")
    print(f"Topic:  {args.topic}")
    if not status or status.get("status") != "RUNNING":
        print("ERROR: MQTT driver not running", file=sys.stderr)
        return 1

    duration = args.warmup_seconds + args.phase_seconds + 5
    pub_proc = run_publisher_ssh(
        args.publish_via_ssh,
        args.remote_deploy_dir,
        args.broker_url,
        args.topic,
        args.messages_per_second,
        duration,
    )

    print(f"\nWarmup {args.warmup_seconds}s, measure {args.phase_seconds}s @ ~{args.messages_per_second} msg/s")
    time.sleep(args.warmup_seconds)
    temp = read_temperature_raw(client, args.device_path)
    print(f"  live temperature.raw={temp!r}")

    phase = measure_historian(
        client,
        args.device_path,
        args.variable,
        args.field,
        args.phase_seconds,
        remote_host=args.publish_via_ssh,
    )
    phase.update(
        {
            "devicePath": args.device_path,
            "topic": args.topic,
            "messagesPerSecondTarget": args.messages_per_second,
            "brokerUrl": args.broker_url,
        }
    )

    pub_out, _ = pub_proc.communicate(timeout=duration + 120)
    if pub_out:
        for line in pub_out.strip().splitlines()[-3:]:
            print(f"  publisher: {line}")

    expected = args.messages_per_second * args.phase_seconds
    efficiency = 100.0 * phase["historianSamplesGenerated"] / max(expected, 1)
    print(
        f"\n{'Target msg/s':>18} {'Samples/s':>12} {'Samples':>10} {'Efficiency':>12}\n"
        f"{args.messages_per_second:>18.1f} "
        f"{phase['historianSamplesPerSecond']:>12.1f} "
        f"{phase['historianSamplesGenerated']:>10d} "
        f"{efficiency:>11.1f}%"
    )
    print(f"  historian target: {args.device_path}.{args.variable}.{args.field}")
    if phase.get("historianMinIntervalMs"):
        print(f"  historian min-interval-ms: {phase['historianMinIntervalMs']}")

    report_path = Path(__file__).with_name(f"mqtt-device-historian-test-report-{int(time.time())}.json")
    report_path.write_text(json.dumps({"phase": phase, "driverStatus": status}, indent=2), encoding="utf-8")
    print(f"Report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
