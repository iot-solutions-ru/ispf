#!/usr/bin/env python3
"""Repair mqtt-sensor structure on existing loadtest devices (403-safe)."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from mqtt_loadtest_lib import (
    Client,
    apply_mqtt_sensor_model,
    device_path,
    ensure_mqtt_sensor_model,
    restart_drivers_until_running,
)


def main() -> int:
    parser = argparse.ArgumentParser(description="Repair loadtest device model structure")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--devices", type=int, default=100)
    parser.add_argument("--batch-size", type=int, default=10)
    args = parser.parse_args()

    pad = max(5, len(str(args.devices)))
    paths = [device_path(i, pad) for i in range(1, args.devices + 1)]

    client = Client(args.base_url, None, 120.0)
    client.login(args.username, args.password)
    model_id = None
    try:
        model_id = ensure_mqtt_sensor_model(client)
    except RuntimeError as error:
        if "403" not in str(error):
            raise
        print("  blueprint API forbidden — applying variables/events directly", flush=True)

    for index, path in enumerate(paths, start=1):
        apply_mqtt_sensor_model(client, path, model_id)
        if index % 10 == 0 or index == len(paths):
            print(f"  repaired {index}/{len(paths)}", flush=True)

    running, total = restart_drivers_until_running(
        client, paths, batch_size=args.batch_size, pause_s=1.5, max_passes=4
    )
    print(f"drivers running: {running}/{total}")
    return 0 if running == total else 1


if __name__ == "__main__":
    raise SystemExit(main())
