# SCADA mimic — справочник diagramJson и API

Технический справочник формата документа и REST API. Обзор возможностей, workflow и архитектуры: **[SCADA.md](SCADA.md)**.

---

## Concepts

| Concept | Description |
|---------|-------------|
| `scada-mimic` widget | Dashboard widget that renders a mimic document |
| `MIMIC` object | Reusable diagram at `root.platform.mimics.*` (`mimic-v1` model) |
| `diagramJson` | JSON document: elements, connections, bindings, customSymbols |
| `grid.snap` | When `true`, placement and drag snap to `grid.size` (default **off**; toggle in editor toolbar) |
| `grid.visible` | Show editor grid overlay (default **off**; toggle in editor toolbar) |
| Symbol registry | Built-in SVG symbols + per-document `customSymbols` |

---

## Document schema (v2)

Only **version 2** is supported. Legacy v1 documents are normalized to v2 on load (empty v1 → default empty document).

```json
{
  "version": 2,
  "width": 1600,
  "height": 900,
  "background": "var(--bg)",
  "grid": { "size": 1, "snap": false, "visible": false },
  "layers": [{ "id": "layer-default", "name": "Main", "visible": true }],
  "elements": [{
    "id": "t1",
    "symbolId": "tank.vertical",
    "layerId": "layer-default",
    "x": 100,
    "y": 80,
    "rotation": 0,
    "bindings": {
      "fillLevel": {
        "objectPath": "root.platform.devices.demo-sensor-01",
        "variableName": "level",
        "valueField": "value",
        "transform": "number"
      }
    },
    "actions": [{
      "id": "act1",
      "type": "toggleVariable",
      "objectPath": "root.platform.devices.demo-valve-01",
      "variableName": "open",
      "valueField": "value"
    }]
  }],
  "connections": [{
    "id": "c1",
    "layerId": "layer-default",
    "from": { "elementId": "t1", "port": "e" },
    "to": { "elementId": "v1", "port": "n" },
    "points": [
      { "x": 180, "y": 140 },
      { "x": 249, "y": 140 },
      { "x": 249, "y": 120 },
      { "x": 318, "y": 120 }
    ],
    "bindings": {
      "flowing": {
        "objectPath": "root.platform.devices.demo-pump-01",
        "variableName": "running",
        "valueField": "value",
        "transform": "bool"
      }
    }
  }],
  "customSymbols": []
}
```

### Element fields

| Field | Description |
|-------|-------------|
| `symbolId` | Built-in id (`tank.vertical`) or `custom:{id}` / `custom.svg` |
| `x`, `y` | Top-left position on artboard (px) |
| `rotation` | `0` \| `90` \| `180` \| `270` |
| `scale` | Optional multiplier; editor resize writes `props.width/height` and sets `scale` to `1` |
| `bindings` | Map binding key → `MimicBinding` |
| `formatRules` | Conditional styling by binding value |
| `labels` | Text labels on symbol |
| `actions` | Operator click handlers |
| `props` | Symbol-specific props (see below) |

**Common `props` keys (editor):**

| Key | Description |
|-----|-------------|
| `width`, `height` | Explicit symbol size in px (overrides registry default when set) |
| `flipX`, `flipY` | Boolean mirror flags (toolbar Flip H/V) |
| `svg`, `viewBox`, … | Custom SVG inner markup (`custom.svg` / `custom:{id}`) |

Effective render size: `symbolSize()` in `registry.ts` — `(props.width \|\| defaultWidth) * (scale ?? 1)`.

### Connection routing

Stored `points` are updated when endpoints move. Runtime display and reroute always recompute an **orthogonal** path from port positions (`routeOrthogonal` in `connectionRouting.ts`).

---

## Editor entry points

- **Dashboard Builder:** widget `scada-mimic` → «Open mimic editor»
- **Explorer:** `root.platform.mimics` → create mimic → open `MIMIC` object

### Tools (toolbar + keyboard)

| Tool | Keys | Notes |
|------|------|-------|
| Select | `V` | Click, Shift+multi-select, drag, resize handles (single selection) |
| Place | `P` | Palette → canvas click |
| Connect | `C` | Port-to-port orthogonal line |
| Flip H / V | toolbar | Toggles `props.flipX` / `props.flipY` |
| Rotate ±90° | toolbar | Cycles `rotation` |
| Align L/C/R/T/M/B | toolbar | Requires ≥ 2 selected elements |
| Distribute H/V | toolbar | Requires ≥ 3 selected elements |
| Grid visible / snap | toolbar | Toggles `grid.visible` / `grid.snap` |
| Undo / Redo | `Ctrl+Z` / `Ctrl+Y` | Document history |
| Delete | `Del`, `Backspace` | Selected elements or connection |
| Import / Export JSON | panel | Raw `diagramJson` edit |

Smart-snap during drag: element edges/centers/ports align to other elements within ~10 px (`elementSnap.ts`). Group drag moves all selected elements together.

Implementation: `ScadaMimicEditor.tsx`, `ScadaMimicCanvas.tsx`, `layoutOps.ts`, `elementSnap.ts`.

Overview (RU): [SCADA.md § Редактор](SCADA.md#редактор-мнемосхемы).

---

## REST API

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/mimics/by-path?path=` | Load mimic |
| PUT | `/api/v1/mimics/by-path/diagram?path=` | Save `diagramJson` |
| PUT | `/api/v1/mimics/by-path/title?path=` | Save title |

---

## Bootstrap demos

| Demo | Mimic | Dashboard | appId |
|------|-------|-----------|-------|
| Tank farm | `root.platform.mimics.tank-farm-demo` | `root.platform.dashboards.tank-farm-hmi` | `tank-farm-demo` |
| Pipeline SCADA (РП) | `root.platform.mimics.pipeline-rp` | `root.platform.dashboards.pipeline-scada-hmi` | `pipeline-scada` |
| Mini-TEC SLD | `root.platform.mimics.mini-tec-single-line` | `root.platform.dashboards.mini-tec-single-line` | — |

Devices (tank farm): `root.platform.devices.tank-farm-demo.tank-11` … `tank-24`, `manifold-hub`.
Virtual driver profiles: `tank-farm-tank`, `tank-farm-hub`.

Re-export server JSON after editing TypeScript templates:

```bash
cd apps/web-console && npx tsx -e "import { MINI_TEC_SLD_DOCUMENT_JSON } from './src/scada/templates/miniTecSld.ts'; import fs from 'fs'; fs.writeFileSync('../../packages/ispf-server/src/main/resources/bootstrap/mini-tec-mimic.json', MINI_TEC_SLD_DOCUMENT_JSON)"
```

```bash
cd apps/web-console && npx tsx src/scada/templates/exportTankFarmMimic.ts
```

```bash
cd apps/web-console && npx tsx src/scada/templates/pipeline-scada/exportPipelineScadaMimics.ts
```

---

## Symbol categories

- **process:** tanks, valves, pumps, pipes, sensors, pipeline track, compressors, …
- **electrical:** generators, breakers, busbars, transformers, motors, …
- **common:** labels, tables, alarm banner, shapes

Extend the catalog in `apps/web-console/src/scada/symbols/`.
