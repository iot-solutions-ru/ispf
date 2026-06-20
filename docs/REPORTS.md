# Отчёты приложений (REQ-PF-12)

Generic-слой SQL-отчётов для любого `appId`. Определения деплоятся через bundle или отдельный API; выполнение — в **изолированной schema** приложения.

## Модель

| Поле | Описание |
|------|----------|
| `reportId` | Уникальный id в рамках app |
| `title` | Заголовок |
| `query` | Только `SELECT` / `WITH` (параметры `?` по порядку `parameters`) |
| `parameters` | Имена параметров SQL, например `["status"]` |
| `columns` | `[{ "field": "item_code", "label": "Код" }]` |
| `maxRows` | Лимит строк (default 1000) |

## Deploy

Через bundle:

```json
{
  "reports": [
    {
      "reportId": "ready-items",
      "title": "Готовые позиции",
      "query": "SELECT item_code, status FROM demo_item WHERE status = ?",
      "parameters": ["status"],
      "columns": [
        { "field": "item_code", "label": "Код" },
        { "field": "status", "label": "Статус" }
      ],
      "maxRows": 500
    }
  ]
}
```

Отдельно:

```http
POST /api/v1/applications/{appId}/reports/deploy
Content-Type: application/json

{
  "reportId": "ready-items",
  "title": "Готовые позиции",
  "query": "SELECT item_code, status FROM demo_item WHERE status = ?",
  "parameters": ["status"],
  "columns": [
    { "field": "item_code", "label": "Код" },
    { "field": "status", "label": "Статус" }
  ]
}
```

## API

```http
GET  /api/v1/applications/{appId}/reports
POST /api/v1/applications/{appId}/reports/{reportId}/run
GET  /api/v1/applications/{appId}/reports/{reportId}/export?status=ready
```

### Run

```http
POST /api/v1/applications/myapp/reports/ready-items/run
Content-Type: application/json

{ "parameters": { "status": "ready" } }
```

Ответ:

```json
{
  "reportId": "ready-items",
  "title": "Готовые позиции",
  "columns": [{ "field": "item_code", "label": "Код" }],
  "rows": [{ "item_code": "ITEM-001", "status": "ready" }],
  "rowCount": 1,
  "truncated": false
}
```

### CSV export

`GET .../export?status=ready` — `text/csv`, attachment.

Параметры передаются query string (имена = `parameters` в определении).

## Operator manifest

Экран с типом `report` (вместо `table` + BFF function):

```json
{
  "id": "items-report",
  "title": "Отчёт",
  "report": {
    "reportId": "ready-items",
    "parameters": { "status": "ready" },
    "refreshIntervalMs": 30000,
    "emptyMessage": "Нет строк"
  }
}
```

Web Console: кнопки **Обновить** и **CSV** (`src/api/reports.ts`).

## Права

| Endpoint | Роль |
|----------|------|
| `GET .../reports`, `GET .../export` | `operator`, `admin` |
| `POST .../reports/deploy` | `admin` |
| `POST .../reports/{id}/run` | `operator`, `admin` |

## Ограничения

- Только read-only SQL (без `INSERT`/`UPDATE`/`DELETE`/DDL).
- Запрос выполняется в app schema (`schema_name` из регистрации приложения).
- PDF/печатные формы — вне scope platform; app bundle или внешний сервис.

## Пример

Готовый bundle: [examples/demo-app/](../examples/demo-app/) — deploy на `demo`, отчёты `ready-items`, `items-summary`, `items-by-category`.

## Связанные документы

- [APPLICATIONS.md](APPLICATIONS.md) — bundle deploy
- [WEB_CONSOLE.md](WEB_CONSOLE.md) — operator manifest
