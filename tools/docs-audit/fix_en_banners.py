#!/usr/bin/env python3
"""Set canonical English language banners in docs/en/."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EN = ROOT / "docs" / "en"
SKIP = {"readme.md", "documentation-audit.md"}


def banner(rel: Path) -> str:
    depth = len(rel.parts)
    prefix = "../" * depth
    href = f"{prefix}ru/{rel.as_posix()}"
    return (
        "> **Language:** Canonical English. Russian edition: "
        f"[ru/{rel.as_posix()}]({href}).\n\n"
    )


def main() -> int:
    n = 0
    for path in sorted(EN.rglob("*.md")):
        if path.name.lower() in SKIP:
            continue
        text = path.read_text(encoding="utf-8")
        body = re.sub(
            r"^> \*\*(?:Language|Язык):\*\*.*\n\n", "", text, count=1, flags=re.MULTILINE
        )
        rel = path.relative_to(EN)
        new = banner(rel) + body
        if new != text:
            path.write_text(new, encoding="utf-8")
            n += 1
    print(f"Updated banners on {n} files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
