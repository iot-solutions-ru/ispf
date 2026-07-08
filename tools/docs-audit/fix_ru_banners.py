#!/usr/bin/env python3
"""Fix language banners in docs/ru/**/*.md without retranslating bodies."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EN = ROOT / "docs" / "en"
RU = ROOT / "docs" / "ru"
SKIP = {"readme.md", "documentation-audit.md"}


def lang_banner(en_rel: Path) -> str:
    depth = len(en_rel.parts)
    prefix = "../" * depth
    href = f"{prefix}en/{en_rel.as_posix()}"
    return (
        "> **Язык:** русская версия (вычитка). Канонический английский: "
        f"[en/{en_rel.as_posix()}]({href}).\n\n"
    )


def main() -> int:
    count = 0
    for en_path in sorted(EN.rglob("*.md")):
        if en_path.name.lower() in SKIP:
            continue
        ru_path = RU / en_path.relative_to(EN)
        if not ru_path.exists():
            continue
        text = ru_path.read_text(encoding="utf-8")
        body = re.sub(r"^> \*\*Язык:\*\*.*\n\n", "", text, count=1, flags=re.MULTILINE)
        body = re.sub(r"^> \*\*Language:\*\*.*\n\n", "", body, count=1, flags=re.MULTILINE)
        rel = en_path.relative_to(EN)
        ru_path.write_text(lang_banner(rel) + body, encoding="utf-8")
        count += 1
    print(f"Fixed banners on {count} files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
