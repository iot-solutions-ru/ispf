# storetwin-ui — Hosted SPA pack (ADR-0054)

| | |
|---|---|
| **packId** | `storetwin-ui` |
| **appId** | `storetwin` |
| **basePath** | `/apps/storetwin/` |
| **version** | `1.0.0` |
| **artifact** | `storetwin-ui-1.0.0.zip` |
| **listing** | `listing.manifest.json` · catalog `../marketplace-catalog/storetwin-ui/` |

## Install

Marketplace listing `storetwin-ui`, or drop zip into `ISPF_UI_PACKS_DIR/storetwin/` (`ui-pack.json` + `index.html`).

After install: `https://<ispf-host>/apps/storetwin/`

## Build

From the StoreTwin SPA project:

```bash
cd app && npm run pack:ui
# → ispf/storetwin-ui-1.0.0.zip + ispf/ui-pack/
```

Vite `base: '/apps/storetwin/'`. Live data via `/api/v1/bff/invoke` (service login); `VITE_DATA_SOURCE=mock` for offline.
