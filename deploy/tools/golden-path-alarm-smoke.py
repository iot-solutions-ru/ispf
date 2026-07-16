#!/usr/bin/env python3
"""Wave 4 R1 — golden path: fire alert → journal → acknowledgeAlarm.

Requires a running ISPF with fixtures (demo-sensor-01) or equivalent stand.

  python deploy/tools/golden-path-alarm-smoke.py
  BASE_URL=https://host ISPF_USERNAME=admin ISPF_PASSWORD=... python deploy/tools/golden-path-alarm-smoke.py
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("BASE_URL", "http://127.0.0.1:8080").rstrip("/")
USER = os.environ.get("ISPF_USERNAME", "admin")
PASSWORD = os.environ.get("ISPF_PASSWORD", "admin")
DEVICE = os.environ.get(
    "ISPF_GOLDEN_DEVICE", "root.platform.devices.demo-sensor-01"
)
EVENT = os.environ.get("ISPF_GOLDEN_EVENT", "thresholdExceeded")
ACK_FN = os.environ.get("ISPF_GOLDEN_ACK_FUNCTION", "acknowledgeAlarm")
ACK_VAR = os.environ.get("ISPF_GOLDEN_ACK_VARIABLE", "alarmAcknowledged")


def die(msg: str, code: int = 1) -> None:
    print(f"FAIL: {msg}", file=sys.stderr)
    raise SystemExit(code)


def ok(msg: str) -> None:
    print(f"OK: {msg}")


def api(method: str, path: str, body: dict | None = None, token: str | None = None):
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read()
            if not raw:
                return None
            return json.loads(raw.decode())
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode(errors="replace")
        die(f"{method} {path} → HTTP {exc.code}: {detail[:500]}")
    except urllib.error.URLError as exc:
        die(f"{method} {path} → {exc.reason} (is server up at {BASE}?)")


def main() -> None:
    print(f"Golden path smoke against {BASE}")
    print(f"  device={DEVICE} event={EVENT} ack={ACK_FN}")

    login = api("POST", "/api/v1/auth/login", {"username": USER, "password": PASSWORD})
    if not login or not login.get("token"):
        die("login returned no token")
    token = login["token"]
    ok("login")

    enc_path = urllib.parse.quote(DEVICE, safe="")
    fired = api(
        "POST",
        f"/api/v1/events/fire?objectPath={enc_path}&eventName={urllib.parse.quote(EVENT)}",
        token=token,
    )
    if not fired or fired.get("eventName") != EVENT:
        die(f"fire response missing eventName={EVENT}: {fired!r}")
    fire_id = fired.get("id")
    ok(f"fire {EVENT}" + (f" id={fire_id}" if fire_id else ""))

    events = api(
        "GET",
        f"/api/v1/events?objectPath={enc_path}&limit=50",
        token=token,
    )
    if not isinstance(events, list):
        die(f"events list expected array, got {type(events).__name__}")
    match = next((e for e in events if e.get("eventName") == EVENT), None)
    if fire_id:
        match = next((e for e in events if e.get("id") == fire_id), match)
    if not match:
        die(f"journal has no {EVENT} for {DEVICE} (got {len(events)} events)")
    ok(f"journal contains {EVENT}")

    ack = api(
        "POST",
        f"/api/v1/objects/by-path/functions/invoke?path={enc_path}&name={urllib.parse.quote(ACK_FN)}",
        token=token,
    )
    rows = (ack or {}).get("rows") or []
    success = rows[0].get("success") if rows else None
    if success is not True:
        die(f"{ACK_FN} did not return success=true: {ack!r}")
    ok(f"invoke {ACK_FN}")

    detail = api(
        "GET",
        f"/api/v1/objects/by-path/variables/detail?path={enc_path}&name={urllib.parse.quote(ACK_VAR)}",
        token=token,
    )
    value = detail.get("value") if isinstance(detail, dict) else None
    # value may be bool or DataRecord-shaped
    acknowledged = value is True
    if isinstance(value, dict):
        rows = value.get("rows") or []
        if rows:
            cell = rows[0].get("value", rows[0])
            acknowledged = cell is True or cell == "true" or cell == 1
    if not acknowledged:
        die(f"{ACK_VAR} not true after ack (value={value!r})")
    ok(f"{ACK_VAR}=true")

    print("PASS: golden path alarm → journal → ack")


if __name__ == "__main__":
    main()
