#!/usr/bin/env python3
"""Restore markdown link targets in docs/ru from docs/en (machine translation often corrupts paths)."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EN = ROOT / "docs" / "en"
RU = ROOT / "docs" / "ru"
SKIP = {"readme.md", "documentation-audit.md"}

LINK_RE = re.compile(r"(\[[^\]]*\]\()([^)#]+)(\))")


def strip_banner(text: str) -> str:
    return re.sub(r"^> \*\*(?:Язык|Language):\*\*.*\n\n", "", text, count=1, flags=re.MULTILINE)


def en_hrefs(body: str) -> list[str]:
    return [m.group(2) for m in LINK_RE.finditer(body)]


def sync_links(ru_body: str, en_body: str) -> tuple[str, int]:
    hrefs = en_hrefs(en_body)
    matches = list(LINK_RE.finditer(ru_body))
    if len(hrefs) != len(matches):
        return ru_body, 0
    out: list[str] = []
    last = 0
    fixes = 0
    for i, m in enumerate(matches):
        out.append(ru_body[last : m.start(2)])
        new_href = hrefs[i]
        if m.group(2) != new_href:
            fixes += 1
        out.append(new_href)
        last = m.end(2)
    out.append(ru_body[last:])
    return "".join(out), fixes


def main() -> int:
    total = 0
    warned: list[str] = []
    for en_path in sorted(EN.rglob("*.md")):
        if en_path.name.lower() in SKIP:
            continue
        rel = en_path.relative_to(EN)
        ru_path = RU / rel
        if not ru_path.exists():
            continue
        en_body = strip_banner(en_path.read_text(encoding="utf-8"))
        ru_text = ru_path.read_text(encoding="utf-8")
        banner_m = re.match(r"^(> \*\*Язык:\*\*.*\n\n)", ru_text)
        banner = banner_m.group(1) if banner_m else ""
        ru_body = strip_banner(ru_text)
        fixed, n = sync_links(ru_body, en_body)
        if n:
            ru_path.write_text(banner + fixed, encoding="utf-8")
            total += n
            print(f"fixed {n} links: {rel.as_posix()}")
        elif len(en_hrefs(en_body)) != len(list(LINK_RE.finditer(ru_body))):
            warned.append(rel.as_posix())
    if warned:
        print("WARN count mismatch:", ", ".join(warned), file=sys.stderr)
    print(f"Total link targets fixed: {total}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
