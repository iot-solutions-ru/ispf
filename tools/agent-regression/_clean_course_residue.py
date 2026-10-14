#!/usr/bin/env python3
"""Delete prior AggreGate-course residue on it-demo before a fresh run."""
from __future__ import annotations

import json
import os
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

BASE = "https://it-demo.iot-solutions.ru"
TEMP = Path(os.environ["TEMP"])

PREFIXES = (
    "root.platform.devices.virt-device-",
    "root.platform.devices.virt-dev-",
    "root.platform.dashboards.virtual-devices-",
    "root.platform.dashboards.virt-dev-",
    "root.platform.dashboards.event-form-",
    "root.platform.dashboards.grid-layout-",
    "root.platform.dashboards.svg-",
    "root.platform.dashboards.button-widget",
    "root.platform.dashboards.event-generator-widget",
    "root.platform.dashboards.sine-history-widget",
    "root.platform.mimics.virtual-devices-",
    "root.platform.mimics.fan-control-",
    "root.platform.alert-rules.event1-",
    "root.platform.alert-rules.sum-delayed-",
    "root.platform.alert-rules.table-int-",
    "root.platform.alert-rules.event-triggered-",
    "root.platform.event-filters.event2-",
    "root.platform.event-filters.event-filter-",
    "root.platform.reports.virt-devices-",
    "root.platform.reports.table-variables-",
    "root.platform.queries.virt-device-",
    "root.platform.security.users.user-one",
    "root.platform.security.users.user-two",
    "root.platform.security.users.user1",
    "root.platform.security.users.user2",
    "root.platform.calculator-hub",
)


def http(method: str, path: str, body: dict | None = None, token: str | None = None):
    data = None if body is None else json.dumps(body).encode()
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read().decode()
            return r.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode(errors="replace")
        try:
            payload = json.loads(raw) if raw else {}
        except Exception:
            payload = {"raw": raw[:200]}
        return e.code, payload


def main() -> int:
    tok_path = TEMP / "ispf-course-token.txt"
    if tok_path.exists():
        token = tok_path.read_text(encoding="utf-8").strip()
    else:
        _, login = http("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin"})
        token = login["token"]
        tok_path.write_text(token, encoding="utf-8")

    code, objs = http("GET", "/api/v1/objects?path=root.platform&depth=3", token=token)
    if code != 200 or not isinstance(objs, list):
        print("list_failed", code, objs)
        return 1
    targets = []
    for o in objs:
        path = str(o.get("path") or "")
        if any(path.startswith(p) or path == p.rstrip("-") for p in PREFIXES):
            targets.append(path)
    targets = sorted(set(targets), key=len, reverse=True)
    print(f"targets={len(targets)}")
    for path in targets:
        q = urllib.parse.quote(path, safe="")
        # try common delete endpoints
        for ep in (
            f"/api/v1/objects/by-path?path={q}",
            f"/api/v1/objects?path={q}",
        ):
            status, body = http("DELETE", ep, token=token)
            if status in (200, 204):
                print("DELETED", path)
                break
            if status == 404:
                continue
        else:
            # agent tool fallback
            status, body = http(
                "POST",
                "/api/v1/ai/tools/execute",
                {"tool": "delete_object", "arguments": {"path": path}},
                token,
            )
            print("TOOL", path, status, str(body)[:120])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
