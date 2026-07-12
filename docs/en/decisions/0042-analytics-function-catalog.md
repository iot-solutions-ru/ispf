# ADR-0042: Unified analytics function catalog and extensibility

## Status

**Accepted** (2026-07-10)

## Context

OSIsoft PI Asset Framework exposes a **large built-in analyses library**, **vendor/industry plugins**, a **single browsable formula catalog**, and **user-defined analyses** (templates and custom functions). Operators discover formulas in one place and deploy them onto assets.

ISPF after ADR-0041 has a working but **fragmented** surface:

| Surface | Where | Examples |
|---------|--------|----------|
| Historian helpers | `AnalyticsEvaluatorRegistry` (Java) | `rollingAvg`, `rateOfChange`, `oee` |
| Historian CEL | PlatformRef aggregates in expression compiler | `avg`, `min`, `sum`, `live` |
| Reactive CEL | binding rules `kind: reactive` | `movingAvg`, `delta`, `scale`, `callFunction` |
| Dormant evaluators | engine, not wired to binding compiler | `totalizer`, `min`, `max`, `last` |
| UI catalog | static TS (`historianExpressionBindings.ts`, `analyticsCelBindings.ts`) | partial, duplicated |

Custom logic today:

- **Reactive:** platform/application **JavaScript functions** via `callFunction(...)` (instant, not historian-backed).
- **Historian:** only **new Java** `AnalyticsEvaluator` + server deploy — no end-user UDF.

Goal: **catalog browse and extensibility comparable to PI AF** without abandoning ISPF’s **tree-first** model ([0001-app-platform-boundary](0001-app-platform-boundary.md), [0041-multi-tag-historian-computations](0041-multi-tag-historian-computations.md)).

## Decision

### 1. One logical catalog, three function tiers

All analytics functions are entries in a **unified catalog**. Deployment still uses **binding rules** (`kind: historian` | `reactive`) on objects — we do **not** introduce a second asset tree like PI AF.

```
┌─────────────────────────────────────────────────────────────┐
│           Unified analytics function catalog                 │
├──────────────┬────────────────────┬─────────────────────────┤
│ Tier A       │ Tier B             │ Tier C                  │
│ Built-in     │ User / app         │ Extension packs         │
│ (core +      │ formulas           │ (SPI JAR, commercial)   │
│  open packs) │                    │                         │
└──────────────┴────────────────────┴─────────────────────────┘
                              │
                              ▼
              Binding rule expression on device
              (historian schedule → derived variable)
```

| Tier | Source | Who creates | Runtime |
|------|--------|-------------|---------|
| **A — Built-in** | Core registry + optional `ispf-analytics-*` packs | Platform team | Java evaluators + CEL builtins |
| **B — User formulas** | Platform tree or application bundle | Operator / solution dev | Stored expression + parameter schema; expanded at compile time |
| **C — Extension packs** | Separate JAR (AGPL or commercial) | Partner / ISV | `AnalyticsFunction` SPI, same as Tier A |

**Principle:** catalog is for **discovery, documentation, validation, and insertion**; the **rule on the object** is the deployed instance (like AF analysis on an element, but metadata stays on the ISPF object).

### 2. Catalog API (BL-212)

Single read API for console, MCP, and agents:

```
GET /api/v1/platform/analytics/catalog
GET /api/v1/platform/analytics/catalog/{functionId}
POST /api/v1/platform/analytics/catalog/validate   # body: { kind, expression, context }
```

Response item schema (conceptual):

```json
{
  "id": "avg",
  "displayName": "Rolling average",
  "tier": "builtin",
  "kinds": ["historian"],
  "syntax": "avg(sourceRef, window)",
  "parameters": [
    { "name": "sourceRef", "type": "platformRef", "required": true },
    { "name": "window", "type": "duration", "required": true }
  ],
  "description": "...",
  "examples": ["avg(root.platform.devices.sensor-a/temperature, 5m)"],
  "tags": ["statistics", "smoothing"],
  "pack": "core"
}
```

Registry merges:

1. Java `AnalyticsEvaluator` + `AnalyticsFunction` SPI registrations
2. CEL `hist.*` and reactive builtins (from expression module metadata)
3. Tier B user formulas (see §3)
4. Static **recipe** documents (cookbook) linked by `docAnchor`, not duplicated prose in API

Optional browse node (read-only mirror): `root.platform.analytics.catalog` — Haystack-tagged objects for Explorer search; **canonical definitions remain server registry** (avoid drift).

UI ([0040-unified-computations-ui](0040-unified-computations-ui.md)): **Formula browser** in `BindingExpressionEditorModal` — search, filter by kind/tag/pack, insert template with placeholders, validate before save. Replaces scattered static TS lists.

### 3. User-defined formulas (BL-214)

Operators and solution developers can **save reusable formulas** without Java.

**Storage (v1):** JSON document on platform context, e.g. `@analyticsFormulas`:

```json
{
  "id": "tank-fill-rate",
  "displayName": "Tank fill rate (m³/h)",
  "kind": "historian",
  "expression": "rateOfChange({{levelPath}}, 1h) * {{tankArea}}",
  "parameters": [
    { "name": "levelPath", "type": "tagPath" },
    { "name": "tankArea", "type": "number", "default": 1 }
  ],
  "createdBy": "operator@site",
  "version": 1
}
```

**Apply:** UI “New from formula…” → pick catalog entry → bind parameters → creates `BindingRule` with **expanded** expression (or stores `formulaRef` + params in rule metadata for re-bind on edit — implementation choice in BL-214).

**Scope:**

