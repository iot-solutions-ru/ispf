# oil-control-ui — Hosted SPA pack (ADR-0054)

React SPA **Ойл Контроль** as an ISPF Marketplace **`ui-pack`**.

| | |
|---|---|
| **packId / slug** | `oil-control-ui` |
| **appId** | `oil-control` |
| **version** | `0.5.0` (aligned with application bundle) |
| **Serve path** | `/apps/oil-control/` (same origin as `/api`) |
| **Artifact** | `oil-control-ui-0.5.0.zip` |
| **Entry** | `index.html` |

## Zip layout

```
ui-pack.json
index.html
assets/*
favicon.svg
…
```

`ui-pack.json` (minimum contract):

```json
{
  "appId": "oil-control",
  "packId": "oil-control-ui",
  "version": "0.5.0",
  "entry": "index.html",
  "basePath": "/apps/oil-control/"
}
```

## Build (from SPA repo `oil-control-azs-web`)

```bash
npm ci
npm run pack:ui
# → pack/oil-control-ui-0.5.0.zip
# → pack/ui-pack.json
```

Defaults: Vite `base: '/apps/oil-control/'`, API calls relative `/api/v1` (login + `bff/invoke`).

Standalone root host (demo nginx): `npm run build:standalone` (`base: /`).

## Marketplace

- Listing: `listing.manifest.json` — `artifactKind: ui-pack`
- Application listing `oil-control` sets `uiPackSlug: "oil-control-ui"` so free install can attach SPA with the bundle
- Catalog copy: `examples/marketplace-catalog/oil-control-ui/`

## Smoke (after platform ui-pack install)

1. Install `oil-control` (+ ui-pack) from System → Solutions  
2. Open `https://<ispf-host>/apps/oil-control/`  
3. Login → Balance; BFF calls go to same-origin `/api/v1/bff/invoke`

## Bridge (until ui-pack runtime is live)

Application bundle `operatorUi.externalSpaUrl` = `http://82.146.32.188/` — Operator can show “Open app UI”.
