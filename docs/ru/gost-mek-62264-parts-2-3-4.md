# ГОСТ Р МЭК 62264 / IEC 62264 — покрытие Parts 1–5 в ERP-MES Core

Связь с национальными/международными редакциями (по PDF пользователя, 2026-07):

| Часть | Национальный документ | = IEC | Примечание |
|-------|------------------------|-------|------------|
| **1** | **ГОСТ Р МЭК 62264-1—2014** | IEC 62264-1:2013 | Модели и терминология |
| **2** | **ГОСТ Р МЭК 62264-2—2016** | IEC 62264-2:2013 | Объекты и атрибуты L4↔L3 |
| **3** | **ГОСТ Р МЭК 62264-3—2012** | IEC 62264-3:2007 | Activity models MOM |
| **4** | **ПНСТ 172—2016 / МЭК 62264-4—2016** | IEC 62264-4:2016 | Объекты L3; это **предварительный** нацстандарт, не полный ГОСТ Р |
| **5** | IEC 62264-5 | IEC 62264-5 | B2M-транзакции |

Реализация Level 3 MOM — бандл `examples/erp-mes-core` (`emc_*`) **2.2.0 / M5**.

**Источник истины атрибутного покрытия:**  
[`examples/erp-mes-core/uml-conformance/uml-catalog.json`](../../examples/erp-mes-core/uml-conformance/uml-catalog.json)  
(валидатор: `uml-conformance/validate_uml_catalog.py`).

Статусы: **covered** | **partial** | **missing**. На milestone **M5** открытых `missing` нет.

| Milestone | Бандл | Scope |
|-----------|--------|--------|
| **M3** | **2.0.x** | UML Parts **2+4** (каталог); Part **3** — 32 ячейки + BFF-map |
| **M4** | **2.1.x** | Part **5** UML на `emc_erp_*`; KPI ISO 22400; APS-lite |
| **M5** | **2.2.x** | GOST object completeness: RRN, Container/Tool/Software, Ops Definition/Schedule, Work Capability/WMC, Work Alert; GOST UI |
| Product line | apps | **`erp-mes-aps`**, **`erp-mes-cmms`**, pharma ISA-88, B2MML/1C |

EN twin: [gost-mek-62264-parts-2-3-4.md](../en/gost-mek-62264-parts-2-3-4.md).

---

## Demostand — ручная приёмка (только UI)

Стенд: [mes.iot-solutions.ru](https://mes.iot-solutions.ru/) (`?mode=operator`, `admin`/`admin`).

Полное руководство оператора со скриншотами всех модулей: [erp-mes-demo.md](erp-mes-demo.md).

**Правило:** проверка сценариев **только вручную в браузере**. Без REST/MCP/API. При баге — фикс → redeploy → повтор того же шага в UI.

| # | Шаг | Где в UI | Ожидание |
|---|-----|----------|----------|
| 1 | GOST dashboard | Operator → **ГОСТ 62264** | Таблицы RRN / Containers / Tools / Software / Ops Def / Work Cap / Alerts с seed-строками |
| 2 | RRN edges | ГОСТ 62264 → RRN Edges | `WU-A01`→`TOOL-FIX-A01`, container, software |
| 3 | Ack alert | ГОСТ 62264 → Acknowledge | WA-DEMO-001 → ACKNOWLEDGED, статус в таблице |
| 4 | Ops Definition upsert | ГОСТ 62264 → Ops Definition upsert | Выполнено; строка в Ops Definitions |
| 5 | Maintenance | Operator → **ТОиР** | MR-DEMO-001 / MWO-DEMO-001; создать→принять→завершить |
| 6 | Quality | **Качество** | Дефекты + QA Test Results; записать тест |
| 7 | Inventory | **Склад** | INV-DEMO-001; Submit / Accept |
| 8 | MOM matrix | **MOM 62264-3** | 32 ячейки COVERED + domain schedules |
| 9 | CMMS (опционально) | app **erp-mes-cmms** | Spares / PM reports (product-line depth) |

---

## Part 1 — терминология / иерархия

Иерархия Enterprise→Site→Area→Work Center→Work Unit (+ Storage Zone/Unit) — `emc_equipment.level` и Hierarchy Scope.

## Part 2 — объекты Level 4↔3

| Объектная модель | Статус (2.2+) | Таблицы / BFF / UI |
|---|---|---|
| Hierarchy Scope | covered | `emc_hierarchy_scope` |
| Equipment / Class / Property | covered | `emc_equipment*` |
| Physical Asset / Class / Property | covered | `emc_physical_asset*` |
| Personnel / Class / Person Property | covered | `emc_person*` |
| Material Class / Definition / Lot / Sublot | covered | `emc_material_*` |
| Process Segment + specs | covered | `emc_segment_*` |
| Product Definition + Product Segment | covered | `emc_product_*` |
| Operations Capability | covered | `emc_operations_capability*` |
| Operational Location | covered | `emc_operational_location` |
| **Containers / Tools / Software (§5.6)** | **covered** | `emc_container*` / `emc_tool*` / `emc_software*` · дашборд ГОСТ |
| **Operations Definition** | **covered** | `emc_operations_definition*` |
| **Operations Schedule** | **covered** | `emc_operations_schedule` · `emc_operations_request` |

## Part 3 — матрица деятельности MOM (4×8)

Все 32 ячейки **covered**. UI: **MOM 62264-3** + **ТОиР** / **Качество** / **Склад** с list+action.

## Part 4 — объекты Level 3 (ПНСТ 172)

| Модель | Статус (2.2+) | Таблицы / UI |
|---|---|---|
| Work Master (+ nodes/edges) | covered | `emc_work_master*` |
| Work Schedule / Request / Job Order | covered | `emc_work_*` |
| Job Response + Actuals | covered | `emc_job_response*` |
| Lot Genealogy | covered | `emc_lot_genealogy` |
| Work Calendar / Work Record | covered | `emc_work_calendar*` · `emc_work_record*` |
| Operations Event | covered | `emc_operations_event*` |
| **Resource Relationship Network** | **covered** | `emc_resource_relationship*` · ГОСТ dashboard |
| **Work Capability / Work Master Capability** | **covered** | `emc_work_capability*` · `emc_work_master_capability` |
| **Work Alert** | **covered** | `emc_work_alert` + ack |
| **Work KPI** | **covered** | `emc_kpi_*` (ISO 22400) |

## Part 5 — B2M-транзакции

UML Transaction / ACK / IntegrationLog / MasterDataReplica → `emc_erp_*`.  
Живой B2MML/1C — **`mes-integration-catalog`**.

## Нормативная оговорка

Part 4 в РФ = **ПНСТ 172—2016**, не ГОСТ Р. Это не открытый пробел модели demostand, а статус национального документа.

## Связанные приложения

| App | Роль |
|---|---|
| `erp-mes-aps` **1.1+** | Планировщик |
| `erp-mes-cmms` **1.1+** | CMMS поверх maint |
| `erp-mes-pharma` 2.0+ | ISA-88 |
| `mes-integration-catalog` **1.1+** | B2MML + коннекторы |

## Версии бандла

| Версия | Что добавлено |
|---|---|
| **2.0.0** | UML Parts 2+4 attribute-complete + Part 3 BFF map |
| **2.1.0** | Part 5 UML + KPI registry + APS-lite |
| **2.2.0** | M5 GOST gaps closed + GOST/ТОиР dashboards |
