#!/usr/bin/env python3
"""Stop ghost mqtt drivers (index > max) even when object node is gone."""
import json
import sys
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:8000"
MAX_INDEX = int(sys.argv[1]) if len(sys.argv) > 1 else 16
PAD = 5

login = urllib.request.Request(
    f"{BASE}/api/v1/auth/login",
    data=json.dumps({"username": "admin", "password": "admin"}).encode(),
    headers={"Content-Type": "application/json"},
    method="POST",
)
TOKEN = json.load(urllib.request.urlopen(login))["token"]
H = {"Authorization": f"Bearer {TOKEN}"}


def stop(path: str) -> str:
    q = urllib.parse.quote(path, safe="")
    req = urllib.request.Request(
        f"{BASE}/api/v1/drivers/runtime/stop?devicePath={q}",
        headers=H,
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = json.load(resp)
            return body.get("status") or "ok"
    except Exception as ex:
        return f"err:{ex}"


stopped = 0
still_running = 0
for i in range(MAX_INDEX + 1, 101):
    path = f"root.platform.devices.loadtest-mqtt-dev-{i:0{PAD}d}"
    result = stop(path)
    if result == "STOPPED" or "STOP" in str(result).upper():
        stopped += 1
    else:
        still_running += 1
        if still_running <= 3:
            print(f"  {path} -> {result}", flush=True)
print(f"stopped={stopped} attempted={100-MAX_INDEX}", flush=True)
