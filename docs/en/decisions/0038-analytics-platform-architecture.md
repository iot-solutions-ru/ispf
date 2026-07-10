# ADR-0038: Analytics platform architecture (AF-capable)

## Status

**Proposed** (2026-07-09)

## Context

Phase 28 delivered historian scale foundations (tiers config, dual-write, aggregate API, BL-160 AF-lite catalog). That covers **storage and on-read rollups** for dashboards, not a full **calculation + OLAP plane** comparable to OSIsoft PI Asset Framework.

Operators need:

1. **Derived tags** as first-class live variables (alarms, bindings, dashboards share one value).
2. **Pre-computed rollups** for long-range trends without re-aggregating millions of raw samples per chart.
3. **Multi-tag analytics** (ad-hoc queries, KPI chains) backed by ClickHouse warm tier.
4. **Flexible topology** — single JVM on a small site **or** role-separated cluster (ingest / HMI / analytics workers) without forking the product.

Binding rules ([0010-binding-rules-only](0010-binding-rules-only.md)) remain the mechanism for **per-object reactive logic**. Analytics calculations that depend on historian windows, cross-tag DAGs, and backfill are a **separate engine** that **writes results back** into the object tree (and NATS live sync in cluster).

## Decision

### 1. Layered model

| Layer | Responsibility | Stores | Existing |
|-------|----------------|--------|----------|
| **OLTP / tree** | Objects, blueprints, RBAC, workflow | PostgreSQL | Yes |
| **Historian ingest** | Sample append, retention, tiers | PG/Timescale, CH, optional cold | Partial (BL-159) |
| **Calculation engine** | Derived tags, DAG, schedules, backfill | Stateless; reads historian, writes tree vars | No (BL-203–204) |
| **OLAP rollups** | Materialized buckets per tag/window | ClickHouse tables / MVs | No (BL-205) |
| **Query API** | Multi-tag trends, expressions | Routes to hot/warm/cold | Partial (single-tag REST) |

Object tree remains **source of truth for metadata and live derived values**. ClickHouse is the **analytics read plane** for warm/cold ranges and rollup tables.

### 2. Object model extensions (BL-209)

> **Amendment (ADR-0041, 2026-07-09):** Historian computations are **`BindingRule` with `kind: historian`** in `@bindingRules`, not `ANALYTICS_TEMPLATE` tree objects. Tag path = `objectPath#ruleId`; presets are static code + [analytics-historian-cookbook](../analytics-historian-cookbook.md). The bullets below describe the original BL-209 proposal; template catalog and `analytics-tag-v1` metadata vars are **deprecated** for new work.

- ~~Keep `ANALYTICS_TEMPLATE` catalog (`root.platform.analytics`) for built-in KPI recipes.~~ → static presets + binding rules
- Add optional **`ANALYTICS_TAG`** (or RELATIVE blueprint `analytics-tag-v1` on `DEVICE`) for deployed derived tags with:
  - `expression` / `helper` + `sourcePaths[]`
  - `schedule` (periodicMs, alignToWallClock)
  - `rollupBuckets[]` (which materialized windows to maintain)
  - `lineage` (upstream tag paths for impact analysis)

Haystack / Brick tags continue as semantic overlay ([0021-haystack-semantic-overlay](0021-haystack-semantic-overlay.md)); analytics metadata is additive.

### 3. Calculation engine (BL-203–204)

New package: `packages/ispf-analytics-engine` (library consumed by `ispf-server`; optional standalone worker later).

- Build **dependency DAG** from analytics tag definitions (cycle detection).
- Triggers: **periodic** (indexed like binding periodic rules), **on source sample** (coalesced), **manual backfill** API.
- Evaluation functions (v1): `rollingAvg`, `rateOfChange`, `totalizer`, `min`/`max` over window, `oeeComposite` (delegates to MES BFF where configured).
- Output: `setVariableValue` on target path + cluster NATS fan-out ([0029-cluster-live-variable-replica-sync](0029-cluster-live-variable-replica-sync.md)).

