#!/usr/bin/env python3
"""Demostand CEL formal verify smoke (ADR-0055).

Login → POST /expressions/verify (tautology + unsat) → validate → verify-equivalence.
Writes a JSON report (stdout and optional --out). Does not archive invented results.

  BASE_URL=https://ispf.iot-solutions.ru \\
  ISPF_USERNAME=admin ISPF_PASSWORD=… \\
  python3 deploy/tools/demostand-expressions-verify-smoke.py \\
    --out docs/evidence/cel-formal/YYYY-MM-DD-ispf-vps-<ver>-verify-smoke.json

Default BASE_URL is local; point at demostand explicitly for dated evidence.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone

BASE = os.environ.get("BASE_URL", "http://127.0.0.1:8080").rstrip("/")
USER = os.environ.get("ISPF_USERNAME", "admin")
PASSWORD = os.environ.get("ISPF_PASSWORD", "admin")

# Match dated 0.9.192 evidence expressions where applicable.
TAUTOLOGY = 'true || self.status == "FAULT"'
UNSAT = 'self.temp > 100.0 && self.temp < 50.0'
NORMAL = 'self.status == "FAULT"'
EQ_LEFT = "self.x > 10.0"
EQ_RIGHT = "10.0 < self.x"


def die(msg: str, code: int = 1) -> None:
    print(f"FAIL: {msg}", file=sys.stderr)
    raise SystemExit(code)


def api(
    method: str,
    path: str,
    body: dict | None = None,
    token: str | None = None,
) -> tuple[int, dict | None]:
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read()
            payload = json.loads(raw.decode()) if raw else None
            return resp.status, payload if isinstance(payload, dict) else None
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode(errors="replace")
        try:
            payload = json.loads(detail) if detail else None
        except json.JSONDecodeError:
            payload = {"raw": detail[:500]}
        return exc.code, payload if isinstance(payload, dict) else {"raw": detail[:500]}
    except urllib.error.URLError as exc:
        die(f"{method} {path} → {exc.reason} (is server up at {BASE}?)")


def verification_slice(body: dict | None) -> dict:
    if not body:
        return {}
    v = body.get("verification") if isinstance(body.get("verification"), dict) else body
    if not isinstance(v, dict):
        return {}
    return {
        "ok": body.get("ok", body.get("valid")),
        "codes": v.get("codes") or [],
        "status": v.get("status"),
        "alwaysTrue": v.get("alwaysTrue"),
        "satisfiable": v.get("satisfiable"),
        "equivalent": v.get("equivalent"),
    }


def case(label: str, http: int, body: dict | None) -> dict:
    slice_ = verification_slice(body)
    return {
        "label": label,
        "http": http,
        "ok": slice_.get("ok"),
        "codes": slice_.get("codes") or [],
        "status": slice_.get("status"),
        "alwaysTrue": slice_.get("alwaysTrue"),
        "satisfiable": slice_.get("satisfiable"),
        "equivalent": slice_.get("equivalent"),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--out",
        help="Write JSON report to this path (also printed to stdout)",
    )
    args = parser.parse_args()

    print(f"Expressions verify smoke against {BASE}", file=sys.stderr)

    http, login = api("POST", "/api/v1/auth/login", {"username": USER, "password": PASSWORD})
    if http != 200 or not login or not login.get("token"):
        die(f"login → HTTP {http}: {login!r}")
    token = login["token"]
    print("OK: login", file=sys.stderr)

    http_info, info = api("GET", "/api/v1/info", token=token)
    platform_version = None
    if http_info == 200 and isinstance(info, dict):
        platform_version = info.get("version") or info.get("platformVersion")
        print(f"OK: /api/v1/info version={platform_version}", file=sys.stderr)

    cases: list[dict] = []

    http, body = api("POST", "/api/v1/expressions/verify", {"expression": TAUTOLOGY}, token)
    cases.append(case("tautology-verify", http, body))
    print(f"OK: tautology-verify http={http} ok={cases[-1].get('ok')}", file=sys.stderr)

    http, body = api("POST", "/api/v1/expressions/verify", {"expression": UNSAT}, token)
    cases.append(case("unsat-verify", http, body))
    print(f"OK: unsat-verify http={http} ok={cases[-1].get('ok')}", file=sys.stderr)

    http, body = api("POST", "/api/v1/expressions/validate", {"expression": NORMAL}, token)
    # validate returns valid + verification; map valid → ok for evidence shape
    if body is not None and "ok" not in body and "valid" in body:
        body = {**body, "ok": body.get("valid")}
    cases.append(case("normal-validate", http, body))
    print(f"OK: normal-validate http={http} ok={cases[-1].get('ok')}", file=sys.stderr)

    http, body = api(
        "POST",
        "/api/v1/expressions/verify-equivalence",
        {"left": EQ_LEFT, "right": EQ_RIGHT},
        token,
    )
    cases.append(case("equivalence-verify", http, body))
    print(f"OK: equivalence-verify http={http} ok={cases[-1].get('ok')}", file=sys.stderr)

    report = {
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "source": "demostand-expressions-verify-smoke",
        "baseUrl": BASE,
        "platformVersion": platform_version,
        "cases": cases,
        "note": (
            "Archive only after a live demostand run. Existing dated evidence: "
            "docs/evidence/cel-formal/2026-08-30-ispf-vps-0.9.192-verify-smoke.json; "
            "demostand 0.9.195 available for re-run."
        ),
    }

    text = json.dumps(report, indent=2, ensure_ascii=False) + "\n"
    sys.stdout.write(text)
    if args.out:
        out_path = args.out
        parent = os.path.dirname(out_path)
        if parent:
            os.makedirs(parent, exist_ok=True)
        with open(out_path, "w", encoding="utf-8") as fh:
            fh.write(text)
        print(f"Wrote {out_path}", file=sys.stderr)


if __name__ == "__main__":
    main()
