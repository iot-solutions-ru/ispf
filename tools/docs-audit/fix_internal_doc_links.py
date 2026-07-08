#!/usr/bin/env python3
"""Fix internal doc cross-links after migration (../UPPER.md -> ../kebab.md)."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCS_EN = ROOT / "docs" / "en"


def kebab_from_upper(name: str) -> str:
    stem = name.replace(".md", "").upper()
    from migrate_docs import RENAME  # noqa: WPS433

    for old, new in RENAME.items():
        if old.replace(".md", "").upper() == stem:
            return new
    return name.lower().replace("_", "-")


def fix_text(text: str) -> str:
    # [text](../FOO_BAR.md) inside en/ or ru/
    def repl(m: re.Match) -> str:
        prefix, name, suffix = m.group(1), m.group(2), m.group(3)
        if name.startswith("http"):
            return m.group(0)
        if "/" in name and not name.startswith("../"):
            return m.group(0)
        if name.endswith("/"):
            return m.group(0)
        if not name.endswith(".md"):
            return m.group(0)
        base = Path(name).name
        if base == base.upper() or "_" in base:
            new_base = kebab_from_upper(base)
            new_path = str(Path(name).parent / new_base) if "/" in name else new_base
            if name.startswith("../"):
                # keep ../ prefix depth
                parts = name.split("/")
                parts[-1] = new_base
                new_path = "/".join(parts)
            return f"{prefix}{new_path}{suffix}"
        return m.group(0)

    text = re.sub(r"(\]\()(\.\./)?([^)#]+\.md)(#[^)]*)?(\))", repl, text)
    # decisions readme links
    text = re.sub(
        r"\]\((\.\./)?([A-Z][A-Z0-9_]+)\.md\)",
        lambda m: f"]({m.group(1) or ''}{kebab_from_upper(m.group(2) + '.md')})",
        text,
    )
    return text


def main() -> None:
    for lang in ("en", "ru"):
        base = ROOT / "docs" / lang
        for md in sorted(base.rglob("*.md")):
            orig = md.read_text(encoding="utf-8")
            fixed = fix_text(orig)
            if fixed != orig:
                md.write_text(fixed, encoding="utf-8")
                print(f"fixed: {md.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
