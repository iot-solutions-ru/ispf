#!/usr/bin/env python3
"""BL-140 Pilot #2 — bootstrap MQTT fleet device on lab ISPF.

Creates (if missing) device object, configures mqtt driver, adds topic-mapped
variables, starts driver. Does **not** start Mosquitto — bring broker up first.

Examples (on lab host):
  ISPF_BASE_URL=http://127.0.0.1:8080 \\
    python3 tools/ot-trust/pilot2-mqtt-lab-bootstrap.py \\
      --broker-url tcp://172.17.0.1:1883

  # dry-run plan only:
  python3 tools/ot-trust/pilot2-mqtt-lab-bootstrap.py --dry-run
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


DEFAULT_PARENT = "root.platform.devices.pilot2-mqtt"
DEFAULT_DEVICE = "root.platform.devices.pilot2-mqtt.fleet"
DEFAULT_BROKER = "tcp://192.168.100.10:1883"
DEFAULT_PREFIX = ""


def api(base: str, token: str, method: str, path: str, body: Any = None) -> Any:
    url = base.rstrip("/") + path
    data = None if body is None else json.dumps(body).encode()
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers=headers,
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        raw = resp.read()
        return json.loads(raw) if raw else None


def login_raw(base: str, user: str, password: str) -> str:
    url = base.rstrip("/") + "/api/v1/auth/login"
    req = urllib.request.Request(
        url,
        data=json.dumps({"username": user, "password": password}).encode(),
        method="POST",
        headers={"Content-Type": "application/json", "Accept": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read())["token"]


def exists(base: str, token: str, path: str) -> bool:
    try:
        api(base, token, "GET", f"/api/v1/objects/by-path?path={path}")
        return True
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return False
        raise


def ensure_folder(base: str, token: str, path: str, display: str) -> None:
    if exists(base, token, path):
        return
    parent = path.rsplit(".", 1)[0]
    name = path.rsplit(".", 1)[1]
    api(
        base,
        token,
        "POST",
        f"/api/v1/objects/by-path/children?path={parent}",
        {
            "name": name,
            "displayName": display,
            "type": "FOLDER",
            "templateId": None,
        },
    )


def ensure_device(base: str, token: str, path: str, display: str) -> None:
    if exists(base, token, path):
        return
    parent = path.rsplit(".", 1)[0]
    name = path.rsplit(".", 1)[1]
    # Prefer DEVICE template when available; fall back to generic object create.
    body = {
        "name": name,
        "displayName": display,
        "type": "DEVICE",
        "templateId": "device-v1",
    }
    try:
        api(base, token, "POST", f"/api/v1/objects/by-path/children?path={parent}", body)
    except urllib.error.HTTPError:
        body["templateId"] = None
        api(base, token, "POST", f"/api/v1/objects/by-path/children?path={parent}", body)


def configure_mqtt(
    base: str,
    token: str,
    device: str,
    broker_url: str,
    topic_prefix: str,
    points: dict[str, str],
) -> Any:
    # API expects pointMappings as Record<string,string> (not an array / not "points").
    # pollIntervalMs must be >0 so the runtime scheduler can call readPoints (subscribe).
    payload = {
        "driverId": "mqtt",
        "configuration": {
            "brokerUrl": broker_url,
            "topicPrefix": topic_prefix,
        },
        "pointMappings": points,
        "pollIntervalMs": 1000,
        "autoStart": True,
    }
    return api(
        base,
        token,
        "PUT",
        f"/api/v1/drivers/runtime/configure?devicePath={device}",
        payload,
    )


def stop(base: str, token: str, device: str) -> Any:
    return api(base, token, "POST", f"/api/v1/drivers/runtime/stop?devicePath={device}")


def start(base: str, token: str, device: str) -> Any:
    return api(base, token, "POST", f"/api/v1/drivers/runtime/start?devicePath={device}")

def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--base-url", default=os.environ.get("ISPF_BASE_URL", "http://127.0.0.1:8080"))
    p.add_argument("--user", default=os.environ.get("ISPF_USER", "admin"))
    p.add_argument("--password", default=os.environ.get("ISPF_PASSWORD", os.environ.get("ISPF_PASS", "admin")))
    p.add_argument("--parent-path", default=DEFAULT_PARENT)
    p.add_argument("--device-path", default=DEFAULT_DEVICE)
    p.add_argument(
        "--broker-url",
        default=os.environ.get("ISPF_MQTT_BROKER_URL", "tcp://192.168.100.10:1883"),
        help="From ISPF container on this lab use tcp://192.168.100.10:1883 (not 172.17.0.1)",
    )
    p.add_argument("--topic-prefix", default=DEFAULT_PREFIX)
    p.add_argument("--count", type=int, default=10, help="Number of fleet topics/variables")
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--out", default="docs/evidence/ot-trust/pilot2-lab/bootstrap-plan.json")
    args = p.parse_args()

    points = {
        f"dev{i:02d}": f"ispf/pilot2/fleet/dev{i:02d}/telemetry" for i in range(args.count)
    }
    plan = {
        "utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "baseUrl": args.base_url,
        "parentPath": args.parent_path,
        "devicePath": args.device_path,
        "brokerUrl": args.broker_url,
        "topicPrefix": args.topic_prefix,
        "points": points,
        "dryRun": args.dry_run,
    }

    if args.dry_run:
        out = Path(args.out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(plan, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"ok": True, "dryRun": True, "out": str(out), "pointCount": len(points)}, indent=2))
        return 0

    try:
        token = login_raw(args.base_url, args.user, args.password)
    except Exception as e:
        print(f"FAIL login: {e}", file=sys.stderr)
        return 2

    try:
        # Parent may be DEVICE (lab API rejects FOLDER); nested DEVICE still works.
        ensure_device(args.base_url, token, args.parent_path, "Pilot2 MQTT")
        ensure_device(args.base_url, token, args.device_path, "Pilot2 MQTT fleet")
        status = configure_mqtt(
            args.base_url, token, args.device_path, args.broker_url, args.topic_prefix, points
        )
        # Configure persists binding only — restart so ActiveDriver reloads mappings / subscribe.
        try:
            stop(args.base_url, token, args.device_path)
        except Exception:
            pass
        started = start(args.base_url, token, args.device_path)
    except urllib.error.HTTPError as e:
        print(f"FAIL api HTTP {e.code}: {e.read()[:500]!r}", file=sys.stderr)
        return 3
    except Exception as e:
        print(f"FAIL: {e}", file=sys.stderr)
        return 3

    report = {**plan, "configure": status, "start": started}
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"ok": True, "status": (started or {}).get("status"), "out": str(out)}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
