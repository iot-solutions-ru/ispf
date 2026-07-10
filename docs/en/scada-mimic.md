> **Language:** Canonical English. Russian edition: [ru/scada-mimic.md](../ru/scada-mimic.md).

# SCADA mimic — diagramJson and API reference

Technical reference for document format and REST API. Overview of capabilities, workflow, and architecture: **[scada](scada.md)**.

---

## Concepts

| Concept | Description |
|---------|-------------|
| `scada-mimic` widget | Dashboard widget that renders a mimic document |
| `MIMIC` object | Reusable diagram at `root.platform.mimics.*` (`mimic-v1` model) |
| `diagramJson` | JSON document: elements, connections, bindings, customSymbols |
| `grid.snap` | When `true`, placement and drag snap to `grid.size` (default **off**; toggle in editor toolbar) |
| `grid.visible` | Show editor grid overlay (default **off**; toggle in editor toolbar) |
| Symbol registry | Pack SVG (`pack.ispf-pid.*`) + per-document `customSymbols` |

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
    "symbolId": "pack.ispf-pid.vertical-tank",
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
| `symbolId` | Pack id (`pack.ispf-pid.vertical-tank`), `custom:{libraryId}` or `custom.svg` |
| `x`, `y` | Top-left position on artboard (px) |
| `rotation` | `0` \| `90` \| `180` \| `270` |
| `scale` | Optional multiplier; editor resize writes `props.width/height` and sets `scale` to `1` |
| `bindings` | Map binding key → `MimicBinding` |
| `actions` | Operator click handlers |
| `formatRules` | Conditional styling by binding value |
| `labels` | Text labels on symbol |
| `tooltip` | Hover text (recommended actions, alarm hints) |
| `props` | Symbol-specific props (see below) |

### Mimic actions (`actions[]`)

| `type` | Fields | Runtime behavior |
|--------|--------|------------------|
| `toggleVariable` | `objectPath`, `variableName`, optional `value` | Flip or set a boolean/command variable |
| `invokeFunction` | `objectPath`, `functionName`, optional `paramsJson` | Call object function |
| `navigate` | `dashboardPath`, optional `selectionJson` | Open another dashboard; optional `{"equipment":"..."}` seeds session selection |
| `setSelection` | `selectionKey` + `objectPath`, **or** `selectionJson` | Stay on current HMI; update dashboard session selection (e.g. equipment card drill-down) |
| `pulse` (function) | `sourceType: "pulse"`, `sourceBody: {"variable":"cmdStart"}` | Platform handler writes boolean command variable on invoke |
| `toggleLayer` | `layerId` | Show/hide mimic layer |
| `toggleExpand` | `elementId` | Expand/collapse symbol group |
| `cycleUnit` | `bindingKey`, `units[]` | Cycle displayed engineering unit |

**Integrated operator HMI (mini-TEC):** bind GPU/GRPB clicks to `setSelection` with `selectionKey: "equipment"` so the right-hand card panel updates without leaving `mini-tec-hmi`. Use `navigate` only when opening a separate detail dashboard.

Example `setSelection`:

```json
{
  "id": "sel-gpu2",
  "type": "setSelection",
  "selectionKey": "equipment",
  "objectPath": "root.platform.devices.mini-tec-plant.gpu-02"
}
```

Or multiple slots via `selectionJson`:

```json
{
  "id": "sel-hub",
  "type": "setSelection",
  "selectionJson": "{\"equipment\":\"root.platform.devices.mini-tec-plant.station-hub\"}"
}
```

**PNG export:** operator mimic widget toolbar → Export PNG (`ScadaMimicWidgetView`).

**Common `props` keys (editor):**

| Key | Description |
|-----|-------------|
| `width`, `height` | Explicit symbol size in px (overrides registry default when set) |
| `flipX`, `flipY` | Boolean mirror flags (toolbar Flip H/V) |
| `svg`, `viewBox`, … | Custom SVG inner markup (`custom.svg` / `custom:{id}`) |

Effective render size: `symbolSize()` in `registry.ts` — `(props.width \|\| defaultWidth) * (scale ?? 1)`.

### `customSymbols[]` (document library)

SVG symbol definitions referenced by elements with `symbolId: "custom:{id}"`. Fields:

| Field | Description |
|-------|-------------|
| `id` | Unique id in document (element: `custom:{id}`) |
| `name` | Label in editor |
| `svg` | Inner SVG markup (without root `<svg>`) |
| `width`, `height`, `viewBox` | Geometry and viewport |
| `ports` | Connect points |
| `bindingSchema` | Binding slots for properties panel |
| `behaviors` | Dynamic SVG on HMI (see [scada.md § Custom SVG](scada.md)) |
| `sourceSymbolId` | Optional: source pack/legacy id |
| `inUserLibrary` | `true` — symbol visible in **My SVG** palette |

Bootstrap entries without `inUserLibrary` are used only for rendering (mini-TEC, pipeline).

### Connection routing

Stored `points` are updated when endpoints move. Runtime display and reroute always recompute an **orthogonal** path from port positions (`routeOrthogonal` in `connectionRouting.ts`).

---

## Editor entry points

- **Dashboard Builder:** widget `scada-mimic` → Open mimic editor
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

Overview: [scada.md § Editor](scada.md).

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
| Pipeline SCADA | `root.platform.mimics.pipeline-rp` | `root.platform.dashboards.pipeline-scada-hmi` | `pipeline-scada` |
| Mini-TEC SLD | `root.platform.mimics.mini-tec-single-line` | `root.platform.dashboards.mini-tec-hmi` (default operator) | `mini-tec` |
| Mini-TEC zones | `mini-tec-zone-gas`, `mini-tec-zone-electrical` | tabs on `mini-tec-hmi` | `mini-tec` |

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

## Symbol catalog

Editor palette — **SVG only**:

| Category | `symbolId` prefix | Notes |
|----------|-------------------|--------|
| `pack-valves`, `pack-pumps`, `pack-tanks`, `pack-pipes`, `pack-sensors`, `pack-electrical`, `pack-isa`, `pack-misc` | `pack.ispf-pid.` | Standard P&ID pack (~57); see `tools/symbol-pack-isa` |
| `common` | `custom.svg` | Inline SVG in element props |
| Custom (palette) | `custom:` | Only defs with `inUserLibrary: true` |

Dynamic symbols (labels, GPU blocks, breakers with live state): define in `customSymbols[]` with `behaviors` + `bindingSchema`. Reference: `mini-tec-mimic.json`.

Full guide: [scada.md § Symbol catalog](scada.md), [scada-symbol-library](scada-symbol-library.md).
