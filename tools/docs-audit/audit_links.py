#!/usr/bin/env python3
"""Report broken relative .md links under docs/en and docs/ru."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LINK = re.compile(r"\[[^\]]*\]\(([^)#]+)(#[^)]*)?\)")


def check_lang(lang: str) -> list[str]:
    base = ROOT / "docs" / lang
    errors: list[str] = []
    for md in sorted(base.rglob("*.md")):
        text = md.read_text(encoding="utf-8")
        for m in LINK.finditer(text):
            target = m.group(1).strip()
            if target.startswith("http") or target.startswith("mailto:"):
                continue
            if target.startswith("#"):
                continue
            # Links outside docs/ (repo root, packages, .github) are OK
            if target.startswith("../") and not target.startswith("../../docs"):
                continue
            resolved = (md.parent / target).resolve()
            if not resolved.exists():
                errors.append(f"{md.relative_to(ROOT)} -> {target}")
    return errors


def main() -> int:
    all_err = check_lang("en") + check_lang("ru")
    if all_err:
        print(f"BROKEN LINKS ({len(all_err)}):")
        for e in all_err[:100]:
            print(" ", e)
        if len(all_err) > 100:
            print(f"  ... and {len(all_err) - 100} more")
        return 1
    print("No broken internal .md links in docs/en and docs/ru")
    return 0


if __name__ == "__main__":
    sys.exit(main())
