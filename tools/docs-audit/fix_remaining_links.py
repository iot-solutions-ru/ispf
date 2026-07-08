#!/usr/bin/env python3
"""Final pass: fix remaining broken doc links in docs/en and docs/ru."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

UPPER_TO_KEBAB = {
    "ROADMAP.md": "roadmap.md",
    "DEPLOYMENT.md": "deployment.md",
    "FEDERATION.md": "federation.md",
    "OBJECT_MODEL.md": "object-model.md",
    "VARIABLE_HISTORY.md": "variable-history.md",
    "LOAD_TESTING.md": "load-testing.md",
    "WEB_CONSOLE.md": "web-console.md",
    "AI_DEVELOPMENT.md": "ai-development.md",
    "COMMERCIAL_LICENSING.md": "commercial-licensing.md",
    "LICENSED_DRIVER_PACKS.md": "licensed-driver-packs.md",
    "THIRD_PARTY_NOTICES.md": "third-party-notices.md",
    "SOLUTION_DEVELOPER_PUBLIC_API.md": "solution-developer-public-api.md",
    "APPLICATIONS.md": "applications.md",
    "DRIVERS.md": "drivers.md",
    "CLUSTER.md": "cluster.md",
    "ARCHITECTURE.md": "architecture.md",
    "APPLICATION_PRINCIPLES.md": "application-principles.md",
}

REPO_ROOT_LINKS = {
    "license-commercial.md": "../../LICENSE-COMMERCIAL.md",
    "LICENSE-COMMERCIAL.md": "../../LICENSE-COMMERCIAL.md",
    "cla.md": "../../CLA.md",
    "CLA.md": "../../CLA.md",
    "symbol-pack-pid-license.md": "../../apps/web-console/public/legal/SYMBOL-PACK-PID-LICENSE.md",
}


def fix_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    orig = text
    rel_prefix = "../" if "decisions" in path.parts else "../../"

    for old, new in UPPER_TO_KEBAB.items():
        text = text.replace(f"]({old}", f"]({new}")
        text = text.replace(f"](../{old}", f"]({rel_prefix}{new}" if path.parent.name == "decisions" else f"](../{new}")
        text = text.replace(f"](../../{old}", f"](../../{new}")
        # decisions: ../ROADMAP.md
        text = text.replace(f"](../{old}", f"](../{new}")

    for old, new in REPO_ROOT_LINKS.items():
        text = text.replace(f"]({old})", f"]({new})")
        text = text.replace(f"]({old}#", f"]({new}#")

    # decisions without ../ prefix mistake: (ROADMAP.md# -> (../roadmap.md#
    text = re.sub(r"\]\(ROADMAP\.md", "](../roadmap.md", text)

    if text != orig:
        path.write_text(text, encoding="utf-8")
        return True
    return False


def main() -> None:
    n = 0
    for lang in ("en", "ru"):
        for md in (ROOT / "docs" / lang).rglob("*.md"):
            if fix_file(md):
                n += 1
                print(f"fixed: {md.relative_to(ROOT)}")
    print(f"done: {n} files")


if __name__ == "__main__":
    main()
