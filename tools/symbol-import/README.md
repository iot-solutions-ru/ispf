# P&ID symbol import pipeline

Imports P&ID equipment symbols from **local** Siemens WinCC / TIA Portal graphics archives into the ISPF SCADA symbol pack.

## Prerequisites

1. **Source archives** (not redistributed — place locally):
   - `SIMATIC WinCC flexible Graphics.zip`
   - `TIA_Portal_Graphics_DVDpart.zip`

   Default paths: `%USERPROFILE%\Downloads\`. Override with env vars `WINCC_ZIP`, `TIA_ZIP`.

2. **[Inkscape](https://inkscape.org/)** for WMF/EMF → SVG conversion:
   ```powershell
   winget install Inkscape.Inkscape
   ```

## Usage

```bash
cd tools/symbol-import
npm install
npm run import:pid          # full pipeline
npm run import:pid -- --limit 100   # smoke test (first 100 symbols)
```

Individual steps:

```bash
npm run catalog    # extract-catalog.ts → .work/catalog.json
npm run convert    # convert-wmf.ts → .work/svg-raw/
npm run stylize    # stylize-svg.ts → .work/svg-stylized/
npm run build      # build-pack.ts → apps/web-console/.../packs/ispf-pid-v1/
```

## Output

Stylized SVG symbols are written to:

`apps/web-console/src/scada/symbols/packs/ispf-pid-v1/`

Original WMF/EMF files are **not** committed to the repository.

## Legal

SymbolFactory / TIA graphics are copyrighted by Siemens. This pipeline produces **restyled simplified SVG** for use within ISPF only. Do not redistribute raw Siemens assets.
