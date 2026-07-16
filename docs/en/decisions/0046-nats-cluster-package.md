# ADR-0046: NATS in `cluster` package + TRANSIENT binding persist skip

## Status

**Accepted** (2026-07-16)

## Context

NATS bridge/subscriber/JetStream helpers lived under `com.ispf.server.workflow` even though they serve replica sync and platform messaging, not BPMN. Binding evaluation always called `persistBindingRuleTarget` → DB upsert, including `VariableStorageMode.TRANSIENT` targets whose values are already RAM-updated.

## Decision

### 1. Package move

Move NATS fabric types to `com.ispf.server.cluster`:

- `NatsEventBridge`, `NatsJetStreamSupport`, `NatsObjectChangeSubscriber`, `NatsReplicaEventProcessor`, `RateLimitedNatsErrorListener`

`WorkflowService` remains a **client** of `NatsEventBridge` (PUBLISH_NATS / message tasks). Config stays in `com.ispf.server.config.NatsProperties`.

### 2. Binding target persist policy

`ObjectManager.persistBindingRuleTarget` **skips** DB write when `variable.storageMode() == TRANSIENT`. In-memory value from `setComputedValue` is unchanged; high-rate computed tags stop thrashing the relational store.

## Consequences

- Clearer ownership: workflow = BPMN lifecycle; cluster = fabric + replica apply.
- TRANSIENT binding targets rely on process memory / replica sync paths — restart semantics unchanged from prior TRANSIENT mapping (value not durable).
- Risks: docs that still say `workflow.Nats*` need updates over time; import churn in a few publishers/health services.

## Related

- [0028-horizontal-active-active-cluster](0028-horizontal-active-active-cluster.md)
- [0029-cluster-live-variable-replica-sync](0029-cluster-live-variable-replica-sync.md)
- [0010-binding-rules-only](0010-binding-rules-only.md)
