#!/usr/bin/env python3
"""Seed history-enabled virtual devices for analytics scale lab (BL-210)."""

from __future__ import annotations

import argparse
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

import requests

FOLDER = "analytics-scale-lab"
PARENT = f"root.platform.devices.{FOLDER}"
HISTORY_VARIABLE = "temperature"


def delete_object(session: requests.Session, base_url: str, path: str) -> None:
    response = session.delete(f"{base_url}/api/v1/objects/by-path", params={"path": path}, timeout=120)
    if response.status_code >= 400 and response.status_code not in (404,):
        raise RuntimeError(f"delete {path}: HTTP {response.status_code} {response.text[:200]}")


def purge_folder(session: requests.Session, base_url: str) -> None:
    existing = session.get(f"{base_url}/api/v1/objects/by-path", params={"path": PARENT}, timeout=60)
    if existing.status_code == 404:
        return
    if existing.status_code != 200:
        raise RuntimeError(f"lookup {PARENT}: HTTP {existing.status_code}")
    delete_object(session, base_url, PARENT)
    print(f"  purged {PARENT}", flush=True)


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
        },
        timeout=60,
    )
    if response.status_code >= 400 and response.status_code != 409:
        raise RuntimeError(f"create {path}: HTTP {response.status_code} {response.text[:200]}")
    return path


def wait_object_visible(session: requests.Session, base_url: str, path: str) -> None:
    for attempt in range(24):
        existing = session.get(f"{base_url}/api/v1/objects/by-path", params={"path": path}, timeout=60)
        if existing.status_code == 200:
            return
        time.sleep(0.2 + attempt * 0.1)
    raise RuntimeError(f"object not visible: {path}")


def enable_history(session: requests.Session, base_url: str, path: str) -> None:
    schema = {
        "name": HISTORY_VARIABLE,
        "fields": [{"name": "value", "type": "DOUBLE"}],
    }
    initial_value = {"schema": schema, "rows": [{"value": 0.0}]}
    created = session.post(
        f"{base_url}/api/v1/objects/by-path/variables",
        params={"path": path},
        json={
            "name": HISTORY_VARIABLE,
            "schema": schema,
            "readable": True,
            "writable": False,
            "initialValue": initial_value,
            "historyEnabled": True,
        },
        timeout=60,
    )
    if created.status_code < 400:
        return
    if created.status_code not in (409,) and "already exists" not in created.text.lower():
        raise RuntimeError(
            f"create variable {path}.{HISTORY_VARIABLE}: HTTP {created.status_code} {created.text[:200]}"
        )

    for attempt in range(12):
        enabled = session.patch(
            f"{base_url}/api/v1/objects/by-path/variables/history",
            params={"path": path, "name": HISTORY_VARIABLE},
            json={"historyEnabled": True},
            timeout=60,
        )
        if enabled.status_code < 400:
            return
        if "unknown variable" in enabled.text.lower() and attempt < 11:
            time.sleep(0.3 + attempt * 0.1)
            continue
        raise RuntimeError(
            f"enable history {path}.{HISTORY_VARIABLE}: HTTP {enabled.status_code} {enabled.text[:200]}"
        )


def worker_session(base_url: str, token: str) -> requests.Session:
    session = requests.Session()
    session.headers.update({"Authorization": f"Bearer {token}"})
    return session


def seed_one(
    base_url: str,
    token: str,
    parent: str,
    name: str,
    display: str,
) -> str:
    session = worker_session(base_url, token)
    last_error: Exception | None = None
    for attempt in range(12):
        try:
            path = ensure_object(session, base_url, parent, name, "DEVICE", display)
            wait_object_visible(session, base_url, path)
            enable_history(session, base_url, path)
            return path
        except RuntimeError as exc:
            last_error = exc
            message = str(exc).lower()
            if "not found" in message or "http 404" in message or "http 409" in message:
                time.sleep(0.2 + attempt * 0.1)
                continue
            raise
    raise RuntimeError(f"seed {parent}.{name} failed after retries: {last_error}") from last_error


def seed_tags(
    session: requests.Session,
    base_url: str,
    tag_count: int,
    *,
    batch: int,
    start_index: int = 1,
    fresh: bool = False,
    workers: int = 12,
) -> None:
    if fresh and start_index <= 1:
        purge_folder(session, base_url)
    ensure_object(session, base_url, "root.platform.devices", FOLDER, "CUSTOM", FOLDER)
    pad = max(5, len(str(tag_count)))
    token = session.headers["Authorization"].removeprefix("Bearer ").strip()
    completed = 0
    errors: list[str] = []

    def submit_range(executor: ThreadPoolExecutor, start: int, end: int) -> list:
        futures = []
        for index in range(start, end + 1):
            name = f"tag-{index:0{pad}d}"
            futures.append(executor.submit(seed_one, base_url, token, PARENT, name, name))
        return futures

    with ThreadPoolExecutor(max_workers=max(1, workers)) as executor:
        chunk_start = start_index
        while chunk_start <= tag_count:
            chunk_end = min(chunk_start + batch - 1, tag_count)
            futures = submit_range(executor, chunk_start, chunk_end)
            for future in as_completed(futures):
                try:
                    future.result()
                    completed += 1
                except Exception as exc:  # noqa: BLE001
                    errors.append(str(exc))
                    if len(errors) >= 5:
                        raise RuntimeError(f"seed failed ({len(errors)} errors): {errors[0]}") from exc
            print(f"  seeded {chunk_end}/{tag_count}", flush=True)
            chunk_start = chunk_end + 1

    if errors:
        raise RuntimeError(f"seed finished with {len(errors)} errors: {errors[0]}")


def login(session: requests.Session, base_url: str, username: str, password: str) -> str:
    response = session.post(
        f"{base_url}/api/v1/auth/login",
        json={"username": username, "password": password},
        timeout=60,
    )
    response.raise_for_status()
    token = response.json()["token"]
    session.headers.update({"Authorization": f"Bearer {token}"})
    return token


def main() -> int:
    parser = argparse.ArgumentParser(description="Seed analytics scale catalog devices")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--tags", type=int, default=1000, help="Device count (Enterprise L gate: 50000)")
    parser.add_argument("--batch", type=int, default=200)
    parser.add_argument("--workers", type=int, default=12, help="Parallel HTTP workers per batch")
    parser.add_argument("--start-index", type=int, default=1, help="Resume from tag index (1-based)")
    parser.add_argument("--fresh", action="store_true", help="Delete analytics-scale-lab folder before seeding")
    args = parser.parse_args()

    session = requests.Session()
    login(session, args.base_url, args.username, args.password)
    print(
        f"Seeding {args.tags} history-enabled devices under {PARENT} "
        f"(workers={args.workers}, batch={args.batch}, start={args.start_index}) …",
        flush=True,
    )
    seed_tags(
        session,
        args.base_url,
        args.tags,
        batch=args.batch,
        start_index=args.start_index,
        fresh=args.fresh,
        workers=args.workers,
    )
    print("Done. Run analytics-scale-gate.sh with ISPF_ANALYTICS_BENCH_SKIP_CATALOG_GATE=false")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
