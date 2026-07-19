# MES Platform (оператор)

**Статус:** Stable (marketplace product)  
**Аудитория:** Операторы, инженеры производства  
**Связано:** [Reference MES platform](../en/reference-mes-platform.md) · [ISA-95 catalog](isa95-catalog.md) · Roadmap **БЛ-193**

MES — **marketplace-продукт IoT Solutions** (`mes-platform`). Чистая установка ISPF **не** создаёт `root.platform.mes`, пока не задеплоен bundle.

## Genealogy lite (БЛ-193)

Прослеживаемость **lot ↔ material ↔ work-order ↔ quality record** на seed-данных после deploy.

### Deploy

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-platform/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-platform/bundle.json
```

### Operator UI

1. Открыть `?mode=operator&app=mes-platform`
2. Дашборд **Genealogy** (`root.platform.dashboards.mes-platform-genealogy`)
3. Кнопка **Trace seed lot** (`mes_genealogy_queryByLot` для `BATCH-LINE-A01-001`)
4. Или отчёты **Genealogy** / **Genealogy by lot**

### Seed-граф

| Узел | Значение |
|------|----------|
| Lot | `BATCH-LINE-A01-001` (`root.platform.mes.lots.batch-line-a01-001`) |
| Material | `MAT-WIDGET-A01` — Assembly widget A01 |
| Work order | `WO-LINE-A01-001` |
| Quality | `QR-LINE-A01-001` (pass) · `QR-LINE-A01-002` (SCRATCH / ncr) |

### BFF (hub)

| Функция | Назначение |
|---------|------------|
| `mes_genealogy_listGraph` | Полный граф lot–material–WO–quality |
| `mes_genealogy_queryByLot` | Фильтр по `lotId` |

Hub: `root.platform.devices.mes-platform-hub`

### Ручная проверка API

```bash
curl -s -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{
    "objectPath": "root.platform.devices.mes-platform-hub",
    "functionName": "mes_genealogy_queryByLot",
    "input": {
      "schema": { "name": "in", "fields": [{ "name": "lotId", "type": "STRING" }] },
      "rows": [{ "lotId": "BATCH-LINE-A01-001" }]
    }
  }'
```

Ожидается `error_code=OK` и две quality-строки для seed-лота.

**Вне scope:** живой ERP-коннектор (**БЛ-169** отложен). Genealogy использует только таблицы MES-приложения.
