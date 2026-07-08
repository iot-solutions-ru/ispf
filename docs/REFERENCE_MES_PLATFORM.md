# MES Platform walkthrough (BL-164 / BL-165 / BL-166 / BL-167 / BL-168 / BL-169 / BL-170)

End-to-end certification path: **platform MES catalog → deploy bundle → OEE KPI + work-order dispatch + quality SPC + ISA-88 batch + ERP outbox** without custom Java.

**Wave 8 status (complete):** BL-164…BL-170 certified. Production walkthrough ≤30 min verified; `MesPlatformGaSmokeTest` deploys `mes-platform-production` and asserts OEE, dispatch, quality, batch, ERP, and enabled outbox schedule.

| Bundle | `appId` | Artifacts | Status |
|--------|---------|-----------|--------|
| Certification skeleton | `mes-platform` | [examples/mes-platform/](../examples/mes-platform/) | Reference |
| Production walkthrough | `mes-platform-production` | [examples/mes-platform-production/](../examples/mes-platform-production/) | **Certified** (BL-170) |
**See also:** [ISA95_CATALOG.md](ISA95_CATALOG.md), [REFERENCE_MES_OEE_WALKTHROUGH.md](REFERENCE_MES_OEE_WALKTHROUGH.md), [OBJECT_MODEL.md](OBJECT_MODEL.md).

---

## Platform MES catalog (BL-164)

Created at server startup by `MesPlatformBootstrap` and Flyway `V2__mes_platform_catalog.sql`:

| Path | ObjectType | Purpose |
|------|------------|---------|
| `root.platform.mes` | `MES` | MES root catalog |
| `...mes.work-orders` | `WORK_ORDERS` | Work order instances (`WORK_ORDER`) |
| `...mes.operations` | `OPERATIONS` | Routing steps (`OPERATION`) |
| `...mes.lots` | `LOTS` | Material lots (`LOT`) — use INSTANCE `batch-v1` (BL-168) |
| `...mes.shifts` | `SHIFTS` | Production shifts (`SHIFT`) |
| `...mes.quality-records` | `QUALITY_RECORDS` | Quality records (`QUALITY_RECORD`) + `quality-record-v1` |
| `...mes.instances` | `MES_INSTANCES` | Site / area / line hierarchy |

Instance types `batch-v1` and `work-order-v1` are registered under `root.platform.instance-types` at startup (`MesBlueprintBootstrap`).

---

## Work order dispatch (BL-166)

| Artifact | Path / config |
|----------|----------------|
| BPMN workflow | `root.platform.workflows.mes-work-order-dispatch` |
| Correlator | `workOrderDispatched` → RUN_WORKFLOW (bundle, disabled) |
| Operator UI | Dashboard `mes-platform-dispatch` with `work-queue` widget |
| Instantiate example | [work-order-instantiate.example.json](../examples/mes-platform/work-order-instantiate.example.json) |
| BPMN source | [examples/mes-platform/bpmn/work-order-dispatch.bpmn.xml](../examples/mes-platform/bpmn/work-order-dispatch.bpmn.xml) |

Fire `workOrderDispatched` on the hub (or enable correlator) to create an operator work-queue task.

---

## Quality module / SPC (BL-167)

| Artifact | Purpose |
|----------|---------|
| RELATIVE `quality-record-v1` | `defectCode`, `severity`, `lotId` on `QUALITY_RECORD` nodes |
| RELATIVE `mes-platform-hub-v1` | `spcMeasurement` (history-enabled), `spcUcl`, `spcLcl`, `spcTarget` |
| Dashboard `mes-platform-quality` | `chart` widget on `spcMeasurement` + UCL/LCL value widgets |
| BFF `mes_quality_listSpcSamples` | Seed rows from `mes_spc_sample` table |

Create a `QUALITY_RECORD` under `root.platform.mes.quality-records` and apply `quality-record-v1` for defect traceability.

---

## ISA-88 batch lite (BL-168)

| Artifact | Purpose |
|----------|---------|
| INSTANCE `batch-v1` | `batchId`, `recipe`, `phase` on `LOT` under `root.platform.mes.lots` |
| Instantiate example | [batch-instantiate.example.json](../examples/mes-platform/batch-instantiate.example.json) |
| BFF `mes_batch_runPhase` | Advance phase in `mes_batch_run` registry |
| BFF `mes_batch_getStatus` | Read batch path → phase |

---

## ERP outbox pattern (BL-169)

Idempotent SAP / 1C sync stub — [erp-outbox.json](../examples/mes-platform/erp-outbox.json)

| Field | Purpose |
|-------|---------|
| `mes_erp_outbox` table | `pending` → `sent` (stub connector) |
| `idempotency_key` | `${entityType}:${entityId}` |
| `mes_erp_enqueueOutbox` | Insert-if-absent enqueue |
| `mes_erp_pollOutbox` | Poll pending rows, mark `sent` |
| Schedule `mes-erp-outbox-poll` | `invoke_function` every 5s — **enabled** in `mes-platform-production` (disabled in skeleton bundle) |

---

## Production bundle (BL-170)

Full walkthrough: [examples/mes-platform-production/](../examples/mes-platform-production/)

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

Test class: `com.ispf.server.application.MesPlatformGaSmokeTest`

Per-module hardening tests (Wave 5): `MesWorkOrderDispatchIntegrationTest`, `MesQualitySpcDashboardIntegrationTest`, `MesBatchPhaseRunnerIntegrationTest`.

---

## Wave 5 hardening (BL-166 / BL-167 / BL-168)

| BL | Hardening | Integration test |
|----|-----------|------------------|
| BL-166 | Work-order dispatch BPMN full cycle (run → work-queue → confirm → `COMPLETED`) | `MesWorkOrderDispatchIntegrationTest` |
| BL-167 | SPC `chart` widget on `mes-platform-quality` dashboard + `mes_quality_listSpcSamples` | `MesQualitySpcDashboardIntegrationTest` |
| BL-168 | Batch phase runner `charge` → `react` → `discharge` via `mes_batch_runPhase` / `mes_batch_getStatus` | `MesBatchPhaseRunnerIntegrationTest` |

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

---

## Smoke commands

```bash
./gradlew :packages:ispf-server:test \
  --tests "com.ispf.server.application.reference.mes.MesBlueprintBootstrapTest" \
  --tests "com.ispf.server.application.reference.mes.MesWorkOrderDispatchIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesQualitySpcDashboardIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesBatchPhaseRunnerIntegrationTest" \
  --tests "com.ispf.server.application.MesPlatformBundleSmokeTest" \
  --tests "com.ispf.server.application.MesPlatformProductionBundleSmokeTest" \
  --tests "com.ispf.server.application.MesPlatformGaSmokeTest"

bash deploy/tools/mes-platform-production-deploy.sh
```

---

## Related documents

- [REFERENCE_MES_OEE_WALKTHROUGH.md](REFERENCE_MES_OEE_WALKTHROUGH.md) — BL-121 minimal OEE
- [REFERENCE_MES_WALKTHROUGH.md](REFERENCE_MES_WALKTHROUGH.md) — dispatch / tank reference
- [HISTORIAN_TIERS.md](HISTORIAN_TIERS.md) — historian at scale (Phase 28)
