> **Language:** Canonical English. Russian edition: [ru/mes.md](../ru/mes.md).

# MES Platform Operator Guide (`mes-platform`)

> **Status:** Stable — operator quick guide for the `mes-platform` marketplace solution bundle. Status tags: [doc-status.md](doc-status.md).

The **`mes-platform`** solution bundle (`v1.5.0`) is an installable application package in `examples/mes-platform/`. A clean ISPF installation does not create `root.platform.mes` until the bundle is deployed. For full architecture and integration test details, see **[Reference MES Platform (`reference-mes-platform.md`)](reference-mes-platform.md)**.

**Related guides:** [Reference MES platform](reference-mes-platform.md) · [Plan execution AM](mes-plan-execution.md) · [Plan IF map](mes-plan-interfaces.md) · [ISA-95 catalog](isa95-catalog.md) · [Manufacturing patterns](manufacturing-patterns.md) · [MES capability MCP map](mes-capability-mcp.md).

---

## 1. Deploying and Opening the Operator Workspace

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-platform/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-platform/bundle.json
```

Open Operator UI: `http://localhost:8080/?mode=operator&app=mes-platform`  
Application logic hub (`SINGLETON`): `root.platform.singleton-blueprints.mes-platform-hub-v1`

---

## 2. Operator Screens

| Dashboard Tab | Operator Actions |
|---------------|------------------|
| **Dispatch** | Claim and confirm work-order tasks from the BPMN **Work Queue** (`mes_dispatch_confirmWorkOrder`) |
| **OEE** | Inspect line and shift KPIs (`mes_oee_getKpi`, seed Morning shift `oeePct > 80%`) and log downtime (`mes_oee_addDowntime`) |
| **Quality** | View statistical process control (SPC) charts (`spcMeasurement` vs `spcUcl` / `spcLcl`) and quality records |
| **Batch** | Track and advance ISA-88 batch phases (`charge` → `react` → `discharge` via `mes_batch_runPhase`) |
| **Genealogy** | Trace lot genealogy (`BATCH-LINE-A01-001` ↔ material `MAT-WIDGET-A01` ↔ work order `WO-LINE-A01-001` ↔ quality records) |
| **BoM + Ops** | Explode multi-level Bill of Materials (`mes_bom_explode` for `MAT-WIDGET-A01`) and complete ready routing operations (`mes_ops_complete`) |
| **Data / Resources / Publish / Official plan / Monitor / Exec alerts / Integration** | Plan execution in the main operator app: data freshness, resource confirmation, official versions, plan/fact, alerts, delivery (`mes_plan_*`) |

---

## 3. Seeded MES Catalog Objects (`root.platform.mes.*`)

| Object Tree Path | Node Type | Purpose |
|------------------|-----------|---------|
| `root.platform.mes.work-orders.wo-line-a01-001` | `CUSTOM` | Seed work order (`WO-LINE-A01-001`) |
| `root.platform.mes.operations.op-assemble-a01` | `CUSTOM` | Routing operation step |
| `root.platform.mes.lots.batch-line-a01-001` | `CUSTOM` | Seed ISA-88 lot (`BATCH-LINE-A01-001`) |
| `root.platform.mes.shifts.shift-morning-a01` | `CUSTOM` | Morning shift on `LINE-A01` |
| `root.platform.mes.quality-records.qr-line-a01-001` | `CUSTOM` | Quality inspection record |
