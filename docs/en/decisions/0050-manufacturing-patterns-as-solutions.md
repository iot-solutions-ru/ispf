# ADR-0050: Manufacturing Patterns as Solutions

## Status

**Accepted** (2026-07-19) — Docs gate. This records the platform/solution boundary for manufacturing depth before the next MES pattern wave.

## Context

ISPF already has the generic mechanisms needed to build manufacturing applications: object tree, application schemas, bundle packaging, script functions, dashboards, reports, workflows, events, rules, historian, drivers, and MCP-facing agent tools. Existing MES work (`mes-platform`, OEE, dispatch, quality, batch lite, genealogy lite) is delivered as marketplace/application configuration, not as a mandatory domain embedded in the base platform.

The next manufacturing depth wave includes traceability DAG, nested BoM, configure-to-order flows, QMS lite, operations dependency graph, and Level 4 outbox patterns. These are valuable solution patterns, but they are not new platform domains. The base platform should remain a generic industrial application framework.

## Decision

Manufacturing depth is delivered as **solution / marketplace configuration**:

- Traceability DAG: app schema tables + BFF functions + dashboards/reports.
- Nested BoM: app schema + tree metadata + script functions.
- CTO configurator: rules/workflows + app schema + operator/planner UI.
- QMS lite: quality records, nonconformance workflows, attachments, reports.
- Operations dependency graph: app schema DAG + BPMN dispatch/hold/release flows.
- Level 4 outbox: idempotent app outbox and connector workflows, not database-as-primary-API.

### Platform capability gate

New platform work is allowed only when it is generic and explicitly approved as **REQ-PF**. Examples: a generic DAG visualizer, reusable graph query primitive, generic attachment/document capability, or marketplace packaging primitive. Do not add MES-specific entities, tables, controllers, services, or hardcoded domain APIs to `main`.

The rule is:

1. Start in a bundle / marketplace solution.
2. Reuse existing generic engines first.
3. If a missing capability benefits multiple domains, write an explicit REQ-PF and get approval.
4. Keep MES/manufacturing object names, migrations, BFFs, reports, and workflows in the solution artifact.

### Non-goals

- MRP/accounting in core.
- Treating the database as the primary public API.
- A mandatory `root.platform.mes` seed on clean platform boot.
- Platform Java services named after MES/manufacturing entities.
- Replacing customer ERP/MRP systems.

## Consequences

- ISPF can deepen manufacturing scenarios without coupling the base platform to one domain.
- Marketplace bundles become the home for manufacturing IP, demos, and customer variants.
- Generic platform improvements remain reusable across SCADA, BMS, MES, labs, and other solution families.
- Docs and roadmap must present manufacturing as ISPF solution patterns, not platform primitives.

### Risks

- Pattern docs may look like product promises unless acceptance and out-of-scope notes are explicit.
- Solution authors may ask for platform shortcuts; route those through REQ-PF instead of domain code.
- Level 4 integration can be overclaimed; keep live ERP connectors separate from outbox stubs.

## Related

- [0001-app-platform-boundary](0001-app-platform-boundary.md)
- [0007-bundle-tree-packaging](0007-bundle-tree-packaging.md)
- [ISA-95 catalog](../isa95-catalog.md)
- [Manufacturing patterns](../manufacturing-patterns.md)
- [MES Platform](../mes.md)
- [Roadmap Phase 29](../roadmap.md#phase-29--mes-platform)
