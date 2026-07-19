> **Language:** Canonical English. Russian edition: [../ru/manufacturing-patterns.md](../ru/manufacturing-patterns.md).

# ISPF manufacturing patterns

> **Status:** Charter — Solution / marketplace catalog. Hub: [doc-status.md](doc-status.md). Boundary ADR: [0050-manufacturing-patterns-as-solutions](decisions/0050-manufacturing-patterns-as-solutions.md).

This catalog describes ISPF's manufacturing solution patterns. They are delivered as application bundles, marketplace solutions, object-tree configuration, app-schema migrations, dashboards, reports, workflows, and BFF/script functions. They are **not** MES entities in the base platform.

[ISA-95 catalog](isa95-catalog.md) maps levels and hierarchy. This page maps process patterns that can run across those levels: traceability, BoM, routing, quality, integration, documents, and portal access.

## Pattern catalog

### Traceability DAG

**Intent:** Trace material, lot, work-order, operation, equipment, and quality relationships as a directed graph.

**ISPF mapping:** Store graph edges in the app schema; expose BFF functions `mes_genealogy_queryDagByLot` and `mes_genealogy_listDagEdges`; show graph/report widgets in Operator UI; attach tree nodes for seed lots or hubs.

**Home artifact:** `mes-platform` marketplace bundle 1.4.0 (BL-221 **Done**) or customer manufacturing bundle.

**Status:** Implemented on `mes-platform` 1.4.0 (`mes_platform_genealogy_dag_v2`).

**Acceptance:** Given a known lot, the solution returns upstream materials, work order, operation, quality records, and downstream lots with deterministic IDs and timestamps.

**Out of scope:** A platform-wide genealogy database or hardcoded MES graph service.

### Nested BoM

**Intent:** Represent multi-level material definitions and substitutions for planning, work instructions, and traceability.

**ISPF mapping:** App schema tables for item, revision, component, quantity, unit, and alternate; BFF functions for explosion/where-used; tree metadata for key material definitions; dashboards and reports for planners.

**Home artifact:** `mes-platform` marketplace bundle 1.5.0 (BL-222 **Done**) or customer manufacturing bundle.

**Status:** Implemented on `mes-platform` 1.5.0 (`mes_platform_bom_ops_v1`) with `mes_bom_explode` and `mes_bom_whereUsed`.

**Acceptance:** A planner can explode a finished item to nested components and run where-used for a component revision.

**Out of scope:** MRP, costing, accounting, or inventory valuation in core.

### Operations Dependency Graph

**Intent:** Model routing steps, holds, parallel work, rework, and release dependencies.

**ISPF mapping:** App schema DAG for operations and precedence; BPMN for dispatch/hold/release; work-queue widgets for operator tasks; BFF functions for ready-operation queries.

**Home artifact:** `mes-platform` marketplace bundle 1.5.0 (BL-222 **Done**) or MES dispatch/routing bundle.

**Status:** Implemented on `mes-platform` 1.5.0 (`mes_operation_def`, `mes_operation_edge`, `mes_operation_status`) with `mes_ops_listReady` and `mes_ops_complete`.

**Acceptance:** The solution only exposes operations whose predecessors are complete or waived, and records hold/release decisions in an audit trail.

**Out of scope:** A new platform scheduler or a full APS engine.

### CTO Configurator

**Intent:** Turn a customer/order configuration into a valid buildable variant with required operations, materials, documents, and checks.

**ISPF mapping:** App schema for option families, rules, compatibility, and generated order lines; Platform Rules / CEL for validations; BPMN for approval; BFF functions for quote/build validation.

**Home artifact:** [`mes-cto`](../../examples/mes-cto) marketplace bundle.

**Acceptance:** A user selects options, receives deterministic compatibility errors or a valid build package, and can generate a work-order draft.

**Out of scope:** Pricing/accounting engines and ERP master-data ownership.

### Extensible Attributes

**Intent:** Let projects add domain-specific fields without changing platform Java.

**ISPF mapping:** JSON attributes in app schema, typed variables on object-tree nodes, blueprint metadata, schema-described forms, and reports that render configured labels.

**Home artifact:** Shared manufacturing bundle convention.

**Acceptance:** A solution can add a new attribute, expose it in planner/operator UI, validate it, and include it in reports without a server code change.

**Out of scope:** Adding platform columns or controllers for each customer field.

### QMS Lite

**Intent:** Capture inspections, defects, nonconformance, disposition, and corrective-action workflow for a lightweight manufacturing quality loop.

**ISPF mapping:** Use solution-owned app schema records for inspections, SPC samples, defects, NCR/disposition state, attachments, and lot/work-order links. The shipped `mes-platform` quality module already seeds `QUALITY_RECORD` nodes (`root.platform.mes.quality-records.*`), `quality-record-v1`, `mes_genealogy_quality`, the Quality / SPC dashboard, and genealogy reports that connect lot -> quality record. For disposition routing, reuse the `mes-defect-demo` BPMN style: a detected defect becomes a workflow task, the operator confirms the route/disposition, and the solution records the decision in its own app tables and event journal.

