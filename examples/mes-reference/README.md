# MES reference bundle

Упрощённый MES-сценарий: **наряд на отгрузку → резервуар → эстакада → завершение**.

| Артефакт | Назначение |
|----------|------------|
| `bundle.json` | Deploy через `POST /api/v1/applications/mes-reference/deploy` |
| `demo-seed.json` | Описание демо-данных (фактический seed — SQL в migration) |

## Быстрый старт

```bash
# из корня репозитория, server на local/test profile
curl -X POST http://localhost:8080/api/v1/applications/mes-reference/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-reference/bundle.json
```

```bash
curl -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{"objectPath":"root.platform.devices.demo-sensor-01","functionName":"mes_listOrders","input":{"schema":{"name":"in","fields":[]},"rows":[{}]}}'
```

Пошаговый walkthrough: [docs/en/reference-mes-walkthrough.md](../../docs/en/reference-mes-walkthrough.md).

## CI

`MesReferenceBundleSmokeTest` деплоит копию bundle из `packages/ispf-server/src/test/resources/mes-reference-bundle.json`.
