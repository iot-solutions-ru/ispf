> **Язык:** русская версия (вычитка). Канонический английский: [en/reference-mes-platform.md](../en/reference-mes-platform.md).

# MES Platform walkthrough (BL-164 / BL-165 / BL-166 / BL-167 / BL-168 / BL-169 / BL-170)

> **Статус:** Beta — Marketplace MES; smoke ≠ завод. Теги: [doc-status](../en/doc-status.md).

Сквозной путь сертификации: **установить MES-продукт из marketplace → deploy bundle → OEE KPI + work-order dispatch + quality SPC + ISA-88 batch + ERP outbox** без custom Java.

**Поставка:** MES — **продукт marketplace IoT Solutions**, не часть базовой платформы ISPF. Чистая установка **не** создаёт `root.platform.mes` и MES INSTANCE-модели, пока не установлен бандл `mes-platform` / `mes-platform-production`. Опционально (legacy): `ispf.bootstrap.mes-catalog-enabled=true`.

Следующая волна manufacturing depth описана в [manufacturing-patterns](manufacturing-patterns.md): traceability DAG, BoM, operations graph, CTO, QMS lite, integration, documents и portal access остаются паттернами bundle/application.

> **Честность vs [competitive-scorecard](competitive-scorecard.md):** измерение MES **~6.5 PARTIAL**. «Certified» / Wave 8 ниже = **smoke / walkthrough** (`MesPlatformGaSmokeTest`, lab ≤30 мин), **не** plant-ready MES и не live ERP. ERP outbox — stub/schedule.

| Bundle | `appId` | Artifacts | Status |
|--------|---------|-----------|--------|
| Certification skeleton | `mes-platform` | [examples/mes-platform/](../../examples/mes-platform/), listing `mes-platform` (vendor **IoT Solutions**) | Product |
| Production walkthrough | `mes-platform-production` | [examples/mes-platform-production/](../../examples/mes-platform-production/), listing `mes-platform-production` | Smoke-certified (BL-170) |

**См. также:** [isa95-catalog](isa95-catalog.md), [manufacturing-patterns](manufacturing-patterns.md), [reference-mes-oee-walkthrough](reference-mes-oee-walkthrough.md), [marketplace](marketplace.md), [object-model](object-model.md).

---

## Каталог MES (BL-164) — через marketplace-бандл

Создаётся при установке бандла **mes-platform** (или production) — **не** при старте «голого» сервера. v1.3.0 сеет typed instances:

| Path | ObjectType | Purpose |
|------|------------|---------|
| `root.platform.mes` | `MES` | Корневой каталог MES |
| `...mes.work-orders.wo-line-a01-001` | `WORK_ORDER` | Seed WO (`work-order-v1`) |
| `...mes.operations.op-assemble-a01` | `OPERATION` | Seed routing step |
| `...mes.lots.batch-line-a01-001` | `LOT` | Seed ISA-88 batch (`batch-v1`) |
| `...mes.shifts.shift-morning-a01` | `SHIFT` | Seed shift |
| `...mes.quality-records.qr-line-a01-001` | `QUALITY_RECORD` | Seed defect record |
| `...mes.instances.plant-a…line-a01` | `DEVICE` | ISA-95 site/area/line |

Тест: `MesCatalogObjectTypesIntegrationTest`. БЛ-169 (live ERP) остаётся **Отложено**.

---

## Work order dispatch (BL-166)

| Artifact | Path / config |
|----------|----------------|
| BPMN workflow | `root.platform.workflows.mes-work-order-dispatch` |
| Correlator | `workOrderDispatched` → RUN_WORKFLOW (bundle, disabled) |
| Operator UI | Dashboard `mes-platform-dispatch` с виджетом `work-queue` |
| Instantiate example | [work-order-instantiate.example.json](../../examples/mes-platform/work-order-instantiate.example.json) |
| BPMN source | [examples/mes-platform/bpmn/work-order-dispatch.bpmn.xml](../../examples/mes-platform/bpmn/work-order-dispatch.bpmn.xml) |

Вызовите `workOrderDispatched` на hub (или включите correlator), чтобы создать задачу оператора в work queue.

---

## Quality module / SPC (BL-167)

