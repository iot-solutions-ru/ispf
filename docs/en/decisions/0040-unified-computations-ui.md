# ADR-0040: Unified computations UI (bindings + historian)

## Status

**Accepted** (2026-07-09), amended by [0041-multi-tag-historian-computations](0041-multi-tag-historian-computations.md) (2026-07-09)

## Context

Binding rules ([0010-binding-rules-only](0010-binding-rules-only.md)) and analytics derived tags ([0038-analytics-platform-architecture](0038-analytics-platform-architecture.md)) both express **when → expression → target variable**. Operators saw duplicate tabs (**Bindings**, **Analytics tag**) and duplicate tabs and overlapping concepts.

Pre-1.0: no legacy UI compatibility required.

## Decision

### 1. Single inspector tab: **Computations** (`tab.computations`)

Replaces separate **Bindings** and **Analytics tag** tabs in `ObjectPropertiesEditor`.

`ObjectComputationsPanel` sections:

| Section | Content | Engine |
|---------|---------|--------|
| **Rules** | Unified `@bindingRules` list — reactive **and** `kind: historian` | `BindingRuleEngine` (reactive only) + analytics scheduler (historian) |
| **Historian status** | Catalog rows for this device (quality, expression) | `AnalyticsTagCatalogService` |
| **Audit** | Binding invoke journal | `BindingInvokeAuditService` |

**Expression debugger** remains a separate tab (debug tool, not configuration).

### 2. Shared expression language

- Reactive rules: CEL + platform functions + `refAt` / `self.*`
- Historian rules: builtins (`rollingAvg`, `rateOfChange`, `oee`) + CEL + `hist.*` ([analytics-platform-roadmap](../analytics-platform-roadmap.md))

Historian rules are **not** a separate inspector — they are rows in the same rules table with `kind: historian` ([0041-multi-tag-historian-computations](0041-multi-tag-historian-computations.md)).

### 3. Analytics plane (unchanged scope)

- Global analytics DAG, tag catalog API, multi-tag query, ClickHouse rollups — analytics engine
- Historian metadata: `@historianRuleMeta` per rule id (not per-device `analyticsExpression` vars)

## Consequences

- Web console: one tab for all object-level computations
- Docs: [bindings](../bindings.md) (reactive + `kind` field), [analytics-tag-catalog](../analytics-tag-catalog.md), [analytics-historian-cookbook](../analytics-historian-cookbook.md)
- ~~Optional future: `kind: historian` on `BindingRule`~~ — **done in ADR-0041**

## Related

- [0041-multi-tag-historian-computations](0041-multi-tag-historian-computations.md)
- [0010-binding-rules-only](0010-binding-rules-only.md)
- [0019-platform-rule-unification](0019-platform-rule-unification.md)
- [0038-analytics-platform-architecture](0038-analytics-platform-architecture.md)
