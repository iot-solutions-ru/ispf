# MES Platform bundle (BL-165 / BL-166 / BL-167 / BL-168 / BL-169 / BL-170)

Certification skeleton for first-class MES on ISPF: platform catalog folders (`root.platform.mes.*`) + OEE BFF + work-order dispatch workflow.

| File | Purpose |
|------|---------|
| `bundle.json` | Deploy via `POST /api/v1/applications/mes-platform/deploy` |
| `work-order-instantiate.example.json` | Instantiate `WORK_ORDER` from `work-order-v1` INSTANCE blueprint |
| `batch-instantiate.example.json` | Instantiate `LOT` from `batch-v1` + `mes_batch_runPhase` |
| `bpmn/work-order-dispatch.bpmn.xml` | Source BPMN for BL-166 dispatch workflow |
| `erp-outbox.json` | ERP outbox idempotent sync pattern stub (BL-169) |

Production walkthrough: [mes-platform-production](../mes-platform-production/).

## Prerequisites

- Server started with `MesPlatformBootstrap` (default on every boot) — MES catalog folders under `root.platform.mes.*`
- See [REFERENCE_MES_PLATFORM.md](../../docs/en/reference-mes-platform.md)

## Deploy

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-platform/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-platform/bundle.json
```

## OEE functions (from mes-oee-reference pattern)

| Function | Description |
|----------|-------------|
| `mes_oee_listShifts` | List shift rows for OEE |
| `mes_oee_getKpi` | Availability × Performance × Quality |
| `mes_oee_addDowntime` | Register downtime minutes |
| `mes_platform_listLines` | ISA-95 line registry (maps to `root.platform.mes.instances.*`) |

## OEE analytics chart (BL-160)

- Hub variable `oeePct` (history-enabled) on `mes-platform-hub-v1`
- Dashboard `mes-platform-oee` includes `chart` widget with `analyticsTemplateId: oee` (8h historian rollup)
- Platform catalog: `GET /api/v1/platform/analytics/templates`

## Work order dispatch (BL-166)

- INSTANCE blueprint `work-order-v1` → create `WORK_ORDER` under `root.platform.mes.work-orders`
- Example: [work-order-instantiate.example.json](./work-order-instantiate.example.json)
- BPMN: `root.platform.workflows.mes-work-order-dispatch` (user task → work queue)
- Correlator on `workOrderDispatched` → `RUN_WORKFLOW` (disabled by default)
- Operator dashboard widget: `work-queue` on `mes-platform-dispatch`

## Quality module skeleton (BL-167)

- RELATIVE model `quality-record-v1` with `defectCode`, `severity`, `lotId`
- Hub blueprint `mes-platform-hub-v1`: `spcMeasurement` (history chart), `spcUcl`, `spcLcl`, `spcTarget`
- Dashboard `mes-platform-quality` with SPC `chart` widget + BFF `mes_quality_listSpcSamples`
- Apply `quality-record-v1` to `QUALITY_RECORD` nodes under `root.platform.mes.quality-records`

## ISA-88 batch lite (BL-168)

- INSTANCE `batch-v1` (`batchId`, `recipe`, `phase`) — bootstrap via `MesBlueprintBootstrap`
- Example: [batch-instantiate.example.json](./batch-instantiate.example.json)
- BFF: `mes_batch_runPhase`, `mes_batch_getStatus` on hub

## ERP outbox (BL-169)

- Table `mes_erp_outbox` + BFF `mes_erp_enqueueOutbox`, `mes_erp_pollOutbox`
- Schedule `mes-erp-outbox-poll` (disabled by default) — see [erp-outbox.json](./erp-outbox.json)

## Related

- [mes-oee-reference](../mes-oee-reference/) — BL-121 minimal OEE reference
- [ISA95_CATALOG.md](../../docs/en/isa95-catalog.md)
