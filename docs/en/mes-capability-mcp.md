# MES capability MCP map

> **Status:** Stable mapping for agent-facing MES capabilities. Related: [MES Platform](mes.md), [Manufacturing patterns](manufacturing-patterns.md), [Application principles](application-principles.md).

This page names the manufacturing capabilities an agent may expose over MCP or another tool surface. These are mappings to existing solution hub functions and workflows, not new platform tools.

| Capability | Function path | Side effect | Notes |
|------------|---------------|-------------|-------|
| `dispatch.confirmWorkOrder` | `root.platform.devices.mes-platform-hub` / `mes_dispatch_confirmWorkOrder` | Writes WO status | Operator confirm path from dispatch BPMN |
| `genealogy.queryDagByLot` | `root.platform.devices.mes-platform-hub` / `mes_genealogy_queryDagByLot` | Read-only | Trace seed or customer lot through tracked activity DAG |
| `genealogy.listDagEdges` | `root.platform.devices.mes-platform-hub` / `mes_genealogy_listDagEdges` | Read-only | Full DAG edge listing for reports/agents |
| `bom.explode` | Customer/manufacturing hub / `mes_bom_explode` | Read-only | Pattern name reserved for solution bundles; not in base `mes-platform` |
| `batch.runPhase` | `root.platform.devices.mes-platform-hub` / `mes_batch_runPhase` | Writes batch phase | ISA-88 batch-lite phase transition |
| `erp.enqueueOutbox` | `root.platform.devices.mes-platform-hub` / `mes_erp_enqueueOutbox` | Inserts outbox row | Idempotent Level 4 handoff stub; live ERP connector is BL-169 |
| `integration.listConnectors` | `root.platform.devices.mes-integration-catalog-hub` / `mes_integration_listConnectors` | Read-only | Marketplace skeleton rows: `sap-stub`, `1c-stub`, status `deferred` |

## Agent rules

1. Resolve capabilities to tree function paths and call them through the existing function invocation mechanism.
2. Keep role checks and audit on the underlying object/function; do not grant direct app-schema writes.
3. Document side effects in the MCP/tool descriptor so agents distinguish read-only queries from operator actions.
4. Treat live ERP integration as deferred unless a customer connector bundle supplies tested adapter functions.