| Scope | Location | RBAC |
|-------|----------|------|
| Site-wide | `root.platform` `@analyticsFormulas` | platform admin |
| Application | app bundle `analytics-formulas.json` on deploy | app developer |
| Object template | RELATIVE blueprint default `@bindingRules` | blueprint author |

**Not UDF bytecode:** Tier B is **parameterized expressions** in the existing languages (helper + CEL). Full procedural UDFs use Tier C or application functions.

### 4. Extension packs — analytics plugins (BL-213)

Mirror [plugins](../plugins.md) and driver/LLM SPI patterns:

| Module | Purpose |
|--------|---------|
| `packages/ispf-analytics-api` | SPI `AnalyticsFunction`, `AnalyticsFunctionPack` |
| `packages/ispf-analytics-<domain>` | Optional packs (e.g. `water`, `energy`, `mes-kpi`) |
| `ispf-server` | `AnalyticsCatalogRegistry` loads core + classpath packs |

Pack deliverable:

- `analytics-pack.json` — id, version, license, compatible ISPF versions
- JAR with `META-INF/services/com.ispf.analytics.AnalyticsFunction`
- Functions declare: `id`, `kinds`, parameter schema, evaluator or CEL macro expansion

Commercial industry packs stay **outside `main`** ([0003-commercial-bundle-licensing](0003-commercial-bundle-licensing.md)).

**Marketplace distribution (BL-216):** paid and free Tier C packs list on the platform marketplace with `artifactKind: analytics-pack`. Install/activate flow matches application bundles; artifacts unpack to `ISPF_ANALYTICS_PACKS_DIR` with RSA license verification. Operator guide: [analytics-formulas-and-packs.md § Buying Tier C packs](../analytics-formulas-and-packs.md#buying-tier-c-packs-on-the-marketplace).

**First step:** wire dormant evaluators (`totalizer`, `min`, `max`, `last`) into `HistorianBindingRuleCompiler` and register them in the catalog (no new SPI yet).

### 5. Custom functions beyond formulas

| Need | Mechanism | Historian-safe? |
|------|-----------|-----------------|
| Simple math / windows | Tier B formula or CEL + `hist.*` | Yes |
| Multi-step KPI chain | Multiple binding rules + tag paths | Yes |
| Procedural / external IO | Application **tree function** + `callFunction` (reactive) | No (live only) |
| Heavy / certified KPI | Tier C Java pack or MES BFF | Yes (pack) |

Historian rules **cannot** call arbitrary JS on each bucket without breaking determinism and backfill — Tier B stays declarative; Tier C Java functions must implement `AnalyticsEvaluator` with explicit historian port usage.

Future (out of BL-212–214): **sandboxed** app functions invoked from historian with declared inputs/outputs and audit (BL-215+).

### 6. What we explicitly do not copy from PI

| PI AF | ISPF choice |
|-------|-------------|
| Separate AF database / duplicate hierarchy | Object tree + binding rules ([0041-multi-tag-historian-computations](0041-multi-tag-historian-computations.md)) |
| PI Analytics expression language | CEL + helpers + documented recipes ([analytics-historian-cookbook](../analytics-historian-cookbook.md)) |
| 200+ analyses in core | Core ships **lean** set; industry depth via **packs** and Tier B formulas |
| Analysis templates only on AF | Formulas in catalog; **instances** as rules on devices |

## Implementation phases

| Phase | BL | Deliverable |
|-------|-----|-------------|
| **1** | BL-212a | Catalog API from Java registry + CEL metadata; wire `totalizer`/`min`/`max`/`last` |
| **2** | BL-212b | Formula browser UI; remove duplicate static TS catalogs |
| **3** | BL-213 | `ispf-analytics-api` SPI; first open pack (`ispf-analytics-core-ext` or industry demo) |
| **4** | BL-214 | `@analyticsFormulas` CRUD, “save as formula”, app bundle import |
| **5** | BL-215 | Blueprint/marketplace sharing; `formulaRef` on binding rules |
| **6** | BL-216 | Marketplace `analytics-pack` install + `ISPF_ANALYTICS_PACKS_DIR` loader |

Estimated: **4–6 weeks** after ADR-0041 stabilization for phases 1–2; phases 3–5 parallel with OLAP (BL-205).

## Consequences

- For operators: browse → parameterize → deploy rule.
- Solution developers ship formulas in app bundles without forking core.
- ISVs ship certified KPI packs under commercial license.
- Agents/MCP use same catalog API as UI.

Risks:

- Catalog drift if tree mirror and registry diverge → **registry is source of truth**, tree is optional index.
- User formulas need validation and versioning to avoid breaking rules on edit.
- SPI proliferation → require pack manifest and compatibility checks like driver packs.

## Related

- [0038-analytics-platform-architecture](0038-analytics-platform-architecture.md) — analytics layers
- [0041-multi-tag-historian-computations](0041-multi-tag-historian-computations.md) — historian binding rules
- [0040-unified-computations-ui](0040-unified-computations-ui.md) — Computations tab
- [analytics-platform-roadmap](../analytics-platform-roadmap.md) — BL-212+
- [analytics-historian-cookbook](../analytics-historian-cookbook.md) — recipes (linked from catalog)
- [analytics-formulas-and-packs](../analytics-formulas-and-packs.md) — operator and vendor guide (Tier A/B/C, marketplace)

## Changelog

| Date | Change |
|------|--------|
| 2026-07-10 | Status → Accepted; BL-212a in progress |
| 2026-07-09 | Initial proposal (PI AF-style catalog, Tier A/B/C, BL-212–215) |
