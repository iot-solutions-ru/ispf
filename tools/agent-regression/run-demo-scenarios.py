#!/usr/bin/env python3
"""LLM demo runner: AI Studio execute session per platform-* scenario on it-demo.

Reads tools/agent-regression/scenarios/platform-*.json, starts a session per scenario,
polls until finished, checks acceptance.requiredObjectPaths + requiredTools presence.

  python tools/agent-regression/run-demo-scenarios.py --max 5
  python tools/agent-regression/run-demo-scenarios.py --ids platform-virtual-devices,platform-mimic-pack
  python tools/agent-regression/run-demo-scenarios.py --tags object:device --max 0

Results: build/agent-regression/demo-scenarios-results.json
Soft report: LLM fails do not always mean platform broken — use det smoke as hard gate.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SCENARIOS_DIR = Path(__file__).resolve().parent / "scenarios"
OUT_DEFAULT = REPO / "build" / "agent-regression" / "demo-scenarios-results.json"


def http(
    base: str,
    method: str,
    path: str,
    body: dict | None = None,
    token: str | None = None,
    timeout: int = 120,
) -> tuple[int, dict | list | str]:
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{base.rstrip('/')}{path}", data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read().decode("utf-8")
            if not raw:
                return r.status, {}
            try:
                return r.status, json.loads(raw)
            except json.JSONDecodeError:
                return r.status, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            return e.code, {"raw": raw[:800]}


def qpath(path: str) -> str:
    return urllib.parse.quote(path, safe="")


def load_scenarios(ids: set[str] | None, tags: set[str] | None) -> list[dict]:
    files = sorted(SCENARIOS_DIR.glob("platform-*.json"))
    out: list[dict] = []
    for f in files:
        doc = json.loads(f.read_text(encoding="utf-8"))
        if doc.get("domain") != "platform" or doc.get("lane") not in (None, "agent", "both"):
            continue
        if ids and doc.get("id") not in ids:
            continue
        doc_tags = set(doc.get("tags") or [])
        if tags and not (doc_tags & tags):
            continue
        out.append(doc)
    return out


def object_exists(base: str, token: str, path: str) -> bool:
    code, _ = http(base, "GET", f"/api/v1/objects/by-path?path={qpath(path)}", token=token)
    return code == 200


def wipe_prefix(base: str, token: str, prefixes: list[str]) -> int:
    """Delete children whose leaf name starts with any prefix under common folders."""
    folders = [
        "root.platform.devices",
        "root.platform.security.users",
        "root.platform.alert-rules",
        "root.platform.correlators",
        "root.platform.event-filters",
        "root.platform.singleton-blueprints",
        "root.platform.dashboards",
        "root.platform.mimics",
        "root.platform.reports",
        "root.platform.queries",
        "root.platform.operator-apps",
        "root.platform.schedules",
        "root.platform.workflows",
    ]
    deleted = 0
    for folder in folders:
        code, kids = http(base, "GET", f"/api/v1/objects?path={qpath(folder)}&depth=1", token=token)
        rows: list = []
        if isinstance(kids, dict):
            rows = kids.get("objects") or kids.get("children") or kids.get("rows") or []
        elif isinstance(kids, list):
            rows = kids
        for row in rows if isinstance(rows, list) else []:
            if not isinstance(row, dict):
                continue
            p = str(row.get("path") or "")
            leaf = p.rsplit(".", 1)[-1]
            if any(leaf.startswith(pref) for pref in prefixes):
                http(base, "DELETE", f"/api/v1/objects/by-path?path={qpath(p)}", token=token)
                deleted += 1
    return deleted


def start_session(base: str, token: str, prompt: str) -> str:
    code, sess = http(
        base,
        "POST",
        "/api/v1/ai/agent/sessions",
        {"rootPath": "root", "interactionMode": "execute"},
        token,
    )
    if code not in (200, 201) or not isinstance(sess, dict):
        raise RuntimeError(f"create session failed: {code} {sess}")
    sid = str(sess.get("sessionId") or sess.get("id") or "")
    if not sid:
        raise RuntimeError(f"no session id: {sess}")
    code2, accepted = http(
        base,
        "POST",
        f"/api/v1/ai/agent/sessions/{sid}/messages?async=true",
        {
            "message": prompt,
            "rootPath": "root",
            "interactionMode": "execute",
            "uiLocale": "en",
            "clientChannel": "studio",
        },
        token,
    )
    if code2 not in (200, 202):
        raise RuntimeError(f"message rejected: {code2} {accepted}")
    return sid


def poll_session(
    base: str,
    token: str,
    sid: str,
    *,
    max_ticks: int = 80,
    sleep_s: float = 15.0,
) -> dict:
    last_n = 0
    for i in range(max_ticks):
        try:
            code, progress = http(base, "GET", f"/api/v1/ai/agent/sessions/{sid}/progress", token=token)
        except Exception as exc:  # noqa: BLE001
            print(f"  poll_err {exc}", flush=True)
            time.sleep(sleep_s)
            continue
        if code != 200 or not isinstance(progress, dict):
            time.sleep(sleep_s)
            continue
        steps = progress.get("steps") or []
        running = progress.get("running")
        for step in steps[last_n:]:
            if isinstance(step, dict):
                tool = step.get("tool") or step.get("type") or "?"
                res = step.get("result") or {}
                status = res.get("status") if isinstance(res, dict) else ""
                line = f"  {tool} {status}".encode("ascii", "replace").decode("ascii")
                print(line, flush=True)
        last_n = len(steps)
        print(f"  --- tick={i} steps={len(steps)} running={running}", flush=True)
        if not running and i > 0:
            code, sess = http(base, "GET", f"/api/v1/ai/agent/sessions/{sid}", token=token)
            return sess if isinstance(sess, dict) else {"raw": sess}
        time.sleep(sleep_s)
    raise TimeoutError(f"session {sid} still running after {max_ticks} ticks")


def tools_used(sess: dict) -> set[str]:
    found: set[str] = set()
    for turn in sess.get("turns") or []:
        if not isinstance(turn, dict):
            continue
        for step in turn.get("steps") or []:
            if isinstance(step, dict) and step.get("tool"):
                found.add(str(step["tool"]))
    return found


def evaluate(base: str, token: str, scenario: dict, sess: dict) -> dict:
    acceptance = scenario.get("acceptance") or {}
    required_paths = list(acceptance.get("requiredObjectPaths") or [])
    required_tools = list(acceptance.get("requiredTools") or [])
    used = tools_used(sess)
    path_results = {p: object_exists(base, token, p) for p in required_paths}
    missing_paths = [p for p, ok in path_results.items() if not ok]
    # Soft: at least one required tool seen; hard for objects
    tools_hit = [t for t in required_tools if t in used]
    tools_miss = [t for t in required_tools if t not in used]
    paths_ok = len(missing_paths) == 0
    # Pass if all required paths exist (primary). Tool presence is reported.
    status = "pass" if paths_ok else "fail"
    turns = sess.get("turns") or []
    last_status = None
    if turns and isinstance(turns[-1], dict):
        last_status = turns[-1].get("status")
    return {
        "status": status,
        "pathsOk": paths_ok,
        "missingPaths": missing_paths,
        "pathResults": path_results,
        "toolsUsed": sorted(used),
        "toolsHit": tools_hit,
        "toolsMiss": tools_miss,
        "lastTurnStatus": last_status,
        "turnCount": len(turns),
    }


def run_one(base: str, token: str, scenario: dict, poll_ticks: int, sleep_s: float) -> dict:
    sid = start_session(base, token, scenario["prompt"])
    print(f"session={sid} id={scenario['id']}", flush=True)
    try:
        sess = poll_session(base, token, sid, max_ticks=poll_ticks, sleep_s=sleep_s)
    except TimeoutError as exc:
        return {
            "id": scenario["id"],
            "sessionId": sid,
            "status": "fail",
            "error": str(exc),
        }
    verdict = evaluate(base, token, scenario, sess)
    return {
        "id": scenario["id"],
        "sessionId": sid,
        "title": scenario.get("title"),
        **verdict,
    }


def main() -> int:
    ap = argparse.ArgumentParser(description="LLM platform demo scenario runner")
    ap.add_argument("--base-url", default="https://it-demo.iot-solutions.ru")
    ap.add_argument("--user", default="admin")
    ap.add_argument("--password", default="admin")
    ap.add_argument("--max", type=int, default=5, help="Max scenarios (0 = all)")
    ap.add_argument("--ids", default="", help="Comma-separated scenario ids")
    ap.add_argument("--tags", default="", help="Comma-separated tags (OR match)")
    ap.add_argument("--wipe-prefix", default="demo-", help="Leaf name prefix to wipe before run")
    ap.add_argument("--no-wipe", action="store_true")
    ap.add_argument("--poll-ticks", type=int, default=80)
    ap.add_argument("--sleep", type=float, default=15.0)
    ap.add_argument("--out", default=str(OUT_DEFAULT))
    args = ap.parse_args()

    ids = {x.strip() for x in args.ids.split(",") if x.strip()} or None
    tags = {x.strip() for x in args.tags.split(",") if x.strip()} or None
    scenarios = load_scenarios(ids, tags)
    if args.max and args.max > 0:
        scenarios = scenarios[: args.max]
    if not scenarios:
        print("no scenarios matched", flush=True)
        return 1

    code, login = http(args.base_url, "POST", "/api/v1/auth/login", {"username": args.user, "password": args.password})
    if code != 200 or not isinstance(login, dict) or not login.get("token"):
        print(f"login failed: {code} {login}", flush=True)
        return 1
    token = str(login["token"])

    if not args.no_wipe and args.wipe_prefix:
        n = wipe_prefix(args.base_url, token, [args.wipe_prefix, "demo-smoke-"])
        print(f"wiped {n} objects matching prefix {args.wipe_prefix!r}", flush=True)

    results: list[dict] = []
    for sc in scenarios:
        print(f"\n=== {sc['id']}: {sc.get('title')} ===", flush=True)
        try:
            row = run_one(args.base_url, token, sc, args.poll_ticks, args.sleep)
        except Exception as exc:  # noqa: BLE001
            row = {"id": sc["id"], "status": "fail", "error": str(exc)}
        results.append(row)
        print(f"RESULT {row['id']} => {row.get('status')} missing={row.get('missingPaths')}", flush=True)

    passed = sum(1 for r in results if r.get("status") == "pass")
    failed = len(results) - passed
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "baseUrl": args.base_url,
        "passed": passed,
        "failed": failed,
        "total": len(results),
        "results": results,
    }
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"\nwrote {out} passed={passed}/{len(results)}", flush=True)
    # Soft gate: exit 0 even with LLM fails (det smoke is hard gate). Non-zero only if zero ran.
    return 0 if results else 1


if __name__ == "__main__":
    raise SystemExit(main())
