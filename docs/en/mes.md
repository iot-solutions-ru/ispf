# MES Platform (operator)

**Status:** Stable (marketplace product)  
**Audience:** Operators, manufacturing engineers  
**Related:** [Reference MES platform](reference-mes-platform.md) · [ISA-95 catalog](isa95-catalog.md) · Roadmap **BL-164…168, BL-170, BL-193** (BL-169 deferred)

MES is an **IoT Solutions marketplace product** (`mes-platform` v1.3.0). A clean ISPF install does **not** seed `root.platform.mes` until you deploy the bundle.

## Deploy

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-platform/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-platform/bundle.json
```

Open Operator UI: `?mode=operator&app=mes-platform`

## Operator paths

| Dashboard | What to do |
|-----------|------------|
| **Dispatch** | Work-queue tasks from BPMN; confirm start (sets seed WO `status=complete`) |
| **OEE** | List shifts / lines; load Morning KPI (`oeePct` > 80 on seed) |
| **Quality** | SPC chart + list samples; seed `QUALITY_RECORD` under quality-records |
| **Batch** | Get seed batch status; advance phase to `react` |
| **Genealogy** | Trace seed lot `BATCH-LINE-A01-001` |

## Seed typed objects (BL-164)

| Type | Path |
|------|------|
| `WORK_ORDER` | `root.platform.mes.work-orders.wo-line-a01-001` |
| `OPERATION` | `root.platform.mes.operations.op-assemble-a01` |
| `LOT` | `root.platform.mes.lots.batch-line-a01-001` |
| `SHIFT` | `root.platform.mes.shifts.shift-morning-a01` |
| `QUALITY_RECORD` | `root.platform.mes.quality-records.qr-line-a01-001` |

## Genealogy lite (BL-193)

Trace **lot ↔ material ↔ work-order ↔ quality record** from seed data.

1. Open dashboard **Genealogy**
2. Click **Trace seed lot** (`mes_genealogy_queryByLot` for `BATCH-LINE-A01-001`)
3. Or open report **Genealogy** / **Genealogy by lot**

| Node | Seed value |
|------|------------|
| Lot | `BATCH-LINE-A01-001` |
| Material | `MAT-WIDGET-A01` |
| Work order | `WO-LINE-A01-001` |
| Quality | `QR-LINE-A01-001` (pass) · `QR-LINE-A01-002` (SCRATCH / ncr) |

Hub BFF: `mes_genealogy_listGraph`, `mes_genealogy_queryByLot` on `root.platform.devices.mes-platform-hub`.

**Out of scope:** live ERP connector (**BL-169** deferred). Genealogy uses MES app tables only.
