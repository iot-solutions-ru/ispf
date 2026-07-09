#!/usr/bin/env python3
"""Create analytics-demo historian chain example on ISPF (ADR-0041).

Usage:
  python deploy/tools/setup-historian-chain-example.py
  python deploy/tools/setup-historian-chain-example.py https://ispf.iot-solutions.ru
"""
from __future__ import annotations

import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone

BASE = sys.argv[1] if len(sys.argv) > 1 else "https://ispf.iot-solutions.ru"
USER = "admin"
PASSWORD = "admin"

DEMO = "root.platform.devices.analytics-demo"
SENSOR = f"{DEMO}.sensor-a"
CHAIN_A = f"{DEMO}.chain-a"
CHAIN_B = f"{DEMO}.chain-b"
CHAIN_C = f"{DEMO}.chain-c"
WINDOW = "5m"


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


def ensure_object(token: str, parent: str, name: str, obj_type: str, display: str, **extra) -> str:
    path = f"{parent}.{name}"
    code, _ = api("GET", f"/api/v1/objects/by-path?path={urllib.parse.quote(path)}", token=token)
    if code == 200:
        print(f"  exists {path}")
        return path
    body = {
        "parentPath": parent,
        "name": name,
        "type": obj_type,
        "displayName": display,
        "description": "Historian chain example (ADR-0041)",
        **extra,
    }
    code, raw = api("POST", "/api/v1/objects", body, token=token)
    if code != 200:
        raise SystemExit(f"create {path} HTTP {code}: {raw[:400]}")
    print(f"  created {path}")
    return path


def enable_history(token: str, path: str, name: str) -> None:
    code, raw = api(
        "PATCH",
        f"/api/v1/objects/by-path/variables/history?path={urllib.parse.quote(path)}&name={urllib.parse.quote(name)}",
        {"historyEnabled": True, "historyRetentionDays": 30},
        token=token,
    )
    if code != 200:
        print(f"  WARN history {path}.{name} HTTP {code}: {raw[:200]}")
    else:
        print(f"  history on {path}.{name}")


def upsert_rules(token: str, device: str, rules: list[dict]) -> None:
    code, raw = api(
        "PUT",
        f"/api/v1/objects/by-path/binding-rules?path={urllib.parse.quote(device)}",
        rules,
        token=token,
    )
    if code != 200:
        raise SystemExit(f"binding-rules {device} HTTP {code}: {raw[:500]}")
    print(f"  rules saved on {device} ({len(rules)} rule(s))")


def rolling_avg_rule(
    rule_id: str,
    source_path: str,
    source_var: str,
    output_var: str,
    order: int,
) -> dict:
    return {
        "id": rule_id,
        "name": rule_id,
        "enabled": True,
        "order": order,
        "kind": "historian",
        "windowBucket": WINDOW,
        "activators": {
            "onStartup": True,
            "onVariableChange": [{"objectPath": source_path, "variableName": source_var}],
            "onEvent": None,
            "periodicMs": 60_000,
            "onFunctionResult": False,
            "onContextEvent": False,
        },
        "condition": "",
        "expression": f"rollingAvg({source_path}.{source_var}, {WINDOW})",
        "target": {"kind": "variable", "variableName": output_var, "field": "value"},
    }


def read_var(token: str, path: str, name: str) -> str | None:
    code, raw = api(
        "GET",
        f"/api/v1/objects/by-path/variables?path={urllib.parse.quote(path)}",
        token=token,
    )
    if code != 200:
        return None
    for row in json.loads(raw):
        if row.get("name") != name:
            continue
        value = row.get("value") or {}
        rows = value.get("rows") or []
        if rows:
            return str(rows[0].get("value"))
    return None


