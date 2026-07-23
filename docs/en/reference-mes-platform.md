# MES Platform walkthrough (BL-164 / BL-165 / BL-166 / BL-167 / BL-168 / BL-169 / BL-170 / BL-221 / BL-222)

> **Status:** Beta — Marketplace MES; smoke ≠ plant. Hub: [doc-status.md](doc-status.md).

End-to-end path: **install MES marketplace product → deploy bundle → OEE KPI + work-order dispatch + quality SPC + ISA-88 batch + nested BoM / operations graph + ERP outbox** without custom Java.

**Product delivery:** MES is an **IoT Solutions marketplace product**, not part of the base ISPF platform. A clean install does **not** create `root.platform.mes` or MES INSTANCE models until you install `mes-platform` / `mes-platform-production` from the marketplace (or deploy the example bundle). Optional legacy flag: `ispf.bootstrap.mes-catalog-enabled=true`.

Traceability DAG v2 is implemented in the `mes-platform` bundle as BL-221 app-schema tables, BFF functions, widgets, and a report. BL-222 adds nested BoM and operations dependency graph tables/functions/dashboard in the same bundle. Further manufacturing depth uses [manufacturing-patterns](manufacturing-patterns.md): CTO, QMS lite, integration, documents, and portal access remain bundle/application patterns.

> **Honesty vs [competitive-scorecard](competitive-scorecard.md):** MES dimension is **~6.5 PARTIAL**. “Certified” / Wave 8 below means **smoke / walkthrough** (`MesPlatformGaSmokeTest`, ≤30 min lab path) — **not** plant-ready MES or live ERP. ERP outbox remains a stub/schedule path.

| Bundle | `appId` | Artifacts | Status |
|--------|---------|-----------|--------|
| Certification skeleton | `mes-platform` | [examples/mes-platform/](../../examples/mes-platform/), marketplace listing `mes-platform` (vendor **IoT Solutions**) | Product (lab) |
| Production walkthrough | `mes-platform-production` | [examples/mes-platform-production/](../../examples/mes-platform-production/), marketplace listing `mes-platform-production` | Smoke-certified (BL-170) |
**See also:** [isa95-catalog](isa95-catalog.md), [manufacturing-patterns](manufacturing-patterns.md), [reference-mes-oee-walkthrough](reference-mes-oee-walkthrough.md), [marketplace](marketplace.md), [object-model](object-model.md).

---

## MES catalog (BL-164) — via marketplace bundle

Created when the **mes-platform** (or production) marketplace bundle is installed — not at bare server startup:

| Path | ObjectType | Purpose |
|------|------------|---------|
| `root.platform.mes` | `MES` | MES root catalog |
| `...mes.work-orders` | `WORK_ORDERS` | Work order folder |
| `...mes.work-orders.wo-line-a01-001` | `WORK_ORDER` | Seed WO (`work-order-v1`) |
| `...mes.operations.op-assemble-a01` | `OPERATION` | Seed routing step |
| `...mes.lots.batch-line-a01-001` | `LOT` | Seed ISA-88 batch (`batch-v1`) |
| `...mes.shifts.shift-morning-a01` | `SHIFT` | Seed shift |
| `...mes.quality-records.qr-line-a01-001` | `QUALITY_RECORD` | Seed defect record (`quality-record-v1`) |
| `...mes.instances.plant-a…line-a01` | `DEVICE` | ISA-95 site/area/line path |

Instance types `batch-v1` and `work-order-v1` are registered under `root.platform.instance-types` by the same bundle (`blueprints[]`). Test: `MesCatalogObjectTypesIntegrationTest`.

---

## Work order dispatch (BL-166)

| Artifact | Path / config |
|----------|----------------|
| BPMN workflow | `root.platform.workflows.mes-work-order-dispatch` |
| Correlator | `workOrderDispatched` → RUN_WORKFLOW (bundle, disabled) |
| Operator UI | Dashboard `mes-platform-dispatch` with `work-queue` widget |
| Instantiate example | [work-order-instantiate.example.json](../../examples/mes-platform/work-order-instantiate.example.json) |
| BPMN source | [examples/mes-platform/bpmn/work-order-dispatch.bpmn.xml](../../examples/mes-platform/bpmn/work-order-dispatch.bpmn.xml) |

Fire `workOrderDispatched` on the hub (or enable correlator) to create an operator work-queue task.

---

## Quality module / SPC (BL-167)

