# ГОСТ Р МЭК 62264 / IEC 62264 — покрытие Parts 2–4 в ERP-MES Core

Связь с **ГОСТ Р МЭК 62264-1—2014** (Part 1: модели и терминология).  
Реализация Level 3 MOM — бандл `examples/erp-mes-core` (`emc_*`).

**Источник истины атрибутного покрытия:** машиночитаемый каталог  
[`examples/erp-mes-core/uml-conformance/uml-catalog.json`](../../examples/erp-mes-core/uml-conformance/uml-catalog.json)  
(валидатор: `uml-conformance/validate_uml_catalog.py`).

Статусы в каталоге: **covered** | **partial** | **missing**.  
С версии **2.0.0** цель A+1: атрибутно-полная UML Parts **2+4**, Part **3** — 32 ячейки с BFF-map (`emc_mom_activity_bff`). Part 5 — вне scope v2.

## Part 2 — объекты Level 4↔3

| Объектная модель | Статус (2.0) | Таблицы / BFF |
|---|---|---|
| Hierarchy Scope | covered | `emc_hierarchy_scope` · `emc_hierarchy_scope_list` |
| Equipment / Class / Property (+ class props) | covered | `emc_equipment*` · `emc_equipment_class_property` · `emc_classprop_list` |
| Physical Asset / Class / Property | covered | `emc_physical_asset*` · class props |
| Personnel / Class / Person Property | covered | `emc_person*` · `emc_person_property` |
| Qualification Test Spec / Result | covered | `emc_qualification_test_*` |
| Material Class / Definition / Lot / Sublot / Property | covered | `emc_material_*` · `emc_sublot_*` |
| Material Assembled From | covered | `emc_material_assembled_from` |
| Process Segment + Mat/Eq/Pers/**Param** Spec | covered | `emc_segment_*` · `emc_segment_param_list` |
| Product Definition + Product Segment specs | covered | `emc_product_*` · `emc_product_segment_specs_list` |
| Operations Capability (nested children) | covered | `emc_operations_capability` · `emc_ops_capability_*` |
| Capability Test Spec / Result | covered | `emc_capability_test_*` |
| Operational Location | covered | `emc_operational_location` |

## Part 3 — матрица деятельности MOM (4×8)

Все 32 ячейки **covered** и привязаны к BFF через `emc_mom_activity_bff` / `emc_mom_listActivityBff`.  
UI: дашборд **MOM (IEC 62264-3)**.

## Part 4 — объекты Level 3

| Модель | Статус (2.0) | Таблицы / BFF |
|---|---|---|
| Work Master (+ multi-segment nodes/edges) | covered | `emc_work_master` · `emc_work_master_node/edge` |
| Work Schedule / Request / Job Order (+ param req) | covered | `emc_work_*` · `emc_job_order_parameter_req` |
| Job Response + Actuals | covered | `emc_job_response*` · `*_actual` |
| Work Directive | covered | `emc_work_directive` |
| Work Performance (header) | covered | `emc_work_performance` · `emc_workperf_*` |
| Lot Genealogy (+ relation metadata / nodes) | covered | `emc_lot_genealogy` · `emc_genealogy_node` |
| Work Calendar / Shift Assignment | covered | `emc_work_calendar` · `emc_shift_assignment` |
| Work Record | covered | `emc_work_record*` |
| Operations Capability / Performance | covered | см. Part 2/4 |
| Operations Event | covered | `emc_operations_event*` |

## Part 5 (вне Parts 2–4 v2)

| | Статус |
|---|---|
| B2M outbox/inbox verbs×nouns | demostand ✓ `emc_erp_*` (полная UML Part 5 — out of scope) |

## Версии бандла

| Версия | Что добавлено |
|---|---|
| ≤1.3 | Каркас + genealogy + OEE |
| 1.4.x | Physical Asset, Product, Capability, Ops Cap/Perf, MOM 4×8, locations/KPIs |
| **2.0.0** | UML attribute-complete Parts 2+4 + Part 3 BFF map (`uml-catalog.json`) |

Отраслевые оверлеи (тонкие packs, `requires ≥ 2.0.0`):

| Оверлей | Версия | Заметка |
|---|---|---|
| `erp-mes-pharma` | 1.4.0+ | только `pha_*` extras + seed в `emc_*` |
| `erp-mes-printing` | 1.4.0+ | только `emp_*` extras + seed в `emc_*` |
