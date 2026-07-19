# MES Platform (operator)

**Status:** Stable (marketplace product)  
**Audience:** Operators, manufacturing engineers  
**Related:** [Reference MES platform](reference-mes-platform.md) · [ISA-95 catalog](isa95-catalog.md) · [Manufacturing patterns](manufacturing-patterns.md) · [MES capability MCP map](mes-capability-mcp.md) · Roadmap **BL-164…168, BL-170, BL-193, BL-220…225** (BL-169 deferred)

MES is an **IoT Solutions marketplace product** (`mes-platform` v1.5.0). A clean ISPF install does **not** seed `root.platform.mes` until you deploy the bundle.

Manufacturing depth follows [manufacturing-patterns](manufacturing-patterns.md): traceability DAG, nested BoM, operations graph, CTO, QMS lite, integration, and portal access are solution configuration, not base platform domain entities. `mes-platform` includes the BL-222 nested BoM + operation dependency seed as bundle schema/BFF/dashboard configuration.

CTO is packaged separately as the `mes-cto` marketplace bundle. It depends on `mes-platform >= 1.4.0`, seeds finish/sensor options and one incompatibility rule, and exposes Operator functions for validation and build draft generation.

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
| **BoM + Ops** | Explode seed material `MAT-WIDGET-A01`; list/complete ready operations for `WO-LINE-A01-001` |

## Seed typed objects (BL-164)

| Type | Path |
|------|------|
| `WORK_ORDER` | `root.platform.mes.work-orders.wo-line-a01-001` |
| `OPERATION` | `root.platform.mes.operations.op-assemble-a01` |
| `LOT` | `root.platform.mes.lots.batch-line-a01-001` |
| `SHIFT` | `root.platform.mes.shifts.shift-morning-a01` |
| `QUALITY_RECORD` | `root.platform.mes.quality-records.qr-line-a01-001` |

## Genealogy lite + DAG (BL-193 / BL-221)

Trace **lot ↔ material ↔ work-order ↔ quality record** from seed data.

1. Open dashboard **Genealogy**
2. Click **Trace seed lot** (`mes_genealogy_queryByLot` for `BATCH-LINE-A01-001`)
3. Click **Trace DAG seed lot** (`mes_genealogy_queryDagByLot`) or **List DAG edges** (`mes_genealogy_listDagEdges`)
4. Or open report **Genealogy** / **Genealogy by lot** / **Genealogy DAG**

| Node | Seed value |
|------|------------|
| Lot | `BATCH-LINE-A01-001` |
| Material | `MAT-WIDGET-A01` |
| Work order | `WO-LINE-A01-001` |
| Quality | `QR-LINE-A01-001` (pass) · `QR-LINE-A01-002` (SCRATCH / ncr) |
| DAG entities | `ENT-RAW-A01` → `ENT-WIP-B01` → `ENT-FG-A01` |

Hub BFF: `mes_genealogy_listGraph`, `mes_genealogy_queryByLot`, `mes_genealogy_queryDagByLot`, `mes_genealogy_listDagEdges` on `root.platform.devices.mes-platform-hub`.

## Nested BoM + operations graph (BL-222)

The bundle seeds BoM `BOM-WIDGET-A01-A` for `MAT-WIDGET-A01` revision `A` with components `MAT-RAW-A01` (2 KG) and `MAT-WIP-A01` (1 EA), plus routing `RT-WIDGET-A01`: `OP-CUT` → `OP-ASSEMBLE` → `OP-TEST`.

Hub BFF on `root.platform.devices.mes-platform-hub`:

| Function | Purpose |
|----------|---------|
| `mes_bom_explode` | Returns flat and nested rows for a parent material |
| `mes_bom_whereUsed` | Returns parent BoMs for a component material |
| `mes_ops_listReady` | Lists ready operations whose predecessors are complete |
| `mes_ops_complete` | Marks an operation complete and releases successors when all predecessors are complete |

Seed operation status for `WO-LINE-A01-001`: `OP-CUT=complete`, `OP-ASSEMBLE=ready`, `OP-TEST=pending`. Completing `OP-ASSEMBLE` makes `OP-TEST` ready.

**Out of scope:** live ERP connector (**BL-169** deferred). Genealogy uses MES app tables only.

## QMS, documents, and integration patterns (BL-224 / BL-225)

Use the shipped quality module as the QMS-lite anchor:

1. Open dashboard **Quality** to view SPC and seed `QUALITY_RECORD` data.
2. Open dashboard **Genealogy** or report **Genealogy** to see `QR-LINE-A01-001` and `QR-LINE-A01-002` linked to lot `BATCH-LINE-A01-001`.
3. For defect disposition workflow, use the `mes-defect-demo` pattern: defect event -> BPMN service/user tasks -> operator confirmation -> app-schema status and report rows.

Documents and labels are implemented as solution reports first. A bundle owns the report query/template, optional BFF parameter preparation, and a document registry row that records type, version, source lot/work-order/quality record, generated user/time, and export/attachment reference. There is no platform document engine in the current MES product.

For Level 4 integration, `mes-platform` exposes an outbox stub: `mes_erp_enqueueOutbox` and `mes_erp_pollOutbox` over `mes_erp_outbox`. The `mes-integration-catalog` skeleton lists placeholder connector rows only; live ERP adapters remain **BL-169 Deferred**.
