#!/usr/bin/env python3
"""Seed 1× mqtt-gateway-v1 + N child sensors (dispatchTelemetry orchestrator)."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from loadtest_cleanup_lib import cleanup_for_mqtt_subscribe_test
from mqtt_loadtest_lib import Client, seed_mqtt_gateway_devices, sensor_path


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--devices", type=int, default=16)
    p.add_argument("--base-url", default="http://127.0.0.1:8000")
    p.add_argument("--broker-url", default="tcp://mqtt:1883")
    p.add_argument("--telemetry-coalesce-ms", type=int, default=1)
    p.add_argument("--poll-ms", type=int, default=5000)
    p.add_argument("--skip-purge", action="store_true")
    p.add_argument("--username", default="admin")
    p.add_argument("--password", default="admin")
    args = p.parse_args()

    if args.devices < 1:
        print("--devices must be >= 1", file=sys.stderr)
        return 2

    client = Client(args.base_url, None, 120.0)
    client.login(args.username, args.password)
    if not args.skip_purge:
        print("Purging prior MQTT loadtest devices under root.platform.devices...")
        stats = cleanup_for_mqtt_subscribe_test(client, purge_mqtt=True)
        print(f"  cleanup: mqtt purged={stats.get('mqttPurged', 0)}")

    gw, sensors = seed_mqtt_gateway_devices(
        client,
        args.devices,
        args.broker_url,
        poll_ms=args.poll_ms,
        telemetry_coalesce_ms=args.telemetry_coalesce_ms,
    )
    print(f"gateway={gw} sensors={len(sensors)} first={sensors[0] if sensors else '-'}")
    print(f"  topic filter: ispf/loadtest/+/temperature")
    print(f"  child example: {sensor_path(1, max(5, len(str(args.devices))))}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
