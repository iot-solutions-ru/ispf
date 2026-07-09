#!/usr/bin/env python3
"""Patch seed-demo.ts LISTINGS from listing manifests (run on marketplace VPS)."""
from __future__ import annotations

import json
import re
from pathlib import Path

SEED_TS = Path("/opt/ispf-marketplace/server/scripts/seed-demo.ts")
CATALOG = Path("/tmp/marketplace-catalog/marketplace-catalog")

SLUG_OVERRIDES = {
    "building-hvac": "building-hvac-reference",
    "mes-reference": "mes-reference-paid-demo",
}


def listing_seed(folder: Path) -> dict:
    m = json.loads((folder / "listing.manifest.json").read_text(encoding="utf-8"))
    slug = SLUG_OVERRIDES.get(m["slug"], m["slug"])
    entry = {
        "slug": slug,
        "title": m["title"],
        "description": m["description"],
        "appId": m["appId"],
        "pricing": m.get("pricing", "free"),
        "bundleFile": f"{slug}-bundle.json",
    }
    if entry["pricing"] == "paid" and m.get("priceCents"):
        entry["priceCents"] = int(m["priceCents"])
    if m.get("minIspfVersion"):
        entry["minIspfVersion"] = m["minIspfVersion"]
    return entry


def render_ts(entries: list[dict]) -> str:
    lines = ["const LISTINGS: ListingSeed[] = ["]
    for e in entries:
        lines.append("  {")
        lines.append(f'    slug: "{e["slug"]}",')
        lines.append(f'    title: {json.dumps(e["title"], ensure_ascii=False)},')
        lines.append(f'    description: {json.dumps(e["description"], ensure_ascii=False)},')
        lines.append(f'    appId: "{e["appId"]}",')
        lines.append(f'    pricing: "{e["pricing"]}",')
        if "priceCents" in e:
            lines.append(f'    priceCents: {e["priceCents"]},')
        lines.append(f'    bundleFile: "{e["bundleFile"]}",')
        if "minIspfVersion" in e:
            lines.append(f'    minIspfVersion: "{e["minIspfVersion"]}",')
        lines.append("  },")
    lines.append("];")
    return "\n".join(lines)


def main() -> None:
    entries = [
        listing_seed(p)
        for p in sorted(CATALOG.iterdir())
        if p.is_dir() and (p / "listing.manifest.json").is_file() and (p / "bundle.json").is_file()
    ]
    block = render_ts(entries)
    text = SEED_TS.read_text(encoding="utf-8")
    new_text, n = re.subn(r"const LISTINGS: ListingSeed\[\] = \[.*?\];", block, text, count=1, flags=re.S)
    if n != 1:
        raise SystemExit("LISTINGS block not found")
    SEED_TS.write_text(new_text, encoding="utf-8")
    print(f"Patched {len(entries)} listings into seed-demo.ts")


if __name__ == "__main__":
    main()
