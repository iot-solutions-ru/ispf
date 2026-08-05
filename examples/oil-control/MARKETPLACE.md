# Marketplace — Ойл Контроль

## Артефакты

| Поле | Значение |
|---|---|
| Package / appId | `oil-control` |
| Display name | Ойл Контроль |
| Bundle | `examples/oil-control/bundle.json` (+ sql V1–V8) |
| UI pack | `examples/oil-control-ui/oil-control-ui-0.5.0.zip` (`artifactKind: ui-pack`) |
| Версия | `0.5.0` (bundle ↔ SPA) |
| Catalog | `examples/marketplace-catalog/oil-control/` + `…/oil-control-ui/` |
| Bridge | `operatorUi.externalSpaUrl` = http://82.146.32.188/ |

## Install path

1. Application listing `oil-control` (`uiPackSlug: oil-control-ui`)
2. UI pack listing `oil-control-ui` — static under `/apps/oil-control/`
3. Smoke: `https://<ispf-host>/apps/oil-control/` + `/api/v1/bff/invoke`

## Build UI pack

From SPA repo `oil-control-azs-web`:

```bash
npm run pack:ui
```

Copy `pack/oil-control-ui-0.5.0.zip` into `examples/oil-control-ui/` (and catalog mirror).

## Не включать в application JAR

- SPA исходники — только ui-pack zip / отдельный релиз
