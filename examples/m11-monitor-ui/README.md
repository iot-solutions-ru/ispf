# m11-monitor-ui — Hosted SPA pack (ITM / Трасса М11)

React MMI **ИТ-Мониторинг** as an ISPF Marketplace **`ui-pack`**.

| | |
|---|---|
| **packId / slug** | `m11-monitor-ui` |
| **appId** | `it-infra-monitoring` |
| **version** | `0.1.0` |
| **Serve path** | `/apps/it-infra-monitoring/` |
| **Artifact** | `m11-monitor-ui-0.1.0.zip` |
| **Entry** | `index.html` |

## Zip layout

```
ui-pack.json
index.html
assets/*
brand/*
…
```

## Build (from SPA repo `M11/m11-monitor`)

```bash
npm ci
npm run pack:ui
# → pack/m11-monitor-ui-0.1.0.zip
```

Copy zip + `ui-pack.json` into this folder (and `examples/marketplace-catalog/m11-monitor-ui/`).

## Bridge (until ui-pack is mounted on nginx)

Application bundle `operatorUi.externalSpaUrl` = `http://127.0.0.1:5173` (local Vite).
Production target: `https://<ispf-host>/apps/it-infra-monitoring/`.
