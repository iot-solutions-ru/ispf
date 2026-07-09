#!/usr/bin/env python3
"""Configure analytics-demo dashboard for historian 3-tag chain (ADR-0041).

Usage:
  python deploy/tools/setup-historian-chain-dashboard.py
  python deploy/tools/setup-historian-chain-dashboard.py https://ispf.iot-solutions.ru
"""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "https://ispf.iot-solutions.ru"
USER = "admin"
PASSWORD = "admin"

DEMO = "root.platform.devices.analytics-demo"
DASHBOARD = "root.platform.dashboards.analytics-demo"
SENSOR = f"{DEMO}.sensor-a"
CHAIN_A = f"{DEMO}.chain-a"
CHAIN_B = f"{DEMO}.chain-b"
CHAIN_C = f"{DEMO}.chain-c"

ANALYTICS_TAGS = [
    {"path": SENSOR, "variable": "temperature", "field": "value", "label": "raw"},
    {"path": CHAIN_A, "variable": "derived-a", "field": "value", "label": "chain-a"},
    {"path": CHAIN_B, "variable": "derived-b", "field": "value", "label": "chain-b"},
    {"path": CHAIN_C, "variable": "derived-c", "field": "value", "label": "chain-c"},
]


def build_layout() -> dict:
    return {
        "columns": 84,
        "rowHeight": 8,
        "widgets": [
            {
                "id": "chain-caption",
                "type": "label",
                "title": "",
                "x": 0,
                "y": 0,
                "w": 84,
                "h": 7,
                "text": (
                    "Historian chain (ADR-0041): sensor-a.temperature "
                    "-> chain-a.derived-a -> chain-b.derived-b -> chain-c.derived-c "
                    "(rollingAvg 5m)"
                ),
            },
            {
                "id": "raw-live",
                "type": "value",
                "title": "Raw temperature",
                "x": 0,
                "y": 7,
                "w": 21,
                "h": 14,
                "objectPath": SENSOR,
                "variableName": "temperature",
                "valueField": "value",
                "unitField": "unit",
                "decimals": 1,
            },
            {
                "id": "chain-a-live",
                "type": "value",
                "title": "chain-a derived-a",
                "x": 21,
                "y": 7,
                "w": 21,
                "h": 14,
                "objectPath": CHAIN_A,
                "variableName": "derived-a",
                "valueField": "value",
                "decimals": 2,
            },
            {
                "id": "chain-b-live",
                "type": "value",
                "title": "chain-b derived-b",
                "x": 42,
                "y": 7,
                "w": 21,
                "h": 14,
                "objectPath": CHAIN_B,
                "variableName": "derived-b",
                "valueField": "value",
                "decimals": 2,
            },
            {
                "id": "chain-c-live",
                "type": "value",
                "title": "chain-c derived-c",
                "x": 63,
                "y": 7,
                "w": 21,
                "h": 14,
                "objectPath": CHAIN_C,
                "variableName": "derived-c",
                "valueField": "value",
                "decimals": 2,
            },
            {
                "id": "chain-multi-chart",
                "type": "chart",
                "title": "Historian chain — multi-tag query",
                "x": 0,
                "y": 21,
                "w": 84,
                "h": 35,
                "chartStyle": "line",
                "historyRange": "6h",
                "maxPoints": 72,
                "decimals": 2,
                "unit": "C",
                "analyticsQueryTagsJson": json.dumps(ANALYTICS_TAGS, ensure_ascii=False),
            },
            {
                "id": "raw-trend",
                "type": "chart",
                "title": "sensor-a temperature (historian)",
                "x": 0,
                "y": 56,
                "w": 42,
                "h": 28,
                "objectPath": SENSOR,
                "variableName": "temperature",
                "valueField": "value",
                "unitField": "unit",
                "chartStyle": "area",
                "historyRange": "live",
                "maxPoints": 120,
                "decimals": 1,
                "color": "#2f81f7",
            },
            {
                "id": "final-trend",
                "type": "chart",
                "title": "chain-c derived-c (historian)",
                "x": 42,
                "y": 56,
                "w": 42,
                "h": 28,
                "objectPath": CHAIN_C,
                "variableName": "derived-c",
                "valueField": "value",
                "chartStyle": "area",
                "historyRange": "live",
                "maxPoints": 120,
                "decimals": 2,
                "color": "#27ae60",
            },
        ],
    }


def api(method: str, path: str, body=None, token: str | None = None) -> tuple[int, str]:
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=45) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode()


def login() -> str:
    code, raw = api("POST", "/api/v1/auth/login", {"username": USER, "password": PASSWORD})
    if code != 200:
        raise SystemExit(f"login failed HTTP {code}: {raw[:300]}")
    return json.loads(raw)["token"]


def ensure_dashboard(token: str) -> None:
    code, _ = api("GET", f"/api/v1/objects/by-path?path={urllib.parse.quote(DASHBOARD)}", token=token)
    if code == 200:
        return
    body = {
        "parentPath": "root.platform.dashboards",
        "name": "analytics-demo",
        "type": "DASHBOARD",
        "displayName": "Analytics historian chain",
        "description": "Historian 3-tag chain demo (ADR-0041)",
    }
    code, raw = api("POST", "/api/v1/objects", body, token=token)
    if code != 200:
        raise SystemExit(f"create dashboard HTTP {code}: {raw[:400]}")


def main() -> None:
    print(f"Dashboard setup @ {BASE}")
    token = login()
    ensure_dashboard(token)

    layout = build_layout()
    layout_json = json.dumps(layout, ensure_ascii=False)

    code, raw = api(
        "PUT",
        f"/api/v1/dashboards/by-path/title?path={urllib.parse.quote(DASHBOARD)}",
        {"title": "Analytics historian chain"},
        token=token,
    )
    print("title", code)

    code, raw = api(
        "PUT",
        f"/api/v1/dashboards/by-path/layout?path={urllib.parse.quote(DASHBOARD)}",
        {"layoutJson": layout_json},
        token=token,
    )
    if code != 200:
        raise SystemExit(f"layout HTTP {code}: {raw[:500]}")
    print(f"layout saved ({len(layout['widgets'])} widgets)")
    print(f"Open dashboard: {DASHBOARD}")


if __name__ == "__main__":
    main()