**Not in v1:** full PI Analytics expression language, ML inference (see BL-175).

### 4. OLAP rollups (BL-205)

- ClickHouse **materialized views** or dedicated `variable_rollups` table keyed by `(object_path, variable_name, field_name, bucket, bucket_start)`.
- Materializer job runs on **`analytics`** capability nodes (or `compute` until profile exists).
- Dashboard / Analytics API prefers rollup table when `range > hotCutoff` and bucket matches catalog.

### 5. Analytics Query API (BL-206)

```
POST /api/v1/platform/analytics/query
```

- Body: `{ tags: [{path, variable, field}], from, to, bucket, agg: avg|min|max|last }`
- Auth: operator+; respects variable ACL when BL-154 lands.
- Implementation: `TierRoutingVariableHistoryQueryStore` + rollup table + CH HTTP.

Optional: `POST .../analytics/expression` for CEL-over-historian (v2).

### 6. Replica profile `analytics` (BL-207)

Extend [0032-replica-profiles-and-capabilities](0032-replica-profiles-and-capabilities.md):

| Profile | Capabilities |
|---------|----------------|
| `analytics` | `analytics`, `replica-sync`, `schedulers` (no `http-public` by default) |

- **Single node:** `ISPF_REPLICA_PROFILE=unified` — engine runs in-process (default for Prod S).
- **Prod L:** dedicated `analytics` pods + CH cluster; `io` replicas do not run materializer.

Env: `ISPF_ANALYTICS_ENGINE_ENABLED=true`, `ISPF_ANALYTICS_MATERIALIZER_ENABLED=true`.

### 7. Event frames (BL-208)

Lightweight **time windows** attached to analytics context (not full PI Event Frames):

- Types: `shift`, `batch`, `downtime`, `custom`
- Sources: MES `SHIFT` / `batch-v1` objects, manual API, correlator-fired events
- Analytics engine scopes aggregate queries to active frame (`frameId` on query API)

### 8. Deployment profiles (same jar, different topology)

| Profile | Tags (history) | Topology | Historian | Analytics engine |
|---------|----------------|----------|-----------|------------------|
| **Lab S** | < 500 | 1× unified | hot-only PG | in-process, no materializer |
| **Site M** | 500–5k | 2× unified + CH | three-tier dual-write | in-process + CH rollups |
| **Enterprise L** | 5k–50k+ | io + hmi-read + analytics×N + CH cluster | warm read primary | dedicated analytics replicas |

See [analytics-platform-roadmap](../analytics-platform-roadmap.md) for BL-200…210 acceptance tests per profile.

### 9. Relationship to BL-160 (AF-lite)

BL-160 **must complete** (editor, PUT API, `derivedValue` runtime) as **BL-201** before enterprise analytics. BL-160 templates become the **seed catalog** for BL-203 built-in helpers.

## Consequences

- New backlog BL-200…210 (Phase 33); historian Phase 33 target shifts from "petabyte storage" to **AF-capable analytics**.
- ClickHouse becomes **required** for Enterprise L analytics SLO; PG remains mandatory for tree.
- Binding rules unchanged; analytics engine must not fork CEL semantics for simple device logic.
- [0041-multi-tag-historian-computations](0041-multi-tag-historian-computations.md) — historian binding rules (supersedes template catalog)
- [analytics-historian-cookbook](../analytics-historian-cookbook.md)

## Related

- [0035-historian-dual-write](0035-historian-dual-write.md) — dual-write migration
- [0032-replica-profiles-and-capabilities](0032-replica-profiles-and-capabilities.md) — replica capabilities
- [historian-tiers](../historian-tiers.md) — hot/warm/cold
- [roadmap.md § Phase 33](../roadmap.md#phase-33--analytics-platform-af-capable)
- BL-160, BL-159, BL-161, BL-165 (OEE)
