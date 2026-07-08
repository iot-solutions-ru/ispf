# Warehouse Reference App

Второй generic reference app для dogfooding REQ-PF (критерий зрелости platform backlog).

Отличия от `demo-app`:

- `appId=warehouse`, отдельная schema `app_warehouse`
- Функция `warehouse_listLocations` использует script steps `selectMany` + `map`
- `operatorUi.eventJournalObjectPath` — конфигурируемый фильтр sidebar событий

## Deploy

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/warehouse/deploy \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d @examples/warehouse-app/bundle.json
```

## Проверка

```http
POST /api/v1/bff/invoke
{
  "objectPath": "root.platform.devices.demo-sensor-01",
  "functionName": "warehouse_listLocations",
  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
}
```

Ожидается `error_code=OK` и 2 строки (`A-01`, `B-02`).

См. [docs/en/roadmap.md](../docs/en/roadmap.md).
