#!/usr/bin/env python3
"""Count history-enabled variables under a path prefix (Enterprise L catalog gate).

Prefer GET /api/v1/platform/analytics/history-enabled-count (SQL). Fallback for older
builds: walk GET /api/v1/objects?parent=… and batch-check variables.

Prints a single integer to stdout. Extra diagnostics go to stderr.
"""

from __future__ import annotations

import argparse
import sys

import requests

DEFAULT_PREFIX = "root.platform.devices.analytics-scale-lab"
BATCH = 50


def login(session: requests.Session, base_url: str, username: str, password: str) -> None:
    response = session.post(
        f"{base_url}/api/v1/auth/login",
        json={"username": username, "password": password},
        timeout=60,
    )
    response.raise_for_status()
    session.headers["Authorization"] = f"Bearer {response.json()['token']}"


def count_via_endpoint(session: requests.Session, base_url: str, prefix: str) -> int | None:
    response = session.get(
        f"{base_url}/api/v1/platform/analytics/history-enabled-count",
        params={"pathPrefix": prefix},
        timeout=120,
    )
    if response.status_code == 404:
        return None
    response.raise_for_status()
    payload = response.json()
    return int(payload["count"])


def list_children(session: requests.Session, base_url: str, parent: str) -> list[dict]:
    response = session.get(
        f"{base_url}/api/v1/objects",
        params={"parent": parent},
        timeout=120,
    )
    if response.status_code == 404:
        return []
    response.raise_for_status()
    data = response.json()
    if isinstance(data, list):
        return data
    return list(data.get("objects") or data.get("items") or [])


def count_via_api_walk(session: requests.Session, base_url: str, prefix: str) -> int:
    children = list_children(session, base_url, prefix)
    paths = []
    for child in children:
        path = child.get("path") or child.get("objectPath")
        if not path:
            name = child.get("name")
            if name:
                path = f"{prefix}.{name}"
        if path:
            paths.append(path)
    if not paths:
        print(f"api-walk: 0 children under {prefix}", file=sys.stderr)
        return 0

    enabled = 0
    for offset in range(0, len(paths), BATCH):
        chunk = paths[offset : offset + BATCH]
        response = session.get(
            f"{base_url}/api/v1/objects/variables/batch",
            params={"paths": ",".join(chunk)},
            timeout=120,
        )
        response.raise_for_status()
        by_path = response.json()
        for path in chunk:
            variables = by_path.get(path) or []
            if any(var.get("historyEnabled") for var in variables):
                enabled += 1
    print(
        f"api-walk: children={len(paths)} history-enabled-objects={enabled}",
        file=sys.stderr,
    )
    return enabled


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--token", default="")
    parser.add_argument("--path-prefix", default=DEFAULT_PREFIX)
    args = parser.parse_args()

    base = args.base_url.rstrip("/")
    session = requests.Session()
    if args.token:
        session.headers["Authorization"] = f"Bearer {args.token}"
    else:
        login(session, base, args.username, args.password)

    count = count_via_endpoint(session, base, args.path_prefix)
    if count is None:
        print(
            "history-enabled-count endpoint missing; falling back to objects API walk",
            file=sys.stderr,
        )
        count = count_via_api_walk(session, base, args.path_prefix)
    else:
        print(f"history-enabled-count endpoint: {count}", file=sys.stderr)

    print(count)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
