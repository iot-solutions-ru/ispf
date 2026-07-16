"""Fix docs/{en,ru} markdown links that point one level short of the repo root.

For each markdown link target starting with ../ and mentioning
packages|examples|tools|LICENSE|NOTICE|.github|apps|deploy|plugins,
walk up adding ../ until the path exists or we hit repo root.
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOC_ROOTS = [ROOT / "docs" / "en", ROOT / "docs" / "ru"]
LINK_RE = re.compile(r"\]\(([^)]+)\)")
PREFIX_RE = re.compile(
    r"^(\.\./)+(packages|examples|tools|LICENSE|NOTICE|\.github|apps|deploy|plugins)(/|$)"
)


def fix_target(doc: Path, target: str) -> str | None:
    if target.startswith(("http://", "https://", "mailto:", "#")):
        return None
    path_part, frag = (target.split("#", 1) + [""])[:2]
    if path_part.startswith("`") or " " in path_part:
        return None
    if not PREFIX_RE.match(path_part.replace("\\", "/")):
        return None
    # Strip query-like junk
    candidate = (doc.parent / path_part).resolve()
    if candidate.exists():
        return None
    parts = path_part.replace("\\", "/").split("/")
    # Count leading ..
    i = 0
    while i < len(parts) and parts[i] == "..":
        i += 1
    rest = parts[i:]
    # Try deeper roots
    for extra in range(1, 6):
        new_rel = "/".join([".."] * (i + extra) + rest)
        if (doc.parent / new_rel).resolve().exists():
            return new_rel + (f"#{frag}" if frag else "")
    return None


def main() -> None:
    changed_files = 0
    replacements = 0
    for docs in DOC_ROOTS:
        for md in sorted(docs.rglob("*.md")):
            text = md.read_text(encoding="utf-8")
            out: list[str] = []
            last = 0
            file_hits = 0
            for m in LINK_RE.finditer(text):
                out.append(text[last : m.start()])
                full = m.group(0)
                target = m.group(1)
                fixed = fix_target(md, target)
                if fixed and fixed != target:
                    out.append(f"]({fixed})")
                    file_hits += 1
                    replacements += 1
                else:
                    out.append(full)
                last = m.end()
            out.append(text[last:])
            if file_hits:
                md.write_text("".join(out), encoding="utf-8", newline="\n")
                changed_files += 1
                print(f"{md.relative_to(ROOT)}: {file_hits} fix(es)")
    print(f"done: {replacements} replacements in {changed_files} files")


if __name__ == "__main__":
    main()
