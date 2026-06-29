#!/usr/bin/env python3
"""Embed assets/screenshots/*.png into index.html → dist/landing.html (single file)."""

from __future__ import annotations

import base64
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "index.html"
OUT_DIR = ROOT / "dist"
OUT = OUT_DIR / "landing.html"
ASSETS = ROOT / "assets" / "screenshots"


def embed(html: str) -> str:
    def repl(match: re.Match[str]) -> str:
        prefix, path, suffix = match.group(1), match.group(2), match.group(3)
        file_path = ROOT / path.replace("/", "\\") if "\\" not in path else ROOT / path
        if not file_path.is_file():
            # try assets/screenshots basename only
            file_path = ASSETS / Path(path).name
        if not file_path.is_file():
            raise FileNotFoundError(f"Missing asset for src={path!r} (tried {file_path})")
        b64 = base64.b64encode(file_path.read_bytes()).decode("ascii")
        return f'{prefix}data:image/png;base64,{b64}{suffix}'

    pattern = re.compile(
        r'(src=")assets/screenshots/([a-zA-Z0-9_.-]+\.png)(")',
    )
    return pattern.sub(repl, html)


def main() -> None:
    if not SRC.is_file():
        raise SystemExit(f"Source not found: {SRC}")
    html = SRC.read_text(encoding="utf-8")
    if "data:image/png;base64," in html:
        raise SystemExit(
            "index.html already contains embedded images. "
            "Restore asset paths (assets/screenshots/*.png) before building."
        )
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out_html = embed(html)
    OUT.write_text(out_html, encoding="utf-8")
    size_mb = OUT.stat().st_size / (1024 * 1024)
    count = len(re.findall(r'data:image/png;base64,', out_html))
    print(f"Built {OUT} ({size_mb:.2f} MB, {count} images embedded)")


if __name__ == "__main__":
    main()
