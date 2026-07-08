#!/usr/bin/env python3
import re
from pathlib import Path

p = Path("tools/ai-pack/build.py")
t = p.read_text(encoding="utf-8")

def kebab_path(m: re.Match) -> str:
    name = m.group(1).lower().replace("_", "-")
    return f'DOCS / "{name}.md"'

t = re.sub(r'DOCS / "([A-Z][A-Z0-9_]+)\.md"', kebab_path, t)
t = t.replace('"path": f"docs/{path.name}"', '"path": f"docs/en/{path.name}"')
t = t.replace('"path": f"docs/decisions/{path.name}"', '"path": f"docs/en/decisions/{path.name}"')
t = t.replace('if path.name == "README.md"', 'if path.name.lower() == "readme.md"')
p.write_text(t, encoding="utf-8")
print("patched build.py")
