> **Language:** Canonical English. Russian edition: [ru/analytics-platform-roadmap.md](../ru/analytics-platform-roadmap.md).

# Analytics platform roadmap — BL-200…210 (AF-capable)

**Goal:** evolve from **AF-lite** (BL-160) to an **AF-capable** analytics plane — derived tags, materialized rollups, multi-tag queries — deployable on **one server** or **role-separated cluster**.

| | |
| --- | --- |
| **Phase** | 33 (extends Phase 28 historian) |
| **ADR** | [0038-analytics-platform-architecture.md](decisions/0038-analytics-platform-architecture.md) |
| **Prerequisites** | BL-159 (tiers), BL-160 (AF-lite), BL-161 (SLO), ClickHouse dual-write on prod |
| **Competitive target** | Historian dimension **10/10** — "petabyte + asset framework" per [roadmap.md § Phase 33](roadmap.md#phase-33--analytics-platform-af-capable) |
| **Status of record** | BL IDs and Done/Partial live only in [roadmap.md](roadmap.md) — this file is the deep charter |

---

## Positioning

| | AF-lite (BL-160) | Analytics platform (BL-200…210) |
| --- | --- | --- |
| Rollups | On-read in Chart / REST aggregate | Pre-computed + on-read fallback |
| Derived values | Single `derivedValue` per template | DAG of analytics tags, backfill |
| Scope | 3 built-in templates | Extensible catalog + custom tags |
| Topology | In ISPF JVM | unified **or** `analytics` replicas |
| Scale target | ~500 tags, operator dashboards | 50k+ tags, enterprise SLO |

---

## Deployment scenarios

### Scenario A — Single server (~500 history-enabled tags)

**When:** one plant, Prod S, demostand, training lab.

```text
ispf-server (unified) + PostgreSQL/Timescale + optional ClickHouse on same host
```

| Setting | Value |
|---------|-------|
| `ISPF_REPLICA_PROFILE` | `unified` |
| `ISPF_HISTORIAN_DEPLOY_PROFILE` | `hot-only` or `three-tier` |
| `ISPF_ANALYTICS_ENGINE_ENABLED` | `true` |
| `ISPF_ANALYTICS_MATERIALIZER_ENABLED` | `false` (optional until >1k tags) |

**Acceptance:** BL-201 + BL-204 Done; derived tag on device updates; alarm on `derivedValue` fires; chart uses template bucket.

### Scenario B — Site cluster (~5k tags)

**When:** HA operators, moderate telemetry, analytics without dedicated analytics farm.

```text
nginx → 2–4× unified replicas
        PostgreSQL, Redis, NATS, ClickHouse (single or 2-node)
```

| Setting | Value |
|---------|-------|
| Historian | three-tier, dual-write, warm read cutover (BL-202) |
| Analytics | engine in-process on all replicas; **one** elected materializer leader |
| SLO | aggregate 1M points <2s (BL-161), 50 concurrent dashboard queries |

**Acceptance:** BL-202…206 Partial+; multi-tag query API; rollups for `5m`/`1h` buckets.

### Scenario C — Enterprise (~50k+ tags, petabyte path)

**When:** corporate historian, many lines, long retention, dedicated analytics load.

```text
nginx → hmi-read replicas (REST/WS)
        io replicas (drivers)
        analytics replicas ×N (engine + materializer)
        ClickHouse cluster
        PostgreSQL (tree only — not primary historian read)
        S3 cold tier
```

| Setting | Value |
|---------|-------|
| `ISPF_REPLICA_PROFILE` | `analytics` on worker nodes |
| Historian write | io → hot JDBC + CH async |
| Historian read | warm CH primary; hot window for last 24h |
| Cold | BL-163 Parquet + federated export (BL-202) |

**Acceptance:** BL-210 lab gate — 50k tags catalog, 1B samples query, p95 multi-tag query <3s on 8 vCPU CH.

---

## BL backlog

### BL-200 — Analytics platform charter

| Field | Value |
|-------|-------|
| Priority | P2 |
| Depends on | — |
| Delivers | ADR-0038 accepted; this document linked from Phase 25/28 |

**Acceptance**

- [ ] ADR-0038 status → Accepted after review
- [x] Phase 33 section in [roadmap.md](roadmap.md)
- [ ] Competitive matrix historian row references BL-200…210

---

### BL-201 — AF-lite completion (BL-160 full)

| Field | Value |
|-------|-------|
| Priority | P1 |
| Depends on | BL-160 partial |
| Delivers | Bridge from catalog-only to runtime derived tags |

**Scope**

1. `AnalyticsTemplateInspector` + Explorer routing for `ANALYTICS_TEMPLATE`
2. `GET`/`PUT /api/v1/platform/analytics/templates`
3. `AnalyticsDerivedTagService` — periodic recompute → `derivedValue` / `oeePct`
4. UI «Apply template to device» (RELATIVE blueprint patch)
5. Example `examples/analytics-rolling-avg/`

**Acceptance**

- [ ] Template `windowBucket` change reflected in Chart without redeploy
- [ ] Device with `rolling-avg-v1`: `derivedValue` updates from historian aggregate
- [ ] Alert rule on `derivedValue` evaluates in integration test
- [ ] `docs/en/reference-asset-analytics.md` published

---

### BL-202 — Historian tier enforcement

| Field | Value |
|-------|-------|
| Priority | P1 |
| Status | **Done** |
| Depends on | BL-159 config, BL-161 SLO |
| Delivers | Complete hot → warm → cold path |

**Scope**

1. Write path: route samples to tier per retention policy (not only dual-write best-effort)
2. Read path: `TierRoutingVariableHistoryQueryStore` default on prod profile
3. Warm read cutover playbook (documented env flip)
4. Cold tier scheduled export job (Parquet to S3); query stub or Athena note
5. Lab: `deploy/run_lab_historian_*.py` green at 1M aggregate SLO

**Acceptance**

- [x] Prod profile `three-tier` documented in [historian-tiers.md](historian-tiers.md) with enforcement (not config-only)
- [x] Query spanning 30d uses CH without full PG scan (`TierRoutingVariableHistoryQueryStore`)
- [x] Cold export job produces Parquet under configured prefix (`HistorianColdArchiveService`)

---

### BL-203 — Calculation engine core

| Field | Value |
|-------|-------|
| Priority | P1 |
| Status | **Done** |
| Depends on | BL-201 |
| Delivers | `ispf-analytics-engine` module |

**Scope**

1. DAG builder from analytics tag definitions (cycle detection, topological order)
2. Scheduler: `platform_analytics_schedules` table + leader-elected tick (reuse scheduler infra)
3. Built-in evaluators: `rollingAvg`, `rateOfChange`, `totalizer`, `last`, `min`/`max` window
4. Coalesced **on-change** trigger when source variable updates (history-enabled sources)
5. Metrics: `ispf.analytics.evaluations.total`, `ispf.analytics.evaluation.latency`

**Acceptance**

- [x] Unit tests: DAG cycle rejected; chain A→B→C evaluates in order
- [x] Integration: 3-tag chain on lab device completes <5s periodic tick
- [x] No regression on binding rule throughput (engine on separate thread pool `analytics-engine-`)

---

### BL-204 — Derived tag write-back & cluster sync

| Field | Value |
|-------|-------|
| Priority | P1 |
| Depends on | BL-203 |
| Status | **Done** |
| Delivers | Live derived values visible everywhere |

**Scope**

1. Engine writes via `ObjectManager.setVariableValue` with `observedAt` timestamp
2. NATS live sync for derived outputs in cluster ([ADR-0029](decisions/0029-cluster-live-variable-replica-sync.md))
3. WebSocket push to subscribed dashboards (via existing `publishConfigVariableChangeAfterCommit`)
4. Backfill API: `POST /api/v1/platform/analytics/tags/backfill?path=&from=&to=`

**Acceptance**

- [x] NATS fan-out on config write with `observedAt` (`AnalyticsDerivedReplicaSyncTest`)
- [x] Backfill repopulates gaps after historian outage simulation (`AnalyticsBackfillIntegrationTest`)
- [x] Binding rule can reference `otherDevice.derivedValue` reliably (`AnalyticsCrossDeviceBindingIntegrationTest`)
- [x] Two-replica lab gate (`docker-compose.analytics.yml`, `AnalyticsClusterWorkloadService`)

---

### BL-205 — Materialized rollups (OLAP layer)

| Field | Value |
|-------|-------|
| Priority | P2 |
| Depends on | BL-202, BL-203 |
| Status | **Done** |
| Delivers | Pre-computed buckets in ClickHouse |

**Scope**

1. Schema: `variable_rollups` table in ClickHouse (`ReplacingMergeTree`)
2. Materializer: per-tag subscription from analytics catalog (`rollupBuckets: 5m,1h,8h`)
3. Dashboard / aggregate API: prefer rollup table when range ≥7d and subscription exists
4. Rebuild job: `POST /api/v1/platform/analytics/rollups/rebuild`

**Acceptance**

- [x] Aggregate API returns `dataSource: rollup|raw|none` (`VariableHistoryAggregateResponse`)
- [x] Rollup-first routing for subscribed tags on long ranges (`HistorianRollupQueryService`)
- [x] Materializer leader-elected tick + rebuild API (`HistorianRollupMaterializerRunner`)
- [ ] 30d chart lab gate with row-scan bound (deferred to BL-210 scale gates)

---

### BL-206 — Multi-tag Analytics Query API

| Field | Value |
|-------|-------|
| Priority | P2 |
| Depends on | BL-202, BL-205 |
| Status | **Done** |
| Delivers | Ad-hoc multi-tag analytics |

**Scope**

1. `POST /api/v1/platform/analytics/query` — aligned time buckets, multiple series
2. `POST /api/v1/platform/analytics/query/export` — CSV/Parquet stream (extends BL-163)
3. Web-console: chart widget optional multi-series via `analyticsQueryTagsJson`
4. Rate limits + query timeout per tenant (soft)

**Acceptance**

- [x] Integration test: 3 tags same device + 2 remote paths (`AnalyticsMultiTagQueryIntegrationTest`)
- [x] Aligned bucket merge unit test (`AnalyticsQueryServiceTest`)
- [x] OpenAPI fragment in [api.md](api.md)
- [x] Query 10 tags × 7d × 1h buckets <3s p95 on Scenario B hardware (BL-210 lab gate — JVM gate in CI; full CH gate in `analytics-scale-gate.sh`)

---

### BL-207 — Analytics replica profile

| Field | Value |
|-------|-------|
| Priority | P2 |
| Depends on | BL-203, [ADR-0032](decisions/0032-replica-profiles-and-capabilities.md) |
| Status | **Done** |
| Delivers | Role-separated analytics workers |

**Scope**

1. `ReplicaProfile.ANALYTICS` + capability `analytics`
2. Gates: materializer + heavy backfill only on `analytics` capability
3. `docker-compose.analytics.yml` — unified + analytics sidecar pattern
4. Helm values: `analytics.replicaCount`, affinity to CH zone
5. [demostands.md](demostands.md) — row «Analytics scale-out»

**Acceptance**

- [x] `io` replica does not run materializer when `analytics` replicas exist
- [x] `GET /api/v1/platform/cluster/health` lists analytics capability (`analyticsReplicasUp`, `analyticsWorkloadActive`, `localAnalyticsCapability`)
- [x] Single-node: `unified` runs engine (no extra containers required)

---

### BL-208 — Event frames & shift context

| Field | Value |
|-------|-------|
| Priority | P2 |
| Depends on | BL-203, BL-165 (OEE) |
| Status | **Done** |
| Delivers | Time-scoped analytics (shift/batch/downtime) |

**Scope**

1. `EVENT_FRAME` object type or blueprint `event-frame-v1`
2. Active frame registry (in-memory + PG) for `shift`, `batch`, `downtime`
3. Integration: MES `SHIFT`, ISA-88 `batch-v1` auto-open/close frames
4. Query API: optional `frameId` filter; OEE template uses shift frame boundary

**Acceptance**

- [x] OEE rollup respects 8h shift window from MES shift object (`open-shift`, `eventFrameScope`, `AnalyticsDerivedTagService`)
- [x] Batch phase change closes previous frame, opens new (`EventFrameVariableChangeListener`, `EventFrameIntegrationTest`)
- [x] Operator report: downtime minutes per frame (`GET /api/v1/platform/analytics/frames/downtime-report`)

---

### BL-209 — Tag catalog, lineage & UI

| Field | Value |
|-------|-------|
| Priority | P2 |
| Depends on | BL-201, BL-203 |
| Delivers | Discoverable analytics metadata |
| Status | **Done** |

**Scope**

1. Blueprint `analytics-tag-v1` on devices / folders
2. Explorer: analytics tag inspector (expression, sources, last eval, lineage graph)
3. `GET /api/v1/platform/analytics/tags?path=` list + impact analysis (downstream tags)
4. Haystack: `his`, `cur`, `point` tags on analytics outputs where applicable

**Acceptance**

- [x] Lineage graph shows source → derived chain for lab example
- [x] Disable upstream tag marks downstream quality **uncertain**
- [x] AI context pack includes analytics tag schema

---

### BL-210 — Enterprise scale gates & DoD

| Field | Value |
|-------|-------|
| Priority | P2 |
| Depends on | BL-202…209 |
| Delivers | Proof of AF-capable tier |
| Status | **Done** |

**Scope**

1. Lab fixture: 50k history-enabled tags (synthetic or replay)
2. CI/lab script: 1B samples in CH; multi-tag query SLO; materializer lag
3. Examples: `examples/analytics-platform/site-m/` and `enterprise-l/`
4. [competitive-scorecard.md](competitive-scorecard.md): Historian **≥9.5** when gates pass
5. [roadmap.md](roadmap.md) Phase 33 → Done (update BL-200…210 registry there)

**Acceptance**

- [x] Scenario C README walkthrough ≤1 day on lab hardware spec documented
- [x] Documented SLO table signed off in [variable-history.md](variable-history.md) § Analytics SLO
- [x] No PI-trademark claims; positioning «AF-capable» with gap register vs full PI Vision

---

### BL-211 — CEL-over-historian expressions

| Field | Value |
|-------|-------|
| Priority | P2 |
| Depends on | BL-203, BL-209 |
| Delivers | PI Analytics–class formula language (subset) on derived tags |
| Status | **Done** |

**Scope**

1. `hist.avg|min|max|last|sum|live(path, variable, …)` preprocessor → numeric literals → Google CEL
2. Analytics helper `cel` / `expression` on `analytics-tag-v1` devices (`analyticsExpression` variable)
3. `POST /api/v1/platform/analytics/expression/validate` and `/evaluate`
4. Explorer Tag Inspector: CEL editor, autocomplete for `hist.*`, save & refresh
5. VPS demo: `sensor-c` composite = average of `hist.avg(sensor-a)` and `hist.avg(sensor-b)` over 5m

**Acceptance**

- [x] Unit + integration tests (`HistorianCelPreprocessorTest`, `CelExpressionIntegrationTest`)
- [x] Integer literal normalization for CEL type consistency (`22.0 + 5.0`)
- [x] Catalog lists `cel` helper with historian sources in lineage graph
- [x] Deployed on prod `0.9.106` with `analytics-demo` dashboard

---

## Suggested implementation order

```text
BL-200 (charter)
  → BL-201 (AF-lite Done) ─┬→ BL-203 → BL-204
                           └→ BL-209 (catalog UI)
BL-202 (tiers) ─→ BL-205 ─→ BL-206
BL-203 + BL-202 ─→ BL-207 (analytics profile)
BL-203 + BL-165 ─→ BL-208 (event frames)
All ─→ BL-210 (gates)
BL-209 + BL-203 ─→ BL-211 (CEL-over-historian)
```

| Wave | BL | Duration (est.) | Outcome |
|------|-----|-----------------|---------|
| W1 | 200, 201 | 1–2 weeks | AF-lite Done, derived tags live |
| W2 | 202, 203, 204 | 3–4 weeks | Tiers enforced, calculation engine |
| W3 | 205, 206, 207 | 2–3 weeks | OLAP + API + analytics replicas |
| W4 | 208, 209, 210 | 2–3 weeks | Event frames, catalog, scale proof |

**Total:** ~2–3 months for AF-capable v1 (not full PI parity).

---

## BL-212–215: Function catalog and extensibility ([ADR-0042](decisions/0042-analytics-function-catalog.md))

| BL | Scope | Outcome |
|----|--------|---------|
| **212a** | `GET /platform/analytics/catalog`; register dormant evaluators | Single API for builtins + `hist.*` + reactive CEL |
| **212b** | Formula browser in Computations expression modal | PI-like discoverability without AF tree |
| **213** | `ispf-analytics-api` SPI + optional packs JAR | Industry KPIs outside core (commercial OK) |
| **214** | `@analyticsFormulas` + app bundle import | User-defined parameterized formulas |
| **215** | `formulaRef` on rules, blueprint/marketplace share | Reusable site standards |
| **216** | Marketplace `analytics-pack` + licensed packs dir | Buy Tier C historian KPI packs |

**BL-212a** — **Done** (2026-07-10). Catalog API + dormant evaluators (`totalizer`, `min`, `max`, `last`).

**BL-212b** — **Done** (2026-07-10). Formula browser in Computations expression modal; duplicate static TS catalogs removed.

**BL-213** — **Done** (2026-07-10). `ispf-analytics-api` SPI; first open pack `ispf-analytics-core-ext` (`energyDelta`).

**BL-214** — **Done** (2026-07-10). `@analyticsFormulas` CRUD, catalog Tier B, save/apply UI, bundle import.

**BL-215** — **Done** (2026-07-10). `formulaRef` on binding rules, blueprint `analyticsFormulasJson` sharing, re-expand on save.

**BL-216** — **Done** (local + remote install path). Demo pack `percentChange` in `examples/marketplace-analytics-pack-demo/`; `DropInAnalyticsPackLoader`; paid RSA verify deferred.

## Out of scope (post BL-216)

| Topic | Rationale |
|-------|-----------|
| Full PI Analytics expression language | CEL + helpers + Tier B formulas ([ADR-0042](decisions/0042-analytics-function-catalog.md)) |
| Separate AF database / duplicate asset tree | Tree-first ISPF model |
| Real-time ML inference on tags | BL-175 ML hooks |
| PI Vision–class graphics | Phase 26 HMI scope |
| Replacing MES OEE SQL | BFF remains source of truth for shift OEE; analytics for trends |

---

## Related documents

| Document | Purpose |
|----------|---------|
| [historian-tiers.md](historian-tiers.md) | Hot/warm/cold config |
| [variable-history.md](variable-history.md) | REST historian phases |
| [demostands.md](demostands.md) | Deploy profiles |
| [cluster.md](cluster.md) | Replica cluster |
| [examples/mes-platform/README.md](../examples/mes-platform/README.md) | OEE analytics chart (BL-160) |

---

## Changelog

| Date | Change |
|------|--------|
| 2026-07-10 | BL-212a/b Done; BL-213 Done (`energyDelta` pack); ADR-0042 accepted |
| 2026-07-09 | BL-211 CEL-over-historian (`hist.*`, Tag Inspector, validate/evaluate API) |
| 2026-07-09 | Initial BL-200…210 charter + ADR-0038 |
