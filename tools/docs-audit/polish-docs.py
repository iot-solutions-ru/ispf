#!/usr/bin/env python3
"""Normalize doc links, trim boilerplate, fix spacing (run after strip-neuro-slang.py)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"

LINK = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")
RISKS_GAP = re.compile(r"\n\n\nRisks:\n", re.MULTILINE)
ADDITIONALLY = re.compile(r"\nAdditionally, ", re.MULTILINE)

REPLACEMENTS: list[tuple[str, str]] = [
    ("One powerful JVM", "One JVM with sufficient CPU"),
    ("Additionally, **exception storms**", "**Exception storms**"),
    ("Additionally:\n", ""),
    (
        "Combines the target approach from [architecture.md](architecture.md), the platform/solution boundary",
        "Links [architecture.md](architecture.md), platform/solution boundary",
    ),
    (
        "approaches A–H from [agent-knowledge.md](agent-knowledge.md), and the unified logic model from [platform-logic.md](platform-logic.md).",
        "[agent-knowledge.md](agent-knowledge.md), and [platform-logic.md](platform-logic.md).",
    ),
    (
        "**For API and widget details** — see specialized documents; this file is the hub for “how to build an application”.",
        "API and widget details live in linked docs below.",
    ),
    (
        "Next development wave: [roadmap.md § Phase 5](roadmap.md) (models, functions, events, workflow, bundle as tree packaging).",
        "Backlog: [roadmap.md](roadmap.md).",
    ),
    (
        "| **Target approach** | Open self-hosted industrial application platform",
        "| **Product direction** | Self-hosted industrial application platform",
    ),
]


def normalize_link_label(label: str, url: str) -> str:
    """Use kebab-case filename as label when label is SHOUTY or *.MD."""
    if not url.endswith(".md") and ".md#" not in url:
        return label
    base = url.rsplit("/", 1)[-1].split("#", 1)[0]
    if not base.endswith(".md"):
        return label
    stem = base[:-3]
    if label.startswith("ADR-"):
        return label
    shouty = label.replace(".md", "").replace(".MD", "")
    if label == base or label.upper() == label or re.fullmatch(r"[A-Za-z0-9_-]+\.md", label):
        return stem
    if shouty.upper() == shouty and ("_" in shouty or shouty.isupper()):
        return stem
    return label


def polish(text: str) -> str:
    for old, new in REPLACEMENTS:
        text = text.replace(old, new)
    text = ADDITIONALLY.sub("\n", text)
    text = RISKS_GAP.sub("\n\nRisks:\n", text)

    def repl(m: re.Match[str]) -> str:
        label, url = m.group(1), m.group(2)
        new_label = normalize_link_label(label, url)
        if new_label != label:
            return f"[{new_label}]({url})"
        return m.group(0)

    return LINK.sub(repl, text)


def main() -> int:
    changed = 0
    for path in sorted(DOCS.rglob("*.md")):
        original = path.read_text(encoding="utf-8")
        updated = polish(original)
        if updated != original:
            path.write_text(updated, encoding="utf-8", newline="\n")
            print(path.relative_to(ROOT))
            changed += 1
    print(f"Updated {changed} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
