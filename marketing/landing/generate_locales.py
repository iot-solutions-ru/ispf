#!/usr/bin/env python3
"""Generate en/de/zh/index.html from Russian index.html + locale JSON maps."""

from __future__ import annotations

import json
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "index.html"
LOCALES_DIR = ROOT / "locales"
LANG_DIRS = ("en", "de", "zh")

HREFLANG = {
    "ru": ("ru", "/"),
    "en": ("en", "/en/"),
    "de": ("de", "/de/"),
    "zh": ("zh-Hans", "/zh/"),
}

LANG_NAV = {
    "ru": [
        ('href="/"', "RU", "active"),
        ('href="/en/"', "EN", ""),
        ('href="/de/"', "DE", ""),
        ('href="/zh/"', "中文", ""),
    ],
    "en": [
        ('href="/"', "RU", ""),
        ('href="/en/"', "EN", "active"),
        ('href="/de/"', "DE", ""),
        ('href="/zh/"', "中文", ""),
    ],
    "de": [
        ('href="/"', "RU", ""),
        ('href="/en/"', "EN", ""),
        ('href="/de/"', "DE", "active"),
        ('href="/zh/"', "中文", ""),
    ],
    "zh": [
        ('href="/"', "RU", ""),
        ('href="/en/"', "EN", ""),
        ('href="/de/"', "DE", ""),
        ('href="/zh/"', "中文", "active"),
    ],
}


def strip_i18n_attrs(html: str) -> str:
    html = re.sub(r'\s+data-i18n(?:-html|-aria|-alt)?="[^"]*"', "", html)
    return html


def fix_asset_paths(html: str, prefix: str) -> str:
    if not prefix:
        return html
    html = html.replace('src="assets/', f'src="{prefix}assets/')
    html = html.replace('href="assets/', f'href="{prefix}assets/')
    html = html.replace('src="theme.js"', f'src="{prefix}theme.js"')
    return html


def inject_hreflang(html: str) -> str:
    block = "\n".join(
        f'  <link rel="alternate" hreflang="{code}" href="https://www.iot-solutions.ru{path}" />'
        for code, path in HREFLANG.values()
    )
    block += '\n  <link rel="alternate" hreflang="x-default" href="https://www.iot-solutions.ru/" />'
    if "hreflang=" in html:
        return html
    return html.replace("</head>", block + "\n</head>", 1)


def inject_lang_nav(html: str, lang: str) -> str:
    links = LANG_NAV[lang]
    parts = []
    for href, label, active in links:
        cls = f' class="active"' if active else ""
        parts.append(f'<a {href}{cls}>{label}</a>')
    nav = (
        '        <div class="lang-switch" role="group" aria-label="Language">\n          '
        + "\n          ".join(parts)
        + "\n        </div>"
    )
    return re.sub(
        r'<div class="lang-switch" role="group" aria-label="Language">[\s\S]*?</div>',
        nav,
        html,
        count=1,
    )


def apply_translations(html: str, mapping: dict[str, str]) -> str:
    for src, dst in sorted(mapping.items(), key=lambda x: len(x[0]), reverse=True):
        if src and src in html:
            html = html.replace(src, dst)
    return html


def set_lang(html: str, lang: str, html_lang: str) -> str:
    html = re.sub(r"<html lang=\"[^\"]+\">", f'<html lang="{html_lang}">', html, count=1)
    html = html.replace("<script src=\"i18n.js\"></script>\n", "")
    html = html.replace('<script src="../i18n.js"></script>\n', "")
    if "theme.js" not in html:
        html = html.replace(
            "  <script>\n    (function () {\n      const menu",
            '  <script src="theme.js"></script>\n  <script>\n    (function () {\n      const menu',
            1,
        )
    # head boot script: drop lang from localStorage
    html = re.sub(
        r"document\.documentElement\.setAttribute\(\"lang\", localStorage\.getItem\(\"iot-site-lang\"\)[^)]+\);",
        f'document.documentElement.setAttribute("lang", "{html_lang}");',
        html,
    )
    return html


def load_mapping(lang: str) -> dict[str, str]:
    path = LOCALES_DIR / f"{lang}.json"
    if not path.is_file():
        raise SystemExit(f"Missing {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def prepare_ru(html: str) -> str:
    html = strip_i18n_attrs(html)
    html = inject_hreflang(html)
    html = inject_lang_nav(html, "ru")
    html = set_lang(html, "ru", "ru")
    html = html.replace("<script src=\"i18n.js\"></script>\n", '<script src="theme.js"></script>\n')
    return html


def generate_lang(lang: str, ru_html: str) -> str:
    html_lang = HREFLANG[lang][0]
    prefix = "../" if lang in LANG_DIRS else ""
    html = apply_translations(ru_html, load_mapping(lang))
    html = fix_asset_paths(html, prefix)
    html = inject_lang_nav(html, lang)
    html = set_lang(html, lang, html_lang)
    return html


def main() -> None:
    if not SRC.is_file():
        raise SystemExit(f"Missing {SRC}")
    raw = SRC.read_text(encoding="utf-8")
    ru = prepare_ru(raw)
    SRC.write_text(ru, encoding="utf-8")
    print(f"Updated {SRC} (RU)")

    for lang in LANG_DIRS:
        out_dir = ROOT / lang
        out_dir.mkdir(parents=True, exist_ok=True)
        out_html = generate_lang(lang, ru)
        out_path = out_dir / "index.html"
        out_path.write_text(out_html, encoding="utf-8")
        remaining = len(re.findall(r"[а-яА-ЯёЁ]", out_html))
        print(f"Wrote {out_path} ({remaining} Cyrillic chars remaining)")

    shutil.copy2(ROOT / "theme.js", ROOT / "en" / "theme.js")
    shutil.copy2(ROOT / "theme.js", ROOT / "de" / "theme.js")
    shutil.copy2(ROOT / "theme.js", ROOT / "zh" / "theme.js")


if __name__ == "__main__":
    main()
