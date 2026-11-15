# Plant North

Fork of the demo SQL reports ([demo-app](../demo-app/)) with `appId=plant-north`. Same tables, demo rows, three reports, and an operator manifest.

Форк демо-отчётов [demo-app](../demo-app/) с `appId=plant-north`. Те же таблицы, демо-строки, три отчёта и манифест оператора.

## Отчёты

| reportId | Описание | Параметры |
|----------|----------|-----------|
| `ready-items` | Позиции с заданным статусом | `status` |
| `items-summary` | COUNT по статусам | — |
| `items-by-category` | JOIN `demo_item` + `demo_category` | — |

Демо-данные: `ITEM-001` и `ITEM-003` — `ready`, `ITEM-002` — `assigned`.

## Deploy

Folder layout: `solution.json`, `sql/`, `reports/`, `dashboards/` — см. `tools/ispf-cli` (`ispf pack` → `bundle.json`).

```powershell
# из корня репозитория, сервер на localhost:8080
$body = Get-Content -Raw examples/plant-north-fork/bundle.json
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/applications/plant-north/deploy" `
  -ContentType "application/json" `
  -Body $body
```

Или curl:

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/plant-north/deploy \
  -H "Content-Type: application/json" \
  -d @examples/plant-north-fork/bundle.json
```

Первый deploy также регистрирует приложение (`displayName`, `schemaName`).

## Проверка

```http
GET  /api/v1/applications/plant-north/reports
POST /api/v1/applications/plant-north/reports/ready-items/run
     { "parameters": { "status": "ready" } }
GET  /api/v1/applications/plant-north/reports/ready-items/export?status=ready
GET  /api/v1/applications/plant-north/operator-manifest
```

Ожидаемый ответ `ready-items/run`: 2 строки (`ITEM-001`, `ITEM-003`).

## Web Console

1. В шапке админ-консоли откройте оператора **plant-north**, или перейдите напрямую:
   ```
   http://localhost:5173/?mode=operator&app=plant-north
   ```
2. Вкладки: **Готовые позиции**, **Сводка по статусам**, **По категориям**.
3. Роль в селекторе: `operator` или `admin` (заголовок `X-ISPF-Role` для local).

См. [docs/en/reports.md](../../docs/en/reports.md).