| Artifact | Purpose |
|----------|---------|
| MIXIN `quality-record-v1` | `defectCode`, `severity`, `lotId` на узлах `QUALITY_RECORD` |
| MIXIN `mes-platform-hub-v1` | `spcMeasurement` (history-enabled), `spcUcl`, `spcLcl`, `spcTarget` |
| Dashboard `mes-platform-quality` | Виджет `chart` по `spcMeasurement` + value widgets UCL/LCL |
| BFF `mes_quality_listSpcSamples` | Seed-строки из таблицы `mes_spc_sample` |

Создайте `QUALITY_RECORD` под `root.platform.mes.quality-records` и примените `quality-record-v1` для трассировки брака.

---

## ISA-88 batch lite (BL-168)

| Artifact | Purpose |
|----------|---------|
| INSTANCE `batch-v1` | `batchId`, `recipe`, `phase` на `LOT` под `root.platform.mes.lots` |
| Instantiate example | [batch-instantiate.example.json](../../examples/mes-platform/batch-instantiate.example.json) |
| BFF `mes_batch_runPhase` | Переход фазы в реестре `mes_batch_run` |
| BFF `mes_batch_getStatus` | Чтение batch path → phase |

---

## ERP outbox pattern (BL-169)

Идемпотентная заглушка синхронизации SAP / 1C — [erp-outbox.json](../../examples/mes-platform/erp-outbox.json)

| Field | Purpose |
|-------|---------|
| `mes_erp_outbox` table | `pending` → `sent` (stub connector) |
| `idempotency_key` | `${entityType}:${entityId}` |
| `mes_erp_enqueueOutbox` | Insert-if-absent enqueue |
| `mes_erp_pollOutbox` | Poll pending rows, mark `sent` |
| Schedule `mes-erp-outbox-poll` | `invoke_function` каждые 5 с — **enabled** в `mes-platform-production` (disabled в skeleton bundle) |

---

## Production bundle (BL-170)

Полный walkthrough: [examples/mes-platform-production/](../../examples/mes-platform-production/)

**Deploy + smoke одной командой** (локально или VPS):

```bash
bash deploy/tools/mes-platform-production-deploy.sh
# or: bash deploy/tools/mes-platform-production-deploy.sh /path/to/bundle.json
```

