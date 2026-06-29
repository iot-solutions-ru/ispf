#!/usr/bin/env python3
"""Provision framed serial-over-TCP gauge device (flexible driver) on ISPF."""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

API = os.environ.get("API", "http://127.0.0.1:8080")
PARENT = os.environ.get("GAUGE_PARENT", "root.platform.devices")
DEVICE_NAME = os.environ.get("GAUGE_DEVICE_NAME", "framed-gauge-01")
DEVICE_PATH = f"{PARENT}.{DEVICE_NAME}"
DISPLAY_NAME = os.environ.get("GAUGE_DISPLAY_NAME", "Framed gauge (SOH/ETX)")
TCP_HOST = os.environ.get("GAUGE_TCP_HOST", "192.168.1.50")
TCP_PORT = os.environ.get("GAUGE_TCP_PORT", "10001")
SECURITY_CODE = os.environ.get("GAUGE_SECURITY_CODE", "")
TANKS = [t.strip() for t in os.environ.get("GAUGE_TANKS", "01,02,03,04").split(",") if t.strip()]
POLL_MS = int(os.environ.get("GAUGE_POLL_MS", "30000"))
AUTO_START = os.environ.get("GAUGE_AUTO_START", "true").lower() in ("1", "true", "yes")
ADMIN_USER = os.environ.get("ISPF_ADMIN_USER", "admin")
ADMIN_PASS = os.environ.get("ISPF_ADMIN_PASS", "admin")


def mappings() -> dict[str, str]:
    fields = {
        "volume": 0,
        "tcVolume": 1,
        "ullage": 2,
        "height": 3,
        "water": 4,
        "temperature": 5,
        "waterVolume": 6,
    }
    result: dict[str, str] = {}
    for tank in TANKS:
        req = f"req:\\x01${{securityCode}}i201{tank}"
        for name, index in fields.items():
            result[f"tank{tank}_{name}"] = f"{req}|extract:asciiHexFloat:{index}:after:07"
    return result


def api(method: str, path: str, body=None, token: str | None = None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(f"{API}{path}", data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=60) as resp:
        raw = resp.read().decode("utf-8")
        return json.loads(raw) if raw else {}


def api_status(method: str, path: str, body=None, token: str | None = None) -> tuple[int, str]:
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(f"{API}{path}", data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as ex:
        return ex.code, ex.read().decode("utf-8")


def main() -> int:
    print(f"==> API {API}")
    token = api("POST", "/api/v1/auth/login", {"username": ADMIN_USER, "password": ADMIN_PASS})["token"]
    if not token:
        print("Login failed", file=sys.stderr)
        return 1

    code, body = api_status("GET", f"/api/v1/objects/by-path?path={urllib.parse.quote(DEVICE_PATH)}", token=token)
    if code == 404:
        print(f"==> Creating device {DEVICE_PATH}")
        create_body = {
            "parentPath": PARENT,
            "name": DEVICE_NAME,
            "type": "DEVICE",
            "displayName": DISPLAY_NAME,
            "description": "ASCII SOH/ETX serial protocol over TCP (flexible driver); no checksum on TCP bridge",
            "driverId": "flexible",
            "driverPollIntervalMs": POLL_MS,
            "autoStartDriver": False,
            "autoApplyRelativeModels": False,
        }
        code, body = api_status("POST", "/api/v1/objects", create_body, token=token)
        print(f"create HTTP {code}: {body[:300]}")
        if code not in (200, 201):
            return 1
    else:
        print(f"==> Device exists: {DEVICE_PATH}")

    configure_body = {
        "driverId": "flexible",
        "pollIntervalMs": POLL_MS,
        "configuration": {
            "protocol": "TCP",
            "host": TCP_HOST,
            "port": TCP_PORT,
            "timeoutMs": "5000",
            "encoding": "escapes",
            "readMode": "delimiter",
            "readUntilHex": "03",
            "checksumAlgorithm": "none",
            "securityCode": SECURITY_CODE,
        },
        "pointMappings": mappings(),
        "autoStart": AUTO_START,
    }
    path = f"/api/v1/drivers/runtime/configure?devicePath={urllib.parse.quote(DEVICE_PATH, safe='')}"
    code, body = api_status("PUT", path, configure_body, token=token)
    print(f"configure HTTP {code}: {body[:500]}")
    if code not in (200, 201):
        return 1

    if AUTO_START:
        start_path = f"/api/v1/drivers/runtime/start?devicePath={urllib.parse.quote(DEVICE_PATH, safe='')}"
        code, body = api_status("POST", start_path, token=token)
        print(f"start HTTP {code}: {body[:300]}")

    status_path = f"/api/v1/drivers/runtime/status?devicePath={urllib.parse.quote(DEVICE_PATH, safe='')}"
    print("runtime", api("GET", status_path, token=token))

    vars_path = f"/api/v1/objects/by-path/variables?path={urllib.parse.quote(DEVICE_PATH, safe='')}"
    names = {v.get("name") for v in api("GET", vars_path, token=token)}
    expected = {f"tank{TANKS[0]}_volume", f"tank{TANKS[0]}_height"}
    print("telemetry vars sample present:", expected.issubset(names))
    print(f"==> Done: {DEVICE_PATH} -> {TCP_HOST}:{TCP_PORT} tanks={','.join(TANKS)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
