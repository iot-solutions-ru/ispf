#!/usr/bin/env python3
"""Login, create agent session in execute mode, send AggreGate course prompt."""
from __future__ import annotations

import json
import pathlib
import sys
import urllib.request

BASE = "https://it-demo.iot-solutions.ru"
TEMP = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else pathlib.Path.home() / "AppData/Local/Temp")
PROMPT = pathlib.Path(__file__).with_name("course-run-prompt.txt").read_text(encoding="utf-8")


def http_json(method: str, path: str, body: dict | None = None, token: str | None = None) -> tuple[int, dict]:
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{BASE}{path}", data=data, method=method, headers=headers)
    with urllib.request.urlopen(req, timeout=120) as r:
        raw = r.read().decode("utf-8")
        return r.status, json.loads(raw) if raw else {}


def main() -> int:
    _, login = http_json("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin"})
    token = login["token"]
    (TEMP / "ispf-course-token.txt").write_text(token, encoding="utf-8")
    # execute mode = explicit mutate consent (no plan gate)
    _, sess = http_json(
        "POST",
        "/api/v1/ai/agent/sessions",
        {"rootPath": "root", "interactionMode": "execute"},
        token,
    )
    sid = sess.get("sessionId") or sess.get("id")
    (TEMP / "ispf-course-session.txt").write_text(sid, encoding="utf-8")
    print(f"session={sid}", flush=True)
    status, accepted = http_json(
        "POST",
        f"/api/v1/ai/agent/sessions/{sid}/messages?async=true",
        {
            "message": PROMPT,
            "rootPath": "root",
            "interactionMode": "execute",
            "uiLocale": "ru",
            "clientChannel": "studio",
        },
        token,
    )
    print(f"message_http={status} body={accepted}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
