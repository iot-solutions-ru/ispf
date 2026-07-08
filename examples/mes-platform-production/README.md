# MES Platform Production bundle (BL-170)

Production-ready certification walkthrough: **OEE → work-order dispatch → quality SPC → ISA-88 batch → ERP outbox** without custom Java.

| File | Purpose |
|------|---------|
| `bundle.json` | Deploy via `POST /api/v1/applications/mes-platform-production/deploy` |
| `work-order-instantiate.example.json` | Instantiate `WORK_ORDER` from `work-order-v1` |
| `batch-instantiate.example.json` | Instantiate `LOT` from `batch-v1` + run phase |
| `erp-outbox.json` | ERP outbox pattern reference (BL-169) |
| `bpmn/work-order-dispatch.bpmn.xml` | Dispatch BPMN source |

## Prerequisites

- Server started (`MesPlatformBootstrap` + `MesBlueprintBootstrap` on boot)
- See [REFERENCE_MES_PLATFORM.md](../../docs/en/reference-mes-platform.md)

## Deploy

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-platform-production/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-platform-production/bundle.json
```

## Walkthrough (≤ 30 min)

| Step | Action | API / path |
|------|--------|------------|
| 1 | Verify MES catalog | `root.platform.mes.*` in Explorer |
| 2 | Deploy bundle | `POST .../mes-platform-production/deploy` |
| 3 | OEE KPI | BFF `mes_oee_getKpi` @ `mes-platform-production-hub` |
| 4 | Instantiate work order | [work-order-instantiate.example.json](./work-order-instantiate.example.json) |
| 5 | Dispatch confirm | Fire `workOrderDispatched` or work-queue BPMN |
| 6 | Quality SPC | Operator dashboard **Quality** — chart on `spcMeasurement` |
| 7 | Instantiate batch | [batch-instantiate.example.json](./batch-instantiate.example.json) |
| 8 | Advance batch phase | BFF `mes_batch_runPhase` |
| 9 | ERP outbox | BFF `mes_erp_enqueueOutbox` then `mes_erp_pollOutbox` |

Hub: `root.platform.devices.mes-platform-production-hub`

Operator UI: `?mode=operator&app=mes-platform-production`

## Related

- [mes-platform](../mes-platform/) — certification skeleton (same BFF contract)
- [REFERENCE_MES_OEE_WALKTHROUGH.md](../../docs/en/reference-mes-oee-walkthrough.md)
