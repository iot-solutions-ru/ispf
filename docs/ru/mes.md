# MES Platform (оператор)

**Статус:** Stable (marketplace product)  
**Аудитория:** Операторы, инженеры производства  
**Связано:** [Reference MES platform](reference-mes-platform.md) · [ISA-95 catalog](isa95-catalog.md) · Roadmap **БЛ-164…168, БЛ-170, БЛ-193** (БЛ-169 отложен)

MES — **marketplace-продукт IoT Solutions** (`mes-platform` v1.3.0). Чистая установка ISPF **не** создаёт `root.platform.mes`, пока не задеплоен bundle.

## Deploy

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-platform/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-platform/bundle.json
```

Operator UI: `?mode=operator&app=mes-platform`

## Пути оператора

| Dashboard | Действие |
|-----------|----------|
| **Dispatch** | Work-queue из BPMN; confirm ставит seed WO `status=complete` |
| **OEE** | Смены / линии; Morning KPI (`oeePct` > 80) |
| **Quality** | SPC chart + samples; seed `QUALITY_RECORD` |
| **Batch** | Статус seed batch; фаза → `react` |
| **Genealogy** | Трассировка lot `BATCH-LINE-A01-001` |

## Seed-типы (БЛ-164)

| Type | Path |
|------|------|
| `WORK_ORDER` | `root.platform.mes.work-orders.wo-line-a01-001` |
| `OPERATION` | `root.platform.mes.operations.op-assemble-a01` |
| `LOT` | `root.platform.mes.lots.batch-line-a01-001` |
| `SHIFT` | `root.platform.mes.shifts.shift-morning-a01` |
| `QUALITY_RECORD` | `root.platform.mes.quality-records.qr-line-a01-001` |

## Genealogy lite (БЛ-193)

1. Дашборд **Genealogy**
2. **Trace seed lot** (`mes_genealogy_queryByLot` для `BATCH-LINE-A01-001`)
3. Или отчёты **Genealogy** / **Genealogy by lot**

**Вне scope:** живой ERP (**БЛ-169** отложен).
