#!/usr/bin/env python3
"""Seed history-enabled virtual devices for analytics scale lab (BL-210)."""

from __future__ import annotations

import argparse

import requests

FOLDER = "analytics-scale-lab"
PARENT = f"root.platform.devices.{FOLDER}"
HISTORY_VARIABLE = "temperature"
VIRTUAL_MODEL = "virtual-lab-v1"


def ensure_object(session: requests.Session, base_url: str, parent: str, name: str, obj_type: str, display: str) -> str:
    path = f"{parent}.{name}"
    existing = session.get(f"{base_url}/api/v1/objects/by-path", params={"path": path}, timeout=60)
    if existing.status_code == 200:
        return path
    response = session.post(
        f"{base_url}/api/v1/objects",
        json={
            "parentPath": parent,
            "name": name,
            "type": obj_type,
            "displayName": display,
            "description": "BL-210 analytics scale lab",
            "templateId": VIRTUAL_MODEL if obj_type == "DEVICE" else None,
        },
        timeout=60,
    )
    if response.status_code >= 400 and response.status_code != 409:
        raise RuntimeError(f"create {path}: HTTP {response.status_code} {response.text[:200]}")
    return path


def enable_history(session: requests.Session, base_url: str, path: str) -> None:
    variables = session.get(
        f"{base_url}/api/v1/objects/by-path/variables",
        params={"path": path},
        timeout=60,
    )
    if variables.status_code != 200:
        raise RuntimeError(f"list variables {path}: HTTP {variables.status_code}")
    names = {item["name"] for item in variables.json()}
    if HISTORY_VARIABLE not in names:
        session.post(
            f"{base_url}/api/v1/objects/by-path/variables",
            params={"path": path},
            json={
                "name": HISTORY_VARIABLE,
                "schemaName": "temperature",
                "historyEnabled": True,
                "fields": [{"name": "value", "type": "DOUBLE"}],
                "defaultValue": {"value": 0.0},
            },
            timeout=60,
        )
    session.put(
        f"{base_url}/api/v1/objects/by-path/variables/history",
        params={"path": path, "name": HISTORY_VARIABLE},
        json={"enabled": True},
        timeout=60,
    )


def seed_tags(session: requests.Session, base_url: str, tag_count: int, *, batch: int) -> None:
    ensure_object(session, base_url, "root.platform.devices", FOLDER, "CUSTOM", FOLDER)
    pad = max(5, len(str(tag_count)))
    for index in range(1, tag_count + 1):
        name = f"tag-{index:0{pad}d}"
        path = ensure_object(session, base_url, PARENT, name, "DEVICE", name)
        enable_history(session, base_url, path)
        if index % batch == 0 or index == tag_count:
            print(f"  seeded {index}/{tag_count}: {path}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Seed analytics scale catalog devices")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--tags", type=int, default=1000, help="Device count (Enterprise L gate: 50000)")
    parser.add_argument("--batch", type=int, default=100)
    args = parser.parse_args()

    session = requests.Session()
    session.auth = (args.username, args.password)
    print(f"Seeding {args.tags} history-enabled devices under {PARENT} …")
    seed_tags(session, args.base_url, args.tags, batch=args.batch)
    print("Done. Run analytics-scale-gate.sh with ISPF_ANALYTICS_BENCH_SKIP_CATALOG_GATE=false")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
