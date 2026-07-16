#!/usr/bin/env python3
"""Report broken relative links under docs/en and docs/ru.

Checks:
- sibling / same-tree `.md` links
- repo-root relatives (`packages/`, `examples/`, `tools/`, `LICENSE`, `NOTICE`,
  `.github/`, `apps/`, `deploy/`, `plugins/`, `gradle.properties.example`, …)
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LINK = re.compile(r"\[[^\]]*\]\(([^)]+)\)")
SKIP_SCHEMES = ("http://", "https://", "mailto:", "#")
REPO_MARKERS = (
    "packages/",
    "examples/",
    "tools/",
    ".github/",
    "apps/",
    "deploy/",
    "plugins/",
    "LICENSE",
    "NOTICE",
    "gradle.properties.example",
    "CLA.md",
    "LICENSE-COMMERCIAL",
)


def should_check(target: str) -> bool:
    t = target.strip()
    if not t or t.startswith(SKIP_SCHEMES):
        return False
    if " " in t and not t.startswith("../") and not t.startswith("./"):
        return False
    return True


def is_repo_relative(target: str) -> bool:
    path = target.split("#", 1)[0].replace("\\", "/")
    return any(m in path for m in REPO_MARKERS)


def check_lang(lang: str) -> list[str]:
    base = ROOT / "docs" / lang
    errors: list[str] = []
    for md in sorted(base.rglob("*.md")):
        text = md.read_text(encoding="utf-8")
        for m in LINK.finditer(text):
            raw = m.group(1).strip()
            if not should_check(raw):
                continue
            path_part = raw.split("#", 1)[0]
            if path_part.startswith("`"):
                continue
            # Skip pure in-page or empty
            if not path_part:
                continue
            resolved = (md.parent / path_part).resolve()
            # Only enforce existence for markdown peers or known repo artifacts
            if path_part.endswith(".md") or is_repo_relative(path_part) or path_part.endswith(
                (".java", ".json", ".yml", ".yaml", ".sh", ".ps1", ".sql", ".xml", ".mjs", ".mdc")
            ):
                if not resolved.exists():
                    # Allow missing optional media
                    if path_part.endswith((".png", ".gif", ".jpg", ".jpeg", ".webp", ".svg")):
                        continue
                    errors.append(f"{md.relative_to(ROOT)} -> {path_part}")
    return errors


def main() -> int:
    all_err = check_lang("en") + check_lang("ru")
    if all_err:
        print(f"BROKEN LINKS ({len(all_err)}):")
        for e in all_err[:120]:
            print(" ", e)
        if len(all_err) > 120:
            print(f"  ... and {len(all_err) - 120} more")
        return 1
    print("No broken relative links in docs/en and docs/ru (md + repo artifacts)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
