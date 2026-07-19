# MES Platform bundle (BL-164 / BL-165 / BL-166 / BL-167 / BL-168 / BL-170 / BL-193)

Marketplace MES product (IoT Solutions): catalog folders + seed typed instances + OEE BFF + work-order dispatch + quality SPC + ISA-88 batch + genealogy lite.

> **BL-169** (live ERP connector) remains **Deferred** — outbox stub only.

| File | Purpose |
|------|---------|
| `bundle.json` | Deploy via `POST /api/v1/applications/mes-platform/deploy` (v1.3.0) |
| `work-order-instantiate.example.json` | Instantiate additional `WORK_ORDER` from `work-order-v1` |
| `batch-instantiate.example.json` | Instantiate additional `LOT` from `batch-v1` |
| `bpmn/work-order-dispatch.bpmn.xml` | Source BPMN for BL-166 dispatch workflow |
| `erp-outbox.json` | ERP outbox pattern stub (BL-169 deferred) |

Production walkthrough: [mes-platform-production](../mes-platform-production/).  
Operator guide: [docs/en/mes.md](../../docs/en/mes.md).  
Walkthrough: [docs/en/reference-mes-platform.md](../../docs/en/reference-mes-platform.md).

## Prerequisites

- Clean ISPF server (MES is **not** base-seeded)
- Marketplace install or direct deploy of this bundle

## Deploy

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-platform/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-platform/bundle.json
```

Operator UI: `?mode=operator&app=mes-platform`

## Seed typed catalog (BL-164)

| Path | Type |
|------|------|
| `root.platform.mes.work-orders.wo-line-a01-001` | `WORK_ORDER` |
| `root.platform.mes.operations.op-assemble-a01` | `OPERATION` |
| `root.platform.mes.lots.batch-line-a01-001` | `LOT` |
| `root.platform.mes.shifts.shift-morning-a01` | `SHIFT` |
| `root.platform.mes.quality-records.qr-line-a01-001` | `QUALITY_RECORD` |
| `...mes.instances.plant-a.areas.assembly.lines.line-a01` | ISA-95 line |

## Operator dashboards

| Dashboard | BL |
|-----------|----|
| Dispatch (`work-queue`) | BL-166 |
| OEE (KPI + analytics chart) | BL-165 |
| Quality / SPC | BL-167 |
| Genealogy | BL-193 |
| Batch (ISA-88) | BL-168 |

## Integration tests

```bash
./gradlew :packages:ispf-server:test \
  --tests "com.ispf.server.application.reference.mes.MesCatalogObjectTypesIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesOeeAnalyticsDashboardIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesWorkOrderDispatchIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesQualitySpcDashboardIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesBatchPhaseRunnerIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesGenealogyLiteIntegrationTest" \
  --tests "com.ispf.server.application.MesPlatformBundleSmokeTest" \
  --tests "com.ispf.server.application.MesPlatformGaSmokeTest"
```

## Related

- [mes-oee-reference](../mes-oee-reference/) — BL-121 minimal OEE reference
- [ISA95_CATALOG.md](../../docs/en/isa95-catalog.md)
