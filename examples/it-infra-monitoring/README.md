# Мониторинг ИТ инфраструктуры (`it-infra-monitoring`)

Универсальное NMS-приложение ISPF. Пилотная площадка: **Трасса М11** (`m11`).

## Структура

| Слой | Путь |
|------|------|
| Product bundle | `examples/it-infra-monitoring/bundle.json` (**v1.1.1**, M11 MMI) |
| M11 overlay | `examples/it-infra-monitoring/m11-ui-overlay.json` (applied by `build-bundle.mjs`) |
| UI pack | `examples/m11-monitor-ui/` (`m11-monitor-ui-0.1.0.zip`, base `/apps/it-infra-monitoring/`) |
| Site inventory | `plugins/itm-site-inventory/sites/m11/bundle.json` |
| Site topology | `plugins/itm-site-topology/sites/m11/bundle.json` |
| Site integrations | `plugins/itm-site-integrations/sites/m11/bundle.json` |
| Ingress driver packs | `packages/ispf-driver-ingress-{syslog,snmp-trap,sflow}/` |
| Marketplace | see [MARKETPLACE.md](MARKETPLACE.md) |

## Сборка bundle

```bash
node examples/it-infra-monitoring/scripts/build-bundle.mjs
node plugins/itm-site-inventory/sites/m11/build-inventory.mjs
node plugins/itm-site-topology/sites/m11/build-topology.mjs
```

## Деплой на ISPF (пилот 185.246.66.158)

Требуется JWT (Keycloak) или сессия оператора. Переменные:

- `ISPF_BASE_URL` — по умолчанию `http://185.246.66.158:8080`
- `ISPF_TOKEN` — Bearer token

```powershell
$env:ISPF_TOKEN = "<jwt>"
.\examples\it-infra-monitoring\scripts\deploy-pilot.ps1
```

Порядок: ingress driver packs → product → site plugins → mimic diagram + SVG assets.

## Driver packs

```bash
./gradlew :packages:ispf-driver-ingress-syslog:assembleDriverPack
./gradlew :packages:ispf-driver-ingress-snmp-trap:assembleDriverPack
./gradlew :packages:ispf-driver-ingress-sflow:assembleDriverPack
```

Скопировать артефакты в `${ISPF_DRIVER_PACKS_DIR}` и перезапустить ISPF.

## Operator UI

После импорта: приложение **«Мониторинг ИТ-инфраструктуры М11»**, дашборды `itm-*`.

React MMI: ui-pack `m11-monitor-ui` → `/apps/it-infra-monitoring/` (или `operatorUi.externalSpaUrl` на Vite `http://127.0.0.1:5173`).
`operatorUi.spaNav` / `uiPack` — в `bundle.json` и `m11-ui-overlay.json`.
