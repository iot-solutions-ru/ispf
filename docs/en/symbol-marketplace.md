# Symbol marketplace (BL-185)

Catalog and distribution for SCADA/HMI symbol packs — P&ID primitives, equipment icons, animated states, and mimic widget bindings compatible with [SCADA.md](scada.md) and `diagramJson` v2.

Related: [MARKETPLACE.md](marketplace.md), [SCADA_MIMIC.md](scada-mimic.md), [ROADMAP_PHASE25.md](roadmap-phase-25.md) (BL-146, BL-185).

**Status (0.9.102):** reference pack `ispf-pid-v1` (218 symbols) ships in web-console. Server listing/install API is **stub** (`MarketplaceSymbolListingService`, `"source": "stub"`). See [COMPETITIVE_SCORECARD.md](competitive-scorecard.md).

---

## Scope

| In scope | Out of scope (v1) |
|----------|-------------------|
| SVG symbol libraries (tanks, valves, pumps, pipes) | Full 3D asset store |
| Symbol pack manifest + license metadata | In-editor purchase flow |
| Install via platform marketplace API | Custom symbol editor SaaS |
| Binding hints for mimic widgets | Auto P&ID from CAD import |

---

## Symbol pack format

```json
{
  "packId": "ispf-symbols-pid-v2",
  "version": "2.0.0",
  "displayName": "P&ID Symbol Library v2",
  "license": "SYMBOL-PACK-PID",
  "symbols": [
    {
      "id": "tank-vertical",
      "category": "equipment",
      "tags": ["tank", "storage", "pid"],
      "svgPath": "symbols/tank-vertical.svg",
      "defaultSize": { "width": 80, "height": 120 },
      "bindingSlots": ["fillLevel", "alarmState"]
    }
  ],
  "categories": ["equipment", "valves", "instruments", "piping"]
}
```

| Field | Required | Description |
|-------|:--------:|-------------|
| `packId` | ✓ | Stable identifier |
| `version` | ✓ | Semver |
| `license` | ✓ | SPDX or custom symbol license ref |
| `symbols[].id` | ✓ | Unique within pack |
| `symbols[].svgPath` | ✓ | Relative path inside pack archive |
| `symbols[].bindingSlots` | — | Suggested variable bindings for mimic editor |

Legal: see [apps/web-console/public/legal/SYMBOL-PACK-PID-LICENSE.md](../../apps/web-console/public/legal/SYMBOL-PACK-PID-LICENSE.md).

---

## Distribution

Symbol packs ship through the same marketplace contract as application bundles ([MARKETPLACE.md](marketplace.md)):

| Step | Action |
|------|--------|
| 1 | Vendor publishes listing with `artifactKind: symbol-pack` |
| 2 | Platform downloads pack archive to `ISPF_SYMBOL_PACKS_DIR` |
| 3 | Web Console mimic editor loads catalog from `GET /api/v1/scada/symbol-packs` |
| 4 | Paid packs require entitlement activation (same as app bundles) |

Configuration (planned):

```yaml
ispf:
  scada:
    symbol-packs-dir: ${ISPF_SYMBOL_PACKS_DIR:/opt/ispf/symbol-packs}
    marketplace-enabled: true
```

---

## Web Console integration

| Surface | Behavior |
|---------|----------|
| Mimic editor palette | Browse installed symbol packs by category |
| Drag-drop | Insert symbol with default size + binding slots |
| Marketplace panel | Browse remote symbol listings; install free / activate paid |
| Agent `save_mimic_diagram` | May reference `symbolPackId` + `symbolId` in element metadata |

---

## Vendor requirements

1. SVG optimized for web (no embedded scripts).
2. License file included in pack root.
3. Minimum icon set for category (e.g. 20+ P&ID primitives for `pid` tag).
4. Interop test: one reference mimic using pack symbols passes CI snapshot.

---

## OEM partner path

Symbol vendors join the [Partner program](partner-program.md) at **OEM** level:

- Signed pack uploads
- Marketplace listing review
- Revenue share on paid symbol packs

---

## Roadmap

| ID | Deliverable |
|----|-------------|
| BL-146 | P&ID symbol library v2 (platform reference pack) |
| BL-183 | Marketplace GA (shared install/activate flow) |
| BL-185 | Symbol marketplace docs + listing contract (this document) |

---

## Related documents

- [SCADA.md](scada.md) — mimic architecture
- [WIDGETS.md](widgets.md) — `scada-mimic` widget
- [MARKETPLACE.md](marketplace.md) — catalog API
