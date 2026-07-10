# ADR-0042: Unified analytics function catalog and extensibility

## Status

**Accepted** (2026-07-10)

## Context

OSIsoft PI Asset Framework exposes a **large built-in analyses library**, **vendor/industry plugins**, a **single browsable formula catalog**, and **user-defined analyses** (templates and custom functions). Operators discover formulas in one place and deploy them onto assets.

ISPF after ADR-0041 has a working but **fragmented** surface:

| Surface | Where | Examples |
|---------|--------|----------|
| Historian helpers | `AnalyticsEvaluatorRegistry` (Java) | `rollingAvg`, `rateOfChange`, `oee` |
| Historian CEL | `hist.*` in expression compiler | `hist.avg`, `hist.min`, `hist.sum`, `hist.live` |
| Reactive CEL | binding rules `kind: reactive` | `movingAvg`, `delta`, `scale`, `callFunction` |
| Dormant evaluators | engine, not wired to binding compiler | `totalizer`, `min`, `max`, `last` |
| UI catalog | static TS (`historianExpressionBindings.ts`, `analyticsCelBindings.ts`) | partial, duplicated |

Custom logic today:

- **Reactive:** platform/application **JavaScript functions** via `callFunction(...)` (instant, not historian-backed).
- **Historian:** only **new Java** `AnalyticsEvaluator` + server deploy — no end-user UDF.

Product goal (user request): **PI-like discoverability and extensibility** without abandoning ISPF’s **tree-first** model ([ADR-0001](0001-app-platform-boundary.md), [ADR-0041](0041-multi-tag-historian-computations.md)).

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
  "id": "rollingAvg",
  "displayName": "Rolling average",
  "tier": "builtin",
  "kinds": ["historian"],
  "syntax": "rollingAvg(sourcePath, window)",
  "parameters": [
    { "name": "sourcePath", "type": "tagPath", "required": true },
    { "name": "window", "type": "duration", "required": true }
  ],
  "description": "...",
  "examples": ["rollingAvg(root.platform.devices.sensor-a.temperature, 5m)"],
  "tags": ["statistics", "smoothing"],
  "pack": "core"
}
```

Registry merges:

1. Java `AnalyticsEvaluator` + `AnalyticsFunction` SPI registrations
2. CEL `hist.*` and reactive builtins (from expression module metadata)
3. Tier B user formulas (see §3)
4. Static **recipe** documents (cookbook) linked by `docAnchor`, not duplicated prose in API

Optional browse node (read-only mirror): `root.platform.analytics.catalog` — Haystack-tagged objects for Explorer search; **authoritative definitions remain server registry** (avoid drift).

UI ([ADR-0040](0040-unified-computations-ui.md)): **Formula browser** in `BindingExpressionEditorModal` — search, filter by kind/tag/pack, insert template with placeholders, validate before save. Replaces scattered static TS lists.

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

Mirror [plugins.md](../plugins.md) and driver/LLM SPI patterns:

| Module | Purpose |
|--------|---------|
| `packages/ispf-analytics-api` | SPI `AnalyticsFunction`, `AnalyticsFunctionPack` |
| `packages/ispf-analytics-<domain>` | Optional packs (e.g. `water`, `energy`, `mes-kpi`) |
| `ispf-server` | `AnalyticsCatalogRegistry` loads core + classpath packs |

Pack deliverable:

- `analytics-pack.json` — id, version, license, compatible ISPF versions
- JAR with `META-INF/services/com.ispf.analytics.AnalyticsFunction`
- Functions declare: `id`, `kinds`, parameter schema, evaluator or CEL macro expansion

Commercial industry packs stay **outside `main`** ([ADR-0003](0003-commercial-bundle-licensing.md)).

**Near-term quick win:** wire dormant evaluators (`totalizer`, `min`, `max`, `last`) into `HistorianBindingRuleCompiler` and register them in the catalog (no new SPI yet).

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
| Separate AF database / duplicate hierarchy | Object tree + binding rules ([ADR-0041](0041-multi-tag-historian-computations.md)) |
| PI Analytics expression language | CEL + helpers + documented recipes ([analytics-historian-cookbook.md](../analytics-historian-cookbook.md)) |
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

Estimated: **4–6 weeks** after ADR-0041 stabilization for phases 1–2; phases 3–5 parallel with OLAP (BL-205).

## Consequences

**Positive**

- One mental model for operators: browse → parameterize → deploy rule.
- Solution developers ship formulas in app bundles without forking core.
- ISVs ship certified KPI packs under commercial license.
- Agents/MCP use same catalog API as UI.

**Negative / risks**

- Catalog drift if tree mirror and registry diverge → **registry is source of truth**, tree is optional index.
- User formulas need validation and versioning to avoid breaking rules on edit.
- SPI proliferation → require pack manifest and compatibility checks like driver packs.

## Related

- [ADR-0038](0038-analytics-platform-architecture.md) — analytics layers
- [ADR-0041](0041-multi-tag-historian-computations.md) — historian binding rules
- [ADR-0040](0040-unified-computations-ui.md) — Computations tab
- [analytics-platform-roadmap.md](../analytics-platform-roadmap.md) — BL-212+
- [analytics-historian-cookbook.md](../analytics-historian-cookbook.md) — recipes (linked from catalog)

## Changelog

| Date | Change |
|------|--------|
| 2026-07-10 | Status → Accepted; BL-212a in progress |
| 2026-07-09 | Initial proposal (PI-like catalog, Tier A/B/C, BL-212–215) |
