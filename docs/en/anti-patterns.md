> **Language:** Canonical English. Russian edition: [ru/anti-patterns.md](../ru/anti-patterns.md) (when published).

# Anti-patterns (field pain → ISPF design)

> **Status:** Stable — poka-yoke lessons from production field wiki. Hub: [doc-status.md](doc-status.md).

Internal knowledge base ([wiki.iot-solutions.ru](https://wiki.iot-solutions.ru/doku.php?id=main)) captures years of field pain from prior SCADA/IoT deployments. ISPF must **not** reproduce those footguns. This page is the transfer list: what hurt there, what we do instead, and how the platform enforces it.

See also: [learn](learn.md) (how we teach the safe path), [application-principles](application-principles.md), [bindings](bindings.md)#execution, [variable-history](variable-history.md), [solution-developer-guide](solution-developer-guide.md) § solution-as-repo.

---

## 1. Heavy logic in widgets / client expressions

| Field pitfall | ISPF |
|-----------|------|
| Large expressions in widgets (~40× slower than server) | Widgets bind to variables; logic on **SINGLETON / INSTANCE hub** (functions, binding rules) |
| Desktop client round-trips data for client-side calc | Hosted SPA + `POST /api/v1/bff/invoke` on hub |

**Enforced:** bundle validate warns `HEAVY_WIDGET_EXPRESSION` (≥400 chars in widget expression/condition/script fields). Binding save logs heavy rule expressions via `BindingCascadeAnalyzer`.

**Rule:** if a widget expression needs branches, joins, or multi-step logic → `call(@/fn/…)` or a binding writing a display variable.

---

## 2. Binding cascade / “queue of activations”

| Field pitfall | ISPF |
|-----------|------|
| Multiple bindings on one event run **strictly sequential**; later rules see mid-wave writes; easy to lock out conditions | Rules on one object: sorted by **`order`**, **multi-pass** until stable (`MAX_PASSES=8`), **depth** limit `16` for cross-object chains |
| No static cycle detection | `BindingCascadeAnalyzer` warns on write↔activate cycles at save time; truncations are logged with counter |

**Rule:** one write “owner” per variable; use `activators.async=true` only for independent side effects; put branching in hub functions.

Details: [bindings.md § Execution](bindings.md#execution).

---

## 3. Unbounded MQTT / high-rate event history

| Field pitfall | ISPF |
|-----------|------|
| MQTT device stores every `message` event by default → UI/DB melt | Default `eventToVariable=false` (last-value variables); **`eventJournalEnabled` off** unless audit is explicit |
| Manual retention rule after the fire | Opt-in journal + retention properties; high-rate ingress uses coalesce/FIFO buffers |

**Rule:** never enable object event journal for catch-all MQTT topics in production without a retention plan.

---

## 4. Previous value for change logic

| Field pitfall | ISPF |
|-----------|------|
| `updated.oldValue` opt-in (off by default for load) | `Variable.includePreviousValueInEvent` opt-in; WS/automation get `previousValue` when enabled |

**Rule:** enable previous value only on variables that rules/audit need; do not turn on globally.

---

## 5. “Granulation” / time buckets

| Field pitfall | ISPF |
|-----------|------|
| Device-level granules as events with custom bucket expressions | Historian **materialized rollups** (`variable_rollups`, `rollupBuckets` on historian binding rules) + aggregate API |

Prefer `kind: historian` rules with `windowBucket` / `rollupBuckets` over hand-rolled event granules. See [analytics-historian-cookbook](analytics-historian-cookbook.md).

---

## 6. Solution snapshot / Application export

| Field pitfall | ISPF |
|-----------|------|
| Save whole app via Application context | **Solution-as-repo**: `ispf pack|validate|diff|deploy` → `bundle.json` + ui-pack; tree pull via bundle sections |

Do not hand-copy trees. SHIP layer = bundle (ADR-0060 W4). Guide: [solution-developer-guide](solution-developer-guide.md).

---

## 7. Authoring naming (coding standard)

| Practice | Why |
|----------|-----|
| `lowerCamelCase` for variables, functions, rule ids | Sortable, expression-friendly |
| Verbs for functions (`getTelemetry`, `ackAlarm`) | Intent at call site |
| No type prefix in names (`pump01`, not `devicePump01`) | Type is in object path / `ObjectType` |
| Descriptions filled on functions and non-obvious variables | AI + humans |

Logic hosts stay **SINGLETON / INSTANCE**, never `DEVICE` ([application-principles](application-principles.md) § Logic objects vs DEVICE).

---

## Agent checklist

Before finishing a solution:

1. No `LOGIC_HOST_DEVICE` / prefer hub functions over widget scripts.
2. No `HEAVY_WIDGET_EXPRESSION` / heavy binding expressions without a hub function.
3. MQTT / flood paths: journal off; last-value or coalesce ingress.
4. Binding cycles: resolve warnings; keep `order` intentional.
5. Ship artifact: `ispf pack` / `validate_bundle` green.

---

*Update when a new field pain maps to a platform constraint or validator code.*
