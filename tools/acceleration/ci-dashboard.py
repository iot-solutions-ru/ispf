#!/usr/bin/env python3
"""Generate CI observability dashboard (S20-06)."""

from __future__ import annotations

import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT_MD = ROOT / "docs" / "CI_DASHBOARD.md"
OUT_JSON = Path(__file__).resolve().parent / "ci-dashboard.json"

WORKFLOWS = [
    ("ci.yml", "PR pr-fast"),
    ("ci-nightly.yml", "Nightly full"),
    ("load-test.yml", "Load gate"),
    ("cluster-load-test.yml", "Cluster gate"),
    ("e2e-live.yml", "E2E live"),
    ("driver-interop.yml", "Driver interop"),
]


def gh_runs(workflow: str, limit: int = 20) -> list[dict]:
    try:
        proc = subprocess.run(
            [
                "gh",
                "run",
                "list",
                f"--workflow={workflow}",
                "--limit",
                str(limit),
                "--json",
                "conclusion,createdAt,updatedAt,displayTitle,event,status",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=60,
            check=False,
        )
        if proc.returncode != 0:
            return []
        return json.loads(proc.stdout or "[]")
    except (FileNotFoundError, json.JSONDecodeError, subprocess.TimeoutExpired):
        return []


def summarize_workflow(name: str, label: str) -> dict:
    runs = gh_runs(name)
    completed = [r for r in runs if r.get("conclusion")]
    successes = [r for r in completed if r.get("conclusion") == "success"]
    durations: list[float] = []
    for run in completed:
        try:
            start = datetime.fromisoformat(run["createdAt"].replace("Z", "+00:00"))
            end = datetime.fromisoformat(run["updatedAt"].replace("Z", "+00:00"))
            durations.append((end - start).total_seconds() / 60.0)
        except (KeyError, ValueError):
            continue
    return {
        "workflow": name,
        "label": label,
        "sample_size": len(completed),
        "success_rate_pct": round(len(successes) / len(completed) * 100, 1) if completed else None,
        "avg_wall_min": round(sum(durations) / len(durations), 1) if durations else None,
        "last_conclusion": completed[0].get("conclusion") if completed else None,
        "last_title": (completed[0].get("displayTitle") or "")[:80] if completed else None,
    }


def render_markdown(report: dict) -> str:
    lines = [
        "# CI dashboard",
        "",
        f"Generated: `{report['generated_at']}` · [Acceleration program](ACCELERATION_PROGRAM.md)",
        "",
        "> Auto-updated by `python tools/acceleration/ci-dashboard.py` (S20-06).",
        "",
        "## Workflow health (last 20 runs)",
        "",
        "| Workflow | Role | Success | Avg wall | Last |",
        "| -------- | ---- | ------- | -------- | ---- |",
    ]
    for row in report["workflows"]:
        success = f"{row['success_rate_pct']}%" if row["success_rate_pct"] is not None else "—"
        avg = f"{row['avg_wall_min']} min" if row["avg_wall_min"] is not None else "—"
        last = row["last_conclusion"] or "—"
        lines.append(
            f"| `{row['workflow']}` | {row['label']} | {success} ({row['sample_size']}) | {avg} | {last} |"
        )
    lines.extend(
        [
            "",
            "## Targets (acceleration)",
            "",
            "| KPI | Target |",
            "| --- | ------ |",
            "| PR pr-fast wall | ≤25 min |",
            "| CI success rate (14d) | ≥95% |",
            "| Nightly full | green 7/7 days |",
            "",
            "## Commands",
            "",
            "```bash",
            "python tools/acceleration/ci-dashboard.py",
            "python tools/acceleration/collect-baseline.py",
            "gh run list --workflow=ci.yml --limit 10",
            "```",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> int:
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    workflows = [summarize_workflow(name, label) for name, label in WORKFLOWS]
    report = {"generated_at": now, "workflows": workflows}
    OUT_JSON.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    OUT_MD.write_text(render_markdown(report), encoding="utf-8")
    print(f"Wrote {OUT_MD}")
    print(f"Wrote {OUT_JSON}")

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        path = Path(summary_path)
        if path.parent.exists():
            path.write_text(render_markdown(report), encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
