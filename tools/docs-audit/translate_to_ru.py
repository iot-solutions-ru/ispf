#!/usr/bin/env python3
"""
Translate docs/en/**/*.md -> docs/ru/**/*.md (full Russian body).

Preserves: fenced code blocks, inline code, URLs, paths, JSON keys, mermaid.
Uses deep-translator (Google, no API key). Install: pip install deep-translator
"""

from __future__ import annotations

import re
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EN = ROOT / "docs" / "en"
RU = ROOT / "docs" / "ru"

SKIP_NAMES = {"readme.md", "documentation-audit.md"}

def lang_banner(en_rel: Path) -> str:
    depth = len(en_rel.parts)
    prefix = "../" * depth
    href = f"{prefix}en/{en_rel.as_posix()}"
    return (
        "> **Язык:** полный русский перевод. Канонический английский: "
        f"[en/{en_rel.as_posix()}]({href}).\n\n"
    )

# Protect segments from translation
FENCE_RE = re.compile(r"(```[\s\S]*?```)", re.MULTILINE)
INLINE_CODE_RE = re.compile(r"(`[^`\n]+`)")
MD_LINK_RE = re.compile(r"(\[[^\]]*\]\([^)]+\))")
URL_RE = re.compile(r"(https?://[^\s)>\]]+)")
PATH_RE = re.compile(
    r"(\b(?:docs|packages|apps|examples|deploy|tools)/[\w./\-{}]+\b"
    r"|\b[A-Z][A-Z0-9_]{2,}\.md\b"
    r"|\b[a-z][a-z0-9-]*\.(?:md|json|yml|yaml|sh|ps1|java|ts|tsx)\b)"
)


def protect(text: str) -> tuple[str, list[str]]:
    vault: list[str] = []

    def stash(m: re.Match) -> str:
        vault.append(m.group(0))
        return f"\uE000{len(vault) - 1}\uE001"

    for pat in (FENCE_RE, MD_LINK_RE, INLINE_CODE_RE, URL_RE):
        text = pat.sub(stash, text)
    return text, vault


def restore(text: str, vault: list[str]) -> str:
    for i, orig in enumerate(vault):
        text = text.replace(f"\uE000{i}\uE001", orig)
    return text


def translate_chunk(text: str, translator) -> str:
    if not text.strip():
        return text
    max_len = 4500
    if len(text) <= max_len:
        try:
            result = translator.translate(text)
            return result if result is not None else text
        except Exception as e:
            print(f"  warn chunk: {e}", file=sys.stderr)
            return text
    parts: list[str] = []
    buf = ""
    for para in text.split("\n"):
        if len(buf) + len(para) + 1 > max_len and buf:
            parts.append(buf)
            buf = para
        else:
            buf = f"{buf}\n{para}" if buf else para
    if buf:
        parts.append(buf)
    out: list[str] = []
    for p in parts:
        try:
            t = translator.translate(p)
            out.append(t if t is not None else p)
        except Exception as e:
            print(f"  warn part: {e}", file=sys.stderr)
            out.append(p)
        time.sleep(0.15)
    return "\n".join(out)


def translate_markdown(body: str, translator) -> str:
    protected, vault = protect(body)
    # Translate line by line for tables/lists stability
    lines = protected.split("\n")
    out_lines: list[str] = []
    buffer: list[str] = []

    def flush():
        nonlocal buffer
        if not buffer:
            return
        chunk = "\n".join(buffer)
        if re.search(r"\uE000\d+\uE001", chunk) and len(chunk) < 80:
            out_lines.extend(buffer)
        else:
            out_lines.append(translate_chunk(chunk, translator))
        buffer = []

    for line in lines:
        if re.match(r"^\uE000\d+\uE001$", line) or line.strip() == "":
            flush()
            out_lines.append(line)
        elif line.startswith("#") or line.startswith("|") or line.startswith("- ") or line.startswith("* "):
            flush()
            buffer.append(line)
            if len("\n".join(buffer)) > 200:
                flush()
        else:
            buffer.append(line)
            if len("\n".join(buffer)) > 400:
                flush()
    flush()
    result = "\n".join(out_lines)
    return restore(result, vault)


def process_file(en_path: Path, ru_path: Path, translator, force: bool) -> bool:
    if en_path.name.lower() in SKIP_NAMES:
        return False
    if ru_path.exists() and not force:
        existing = ru_path.read_text(encoding="utf-8")
        body = re.sub(r"^>.*\n\n", "", existing, count=1, flags=re.MULTILINE)
        cyr = len(re.findall(r"[\u0400-\u04FF]", body))
        latin = len(re.findall(r"[a-zA-Z]", body))
        # Skip only if already predominantly Russian prose
        if cyr > 500 and cyr / max(cyr + latin, 1) > 0.45:
            return False
    en_body = en_path.read_text(encoding="utf-8")
    # strip existing language banners from en copy
    en_body = re.sub(r"^> \*\*Language:\*\*.*\n\n", "", en_body, flags=re.MULTILINE)
    en_body = re.sub(r"^> \*\*Язык:\*\*.*\n\n", "", en_body, flags=re.MULTILINE)
    rel = en_path.relative_to(EN)
    print(f"translate: docs/en/{rel.as_posix()}", flush=True)
    ru_body = translate_markdown(en_body, translator)
    ru_path.parent.mkdir(parents=True, exist_ok=True)
    ru_path.write_text(lang_banner(rel) + ru_body, encoding="utf-8")
    return True


def _worker(args: tuple[str, bool]) -> bool:
    en_path_str, force = args
    from deep_translator import GoogleTranslator

    en_path = Path(en_path_str)
    ru_path = RU / en_path.relative_to(EN)
    translator = GoogleTranslator(source="en", target="ru")
    ok = process_file(en_path, ru_path, translator, force)
    if ok:
        time.sleep(0.1)
    return ok


def main() -> int:
    try:
        from deep_translator import GoogleTranslator
    except ImportError:
        print("Install: pip install deep-translator", file=sys.stderr)
        return 1

    force = "--force" in sys.argv
    only = None
    if "--only" in sys.argv:
        i = sys.argv.index("--only")
        only = sys.argv[i + 1]
    workers = 1
    if "--workers" in sys.argv:
        workers = max(1, int(sys.argv[sys.argv.index("--workers") + 1]))

    pending: list[Path] = []
    for en_path in sorted(EN.rglob("*.md")):
        rel = en_path.relative_to(EN)
        if only and only not in str(rel):
            continue
        if en_path.name.lower() in SKIP_NAMES:
            continue
        ru_path = RU / rel
        if not force and ru_path.exists():
            existing = ru_path.read_text(encoding="utf-8")
            body = re.sub(r"^>.*\n\n", "", existing, count=1, flags=re.MULTILINE)
            cyr = len(re.findall(r"[\u0400-\u04FF]", body))
            latin = len(re.findall(r"[a-zA-Z]", body))
            if cyr > 500 and cyr / max(cyr + latin, 1) > 0.45:
                continue
        pending.append(en_path)

    if workers <= 1:
        translator = GoogleTranslator(source="en", target="ru")
        count = sum(
            1
            for en_path in pending
            if process_file(en_path, RU / en_path.relative_to(EN), translator, True)
        )
    else:
        from concurrent.futures import ProcessPoolExecutor, as_completed

        count = 0
        with ProcessPoolExecutor(max_workers=workers) as pool:
            futs = [pool.submit(_worker, (str(p), True)) for p in pending]
            for fut in as_completed(futs):
                if fut.result():
                    count += 1
    print(f"Translated {count} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
