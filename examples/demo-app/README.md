# Demo Application — пример SQL-отчётов

Готовый bundle для `appId=demo`: таблицы, демо-данные, три отчёта и operator manifest.

## Отчёты

| reportId | Описание | Параметры |
|----------|----------|-----------|
| `ready-items` | Позиции с заданным статусом | `status` |
| `items-summary` | COUNT по статусам | — |
| `items-by-category` | JOIN `demo_item` + `demo_category` | — |

Демо-данные: `ITEM-001` и `ITEM-003` — `ready`, `ITEM-002` — `assigned`.

## Deploy

```powershell
# из корня репозитория, сервер на localhost:8080
$body = Get-Content -Raw examples/demo-app/bundle.json
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/applications/demo/deploy" `
  -ContentType "application/json" `
  -Body $body
```

Или curl:

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/demo/deploy \
  -H "Content-Type: application/json" \
  -d @examples/demo-app/bundle.json
```

Первый deploy также регистрирует приложение (`displayName`, `schemaName`).

## Проверка

```http
GET  /api/v1/applications/demo/reports
POST /api/v1/applications/demo/reports/ready-items/run
     { "parameters": { "status": "ready" } }
GET  /api/v1/applications/demo/reports/ready-items/export?status=ready
GET  /api/v1/applications/demo/operator-manifest
```

Ожидаемый ответ `ready-items/run`: 2 строки (`ITEM-001`, `ITEM-003`).

## Web Console

1. В шапке админ-консоли нажмите **Оператор · demo**, или откройте напрямую:
   ```
   http://localhost:5173/?mode=operator&app=demo
   ```
2. Вкладки: **Готовые позиции**, **Сводка по статусам**, **По категориям**.
3. Роль в селекторе: `operator` или `admin` (заголовок `X-ISPF-Role` для local).

См. [docs/en/reports.md](../docs/en/reports.md).
