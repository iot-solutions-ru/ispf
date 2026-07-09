#!/usr/bin/env python3
"""Generate seed-demo.ts LISTINGS block from examples/marketplace-catalog."""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "examples" / "marketplace-catalog"

# Slug overrides where live marketplace uses a different slug than folder name
SLUG_OVERRIDES: dict[str, str] = {
    "building-hvac": "building-hvac-reference",
    "mes-reference": "mes-reference-paid-demo",
}


def main() -> None:
    catalog = Path(sys.argv[1]) if len(sys.argv) > 1 else CATALOG
    entries: list[dict] = []
    for folder in sorted(catalog.iterdir()):
        if not folder.is_dir():
            continue
        manifest_path = folder / "listing.manifest.json"
        bundle_path = folder / "bundle.json"
        if not manifest_path.is_file() or not bundle_path.is_file():
            continue
        m = json.loads(manifest_path.read_text(encoding="utf-8"))
        slug = SLUG_OVERRIDES.get(m["slug"], m["slug"])
        pricing = m.get("pricing", "free")
        entry: dict = {
            "slug": slug,
            "title": m["title"],
            "description": m["description"],
            "appId": m["appId"],
            "pricing": pricing,
            "bundleFile": f"{slug}-bundle.json",
        }
        if pricing == "paid" and m.get("priceCents"):
            entry["priceCents"] = int(m["priceCents"])
        if m.get("minIspfVersion"):
            entry["minIspfVersion"] = m["minIspfVersion"]
        entries.append(entry)

    print("const LISTINGS: ListingSeed[] = [")
    for e in entries:
        lines = [f'  {{']
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
        print("\n".join(lines))
    print("];")


if __name__ == "__main__":
    main()
