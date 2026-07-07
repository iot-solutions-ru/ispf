# MES Platform bundle (BL-165 / BL-166 / BL-167 / BL-170)

Certification skeleton for first-class MES on ISPF: platform catalog folders (`root.platform.mes.*`) + OEE BFF + work-order dispatch workflow.

| File | Purpose |
|------|---------|
| `bundle.json` | Deploy via `POST /api/v1/applications/mes-platform/deploy` |
| `bpmn/work-order-dispatch.bpmn.xml` | Source BPMN for BL-166 dispatch workflow |
| `erp-outbox.json` | ERP outbox idempotent sync pattern stub (BL-169) |

## Prerequisites

- Server started with `MesPlatformBootstrap` (default on every boot) — MES catalog folders under `root.platform.mes.*`
- See [REFERENCE_MES_PLATFORM.md](../../docs/REFERENCE_MES_PLATFORM.md)

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

## Work order dispatch (BL-166)

- BPMN: `root.platform.workflows.mes-work-order-dispatch` (user task → work queue)
- Correlator on `workOrderDispatched` → `RUN_WORKFLOW` (disabled by default)
- Operator dashboard widget: `work-queue` on `mes-platform-dispatch`

## Quality module skeleton (BL-167)

- RELATIVE model `quality-record-v1` with `defectCode`, `severity`, `lotId`
- Apply to `QUALITY_RECORD` nodes under `root.platform.mes.quality-records`

Hub path: `root.platform.devices.mes-platform-hub`

## Related

- [mes-oee-reference](../mes-oee-reference/) — BL-121 minimal OEE reference
- [ISA95_CATALOG.md](../../docs/ISA95_CATALOG.md)
