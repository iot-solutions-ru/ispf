# MES Platform (operator)

**Status:** Stable (marketplace product)  
**Audience:** Operators, manufacturing engineers  
**Related:** [Reference MES platform](reference-mes-platform.md) · [ISA-95 catalog](isa95-catalog.md) · Roadmap **BL-193**

MES is an **IoT Solutions marketplace product** (`mes-platform`). A clean ISPF install does **not** seed `root.platform.mes` until you deploy the bundle.

## Genealogy lite (BL-193)

Trace **lot ↔ material ↔ work-order ↔ quality record** from seed data after deploy.

### Deploy

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-platform/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-platform/bundle.json
```

### Operator UI

1. Open `?mode=operator&app=mes-platform`
2. Open dashboard **Genealogy** (`root.platform.dashboards.mes-platform-genealogy`)
3. Click **Trace seed lot** (`mes_genealogy_queryByLot` for `BATCH-LINE-A01-001`)
4. Or open report **Genealogy** / **Genealogy by lot**

### Seed graph

| Node | Seed value |
|------|------------|
| Lot | `BATCH-LINE-A01-001` (`root.platform.mes.lots.batch-line-a01-001`) |
| Material | `MAT-WIDGET-A01` — Assembly widget A01 |
| Work order | `WO-LINE-A01-001` |
| Quality | `QR-LINE-A01-001` (pass) · `QR-LINE-A01-002` (SCRATCH / ncr) |

### BFF (hub)

| Function | Purpose |
|----------|---------|
| `mes_genealogy_listGraph` | Full lot–material–WO–quality links |
| `mes_genealogy_queryByLot` | Filter by `lotId` |

Hub: `root.platform.devices.mes-platform-hub`

### Manual API check

```bash
curl -s -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{
    "objectPath": "root.platform.devices.mes-platform-hub",
    "functionName": "mes_genealogy_queryByLot",
    "input": {
      "schema": { "name": "in", "fields": [{ "name": "lotId", "type": "STRING" }] },
      "rows": [{ "lotId": "BATCH-LINE-A01-001" }]
    }
  }'
```

Expect `error_code=OK` and two quality rows for the seed lot.

**Out of scope:** live ERP connector (**BL-169** deferred). Genealogy uses MES app tables only.
