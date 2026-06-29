#!/usr/bin/env python3
"""Create In-Tank Inventory dashboard for framed-gauge device on ISPF."""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

API = os.environ.get("API", "http://127.0.0.1:8080")
DEVICE_PATH = os.environ.get("GAUGE_DEVICE_PATH", "root.platform.devices.framed-gauge-01")
DASHBOARD_PATH = os.environ.get("GAUGE_DASHBOARD_PATH", "root.platform.dashboards.tank-inventory")
DASHBOARD_NAME = DASHBOARD_PATH.rsplit(".", 1)[-1]
TANKS = [t.strip() for t in os.environ.get("GAUGE_TANKS", "01,02,03,04").split(",") if t.strip()]
VOLUME_MAX = float(os.environ.get("GAUGE_VOLUME_MAX", "15000"))
ADMIN_USER = os.environ.get("ISPF_ADMIN_USER", "admin")
ADMIN_PASS = os.environ.get("ISPF_ADMIN_PASS", "admin")


def api(method: str, path: str, body=None, token: str | None = None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(f"{API}{path}", data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, raw
    except urllib.error.HTTPError as ex:
        return ex.code, ex.read().decode("utf-8")


def build_layout() -> dict:
    widgets: list[dict] = []
    col_w = max(2, 12 // len(TANKS))
    for i, tank in enumerate(TANKS):
        x = i * col_w
        prefix = f"t{tank}"
        widgets.extend([
            {
                "id": f"{prefix}-volume",
                "type": "liquid-gauge",
                "title": f"Tank {int(tank)} Volume",
                "x": x, "y": 0, "w": col_w, "h": 3,
                "objectPath": DEVICE_PATH,
                "variableName": f"tank{tank}_volume",
                "valueField": "value",
                "min": 0,
                "max": VOLUME_MAX,
                "decimals": 0,
                "unit": "gal",
            },
            {
                "id": f"{prefix}-height",
                "type": "value",
                "title": f"T{tank} Height",
                "x": x, "y": 3, "w": col_w, "h": 2,
                "objectPath": DEVICE_PATH,
                "variableName": f"tank{tank}_height",
                "valueField": "value",
                "decimals": 2,
            },
            {
                "id": f"{prefix}-water",
                "type": "value",
                "title": f"T{tank} Water",
                "x": x, "y": 5, "w": col_w, "h": 2,
                "objectPath": DEVICE_PATH,
                "variableName": f"tank{tank}_water",
                "valueField": "value",
                "decimals": 2,
            },
            {
                "id": f"{prefix}-temp",
                "type": "value",
                "title": f"T{tank} Temp",
                "x": x, "y": 7, "w": col_w, "h": 2,
                "objectPath": DEVICE_PATH,
                "variableName": f"tank{tank}_temperature",
                "valueField": "value",
                "decimals": 2,
                "unit": "°F",
            },
            {
                "id": f"{prefix}-tc",
                "type": "value",
                "title": f"T{tank} TC Vol",
                "x": x, "y": 9, "w": col_w, "h": 2,
                "objectPath": DEVICE_PATH,
                "variableName": f"tank{tank}_tcVolume",
                "valueField": "value",
                "decimals": 0,
            },
            {
                "id": f"{prefix}-ullage",
                "type": "value",
                "title": f"T{tank} Ullage",
                "x": x, "y": 11, "w": col_w, "h": 2,
                "objectPath": DEVICE_PATH,
                "variableName": f"tank{tank}_ullage",
                "valueField": "value",
                "decimals": 0,
            },
        ])
    return {"columns": 12, "rowHeight": 72, "widgets": widgets}


def main() -> int:
    print(f"==> API {API}")
    _, raw = api("POST", "/api/v1/auth/login", {"username": ADMIN_USER, "password": ADMIN_PASS})
    token = json.loads(raw).get("token")
    if not token:
        print("Login failed", file=sys.stderr)
        return 1

    code, _ = api("GET", f"/api/v1/objects/by-path?path={urllib.parse.quote(DASHBOARD_PATH)}", token=token)
    if code == 404:
        print(f"==> Creating dashboard {DASHBOARD_PATH}")
        code, body = api(
            "POST",
            "/api/v1/objects",
            {
                "parentPath": "root.platform.dashboards",
                "name": DASHBOARD_NAME,
                "type": "DASHBOARD",
                "displayName": "In-Tank Inventory",
                "description": "Tank levels from framed-gauge device (flexible driver i201)",
                "templateId": "dashboard-v1",
                "autoApplyRelativeModels": False,
            },
            token=token,
        )
        print(f"create HTTP {code}: {body[:200]}")
        if code not in (200, 201):
            return 1
    else:
        print(f"==> Dashboard exists: {DASHBOARD_PATH}")

    layout = build_layout()
    code, body = api(
        "PUT",
        f"/api/v1/dashboards/by-path/layout?path={urllib.parse.quote(DASHBOARD_PATH, safe='')}",
        {"layoutJson": json.dumps(layout, ensure_ascii=False)},
        token=token,
    )
    print(f"layout HTTP {code}")
    if code not in (200, 201):
        print(body[:500], file=sys.stderr)
        return 1

    code, _ = api(
        "PUT",
        f"/api/v1/dashboards/by-path/title?path={urllib.parse.quote(DASHBOARD_PATH, safe='')}",
        {"title": "In-Tank Inventory"},
        token=token,
    )
    print(f"title HTTP {code}")

    code, _ = api(
        "PUT",
        f"/api/v1/objects/by-path/variables?path={urllib.parse.quote(DASHBOARD_PATH, safe='')}&name=refreshIntervalMs",
        {
            "schema": {"name": "refreshIntervalMs", "fields": [{"name": "value", "type": "INTEGER"}]},
            "rows": [{"value": 30000}],
        },
        token=token,
    )
    print(f"refreshIntervalMs HTTP {code} (may skip if model owns var)")

    print(f"==> Open: /dashboards/{DASHBOARD_PATH}")
    print(f"    device: {DEVICE_PATH}, widgets: {len(layout['widgets'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