| Artifact | Purpose |
|----------|---------|
| MIXIN `quality-record-v1` | `defectCode`, `severity`, `lotId` on `QUALITY_RECORD` nodes |
| MIXIN `mes-platform-hub-v1` | `spcMeasurement` (history-enabled), `spcUcl`, `spcLcl`, `spcTarget` |
| Dashboard `mes-platform-quality` | `chart` widget on `spcMeasurement` + UCL/LCL value widgets |
| BFF `mes_quality_listSpcSamples` | Seed rows from `mes_spc_sample` table |

Create a `QUALITY_RECORD` under `root.platform.mes.quality-records` and apply `quality-record-v1` for defect traceability.

---

## ISA-88 batch lite (BL-168)

| Artifact | Purpose |
|----------|---------|
| Seed `LOT` | `root.platform.mes.lots.batch-line-a01-001` (`batch-v1`) |
| INSTANCE `batch-v1` | `batchId`, `recipe`, `phase` |
| Operator dashboard | `mes-platform-batch` — status + runPhase widgets |
| Instantiate example | [batch-instantiate.example.json](../../examples/mes-platform/batch-instantiate.example.json) |
| BFF `mes_batch_runPhase` | Advance phase in `mes_batch_run` registry |
| BFF `mes_batch_getStatus` | Read batch path → phase |

---

## ERP outbox pattern (BL-169)

Idempotent SAP / 1C sync stub — [erp-outbox.json](../../examples/mes-platform/erp-outbox.json)

| Field | Purpose |
|-------|---------|
| `mes_erp_outbox` table | `pending` → `sent` (stub connector) |
| `idempotency_key` | `${entityType}:${entityId}` |
| `mes_erp_enqueueOutbox` | Insert-if-absent enqueue |
| `mes_erp_pollOutbox` | Poll pending rows, mark `sent` |
| Schedule `mes-erp-outbox-poll` | `invoke_function` every 5s — **enabled** in `mes-platform-production` (disabled in skeleton bundle) |

---

## Nested BoM + operations graph (BL-222)

`mes-platform` 1.5.0 seeds schema migration `mes_platform_bom_ops_v1`:

| Artifact | Purpose |
|----------|---------|
| `mes_bom_header` / `mes_bom_line` | BoM `BOM-WIDGET-A01-A` for `MAT-WIDGET-A01` revision `A` |
| `mes_operation_def` / `mes_operation_edge` | Routing `RT-WIDGET-A01`: `OP-CUT` → `OP-ASSEMBLE` → `OP-TEST` |
| `mes_operation_status` | Seed status for `WO-LINE-A01-001`: complete / ready / pending |
| Dashboard `mes-platform-bom-ops` | Operator widgets for BoM explosion and ready-operation release |
| BFF `mes_bom_explode` / `mes_bom_whereUsed` | Material explosion and parent lookup |
| BFF `mes_ops_listReady` / `mes_ops_complete` | Ready-operation query and successor release |

Test: `MesBomOpsIntegrationTest` deploys `mes-platform-bundle.json`, verifies BoM explosion returns seed components, `OP-ASSEMBLE` is ready, and completing it releases `OP-TEST`.

---

## Production bundle (BL-170)

Full walkthrough: [examples/mes-platform-production/](../../examples/mes-platform-production/)

**One-command deploy + smoke** (local or VPS):

```bash
bash deploy/tools/mes-platform-production-deploy.sh
# or: bash deploy/tools/mes-platform-production-deploy.sh /path/to/bundle.json
```

Manual deploy:

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

Single integration test deploys the production bundle and verifies every MES module in one pass:

| BL | Module | Assertion |
|----|--------|-----------|
| BL-164 / BL-165 | MES catalog + OEE | `mes_platform_listLines`, `mes_oee_getKpi` > 80% |
| BL-166 | Work-order dispatch | `mes_dispatch_confirmWorkOrder` |
| BL-167 | Quality SPC | `mes_quality_listSpcSamples` (3 seed rows) |
| BL-168 | ISA-88 batch | `mes_batch_runPhase` → `react` |
| BL-169 | ERP outbox | enqueue + poll round-trip; schedule `mes-erp-outbox-poll` enabled |
| BL-170 | Production bundle | full `mes-platform-production` deploy |
| BL-193 | Genealogy lite | `mes_genealogy_queryByLot` + report `mes-genealogy` (seed lot) |
| BL-221 | Traceability DAG v2 | `mes_genealogy_queryDagByLot` + `mes_genealogy_listDagEdges` (seed entity hops) |
| BL-222 | Nested BoM + operations graph | `mes_bom_explode`, `mes_ops_listReady`, `mes_ops_complete` (`OP-ASSEMBLE` → `OP-TEST`) |

