# SCADA symbol library (BL-94)

Complete reference for **P&ID pack symbols**, **custom SVG upload**, and **document library** in ISPF mimic editor.

See also: [SCADA.md](SCADA.md), [SCADA_MIMIC.md](SCADA_MIMIC.md), [tools/symbol-pack-isa](../tools/symbol-pack-isa/README.md).

---

## Catalog overview

| Source | Palette category | `symbolId` | Count |
|--------|------------------|------------|-------|
| ISA/ISO P&ID pack | `pack-valves`, `pack-pumps`, … | `pack.ispf-pid.*` | 57 |
| Inline SVG | `common` | `custom.svg` | 1 template |
| User library | `custom` | `custom:{id}` | Per mimic document |

Pack manifest: `apps/web-console/src/scada/symbols/packs/ispf-pid-v1/manifest.json`  
Regenerate: `npm run build:pid-symbols --prefix apps/web-console`

---

## Standard pack symbols

1. Open mimic editor (Explorer → mimic → **Edit diagram**).
2. Tool **Place** → category **pack-tanks** / **pack-valves** / …
3. Click canvas → element gets `symbolId: pack.ispf-pid.vertical-tank` (example).
4. Bindings in properties panel → device variables.

Pack symbols are **static SVG** in the repo; they are not copied into `diagramJson` unless you convert to library (below).

---

## Custom SVG — three modes

| Mode | When to use | Storage |
|------|-------------|---------|
| **Inline** | One-off graphic | `element.props.svg` on element with `symbolId: custom.svg` |
| **Document library** | Reuse across one mimic | `customSymbols[]` + `symbolId: custom:{id}` |
| **Pack customization** | Start from standard, tweak geometry | Convert pack → library entry |

---

## Upload SVG (user library)

1. Mimic editor → palette category **Custom** (Свои SVG).
2. **Upload SVG** → select `.svg` file (max recommended 256 KB).
3. Symbol appears in palette with `inUserLibrary: true`.
4. Place on canvas like any pack symbol.

**Server-side:** SVG is stored in mimic `diagramJson` on the MIMIC object (no separate asset store).

### Upload rules (enforced in UI)

`sanitizeSvgMarkup()` strips:

- Tags: `script`, `iframe`, `object`, `embed`, `link`, `meta`, `style`, `foreignObject`
- Event handlers: `onclick`, `onload`, …
- Dangerous `href` / `xlink:href` (`javascript:`, `data:text/html`)

Use plain vector markup (`path`, `rect`, `circle`, `g`, `text`). Prefer `fill="var(--text)"` for theme-aware colors.

### Example minimal SVG file

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" height="64">
  <rect x="4" y="4" width="56" height="56" rx="8" fill="#161b22" stroke="#30363d" stroke-width="2"/>
  <text x="32" y="36" text-anchor="middle" fill="#8b949e" font-size="10">PUMP</text>
</svg>
```

After upload: inner HTML stored in `customSymbols[].svg`, default **ports** at N/S/E/W midpoints.

---

## Convert pack symbol to editable library

1. Place a pack symbol (e.g. gate valve).
2. Select element → **Edit as SVG** (Редактировать как SVG).
3. Edit markup in code panel or upload replacement.
4. **Update symbol in library** → sets `inUserLibrary: true`, adds to **Custom** palette.
5. New instances use `symbolId: custom:{id}`; original pack id preserved in `props.sourceSymbolId` if converted via editor.

Bulk convert (legacy diagrams): editor menu **Convert built-ins to library** — one-time migration for old React symbol ids.

---

## Behaviors and bindings

For live HMI (color, level, visibility):

```json
{
  "id": "tank-1",
  "symbolId": "custom:lib-tank-a",
  "bindings": [
    { "target": "fill", "objectPath": "root.platform.devices.t1", "variable": "level", "field": "value" }
  ],
  "props": { "behaviors": [{ "type": "tankLevel", "binding": "level" }] }
}
```

Define `bindingSchema` on `customSymbols[]` entry for editor hints. Reference: `packages/ispf-server/src/main/resources/bootstrap/mini-tec-mimic.json`.

---

## Import / export

| Action | Path |
|--------|------|
| Export full diagram | Editor → Import/Export → copy `diagramJson` |
| CI re-export | `npx tsx src/scada/templates/exportTankFarmMimic.ts` |
| Bootstrap seed | `PUT /api/v1/mimics/by-path/diagram` |

`customSymbols` travels with `diagramJson` — backup mimics before bulk edits.

---

## CI / tests

| Test | Path |
|------|------|
| Pack manifest load | `symbolPackLoader.test.ts` — ≥50 symbols, 8 categories |
| SVG sanitize / upload parse | `customSvg.test.ts` |
| Behavior engine | `symbolBehaviors.test.ts`, `svgSymbolEngine.test.ts` |

```bash
cd apps/web-console && npm run test -- src/scada/customSvg.test.ts src/scada/symbols/symbolPackLoader.test.ts
```

---

## Legal

- **ispf-pid-v1** pack: Apache-2.0 original work ([LICENSE](../apps/web-console/src/scada/symbols/packs/ispf-pid-v1/LICENSE.md)).
- Do **not** import vendor SymbolFactory / TIA / copyrighted P&ID art.
- Legacy WMF importer (`tools/symbol-import/`) is **deprecated**.

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Custom symbol not in palette | Set `inUserLibrary: true` on `customSymbols[]` entry |
| Upload blank / broken | Ensure root `<svg>` with valid `viewBox`; avoid `<style>` blocks |
| Colors wrong in dark theme | Use `var(--text)`, `var(--border)` or pack CSS vars |
| Pack category empty | Run `npm run build:pid-symbols`; hard-refresh browser |
