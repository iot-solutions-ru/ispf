#!/usr/bin/env python3
"""Shared lab host SSH/API helpers. Credentials via ISPF_LAB_* environment variables."""
from __future__ import annotations

import json
import os
import sys
import urllib.request

import paramiko

HOST: str = os.environ.get("ISPF_LAB_HOST", "84.42.21.226")
PORT: int = int(os.environ.get("ISPF_LAB_SSH_PORT", "5031"))
USER: str = os.environ.get("ISPF_LAB_USER", "iot-solutions")
API_BASE: str = os.environ.get("ISPF_LAB_API_BASE", f"http://{HOST}:8000")
ADMIN_USER: str = os.environ.get("ISPF_LAB_ADMIN_USER", "admin")
ADMIN_PASSWORD: str = os.environ.get("ISPF_LAB_ADMIN_PASSWORD", "admin")


def lab_password() -> str:
    password = os.environ.get("ISPF_LAB_PASSWORD")
    if not password:
        print("Set ISPF_LAB_PASSWORD (lab SSH password).", file=sys.stderr)
        sys.exit(2)
    return password


def connect_ssh(timeout: int = 60) -> paramiko.SSHClient:
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, port=PORT, username=USER, password=lab_password(), timeout=timeout)
    return client


def api_token(username: str | None = None, password: str | None = None, base_url: str | None = None) -> str:
    user = username or ADMIN_USER
    pw = password or ADMIN_PASSWORD
    base = (base_url or API_BASE).rstrip("/")
    req = urllib.request.Request(
        f"{base}/api/v1/auth/login",
        data=json.dumps({"username": user, "password": pw}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)["token"]
