#!/usr/bin/env python3
"""Seed N MQTT loadtest devices with EVENT_JOURNAL_ONLY (internal event journal path)."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from mqtt_loadtest_lib import (
    Client,
    configure_mqtt_driver,
    device_path,
    driver_status,
    ensure_device,
    mqtt_bench_ingress_configuration,
    mqtt_topic,
    restart_drivers_until_running,
    start_drivers_in_batches,
)


def seed_event_journal_devices(
    client: Client,
    device_count: int,
    broker_url: str,
    *,
    event_name: str = "messageReceived",
    telemetry_coalesce_ms: int | None = 1,
    poll_ms: int = 5000,
    bench_no_l0_coalesce: bool = True,
    callback_threads: int | None = None,
    callback_queue_capacity: int | None = None,
    shared_topic: str | None = None,
) -> list[tuple[str, str]]:
    pad = max(5, len(str(device_count)))
    seeded: list[tuple[str, str]] = []
    ingress_cfg = mqtt_bench_ingress_configuration(
        no_l0_coalesce=bench_no_l0_coalesce,
        callback_threads=callback_threads,
        callback_queue_capacity=callback_queue_capacity,
    )
    driver_cfg = {"ingressEventName": event_name, **ingress_cfg}

    for index in range(1, device_count + 1):
        path = ensure_device(client, index, pad)
        topic = shared_topic if shared_topic else mqtt_topic(index, pad)
        configure_mqtt_driver(
            client,
            path,
            broker_url,
            topic,
            telemetry_publish_mode="EVENT_JOURNAL_ONLY",
            telemetry_coalesce_ms=telemetry_coalesce_ms,
            poll_ms=poll_ms,
            auto_start=False,
            configuration=driver_cfg,
        )
        seeded.append((path, topic))
        if index % 10 == 0 or index == device_count:
            print(f"  configured {index}/{device_count}: {path} -> {topic!r}")

    return seeded


def main() -> int:
    parser = argparse.ArgumentParser(description="MQTT devices with EVENT_JOURNAL_ONLY policy")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--broker-url", default="tcp://127.0.0.1:1883")
    parser.add_argument("--devices", type=int, default=8)
    parser.add_argument("--event-name", default="messageReceived")
    parser.add_argument("--telemetry-coalesce-ms", type=int, default=1)
    parser.add_argument(
        "--bench-no-l0-coalesce",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Disable MQTT driver L0 last-value-wins coalesce (default: true for load tests)",
    )
    parser.add_argument("--callback-threads", type=int, default=None)
    parser.add_argument("--callback-queue-capacity", type=int, default=None)
    parser.add_argument(
        "--shared-topic",
        default=None,
        help="All devices subscribe to one MQTT topic (broker fan-out → N journal writes per publish)",
    )
    parser.add_argument("--start-batch-size", type=int, default=10)
    parser.add_argument("--start-batch-pause-s", type=float, default=1.0)
    args = parser.parse_args()

    coalesce = args.telemetry_coalesce_ms if args.telemetry_coalesce_ms > 0 else None
    client = Client(args.base_url, None, 120.0)
    client.login(args.username, args.password)

    mode = "ingressCoalesceEnabled=false" if args.bench_no_l0_coalesce else "L0 coalesce on"
    topic_mode = f"shared-topic={args.shared_topic!r}" if args.shared_topic else "one-topic-per-device"
    print(
        f"  seeding {args.devices} devices (EVENT_JOURNAL_ONLY, event={args.event_name}, "
        f"{mode}, {topic_mode})"
    )
    paths = seed_event_journal_devices(
        client,
        args.devices,
        args.broker_url,
        event_name=args.event_name,
        telemetry_coalesce_ms=coalesce,
        bench_no_l0_coalesce=args.bench_no_l0_coalesce,
        callback_threads=args.callback_threads,
        callback_queue_capacity=args.callback_queue_capacity,
        shared_topic=args.shared_topic,
    )
    pad = max(5, len(str(args.devices)))
    all_paths = [device_path(i, pad) for i in range(1, args.devices + 1)]
    for path, topic in paths[:3]:
        print(f"  sample: {path} topic={topic}")
    if len(paths) > 3:
        print(f"  ... and {len(paths) - 3} more")
    print(f"  starting drivers in batches of {args.start_batch_size} ...")
    start_drivers_in_batches(
        client,
        all_paths,
        batch_size=args.start_batch_size,
        pause_s=args.start_batch_pause_s,
    )
    running, total = restart_drivers_until_running(
        client,
        all_paths,
        batch_size=args.start_batch_size,
        pause_s=max(args.start_batch_pause_s, 1.5),
    )
    print(f"  drivers running after restart: {running}/{total}")
    return 0 if running == total else 1


if __name__ == "__main__":
    raise SystemExit(main())
