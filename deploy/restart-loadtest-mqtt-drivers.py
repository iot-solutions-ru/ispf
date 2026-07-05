#!/usr/bin/env python3
"""Restart all loadtest MQTT drivers on lab (no re-seed)."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from mqtt_loadtest_lib import Client, device_path, restart_drivers_until_running


def main() -> int:
    parser = argparse.ArgumentParser(description="Restart loadtest MQTT drivers in batches")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--devices", type=int, default=100)
    parser.add_argument("--batch-size", type=int, default=10)
    parser.add_argument("--pause-s", type=float, default=1.5)
    parser.add_argument("--max-passes", type=int, default=5)
    args = parser.parse_args()

    pad = max(5, len(str(args.devices)))
    paths = [device_path(i, pad) for i in range(1, args.devices + 1)]

    client = Client(args.base_url, None, 120.0)
    client.login(args.username, args.password)
    running, total = restart_drivers_until_running(
        client,
        paths,
        max_passes=args.max_passes,
        batch_size=args.batch_size,
        pause_s=args.pause_s,
    )
    print(f"drivers running: {running}/{total}")
    return 0 if running == total else 1


if __name__ == "__main__":
    raise SystemExit(main())
