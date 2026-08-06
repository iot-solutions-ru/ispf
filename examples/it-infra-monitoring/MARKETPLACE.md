# Marketplace — IT Infrastructure Monitoring (М11)

## Артефакты

| Поле | Значение |
|---|---|
| Package / appId | `it-infra-monitoring` |
| Display name | Мониторинг ИТ-инфраструктуры М11 |
| Bundle | `examples/it-infra-monitoring/bundle.json` **v1.1.1** |
| UI pack | `examples/m11-monitor-ui/m11-monitor-ui-0.1.0.zip` |
| Site plugins | inventory / topology / integrations (`sites/m11`) |
| Catalog | `examples/marketplace-catalog/it-infra-monitoring/` + `…/m11-monitor-ui/` |
| Bridge | `operatorUi.externalSpaUrl` = http://127.0.0.1:5173 |

## Install path

1. Application listing `it-infra-monitoring` (`uiPackSlug: m11-monitor-ui`)
2. UI pack listing `m11-monitor-ui` — static under `/apps/it-infra-monitoring/`
3. Site plugins M11 (inventory → topology → integrations)
4. Smoke: `https://<ispf-host>/apps/it-infra-monitoring/`

## Signed deploy note

Prod with `ispf.license.require-signed-bundles=true` needs a signed `license` block
(`installationId` from `GET /api/v1/platform/installation-id`) or stepwise skeleton deploy.
