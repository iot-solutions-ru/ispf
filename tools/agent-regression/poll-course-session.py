#!/usr/bin/env python3
"""Poll an AI Studio agent session until the turn stops running."""
from __future__ import annotations

import json
import pathlib
import sys
import time
import urllib.error
import urllib.request

BASE = "https://it-demo.iot-solutions.ru"
TEMP = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else pathlib.Path.home() / "AppData/Local/Temp")
TOKEN = (TEMP / "ispf-course-token.txt").read_text(encoding="utf-8").strip()
SID = (TEMP / "ispf-course-session.txt").read_text(encoding="utf-8").strip()
LOG = TEMP / "ispf-course-progress.log"


def get(path: str) -> dict:
    req = urllib.request.Request(
        f"{BASE}{path}",
        headers={"Authorization": f"Bearer {TOKEN}", "Accept": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def step_line(step: dict) -> str:
    tool = step.get("tool") or step.get("type") or "?"
    res = step.get("result") or {}
    status = res.get("status") or step.get("status") or ""
    err = str(res.get("error") or "")[:200]
    human = step.get("summary") or step.get("humanSummary") or step.get("title") or ""
    detail = err or human
    # Avoid Windows console cp1251 crashes on arrow/emoji in tool hints.
    text = f"{tool} {status} {detail}".strip()
    return text.encode("ascii", "replace").decode("ascii")


def main() -> int:
    last_n = 0
    LOG.write_text("", encoding="utf-8")
    for i in range(120):
        try:
            progress = get(f"/api/v1/ai/agent/sessions/{SID}/progress")
        except Exception as exc:
            print(f"poll_err {exc}", flush=True)
            time.sleep(10)
            continue
        steps = progress.get("steps") or []
        running = progress.get("running")
        for step in steps[last_n:]:
            line = step_line(step)
            print(line, flush=True)
            with LOG.open("a", encoding="utf-8") as fh:
                fh.write(line + "\n")
        last_n = len(steps)
        print(f"--- tick={i} steps={len(steps)} running={running}", flush=True)
        if not running and i > 0:
            try:
                sess = get(f"/api/v1/ai/agent/sessions/{SID}")
                (TEMP / "ispf-course-session.json").write_text(
                    json.dumps(sess, ensure_ascii=False)[:400000],
                    encoding="utf-8",
                )
                turns = sess.get("turns") or []
                print(f"turns={len(turns)}", flush=True)
                if turns:
                    last = turns[-1]
                    summary = str(last.get("summary") or last.get("result") or "")[:800]
                    print(f"last_status={last.get('status')} summary={summary}", flush=True)
                    # dump ERROR steps from last turn
                    for step in last.get("steps") or []:
                        res = step.get("result") or {}
                        if str(res.get("status")) == "ERROR":
                            print(
                                "ERROR_STEP",
                                step.get("tool"),
                                str(res.get("error"))[:300],
                                flush=True,
                            )
            except Exception as exc:
                print(f"sess_err {exc}", flush=True)
            return 0
        time.sleep(15)
    print("timeout waiting for agent", flush=True)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
