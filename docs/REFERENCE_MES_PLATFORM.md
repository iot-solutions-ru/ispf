# MES Platform walkthrough (BL-164 / BL-165 / BL-166 / BL-167 / BL-170)

End-to-end certification path: **platform MES catalog → deploy mes-platform bundle → OEE KPI + work-order dispatch without custom Java**.

Artifacts: [examples/mes-platform/](../examples/mes-platform/), bundle `appId` = `mes-platform`.

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

Instance type `batch-v1` is registered under `root.platform.instance-types` at startup (`MesBlueprintBootstrap`).

---

## Work order dispatch (BL-166)

| Artifact | Path / config |
|----------|----------------|
| BPMN workflow | `root.platform.workflows.mes-work-order-dispatch` |
| Correlator | `workOrderDispatched` → RUN_WORKFLOW (bundle, disabled) |
| Operator UI | Dashboard `mes-platform-dispatch` with `work-queue` widget |
| BPMN source | [examples/mes-platform/bpmn/work-order-dispatch.bpmn.xml](../examples/mes-platform/bpmn/work-order-dispatch.bpmn.xml) |

Fire `workOrderDispatched` on `mes-platform-hub` (or enable correlator) to create an operator work-queue task.

---

## Quality module skeleton (BL-167)

Bundle model `quality-record-v1` (RELATIVE) defines:

| Variable | Purpose |
|----------|---------|
| `defectCode` | Defect / NCR code |
| `severity` | minor / major / critical |
| `lotId` | Traceability to material lot |

Create a `QUALITY_RECORD` under `root.platform.mes.quality-records` and apply the model.

---

## ERP outbox pattern (BL-169)

Idempotent SAP / 1C sync stub: [examples/mes-platform/erp-outbox.json](../examples/mes-platform/erp-outbox.json)

| Field | Purpose |
|-------|---------|
| `mes_erp_outbox` table | pending → processing → sent / failed |
| `idempotency_key` | `${entityType}:${entityId}:${payloadHash}` |
| `mes_erp_pollOutbox` | Scheduled BFF poll (not in bundle — wire per connector) |

---

## Scenario steps

| # | Action | Path / API | Effect | Role |
|---|--------|------------|--------|------|
| 1 | Verify MES catalog | Object tree → `root.platform.mes` | Folders present | Admin |
| 2 | Deploy bundle | `POST /api/v1/applications/mes-platform/deploy` | Schema `app_mes_platform`, hub device, OEE BFF | Admin |
| 3 | List lines | BFF `mes_platform_listLines` @ `mes-platform-hub` | ISA-95 line registry | Operator |
| 4 | List shifts | BFF `mes_oee_listShifts` | Seed shift LINE-A01 / Morning | Operator |
| 5 | OEE KPI | BFF `mes_oee_getKpi` + `shiftId` | A×P×Q composite | Dashboard |
| 6 | Register downtime | BFF `mes_oee_addDowntime` | Updates shift row | Operator confirm |
| 7 | Dispatch WO | Fire `workOrderDispatched` or enable correlator | User task in work-queue | Operator |
| 8 | Quality record | Create `QUALITY_RECORD` + `quality-record-v1` | defectCode / severity / lotId | Quality |

---

## OEE reference functions (BL-165)

Same BFF contract as [mes-oee-reference](../examples/mes-oee-reference/):

| Function | Hub object |
|----------|------------|
| `mes_oee_listShifts` | `root.platform.devices.mes-platform-hub` |
| `mes_oee_getKpi` | same |
| `mes_oee_addDowntime` | same |
| `mes_platform_listLines` | same (mes-platform extension) |

Seed shift UUID: `dddddddd-dddd-dddd-dddd-dddddddddddd` → OEE ≈ **85%** for demo data.

---

## Certification checklist (≤ 30 min)

- [ ] `root.platform.mes.*` visible in Explorer after server start
- [ ] Bundle deploy succeeds (`schemaName` = `app_mes_platform`)
- [ ] `mes_platform_listLines` returns `LINE-A01`
- [ ] `mes_oee_getKpi` returns `oeePct` > 80 for seed shift
- [ ] Operator UI opens with `?mode=operator&app=mes-platform`
- [ ] Work-queue widget visible on Dispatch dashboard (BL-166)
- [ ] `batch-v1` visible under `root.platform.instance-types` (BL-168)

---

## Smoke commands

```bash
./gradlew :packages:ispf-server:test --tests "com.ispf.server.application.reference.mes.MesPlatformBootstrapTest" --tests "com.ispf.server.application.reference.mes.MesBlueprintBootstrapTest" --tests "com.ispf.server.correlator.EventCorrelatorWindowTest" --tests "com.ispf.plugin.workflow.BpmnParserTest"

./gradlew :packages:ispf-plugin-workflow:test --tests "com.ispf.plugin.workflow.BpmnParserTest"

curl -s -X POST http://localhost:8080/api/v1/applications/mes-platform/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-platform/bundle.json

curl -s -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{"objectPath":"root.platform.devices.mes-platform-hub","functionName":"mes_oee_getKpi","input":{"schema":{"name":"in","fields":[{"name":"shiftId","type":"STRING"}]},"rows":[{"shiftId":"dddddddd-dddd-dddd-dddd-dddddddddddd"}]}}'
```

---

## Related documents

- [REFERENCE_MES_OEE_WALKTHROUGH.md](REFERENCE_MES_OEE_WALKTHROUGH.md) — BL-121 minimal OEE
- [REFERENCE_MES_WALKTHROUGH.md](REFERENCE_MES_WALKTHROUGH.md) — dispatch / tank reference
- [HISTORIAN_TIERS.md](HISTORIAN_TIERS.md) — historian at scale (Phase 28)
