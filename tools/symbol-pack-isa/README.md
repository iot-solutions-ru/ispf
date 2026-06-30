# ISA / ISO P&ID symbol pack generator

Generates **original** functional P&ID SVG symbols for ISPF SCADA (no WMF tracing).

## Build

```bash
cd tools/symbol-pack-isa
npm install
npm run build    # writes apps/web-console/src/scada/symbols/packs/ispf-pid-v1/
npm test
```

From repo root (web-console):

```bash
npm run build:pid-symbols --prefix apps/web-console
```

## Adding symbols

Edit `src/symbols.ts` — use helpers from `src/svg.ts` (64×64 canvas, CSS variables).

Conventions: ISA-5.1 instrument bubbles (PI, TI, FT, …), valve wedges, equipment outlines.

## Legal

Output is **Apache-2.0** original work. Do **not** import vendor SymbolFactory/TIA artwork
into this pipeline. Legacy WMF importer: `tools/symbol-import/` (**deprecated**).
