#!/usr/bin/env python3
"""Delete and recreate one MQTT event-journal loadtest device on ISPF."""
from __future__ import annotations

import argparse
import importlib.util
import sys
from pathlib import Path

DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(DIR))

from mqtt_loadtest_lib import Client, delete_device, driver_status

_spec = importlib.util.spec_from_file_location(
    "setup_mqtt_event_journal_devices",
    DIR / "setup-mqtt-event-journal-devices.py",
)
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
seed_event_journal_devices = _mod.seed_event_journal_devices

DEFAULT_DEVICE = "loadtest-mqtt-dev-00001"
DEFAULT_TOPIC = "ispf/loadtest/00001/temperature"


def main() -> int:
    parser = argparse.ArgumentParser(description="Recreate ISPF MQTT event-journal device")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--broker-url", default="tcp://127.0.0.1:1883")
    parser.add_argument("--device-name", default=DEFAULT_DEVICE)
    parser.add_argument("--topic", default=DEFAULT_TOPIC)
    args = parser.parse_args()

    path = f"root.platform.devices.{args.device_name}"
    client = Client(args.base_url, None, 120.0)
    client.login(args.username, args.password)

    print(f"Deleting existing device (if any): {path}")
    try:
        delete_device(client, path)
        print("  deleted")
    except Exception as exc:  # noqa: BLE001
        print(f"  skip delete: {exc}")

    print(f"Creating fresh device: {path} topic={args.topic!r}")
    seeded = seed_event_journal_devices(
        client,
        1,
        args.broker_url,
        event_name="messageReceived",
        telemetry_coalesce_ms=1,
        bench_no_l0_coalesce=True,
    )
    for p, topic in seeded:
        print(f"  device: {p}")
        print(f"  topic:  {topic}")
        print(f"  driver: {driver_status(client, p)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
