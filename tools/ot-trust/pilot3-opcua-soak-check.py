#!/usr/bin/env python3
"""BL-140 Pilot #3 — daily OPC UA soak health check (lab loopback or plant).

Checks opcua-server + opcua client runtime status and sample tags.
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

DEFAULT_CLIENT = "root.platform.devices.pilot3-opcua.line"
DEFAULT_SERVER = "root.platform.devices.pilot3-opcua.server"
DEFAULT_SAMPLES = ("lineSpeed", "cellTemp", "tag00", "tag09")


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
    p.add_argument("--client-path", default=os.environ.get("ISPF_PILOT3_CLIENT", DEFAULT_CLIENT))
    p.add_argument("--server-path", default=os.environ.get("ISPF_PILOT3_SERVER", DEFAULT_SERVER))
    p.add_argument("--day", type=int, default=0)
    p.add_argument("--out", default="docs/evidence/ot-trust/pilot3-lab")
    p.add_argument("--sample-tags", default=",".join(DEFAULT_SAMPLES))
    p.add_argument("--min-tags", type=int, default=10)
    args = p.parse_args()
    sample_tags = [t.strip() for t in args.sample_tags.split(",") if t.strip()]

    try:
        token = login_raw(args.base_url, args.user, args.password)
    except Exception as e:
        print(f"FAIL login: {e}", file=sys.stderr)
        return 2

    try:
        try:
            api(args.base_url, token, "POST", f"/api/v1/drivers/runtime/poll?devicePath={args.client_path}")
        except Exception:
            pass
        st_c = api(args.base_url, token, "GET", f"/api/v1/drivers/runtime/status?devicePath={args.client_path}")
        st_s = api(args.base_url, token, "GET", f"/api/v1/drivers/runtime/status?devicePath={args.server_path}")
        variables = api(args.base_url, token, "GET", f"/api/v1/objects/by-path/variables?path={args.client_path}")
    except urllib.error.HTTPError as e:
        print(f"FAIL api HTTP {e.code}: {e.read()[:300]!r}", file=sys.stderr)
        return 3
    except Exception as e:
        print(f"FAIL api: {e}", file=sys.stderr)
        return 3

    samples: dict[str, Any] = {}
    tag_count = 0
    if isinstance(variables, list):
        wanted = set(sample_tags)
        for v in variables:
            name = str(v.get("name") or "")
            if name.startswith("driver") or name in ("status", "timeZone"):
                continue
            tag_count += 1
            if name in wanted:
                rows = (v.get("value") or {}).get("rows") or []
                samples[name] = rows[0] if rows else None

    ok = (
        (st_c or {}).get("status") == "RUNNING"
        and (st_c or {}).get("connected") is True
        and (st_s or {}).get("status") == "RUNNING"
        and tag_count >= args.min_tags
        and all(samples.get(t) is not None for t in sample_tags if t in ("lineSpeed", "cellTemp", "tag00"))
    )

    utc = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    date = time.strftime("%Y-%m-%d", time.gmtime())
    report = {
        "utc": utc,
        "date": date,
        "day": args.day or None,
        "site": "lab-ot-vlan-192.168.100",
        "baseUrl": args.base_url,
        "clientPath": args.client_path,
        "serverPath": args.server_path,
        "clientStatus": st_c,
        "serverStatus": st_s,
        "tagCount": tag_count,
        "samples": samples,
        "pass": ok,
        "incidents": [] if ok else ["driver_not_healthy_or_tags_missing"],
    }
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    day_part = f"day{args.day}-" if args.day else ""
    out_path = out_dir / f"soak-{day_part}{date}.json"
    out_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    (out_dir / "soak-latest.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"pass": ok, "tagCount": tag_count, "client": (st_c or {}).get("status"), "out": str(out_path)}, indent=2))
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
