#!/usr/bin/env python3
"""Embed assets into index.html variants → dist/ (multi-locale site build)."""

from __future__ import annotations

import base64
import io
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "assets" / "screenshots"
MAX_IMAGE_WIDTH = 1280
LOCALE_PAGES = [
    ROOT / "index.html",
    ROOT / "en" / "index.html",
    ROOT / "de" / "index.html",
    ROOT / "zh" / "index.html",
]
OUT_ROOT = ROOT / "dist"
THEME_JS = ROOT / "theme.js"
I18N_MARKER = '<script src="theme.js"></script>'
I18N_MARKER_SUB = '<script src="../theme.js"></script>'


def _optimize_image(data: bytes) -> tuple[bytes, str]:
    try:
        from PIL import Image
    except ImportError:
        return data, "image/png"

    with Image.open(io.BytesIO(data)) as img:
        if img.mode in ("RGBA", "LA", "P"):
            background = Image.new("RGB", img.size, (13, 17, 23))
            if img.mode == "P":
                img = img.convert("RGBA")
            alpha = img.split()[-1] if img.mode in ("RGBA", "LA") else None
            rgb = img.convert("RGB")
            if alpha is not None:
                background.paste(rgb, mask=alpha)
                img = background
            else:
                img = rgb
        elif img.mode != "RGB":
            img = img.convert("RGB")
        if img.width > MAX_IMAGE_WIDTH:
            ratio = MAX_IMAGE_WIDTH / img.width
            size = (MAX_IMAGE_WIDTH, max(1, round(img.height * ratio)))
            img = img.resize(size, Image.Resampling.LANCZOS)
        out = io.BytesIO()
        img.save(out, format="JPEG", quality=82, optimize=True)
        return out.getvalue(), "image/jpeg"


def embed_assets(html: str, asset_prefix: str) -> str:
    def repl(match: re.Match[str]) -> str:
        prefix, path, suffix = match.group(1), match.group(2), match.group(3)
        full = match.group(0)
        rel = path
        if rel.startswith("../"):
            rel = rel[3:]
        if "assets/screenshots/" in full or "/screenshots/" in rel:
            file_path = ASSETS / Path(rel).name
        else:
            file_path = ROOT / rel.replace("/", "\\") if "\\" not in rel else ROOT / rel
            if not file_path.is_file():
                file_path = ROOT / "assets" / Path(rel).name
        if not file_path.is_file():
            raise FileNotFoundError(f"Missing asset for src={path!r} (tried {file_path})")
        raw = file_path.read_bytes()
        optimized, mime = _optimize_image(raw)
        b64 = base64.b64encode(optimized).decode("ascii")
        return f'{prefix}data:{mime};base64,{b64}{suffix}'

    pattern = re.compile(
        r'((?:src|href)=")(?:\.\./)?assets/(?:screenshots/)?([a-zA-Z0-9_.-]+\.(?:png|svg))(")',
    )
    return pattern.sub(repl, html)


def inline_theme(html: str) -> str:
    js = THEME_JS.read_text(encoding="utf-8")
    for marker in (I18N_MARKER, I18N_MARKER_SUB):
        if marker in html:
            return html.replace(marker, f"<script>\n{js}\n</script>", 1)
    return html


def rel_out_path(src: Path) -> Path:
    if src == ROOT / "index.html":
        return OUT_ROOT / "index.html"
    return OUT_ROOT / src.relative_to(ROOT)


def main() -> None:
    gen = ROOT / "generate_locales.py"
    merge = ROOT / "merge_translations.py"
    if gen.is_file():
        subprocess.run([sys.executable, str(gen)], check=True)
    if merge.is_file():
        subprocess.run([sys.executable, str(merge)], check=True)

    OUT_ROOT.mkdir(parents=True, exist_ok=True)
    total_images = 0
    for src in LOCALE_PAGES:
        if not src.is_file():
            raise SystemExit(f"Missing page: {src} (run generate_locales.py first)")
        html = src.read_text(encoding="utf-8")
        if "data:image/png;base64," in html or "data:image/jpeg;base64," in html:
            raise SystemExit(f"{src} already has embedded images — restore asset paths first")
        out_html = inline_theme(embed_assets(html, ""))
        out_path = rel_out_path(src)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(out_html, encoding="utf-8")
        n = len(re.findall(r"data:image/(?:png|jpeg);base64,", out_html))
        total_images += n
        print(f"Built {out_path}")

    # sync static assets for deploy
    for name in ("theme.js",):
        p = ROOT / name
        if p.is_file():
            (OUT_ROOT / name).write_text(p.read_text(encoding="utf-8"), encoding="utf-8")

    size_mb = sum(f.stat().st_size for f in OUT_ROOT.rglob("*") if f.is_file()) / (1024 * 1024)
    print(f"Done: dist/ ({size_mb:.2f} MB total, {total_images} image refs embedded)")


if __name__ == "__main__":
    main()