**Home artifact:** `mes-platform` quality module plus optional customer QMS bundle.

**Acceptance:** A failed inspection or defect can be represented as a quality/NCR row, routed through BPMN for review/disposition, linked to lot/work-order/equipment, and surfaced in genealogy, quality dashboards, and reports without platform Java.

**Out of scope:** Full eQMS, regulated document-control certification, or external CAPA ownership in core.

### Integration Catalog

**Intent:** Document and package repeatable Level 4 integration patterns.

**ISPF mapping:** App outbox/inbox tables, idempotency keys, connector workflows, retry/DLQ, reports, and solution-specific functions. The `mes-platform` bundle already contains the seed outbox contract (`mes_erp_outbox`, `mes_erp_enqueueOutbox`, `mes_erp_pollOutbox`) for enqueue/poll/idempotency. The separate `mes-integration-catalog` skeleton packages the catalog surface and a placeholder `mes_integration_listConnectors` function with seed rows (`sap-stub`, `1c-stub`, status `deferred`). Public interaction goes through documented APIs/functions, not direct DB access.

**Home artifact:** Manufacturing integration bundle.

**Acceptance:** A solution can enqueue a work-order/material/export event once, poll pending rows, preserve idempotency, and show connector readiness honestly. Live SAP/1C adapters remain BL-169 and are not claimed by this pattern.

**Out of scope:** DB-as-primary-API and guaranteed live ERP connector coverage in the base platform.

### Domain MCP Tools

**Intent:** Expose approved manufacturing actions to agents as bounded, auditable tools.

**ISPF mapping:** Publish selected BFF/script functions and workflows with schemas, descriptions, side-effect class, audit metadata, and role checks. This is an agent/MCP surface over stable hub functions, not new platform tools. Stable capability names map to tree functions such as `dispatch.confirmWorkOrder` -> `mes_dispatch_confirmWorkOrder`, `genealogy.queryDagByLot` -> `mes_genealogy_queryDagByLot`, `genealogy.listDagEdges` -> `mes_genealogy_listDagEdges`, `batch.runPhase` -> `mes_batch_runPhase`, and `erp.enqueueOutbox` -> `mes_erp_enqueueOutbox`. See [MES capability MCP map](mes-capability-mcp.md).

**Home artifact:** Agent-ready manufacturing bundle.

**Acceptance:** An agent can call a documented manufacturing capability, receive structured output, and leave an audit trail without bypassing solution permissions.

**Out of scope:** Free-form agent writes to app tables or hidden REST endpoints.

### Documents / Labels

**Intent:** Generate and track work instructions, travelers, labels, certificates, and packing documents.

**ISPF mapping:** Treat reports as the first document engine: bundle BFF functions prepare rows and parameters, `reports[]` render work instructions/travelers/certificates/labels, and an app-schema document registry records document type, template/version, source lot/work-order, generation user/time, and attachment/export reference. Object-tree links point from lots, work orders, and quality records to the report or registry row. A generic platform document engine is only a future platform request after explicit approval; until then, document control stays in solution bundles.

**Home artifact:** Manufacturing documents bundle.

**Acceptance:** A work order or lot can render the correct document/label version and record who generated or printed it.

**Out of scope:** A regulated enterprise document-management system in core.

### External Portal Role

**Intent:** Give suppliers, customers, or auditors narrow access to manufacturing status and documents.

**ISPF mapping:** Use tenant ACLs, role-scoped users, object/function permissions, dashboards with filtered context, a portal-specific Operator UI profile, and audit events. Customer/supplier scopes should be read-only by default: allowed lots/orders/documents, no internal dispatch/disposition actions, and no direct app-table access.

**Home artifact:** Supplier/customer portal bundle.

**Acceptance:** A portal user sees only allowed lots/orders/documents and cannot invoke internal operator actions.

**Out of scope:** A separate public SaaS portal runtime in the platform.

## Boundary checklist

1. Keep pattern data in app schemas and bundle migrations.
2. Keep manufacturing BFF names and dashboards in the solution artifact.
3. Use BPMN, rules, reports, and script functions before asking for platform work.
4. Ask for platform work only for generic capabilities that apply beyond manufacturing and have explicit approval.
5. Never claim live ERP/MRP/accounting unless the connector is implemented and tested for that customer path.

## Related

- [ADR-0050](decisions/0050-manufacturing-patterns-as-solutions.md)
- [MES Platform](mes.md)
- [MES capability MCP map](mes-capability-mcp.md)
- [Reference MES platform](reference-mes-platform.md)
- [ISA-95 catalog](isa95-catalog.md)
- [Application principles](application-principles.md)
- [Roadmap Phase 29](roadmap.md#phase-29--mes-platform)
