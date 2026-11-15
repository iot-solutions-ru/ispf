#!/usr/bin/env python3
"""Fail when a top-level examples/ directory has no README or is absent from the index."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EXAMPLES = ROOT / "examples"
INDEX = EXAMPLES / "README.md"


def git_ignored(path: Path) -> bool:
    result = subprocess.run(
        ["git", "check-ignore", "-q", "--", path.as_posix()],
        cwd=ROOT,
        check=False,
    )
    return result.returncode == 0


def main() -> int:
    index = INDEX.read_text(encoding="utf-8")
    problems: list[str] = []
    folders = [
        path
        for path in EXAMPLES.iterdir()
        if path.is_dir() and not path.name.startswith(".") and not git_ignored(path)
    ]
    for folder in sorted(folders):
        if not (folder / "README.md").is_file():
            problems.append(f"{folder.name}: missing examples/{folder.name}/README.md")
        needle = f"]({folder.name}/)"
        if needle not in index:
            problems.append(f"{folder.name}: examples/README.md has no link ]({folder.name}/)")
    for target in re.findall(r"\]\(([^)]+)\)", index):
        if target.startswith(("http://", "https://", "../")):
            continue
        path = EXAMPLES / target.split("#", 1)[0]
        if not path.exists() or git_ignored(path):
            problems.append(f"examples/README.md links to missing path {target}")
    if problems:
        print("examples index check failed:", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 1
    print(f"examples index ok ({len(folders)} folders)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
