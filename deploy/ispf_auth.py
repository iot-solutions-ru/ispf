#!/usr/bin/env python3
"""Bearer login helpers for ISPF deploy/maintenance scripts."""
from __future__ import annotations

import json
import os
import urllib.error
import urllib.request


def login(
    api: str | None = None,
    username: str | None = None,
    password: str | None = None,
) -> str:
    base = api or os.environ.get("API", "http://127.0.0.1:8080")
    user = username or os.environ.get("ISPF_USER", "admin")
    passwd = password or os.environ.get("ISPF_PASS", "admin")
    payload = json.dumps({"username": user, "password": passwd}).encode()
    req = urllib.request.Request(
        f"{base}/api/v1/auth/login",
        data=payload,
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req) as resp:
        body = json.loads(resp.read().decode())
    token = body.get("token") or ""
    if not token:
        raise RuntimeError(f"login failed for {user} at {base}")
    return token


def auth_headers(token: str | None = None) -> dict[str, str]:
    resolved = token or login()
    return {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {resolved}",
    }
