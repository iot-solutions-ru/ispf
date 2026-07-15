# Мониторинг ИТ инфраструктуры (`it-infra-monitoring`)

Универсальное NMS-приложение ISPF. Пилотная площадка: **Трасса М11** (`m11`).

## Структура

| Слой | Путь |
|------|------|
| Product bundle | `examples/it-infra-monitoring/bundle.json` |
| Site inventory | `plugins/itm-site-inventory/sites/m11/bundle.json` |
| Site topology | `plugins/itm-site-topology/sites/m11/bundle.json` |
| Site integrations | `plugins/itm-site-integrations/sites/m11/bundle.json` |
| Ingress driver packs | `packages/ispf-driver-ingress-{syslog,snmp-trap,sflow}/` |

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

После импорта: приложение **«Мониторинг ИТ инфраструктуры»**, дашборды `itm-*` в `root.platform.dashboards.*`.
