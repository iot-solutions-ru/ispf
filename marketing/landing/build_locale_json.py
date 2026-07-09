#!/usr/bin/env python3
"""Build locales/{en,de,zh}.json from i18n.js + index.html Cyrillic strings."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
I18N_JS = ROOT / "i18n.js"
HTML = ROOT / "index.html"
OUT = ROOT / "locales"


def parse_i18n_js() -> dict[str, dict[str, str]]:
    text = I18N_JS.read_text(encoding="utf-8")
    packs: dict[str, dict[str, str]] = {}
    for lang in ("ru", "en", "de", "zh"):
        m = re.search(rf"\n\s*{lang}:\s*\{{", text)
        if not m:
            continue
        start = m.end() - 1
        depth = 0
        end = start
        for i in range(start, len(text)):
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
                if depth == 0:
                    end = i + 1
                    break
        block = text[start:end]
        pairs = re.findall(r'"([^"\\]+)"\s*:\s*"((?:\\.|[^"\\])*)"', block)
        pairs += re.findall(r'"([^"\\]+)"\s*:\s*\'((?:\\.|[^\'])*)\'', block)
        obj: dict[str, str] = {}
        for k, v in pairs:
            v = v.replace('\\"', '"').replace("\\n", "\n")
            obj[k] = v
        packs[lang] = obj
    return packs


def extract_cyrillic_chunks(html: str) -> list[str]:
    text = re.sub(r"<script[\s\S]*?</script>", "", html)
    text = re.sub(r"<style[\s\S]*?</style>", "", text)
    found: set[str] = set()
    for m in re.finditer(r">([^<]+)<", text):
        s = m.group(1).strip()
        if not s or not re.search(r"[а-яА-ЯёЁ]", s):
            continue
        if s.startswith("data:") or s in {"☰", "☾", "☀"}:
            continue
        found.add(s)
    for m in re.finditer(r'(?:title|content|aria-label|alt)="([^"]*[а-яА-ЯёЁ][^"]*)"', html):
        found.add(m.group(1))
    return sorted(found, key=len, reverse=True)


def html_unescape(s: str) -> str:
    return s.replace("&nbsp;", " ")


def main() -> None:
    packs = parse_i18n_js()
    ru_by_val = {v: k for k, v in packs.get("ru", {}).items()}
    html = HTML.read_text(encoding="utf-8")
    chunks = extract_cyrillic_chunks(html)

    en_map: dict[str, str] = {}
    de_map: dict[str, str] = {}
    zh_map: dict[str, str] = {}

    for chunk in chunks:
        chunk = html_unescape(chunk)
        key = ru_by_val.get(chunk)
        if key and key in packs.get("en", {}):
            en_map[chunk] = packs["en"][key]
        if key and key in packs.get("de", {}):
            de_map[chunk] = packs["de"][key]
        if key and key in packs.get("zh", {}):
            zh_map[chunk] = packs["zh"][key]

    # Also map by ru values from i18n directly (for HTML with extra whitespace)
    for k, ru_val in packs.get("ru", {}).items():
        ru_val = html_unescape(ru_val)
        if ru_val in chunks or ru_val.replace("<br>", "<br>") in html:
            if k in packs.get("en", {}):
                en_map[ru_val] = packs["en"][k]
            if k in packs.get("de", {}):
                de_map[ru_val] = packs["de"][k]
            if k in packs.get("zh", {}):
                zh_map[ru_val] = packs["zh"][k]

    OUT.mkdir(parents=True, exist_ok=True)
    for lang, mapping in (("en", en_map), ("de", de_map), ("zh", zh_map)):
        path = OUT / f"{lang}.json"
        path.write_text(json.dumps(mapping, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"{path}: {len(mapping)} entries")

    missing = [c for c in chunks if c not in en_map]
    (OUT / "_missing_en.txt").write_text("\n---\n".join(missing), encoding="utf-8")
    print(f"Missing EN translations: {len(missing)} (see locales/_missing_en.txt)")


if __name__ == "__main__":
    main()