Test class: `com.ispf.server.application.MesPlatformGaSmokeTest`

Per-module hardening tests (Wave 5): `MesWorkOrderDispatchIntegrationTest`, `MesQualitySpcDashboardIntegrationTest`, `MesBatchPhaseRunnerIntegrationTest`.

---

## Wave 5 hardening (BL-166 / BL-167 / BL-168)

| BL | Hardening | Integration test |
|----|-----------|------------------|
| BL-164 | Seed typed MES instances + ISA-95 line path after deploy | `MesCatalogObjectTypesIntegrationTest` |
| BL-165 | OEE Operator dashboard widgets + `mes_oee_getKpi` > 80% | `MesOeeAnalyticsDashboardIntegrationTest` |
| BL-166 | Dispatch dashboard `work-queue` + BPMN confirm → WO `status=complete` | `MesWorkOrderDispatchIntegrationTest` |
| BL-167 | SPC `chart` + seed `QUALITY_RECORD` + `mes_quality_listSpcSamples` | `MesQualitySpcDashboardIntegrationTest` |
| BL-168 | Batch Operator dashboard + phase runner `charge` → `react` → `discharge` | `MesBatchPhaseRunnerIntegrationTest` |
| BL-193 / BL-221 | Genealogy BFF + Operator dashboard/report with seed lot graph and DAG edges | `MesGenealogyLiteIntegrationTest` |
| BL-222 | Nested BoM + operation dependency graph BFF/dashboard | `MesBomOpsIntegrationTest` |

Operator genealogy walkthrough: [mes.md](mes.md).

---

## Scenario steps

| # | Action | Path / API | Effect | Role |
|---|--------|------------|--------|------|
| 1 | Verify MES catalog | Object tree → `root.platform.mes` | Folders present | Admin |
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
| 13 | Genealogy | Dashboard **Genealogy** / BFF `mes_genealogy_queryByLot` / `mes_genealogy_queryDagByLot` | Lot–material–WO–quality + DAG entity hops | Operator |
| 14 | BoM + operations | Dashboard **BoM + Ops** / BFF `mes_bom_explode` / `mes_ops_listReady` / `mes_ops_complete` | BoM components + successor operation release | Operator |

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

- [x] Bundle deploy seeds typed `WORK_ORDER` / `OPERATION` / `LOT` / `SHIFT` / `QUALITY_RECORD` (BL-164)
- [x] Bundle deploy succeeds (`schemaName` = `app_mes_platform` or `app_mes_platform_production`)
- [x] `mes_platform_listLines` returns `LINE-A01` (ISA-95 path present)
- [x] `mes_oee_getKpi` returns `oeePct` > 80 for seed shift (BL-165)
- [x] Operator UI opens with `?mode=operator&app=mes-platform` or `mes-platform-production`
- [x] Work-queue widget + confirm sets WO `status=complete` (BL-166)
- [x] SPC chart widget on Quality dashboard + seed QR (BL-167)
- [x] Batch Operator dashboard + `mes_batch_runPhase` advances seed phase (BL-168)
- [x] Genealogy dashboard + `mes_genealogy_queryByLot` returns seed lot graph (BL-193)
- [x] Genealogy DAG + `mes_genealogy_queryDagByLot` returns seed entity hops (BL-221)
- [x] BoM + Ops dashboard + `mes_bom_explode`; completing `OP-ASSEMBLE` releases `OP-TEST` (BL-222)
- [ ] Live ERP connector (BL-169) — **Deferred** (stub outbox only)

---

## Smoke commands

```bash
./gradlew :packages:ispf-server:test \
  --tests "com.ispf.server.application.reference.mes.MesCatalogObjectTypesIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesOeeAnalyticsDashboardIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesWorkOrderDispatchIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesQualitySpcDashboardIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesBatchPhaseRunnerIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesGenealogyLiteIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesBomOpsIntegrationTest" \
  --tests "com.ispf.server.application.MesPlatformBundleSmokeTest" \
  --tests "com.ispf.server.application.MesPlatformProductionBundleSmokeTest" \
  --tests "com.ispf.server.application.MesPlatformGaSmokeTest"

bash deploy/tools/mes-platform-production-deploy.sh
```

---

## Related documents

- [mes.md](mes.md) — Operator genealogy path (BL-193)
- [reference-mes-oee-walkthrough](reference-mes-oee-walkthrough.md) — BL-121 minimal OEE
- [reference-mes-walkthrough](reference-mes-walkthrough.md) — dispatch / tank reference
- [historian-tiers](historian-tiers.md) — historian at scale (Phase 28)
