#!/usr/bin/env python3
"""After factory reset (--no-fixtures): one MQTT device with variable historian."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from loadtest_cleanup_lib import list_all_device_paths, purge_devices, stop_background_drivers
from mqtt_loadtest_lib import (
    Client,
    driver_status,
    ensure_mqtt_sensor_model,
    seed_one_mqtt_device,
)


def purge_all_devices(client: Client) -> int:
    paths = list_all_device_paths(client)
    stop_background_drivers(client)
    return purge_devices(client, paths)


def main() -> int:
    parser = argparse.ArgumentParser(description="Single MQTT device on clean platform (no fixtures)")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--broker-url", default="tcp://127.0.0.1:1883")
    parser.add_argument("--device-name", default="mqtt-device-01")
    parser.add_argument(
        "--topic",
        default="",
        help="MQTT topic (default: ispf/<device-name>/temperature)",
    )
    parser.add_argument("--telemetry-coalesce-ms", type=int, default=1)
    parser.add_argument("--skip-purge", action="store_true")
    args = parser.parse_args()

    topic = args.topic or f"ispf/{args.device_name}/temperature"
    coalesce = args.telemetry_coalesce_ms if args.telemetry_coalesce_ms > 0 else None

    client = Client(args.base_url, None, 60.0)
    client.login(args.username, args.password)

    if not args.skip_purge:
        print("Removing all devices under root.platform.devices ...")
        n = purge_all_devices(client)
        print(f"  removed {n} device(s)")

    print("Ensuring mqtt-sensor-v1 model ...")
    ensure_mqtt_sensor_model(client)

    print(f"Creating MQTT device {args.device_name} ...")
    path = seed_one_mqtt_device(
        client,
        args.device_name,
        topic,
        args.broker_url,
        telemetry_coalesce_ms=coalesce,
    )
    print(f"  device: {path}")
    print(f"  historian: temperature.value")
    print(f"  driver: {driver_status(client, path)}")
    print(f"  topic: {topic}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
