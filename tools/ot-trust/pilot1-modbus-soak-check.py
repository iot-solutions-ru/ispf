#!/usr/bin/env python3
"""BL-140 Pilot #1 — daily Modbus soak health check (lab or plant).

Checks driver runtime status + sample tag values via ISPF HTTP API.
Writes a JSON evidence file suitable for the soak journal.

Examples:
  ISPF_BASE_URL=http://192.168.100.10:8080 \\
  ISPF_USER=admin ISPF_PASSWORD=admin \\
  python3 tools/ot-trust/pilot1-modbus-soak-check.py --day 2

  # from lab host (localhost):
  python3 tools/ot-trust/pilot1-modbus-soak-check.py --day 2 --out /home/iot-solutions/ispf/pilot1-modbus/evidence/
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


DEFAULT_DEVICE = "root.platform.devices.pilot1-modbus.plant"
DEFAULT_SAMPLE_TAGS = ("hr00", "hr01", "hr49")


def api(base: str, token: str, method: str, path: str, body: dict | None = None) -> Any:
    url = base.rstrip("/") + path
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        raw = resp.read()
        return json.loads(raw) if raw else None


def login_raw(base: str, user: str, password: str) -> str:
    url = base.rstrip("/") + "/api/v1/auth/login"
    req = urllib.request.Request(
        url,
        data=json.dumps({"username": user, "password": password}).encode(),
        method="POST",
        headers={"Content-Type": "application/json", "Accept": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read())["token"]


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--base-url", default=os.environ.get("ISPF_BASE_URL", "http://127.0.0.1:8080"))
    p.add_argument("--user", default=os.environ.get("ISPF_USER", "admin"))
    p.add_argument("--password", default=os.environ.get("ISPF_PASSWORD", os.environ.get("ISPF_PASS", "admin")))
    p.add_argument("--device-path", default=os.environ.get("ISPF_PILOT1_DEVICE", DEFAULT_DEVICE))
    p.add_argument("--day", type=int, default=0, help="Soak day number for filename (0=timestamp only)")
    p.add_argument("--out", default="docs/evidence/ot-trust/pilot1-lab", help="Output directory")
    p.add_argument("--sample-tags", default=",".join(DEFAULT_SAMPLE_TAGS))
    args = p.parse_args()

    sample_tags = [t.strip() for t in args.sample_tags.split(",") if t.strip()]
    utc = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    date = time.strftime("%Y-%m-%d", time.gmtime())

    try:
        token = login_raw(args.base_url, args.user, args.password)
    except Exception as e:
        print(f"FAIL login: {e}", file=sys.stderr)
        return 2

    try:
        status = api(
            args.base_url,
            token,
            "GET",
            f"/api/v1/drivers/runtime/status?devicePath={args.device_path}",
        )
        poll = api(
            args.base_url,
            token,
            "POST",
            f"/api/v1/drivers/runtime/poll?devicePath={args.device_path}",
        )
        variables = api(
            args.base_url,
            token,
            "GET",
            f"/api/v1/objects/by-path/variables?path={args.device_path}",
        )
    except urllib.error.HTTPError as e:
        print(f"FAIL api HTTP {e.code}: {e.read()[:300]!r}", file=sys.stderr)
        return 3
    except Exception as e:
        print(f"FAIL api: {e}", file=sys.stderr)
        return 3

    samples: dict[str, Any] = {}
    if isinstance(variables, list):
        wanted = set(sample_tags)
        for v in variables:
            name = v.get("name")
            if name in wanted:
                rows = (v.get("value") or {}).get("rows") or []
                samples[name] = rows[0] if rows else None

    hr_count = 0
    if isinstance(variables, list):
        hr_count = sum(1 for v in variables if str(v.get("name", "")).startswith("hr"))

    ok = (
        (status or {}).get("status") == "RUNNING"
        and (status or {}).get("connected") is True
        and hr_count >= 50
        and all(samples.get(t) is not None for t in sample_tags)
    )

    report = {
        "utc": utc,
        "date": date,
        "day": args.day or None,
        "site": "lab-ot-vlan-192.168.100",
        "baseUrl": args.base_url,
        "devicePath": args.device_path,
        "driverStatus": status,
        "poll": poll,
        "hrCount": hr_count,
        "samples": samples,
        "pass": ok,
        "incidents": [] if ok else ["driver_not_healthy_or_tags_missing"],
    }

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    day_part = f"day{args.day}-" if args.day else ""
    out_path = out_dir / f"soak-{day_part}{date}.json"
    out_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    latest = out_dir / "soak-latest.json"
    latest.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    print(json.dumps({"pass": ok, "hrCount": hr_count, "status": (status or {}).get("status"), "out": str(out_path)}, indent=2))
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
