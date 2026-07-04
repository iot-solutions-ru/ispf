#!/usr/bin/env python3
"""Single-device 10k historian test with global + per-device coalesceMs=0."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path
from urllib.parse import quote

import requests

sys.path.insert(0, str(Path(__file__).resolve().parent))

from mqtt_loadtest_lib import Client, configure_mqtt_driver, driver_status

BASE = "https://ispf.iot-solutions.ru"
DEVICE = "root.platform.devices.mqtt-device-01"
TOPIC = "ispf/mqtt-device-01/temperature"
BROKER = "tcp://127.0.0.1:1883"
SSH_HOST = "root@ispf.iot-solutions.ru"


def auth_headers(client: Client) -> dict[str, str]:
    return {
        "Authorization": f"Bearer {client.token}",
        "Content-Type": "application/json",
    }


def read_global_coalesce(headers: dict[str, str]) -> str:
    resp = requests.get(f"{BASE}/api/v1/platform/runtime-settings", headers=headers, timeout=60)
    resp.raise_for_status()
    for section in resp.json().get("sections", []):
        for setting in section.get("settings", []):
            if setting.get("id") == "runtime-telemetry.coalesce-ms":
                return str(setting.get("value") or "250")
    return "250"


def patch_global_coalesce(headers: dict[str, str], value: str) -> None:
    resp = requests.patch(
        f"{BASE}/api/v1/platform/runtime-settings",
        headers=headers,
        json={"values": {"runtime-telemetry.coalesce-ms": value}},
        timeout=60,
    )
    resp.raise_for_status()
    print(f"  runtime-telemetry.coalesce-ms = {value} ({resp.json().get('appliedLive')})")


def main() -> int:
    client = Client(BASE, None, 60.0)
    client.login("admin", "admin")
    headers = auth_headers(client)

    old_global = read_global_coalesce(headers)
    print(f"Global coalesce-ms before: {old_global}")

    print("Setting global coalesce-ms to 0 ...")
    patch_global_coalesce(headers, "0")

    print(f"Reconfiguring {DEVICE} (no per-device coalesce override) ...")
    client.request("POST", f"/api/v1/drivers/runtime/stop?devicePath={quote(DEVICE, safe='')}")
    configure_mqtt_driver(
        client,
        DEVICE,
        BROKER,
        TOPIC,
        telemetry_publish_mode="TELEMETRY_ONLY",
        telemetry_coalesce_ms=None,
        poll_ms=5000,
        auto_start=True,
    )
    print(f"  driver: {driver_status(client, DEVICE)}")

    print("Running 10k msg/s historian test on VPS ...")
    proc = subprocess.run(
        [
            "ssh",
            "-o",
            "BatchMode=yes",
            SSH_HOST,
            "RATE=10000 PHASE=60 WARMUP=15 INTERVAL_MS=1 "
            "bash /opt/ispf/loadtest/mqtt-device-historian-test-remote.sh",
        ],
        capture_output=True,
        text=True,
        check=False,
    )
    print(proc.stdout)
    if proc.stderr:
        print(proc.stderr, file=sys.stderr)

    print(f"Restoring global coalesce-ms to {old_global} ...")
    patch_global_coalesce(headers, old_global)

    return proc.returncode


if __name__ == "__main__":
    raise SystemExit(main())
