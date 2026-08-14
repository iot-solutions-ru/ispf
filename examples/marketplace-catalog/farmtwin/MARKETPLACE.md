# Marketplace — FarmTwin

## Артефакты

| Поле | Значение |
|------|----------|
| Package / appId | `farmtwin` |
| Display name | Цифровой двойник фермы |
| Bundle | `marketplace/farmtwin/bundle.json` |
| UI pack | `marketplace/farmtwin-ui/farmtwin-ui-1.0.0.zip` (`artifactKind: ui-pack`) |
| Версия | app `1.1.0` · ui-pack `1.0.0` |
| Catalog | `marketplace/marketplace-catalog/farmtwin/` + `…/farmtwin-ui/` |
| Companion | `uiPackSlug: farmtwin-ui` |
| Hosted UI | `/apps/farmtwin/` (Open app UI) |

## Install path

1. Application listing `farmtwin` (`uiPackSlug: farmtwin-ui`) — schema, seed, BFF, operator dashboards
2. Companion ui-pack `farmtwin-ui` — static under `/apps/farmtwin/`
3. Smoke:
   - `https://<ispf-host>/apps/farmtwin/`
   - Operator `/?mode=operator&app=farmtwin` → **Open app UI**
   - `POST /api/v1/bff/invoke` → `ft_listSites` on hub `root.platform.devices.farmtwin.hub`

## Build (this repo)

```bash
cd app && npm run pack:solution
# → ispf/marketplace/{farmtwin,farmtwin-ui,marketplace-catalog/…}
# → ispf/bundle.json + ispf/farmtwin-ui-1.0.0.zip
```

## Stand (air-gap / without marketplace)

```bash
# BFF + operator
python3 ispf/generate_bundle.py && python3 ispf/deploy_piecemeal.py

# UI pack → Open app UI
ISPF_SSH_PASS='…' ./ispf/install-on-stand.sh
```

## PR into ISPF

Copy `ispf/marketplace/*` into `examples/` and `examples/marketplace-catalog/` (see `marketplace/README.md`).

## Не включать в application JAR

- SPA исходники — только ui-pack zip / отдельный релиз
