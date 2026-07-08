#!/usr/bin/env python3
"""Fail if docs/en contains Cyrillic outside allowed exceptions."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EN = ROOT / "docs" / "en"

# Allow proper nouns / registry names in specific files
ALLOW: dict[str, re.Pattern[str]] = {
    "russian-software-registry.md": re.compile(r"РЕД ОС|ред ос|ООО", re.I),
}


def main() -> int:
    bad: list[str] = []
    for path in sorted(EN.rglob("*.md")):
        text = path.read_text(encoding="utf-8")
        body = re.sub(r"^>.*\n\n", "", text, count=1, flags=re.MULTILINE)
        cyr_chars = re.findall(r"[\u0400-\u04FF]+", body)
        if not cyr_chars:
            continue
        joined = " ".join(cyr_chars)
        allow = ALLOW.get(path.name)
        if allow:
            joined = allow.sub("", joined).strip()
        if joined:
            rel = path.relative_to(ROOT)
            bad.append(f"{rel}: {joined[:80]}...")
    if bad:
        print(f"Cyrillic in docs/en ({len(bad)} files):")
        for line in bad[:50]:
            print(" ", line)
        return 1
    print("docs/en: no unexpected Cyrillic")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
