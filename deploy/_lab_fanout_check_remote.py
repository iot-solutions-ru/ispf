#!/usr/bin/env python3
"""Verify shared-topic fan-out: one publish -> N journal events."""
import json
import time
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:8000"
TOPIC = "ispf/loadtest/shared/temperature"

login = urllib.request.Request(
    f"{BASE}/api/v1/auth/login",
    data=json.dumps({"username": "admin", "password": "admin"}).encode(),
    headers={"Content-Type": "application/json"},
    method="POST",
)
TOKEN = json.load(urllib.request.urlopen(login))["token"]
H = {"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"}


def metrics():
    req = urllib.request.Request(f"{BASE}/api/v1/platform/metrics", headers=H)
    data = json.load(urllib.request.urlopen(req))
    out = {}
    for s in data.get("sections", []):
        if s.get("id") in ("automation", "eventJournal"):
            out[s["id"]] = s.get("values") or {}
    return out


def publish_once():
    import subprocess

    subprocess.run(
        [
            "docker",
            "exec",
            "ispf-lab-mqtt-1",
            "mosquitto_pub",
            "-h",
            "localhost",
            "-t",
            TOPIC,
            "-m",
            '{"value":42.0,"ts":"2026-07-05T01:00:00Z"}',
        ],
        check=True,
        capture_output=True,
    )


before = metrics()
print("before", before, flush=True)
publish_once()
time.sleep(2)
after = metrics()
print("after", after, flush=True)
bj = int(before.get("eventJournal", {}).get("eventsFired") or 0)
aj = int(after.get("eventJournal", {}).get("eventsFired") or 0)
print(f"delta eventsFired={aj - bj}", flush=True)