def main() -> None:
    print(f"ISPF historian chain setup @ {BASE}")
    token = login()

    print("1) Objects")
    ensure_object(token, "root.platform.devices", "analytics-demo", "CUSTOM", "Analytics demo")
    ensure_object(
        token,
        DEMO,
        "sensor-a",
        "DEVICE",
        "Sensor A",
        templateId="virtual-lab-v1",
        driverId="virtual",
        driverPollIntervalMs=5000,
        autoStartDriver=True,
    )
    for node in ("chain-a", "chain-b", "chain-c"):
        ensure_object(token, DEMO, node, "DEVICE", node.replace("-", " ").title())

    print("2) Historian on source temperature")
    enable_history(token, SENSOR, "temperature")

    print("3) Binding rules (3-tag chain)")
    upsert_rules(
        token,
        CHAIN_A,
        [rolling_avg_rule("chain-a-rule", SENSOR, "temperature", "derived-a", 10)],
    )
    upsert_rules(
        token,
        CHAIN_B,
        [rolling_avg_rule("chain-b-rule", CHAIN_A, "derived-a", "derived-b", 10)],
    )
    upsert_rules(
        token,
        CHAIN_C,
        [rolling_avg_rule("chain-c-rule", CHAIN_B, "derived-b", "derived-c", 10)],
    )

    print("4) Historian on derived outputs")
    for node, var in (("chain-a", "derived-a"), ("chain-b", "derived-b"), ("chain-c", "derived-c")):
        enable_history(token, f"{DEMO}.{node}", var)

    print("5) Wait for virtual driver samples + analytics tick")
    for _ in range(12):
        temp = read_var(token, SENSOR, "temperature")
        if temp is not None:
            print(f"  sensor-a.temperature = {temp}")
        time.sleep(5)

    for path in (CHAIN_A, CHAIN_B, CHAIN_C):
        code, _ = api(
            "POST",
            f"/api/v1/platform/analytics/derived-tags/refresh?devicePath={urllib.parse.quote(path)}",
            token=token,
        )
        print(f"  refresh {path}: HTTP {code}")

    print("6) Results")
    for path, var, rule in (
        (CHAIN_A, "derived-a", "chain-a-rule"),
        (CHAIN_B, "derived-b", "chain-b-rule"),
        (CHAIN_C, "derived-c", "chain-c-rule"),
    ):
        val = read_var(token, path, var)
        tag = f"{path}#{rule}"
        code, raw = api(
            "GET",
            f"/api/v1/platform/analytics/tags/by-path?path={urllib.parse.quote(tag)}",
            token=token,
        )
        meta = json.loads(raw) if code == 200 else {}
        print(f"  {var}@{path.split('.')[-1]} = {val}")
        if meta:
            print(f"    tag quality={meta.get('quality')} upstream={meta.get('upstreamTagPaths')}")

    now = datetime.now(timezone.utc)
    frm = (now - timedelta(hours=2)).isoformat().replace("+00:00", "Z")
    to = now.isoformat().replace("+00:00", "Z")
    query = {
        "tags": [
            {"path": SENSOR, "variable": "temperature", "field": "value", "label": "raw"},
            {"path": CHAIN_A, "variable": "derived-a", "field": "value", "label": "chain-a"},
            {"path": CHAIN_B, "variable": "derived-b", "field": "value", "label": "chain-b"},
            {"path": CHAIN_C, "variable": "derived-c", "field": "value", "label": "chain-c"},
        ],
        "from": frm,
        "to": to,
        "bucket": WINDOW,
        "agg": "avg",
        "maxBuckets": 24,
    }
    code, raw = api("POST", "/api/v1/platform/analytics/query", query, token=token)
    if code == 200:
        result = json.loads(raw)
        print(f"  multi-query buckets={len(result.get('timestamps', []))} latency={result.get('latencyMs')}ms")
    else:
        print(f"  multi-query HTTP {code}: {raw[:200]}")

    print("Done. Explorer: devices / analytics-demo / chain-a|b|c tab Vychisleniya")
    print("Dashboard: root.platform.dashboards.analytics-demo")


if __name__ == "__main__":
    main()
