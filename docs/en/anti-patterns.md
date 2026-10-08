> **Language:** Canonical English. Russian edition: [ru/anti-patterns.md](../ru/anti-patterns.md).

# Anti-patterns (field pain → ISPF design)

> **Status:** Stable — poka-yoke mappings with **existing** ISPF enforcement. Hub: [doc-status.md](doc-status.md).

This page lists field footguns that **already have** an ISPF answer (validator code, binding engine, driver default, or ADR). It is not a dump of prior-platform docs and does not invent features.

See also: [learn](learn.md), [application-principles](application-principles.md), [bindings](bindings.md)#execution, [variable-history](variable-history.md), [ADR-0061](decisions/0061-high-rate-telemetry-history-defaults.md), [solution-developer-guide](solution-developer-guide.md) § solution-as-repo.

---

## 1. Heavy logic in widgets / client expressions

| Field pitfall | ISPF |
|-----------|------|
| Branching / multi-step logic living in dashboard widgets | Widgets bind variables; logic on a **SINGLETON / INSTANCE** hub (object functions, binding rules) |
| Same logic scattered where HMI cannot reuse or test it | Hosted SPA calls hub via `POST /api/v1/bff/invoke` |

**Enforced:** `BundleManifestValidator` warns `HEAVY_WIDGET_EXPRESSION` when a widget `expression` / `condition` / `script` field is ≥ **400** characters. Heavy binding expressions are warned by `BindingCascadeAnalyzer` on rule save.

**Rule:** if a widget needs branches or multi-step logic → hub function via expression `call(@/fn/…)` (or a binding that writes a display variable). See [expression-language](expression-language.md).

---

## 2. Binding cascade / activation queue

| Field pitfall | ISPF |
|-----------|------|
| Several rules on one change run as a strict queue; later rules see mid-wave writes and lock each other out | Per object: rules sorted by **`order`**; **multi-pass** until stable (`MAX_PASSES=8`); cross-object activation **`MAX_DEPTH=16`** |
| No warning when write↔activate cycles exist | `BindingCascadeAnalyzer` warns on save; truncations are logged |

**Rule:** one write owner per variable; set `activators.async=true` only for independent side effects; put branching in hub functions.

Details: [bindings.md § Execution](bindings.md#execution).

---

## 3. Unbounded MQTT / high-rate event history

| Field pitfall | ISPF |
|-----------|------|
| High-rate ingress kept as a full event history → UI/DB melt | MQTT driver default **`eventToVariable=false`** (last-value / coalesce). New objects keep **`eventJournalEnabled=false`** |
| Journal turned on for catch-all topics without retention | Enable journal only with an explicit audit + retention plan (ADR-0061) |

**Rule:** do not enable object event journal on flood paths in production without retention.

---

## 4. Previous value for change logic

| Field pitfall | ISPF |
|-----------|------|
| Previous sample attached to every update (payload and load cost) | **`Variable.includePreviousValueInEvent`** is opt-in; when enabled, WS/automation payloads may include `previousValue` |

**Rule:** enable only on variables that rules or audit need; do not turn on globally.

---

## 5. Hand-rolled time buckets as events

| Field pitfall | ISPF |
|-----------|------|
| Custom “granules” stored as device events with ad-hoc bucket expressions | Historian **materialized rollups**: binding rules with `kind: historian`, `windowBucket` / **`rollupBuckets`**, plus aggregate query APIs |

Prefer historian rules over inventing event streams for time buckets. See [analytics-historian-cookbook](analytics-historian-cookbook.md).

---

## 6. Hand-copied trees instead of a ship artifact

| Field pitfall | ISPF |
|-----------|------|
| Whole-solution snapshot by copying live trees / ad-hoc export | **Solution-as-repo**: `ispf pack` / `validate` / `diff` / `deploy` → `bundle.json` (+ optional ui-pack) |

Do not hand-copy trees between environments. SHIP layer = bundle ([ADR-0060](decisions/0060-solution-authoring-constraints.md)). Guide: [solution-developer-guide](solution-developer-guide.md).

---

## 7. Authoring naming

| Practice | Why |
|----------|-----|
| `lowerCamelCase` for variables, functions, rule ids | Sortable, expression-friendly |
| Verbs for functions (`getTelemetry`, `ackAlarm`) | Intent at call site |
| No type prefix in names (`pump01`, not `devicePump01`) | Type is object path / `ObjectType` |
| Descriptions on functions and non-obvious variables | Operators + AI tools |

Logic hosts stay **SINGLETON / INSTANCE**, never `ObjectType.DEVICE` ([application-principles](application-principles.md) § Logic objects vs DEVICE). Bundle validate warns `LOGIC_HOST_DEVICE`.

---

## Agent checklist

Before finishing a solution:

1. No `LOGIC_HOST_DEVICE` — hub functions over widget scripts.
2. No unresolved `HEAVY_WIDGET_EXPRESSION` — move logic to the hub.
3. Flood paths: `eventJournalEnabled=false`; MQTT `eventToVariable=false` unless a designed fan-out is required.
4. Binding cycles: clear `BindingCascadeAnalyzer` warnings; keep `order` intentional.
5. Ship artifact: `ispf pack` / agent `validate_bundle` green.

---

*Add a row only when a field pain maps to shipping validator code, engine limits, or an accepted ADR — not when a prior-platform recipe has no ISPF counterpart.*
