#!/usr/bin/env python3
"""Summarize sustained verify from live metrics + scylla count."""
import json
import re
import subprocess
import urllib.request

BASE = "http://127.0.0.1:8080"
DEVICE = "root.platform.devices.loadtest-mqtt-dev-00001"
DURATION = 65

# Parse before values from log if present
log = "/opt/ispf/loadtest/ispf-sustained-verify.log"
before_fired = before_device = None
try:
    text = open(log, encoding="utf-8", errors="replace").read()
    m = re.search(r"eventsFiredTotal=(\d+)", text)
    if m:
        # first occurrence in 'before' section - find block after 'metrics before'
        idx = text.find("--- metrics before ---")
        block = text[idx : idx + 800] if idx >= 0 else text
        bf = re.search(r"eventsFiredTotal=(\d+)", block)
        bd = re.search(r"scylla_device=(\d+)", block)
        if bf:
            before_fired = int(bf.group(1))
        if bd:
            before_device = int(bd.group(1))
except OSError:
    pass

req = urllib.request.Request(
    f"{BASE}/api/v1/auth/login",
    data=json.dumps({"username": "admin", "password": "admin"}).encode(),
    headers={"Content-Type": "application/json"},
    method="POST",
)
with urllib.request.urlopen(req, timeout=30) as r:
    token = json.load(r)["token"]

def get_metrics():
    req = urllib.request.Request(
        f"{BASE}/api/v1/platform/metrics",
        headers={"Authorization": f"Bearer {token}"},
    )
    with urllib.request.urlopen(req, timeout=30) as r:
        data = json.load(r)
    for section in data.get("sections", []):
        if section.get("id") == "automation":
            return section.get("values", {})
    return {}

def scylla_device_count():
    out = subprocess.check_output(
        [
            "docker", "exec", "ispf-scylla", "cqlsh", "-e",
            f"SELECT COUNT(*) FROM ispf.event_history WHERE object_path='{DEVICE}';",
        ],
        text=True,
        stderr=subprocess.DEVNULL,
    )
    m = re.search(r"\n\s*(\d+)\s*\n", out)
    return int(m.group(1)) if m else 0

metrics = get_metrics()
after_fired = int(metrics.get("eventsFiredTotal") or 0)
after_device = scylla_device_count()
queue = int(metrics.get("eventJournalQueueSize") or 0)
flushed = int(metrics.get("eventJournalFlushedTotal") or 0)

print("=== Sustained verify summary (fresh device) ===")
if before_fired is not None:
    df = after_fired - before_fired
    print(f"eventsFired: {before_fired} -> {after_fired}  delta={df}  rate={df/DURATION:.1f}/s")
if before_device is not None:
    dd = after_device - before_device
    print(f"scylla_device: {before_device} -> {after_device}  delta={dd}  rate={dd/DURATION:.1f}/s")
print(f"queue={queue} flushed={flushed} fired={after_fired}")
print(f"Previous ISPF fair test (no metrics fix): ~313/s scylla, ~312/s")
