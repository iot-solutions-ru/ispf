# Symbol marketplace (BL-185)

Catalog and distribution for SCADA/HMI symbol packs — P&ID primitives, equipment icons, animated states, and mimic widget bindings compatible with [scada](scada.md) and `diagramJson` v2.

Related: [marketplace](marketplace.md), [scada-mimic](scada-mimic.md), [roadmap](roadmap.md) (BL-146, BL-185).

**Status:** **Done (local GA)** — filesystem install under `ISPF_SYMBOL_PACKS_DIR`, `GET /api/v1/scada/symbol-packs`, mimic palette loads installed packs. Reference pack `ispf-pid-v1` remains bundled in web-console. Remote partner review / paid OEM path still follows [partner-program](partner-program.md).

---

## Scope

| In scope | Out of scope (v1) |
|----------|-------------------|
| SVG symbol libraries (tanks, valves, pumps, pipes, HVAC) | Full 3D asset store |
| Symbol pack manifest + license metadata | In-editor purchase flow |
| Install via platform marketplace API → filesystem | Custom symbol editor SaaS |
| Mimic palette loads installed packs | Auto P&ID from CAD import |

---

## Symbol pack format

Runtime format (what the mimic editor loads) — category JSON with **inline SVG**:

```
{packId}/
  manifest.json
  equipment.json   # PackSymbolRecord[]
  LICENSE.md
```

`manifest.json`:

```json
{
  "version": 1,
  "id": "hvac-equipment-v1",
  "license": "Apache-2.0",
  "totalSymbols": 4,
  "categories": [
    { "id": "pack-hvac", "file": "equipment.json", "count": 4 }
  ]
}
```

Legal: see [apps/web-console/public/legal/SYMBOL-PACK-PID-LICENSE.md](../../apps/web-console/public/legal/SYMBOL-PACK-PID-LICENSE.md) and [pid-symbols-legal](pid-symbols-legal.md).

---

## Distribution

| Step | Action |
|------|--------|
| 1 | Vendor publishes listing with `artifactKind: symbol-pack` (or local `examples/marketplace-symbol-*`) |
| 2 | Platform installs pack under `ISPF_SYMBOL_PACKS_DIR/{packId}/` |
| 3 | Mimic editor loads `GET /api/v1/scada/symbol-packs` + `/{packId}` |
| 4 | Paid remote packs use the same entitlement activate path as app bundles (zip artifact) |

### Configuration

```yaml
ispf:
  scada:
    symbol-packs-dir: ${ISPF_SYMBOL_PACKS_DIR:./data/symbol-packs}
```

### API

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/marketplace/symbols` | Catalog (bundled `ispf-pid-v1` + local examples) |
| POST | `/api/v1/marketplace/symbols/{id}/install` | Install free local/remote pack |
| GET | `/api/v1/scada/symbol-packs` | Installed packs on this node |
| GET | `/api/v1/scada/symbol-packs/{packId}` | Pack detail + symbols for palette |

### Demo listing

| Path | Purpose |
|------|---------|
| [examples/marketplace-symbol-hvac-demo](../../examples/marketplace-symbol-hvac-demo/) | Free HVAC pack (`hvac-equipment-v1`) |

```bash
curl -X POST /api/v1/marketplace/symbols/hvac-equipment-v1/install
curl /api/v1/scada/symbol-packs/hvac-equipment-v1
```

---

## Web Console integration

| Surface | Behavior |
|---------|----------|
| Mimic editor palette | Bundled `ispf-pid-v1` + installed drop-in packs (`ensureInstalledPacksLoaded`) |
| Drag-drop | Insert symbol with default size + ports |
| Marketplace | `GET/POST /api/v1/marketplace/symbols` |

---

## Vendor requirements

1. SVG optimized for web (no embedded scripts).
2. License file included in pack root.
3. Minimum icon set for category (e.g. 20+ P&ID primitives for `pid` tag; HVAC demo uses 4 for lab).
4. Interop: pack symbols render in mimic editor after install.

---

## OEM partner path

Symbol vendors join the [Partner program](partner-program.md) at **OEM** level (BL-184 — secondary to this GA):

- Signed pack uploads
- Marketplace listing review
- Revenue share on paid symbol packs

---

## Related documents

- [scada](scada.md) — mimic architecture
- [widgets](widgets.md) — `scada-mimic` widget
- [marketplace](marketplace.md) — catalog API
