# Hosted UI pack demo (ADR-0054)

Static React/HTML SPA drop-in for Marketplace install.

## Layout

| File | Role |
|------|------|
| `listing.manifest.json` | Marketplace listing (`artifactKind: ui-pack`) |
| `ui-pack.json` | Pack manifest (`appId`, `entry`, `basePath`) |
| `index.html` | Static entry (replace with Vite `dist/` for real apps) |

## Install (local)

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  "$ISPF/api/v1/marketplace/ui-packs/ui-pack-demo/install"
```

Then open `https://<ispf-host>/apps/ui-pack-demo/`.

## Oil Control

Ship `oil-control-azs-web` build with `base: '/apps/oil-control/'` as a separate `artifactKind: ui-pack` listing (slug e.g. `oil-control-ui`). Application listing may set `uiPackSlug` so free install deploys BFF + SPA together.
