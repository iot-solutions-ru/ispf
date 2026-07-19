# MES Integration Catalog skeleton (BL-225)

Thin marketplace skeleton for the manufacturing integration catalog pattern.

It documents and packages the Level 4 outbox contract without claiming live ERP coverage. Live SAP or 1C adapters remain **BL-169 Deferred**.

## Contract

The production handoff pattern is:

1. `enqueue`: a solution function writes an outbox row with `entityType`, `entityId`, payload JSON, and an idempotency key.
2. `poll`: a connector worker polls pending rows through a function/API boundary, not by treating the database as the public API.
3. `idempotency`: duplicate enqueue for the same business key returns the same key and does not create a second logical message.
4. `status`: the connector updates delivery state, retry/DLQ, and operator-visible diagnostics in solution-owned tables.

The shipped `mes-platform` bundle demonstrates the seed contract with `mes_erp_outbox`, `mes_erp_enqueueOutbox`, and `mes_erp_pollOutbox`. This skeleton adds a catalog surface for connector readiness.

## Included

| File | Purpose |
|------|---------|
| `bundle.json` | Deployable skeleton bundle with connector seed rows |
| `mes_integration_listConnectors` | Read-only placeholder hub function returning `sap-stub` and `1c-stub` with status `deferred` |

Operator UI: `?mode=operator&app=mes-integration-catalog`

Related docs:

- [Manufacturing patterns](../../docs/en/manufacturing-patterns.md)
- [MES capability MCP map](../../docs/en/mes-capability-mcp.md)
- [MES Platform](../../docs/en/mes.md)
