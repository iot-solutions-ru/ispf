# ADR-0046: NATS ownership in `cluster` + TRANSIENT binding persist skip

## Status

**Accepted** (2026-07-16) — two small ownership rules. Package move is **done**; TRANSIENT semantics also belong in [object-model](../object-model.md).

## Context

NATS bridge helpers lived under `com.ispf.server.workflow` though they serve replica sync, not BPMN. Binding evaluation always DB-upserted targets, including `VariableStorageMode.TRANSIENT` values already updated in RAM.

## Decision

1. **NATS fabric types** belong in `com.ispf.server.cluster` (`NatsEventBridge`, JetStream helpers, replica subscribers). `WorkflowService` remains a **client** (PUBLISH_NATS). Config: `NatsProperties`.
2. **`persistBindingRuleTarget` skips DB** when `storageMode == TRANSIENT`. In-memory computed value unchanged; high-rate TRANSIENT tags do not thrash the relational store. Restart durability = prior TRANSIENT contract (not durable) — see [object-model](../object-model.md).

## Consequences

- workflow package = BPMN lifecycle; cluster package = fabric + replica apply.
- This ADR is not a NATS feature catalog — cluster sync semantics stay in [0028](0028-horizontal-active-active-cluster.md)–[0030](0030-cluster-config-structure-replica-sync.md).

## Related

- [0028-horizontal-active-active-cluster](0028-horizontal-active-active-cluster.md)
- [0029-cluster-live-variable-replica-sync](0029-cluster-live-variable-replica-sync.md)
- [0010-binding-rules-only](0010-binding-rules-only.md)
- [object-model](../object-model.md) — TRANSIENT storage mode
