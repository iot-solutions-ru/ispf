#!/usr/bin/env python3
"""Count meter instances under root.platform.instances after a burst publish."""
import json
import subprocess
import sys
import time
import urllib.request

API = "http://127.0.0.1:8080"
PARENT = "root.platform.instances"
PREFIX = "meter-"


def api(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f"{API}{path}", data=data, headers=headers, method=method)
    with urllib.request.urlopen(req) as resp:
        return json.load(resp)


def main() -> int:
    token = api("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin"})["token"]
    print("==> Publishing one burst round (10000 sensors)")
    subprocess.run(
        [sys.executable, "/opt/ispf/meter-sine-simulator.py", "--once", "--count", "10000"],
        check=True,
    )
    for wait in (5, 15, 30, 60):
        time.sleep(wait)
        children = api("GET", f"/api/v1/objects/by-path/children?path={PARENT}", token=token)
        meter_children = [c for c in children if c.get("name", "").startswith(PREFIX)]
        print(f"after {wait}s total wait: {len(meter_children)} instances with prefix {PREFIX}")
        if len(meter_children) >= 10000:
            print("OK: all instances created")
            return 0
    print(f"FAIL: expected 10000 instances, got {len(meter_children)}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
