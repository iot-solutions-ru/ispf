# Marketplace — StoreTwin

## Артефакты

| Поле | Значение |
|---|---|
| Package / appId | `storetwin` |
| Display name | Цифровой двойник розничной точки |
| Bundle | `examples/storetwin/bundle.json` (+ `sql/st_v2_ui_parity.sql`) |
| UI pack | `examples/marketplace-catalog/storetwin-ui/storetwin-ui-1.0.0.zip` (`artifactKind: ui-pack`) |
| Версия | app `1.1.6` · ui-pack `1.0.0` |
| Catalog | `examples/marketplace-catalog/storetwin/` + `…/storetwin-ui/` |
| Bridge | `operatorUi.externalSpaUrl` = https://ispf.ai/apps/storetwin/ |

## Install path

1. Application listing `storetwin` (`uiPackSlug: storetwin-ui`)
2. UI pack listing `storetwin-ui` — static under `/apps/storetwin/`
3. Smoke: `https://<ispf-host>/apps/storetwin/` + Operator `/?mode=operator&app=storetwin` + `/api/v1/bff/invoke`

## Build UI pack

From SPA repo (`app/` in StoreTwin project):

```bash
cd app && npm run pack:ui
```

Copy `ispf/storetwin-ui-1.0.0.zip` into `examples/marketplace-catalog/storetwin-ui/` (the only tracked copy; `tools/repo-hygiene/check-tracked-binaries.mjs` rejects mirrors).

## Не включать в application JAR

- SPA исходники — только ui-pack zip / отдельный релиз