Ручной deploy:

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-platform-production/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-platform-production/bundle.json
```

Hub: `root.platform.devices.mes-platform-production-hub`  
Operator UI: `?mode=operator&app=mes-platform-production`

Dashboards: **Dispatch**, **OEE**, **Quality** (SPC chart reference).

---

## Wave 8 GA smoke (BL-164…BL-170)

Один интеграционный тест деплоит production bundle и проверяет каждый модуль MES за один проход:

| BL | Module | Assertion |
|----|--------|-----------|
| BL-164 / BL-165 | MES catalog + OEE | `mes_platform_listLines`, `mes_oee_getKpi` > 80% |
| BL-166 | Work-order dispatch | `mes_dispatch_confirmWorkOrder` |
| BL-167 | Quality SPC | `mes_quality_listSpcSamples` (3 seed rows) |
| BL-168 | ISA-88 batch | `mes_batch_runPhase` → `react` |
| BL-169 | ERP outbox | enqueue + poll round-trip; schedule `mes-erp-outbox-poll` enabled |
| BL-170 | Production bundle | full `mes-platform-production` deploy |
| BL-193 | Genealogy lite | `mes_genealogy_queryByLot` + report `mes-genealogy` (seed lot) |

Test class: `com.ispf.server.application.MesPlatformGaSmokeTest`

Per-module hardening tests (Wave 5): `MesWorkOrderDispatchIntegrationTest`, `MesQualitySpcDashboardIntegrationTest`, `MesBatchPhaseRunnerIntegrationTest`.

---

## Wave 5 hardening (BL-166 / BL-167 / BL-168)

| BL | Hardening | Integration test |
|----|-----------|------------------|
| BL-166 | Work-order dispatch BPMN full cycle (run → work-queue → confirm → `COMPLETED`) | `MesWorkOrderDispatchIntegrationTest` |
| BL-167 | SPC `chart` widget на dashboard `mes-platform-quality` + `mes_quality_listSpcSamples` | `MesQualitySpcDashboardIntegrationTest` |
| BL-168 | Batch phase runner `charge` → `react` → `discharge` via `mes_batch_runPhase` / `mes_batch_getStatus` | `MesBatchPhaseRunnerIntegrationTest` |
| BL-193 | Genealogy BFF + Operator dashboard/report с seed-графом | `MesGenealogyLiteIntegrationTest` |

Операторский путь genealogy: [mes.md](mes.md).

---

## Scenario steps

| # | Action | Path / API | Effect | Role |
|---|--------|------------|--------|------|
| 1 | Verify MES catalog | Object tree → `root.platform.mes` | Папки на месте | Admin |
| 2 | Deploy bundle | `POST /api/v1/applications/mes-platform/deploy` or `.../mes-platform-production/deploy` | Schema + hub + BFF | Admin |
| 3 | List lines | BFF `mes_platform_listLines` | ISA-95 line registry | Operator |
| 4 | List shifts | BFF `mes_oee_listShifts` | Seed shift LINE-A01 / Morning | Operator |
| 5 | OEE KPI | BFF `mes_oee_getKpi` + `shiftId` | A×P×Q composite | Dashboard |
| 6 | Register downtime | BFF `mes_oee_addDowntime` | Updates shift row | Operator |
| 7 | Instantiate WO | Blueprint `work-order-v1` → `root.platform.mes.work-orders` | Dispatch-ready WO | Planner |
| 8 | Dispatch WO | Fire `workOrderDispatched` or enable correlator | User task in work-queue | Operator |
| 9 | Quality SPC | Open Quality dashboard | Chart + sample list | Quality |
| 10 | Instantiate batch | Blueprint `batch-v1` → `root.platform.mes.lots` | ISA-88 LOT | Planner |
| 11 | Run batch phase | BFF `mes_batch_runPhase` | Phase advance | Operator |
| 12 | ERP sync | BFF `mes_erp_enqueueOutbox` + `mes_erp_pollOutbox` | Outbox stub | Integration |
| 13 | Genealogy | Dashboard **Genealogy** / BFF `mes_genealogy_queryByLot` | Lot–material–WO–quality | Operator |

---

## OEE reference functions (BL-165)

| Function | Hub object (`mes-platform`) |
|----------|----------------------------|
| `mes_oee_listShifts` | `root.platform.devices.mes-platform-hub` |
| `mes_oee_getKpi` | same |
| `mes_oee_addDowntime` | same |
| `mes_platform_listLines` | same (mes-platform extension) |

Production hub: `root.platform.devices.mes-platform-production-hub` (same function names).

Seed shift UUID: `dddddddd-dddd-dddd-dddd-dddddddddddd` → OEE ≈ **85%** for demo data.

---

## Certification checklist (≤ 30 min) — complete

- [x] `root.platform.mes.*` visible in Explorer after server start
- [x] Bundle deploy succeeds (`schemaName` = `app_mes_platform` or `app_mes_platform_production`)
- [x] `mes_platform_listLines` returns `LINE-A01`
- [x] `mes_oee_getKpi` returns `oeePct` > 80 for seed shift
- [x] Operator UI opens with `?mode=operator&app=mes-platform` or `mes-platform-production`
- [x] Work-queue widget visible on Dispatch dashboard (BL-166)
- [x] SPC chart widget on Quality dashboard (BL-167)
- [x] `batch-v1` visible under `root.platform.instance-types` (BL-168)
- [x] `mes_batch_runPhase` advances seed batch phase
- [x] `mes_erp_enqueueOutbox` + `mes_erp_pollOutbox` round-trip (BL-169)
- [x] `mes-erp-outbox-poll` schedule enabled in production bundle (BL-169 harden)
- [x] Genealogy dashboard + `mes_genealogy_queryByLot` returns seed lot graph (BL-193)

---

## Smoke commands

```bash
./gradlew :packages:ispf-server:test \
  --tests "com.ispf.server.application.reference.mes.MesBlueprintBootstrapTest" \
  --tests "com.ispf.server.application.reference.mes.MesWorkOrderDispatchIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesQualitySpcDashboardIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesBatchPhaseRunnerIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesGenealogyLiteIntegrationTest" \
  --tests "com.ispf.server.application.MesPlatformBundleSmokeTest" \
  --tests "com.ispf.server.application.MesPlatformProductionBundleSmokeTest" \
  --tests "com.ispf.server.application.MesPlatformGaSmokeTest"

bash deploy/tools/mes-platform-production-deploy.sh
```

---

## Related documents

- [mes.md](mes.md) — операторский путь genealogy (БЛ-193)
- [reference-mes-oee-walkthrough](reference-mes-oee-walkthrough.md) — BL-121 minimal OEE
- [reference-mes-walkthrough](reference-mes-walkthrough.md) — dispatch / tank reference
- [historian-tiers](historian-tiers.md) — historian at scale (Phase 28)
