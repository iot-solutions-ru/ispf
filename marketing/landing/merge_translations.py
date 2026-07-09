#!/usr/bin/env python3
"""Merge locales/translations.json into en.json, de.json, zh.json."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
LOCALES = ROOT / "locales"
TRANSLATIONS = LOCALES / "translations.json"


def main() -> None:
    if not TRANSLATIONS.is_file():
        print(f"Skip merge: {TRANSLATIONS} not found")
        return
    data = json.loads(TRANSLATIONS.read_text(encoding="utf-8"))
    for lang in ("en", "de", "zh"):
        out = LOCALES / f"{lang}.json"
        existing = json.loads(out.read_text(encoding="utf-8")) if out.is_file() else {}
        merged = {**existing, **data.get(lang, {})}
        out.write_text(json.dumps(merged, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"Merged {out}: {len(merged)} entries")


if __name__ == "__main__":
    main()
