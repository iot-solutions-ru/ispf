> **Language:** Canonical English. Russian edition: [ru/expression-language.md](../ru/expression-language.md).

# Expression language reference

> **Status:** Stable — user reference for CEL, platform bindings, and historian helpers. Hub: [doc-status.md](doc-status.md).

This page is the **full language reference** for writing expressions in ISPF: binding rules, alert conditions, workflow gateways, and historian computations.

| Related | |
|---------|---|
| Binding rules / activators / targets | [bindings](bindings.md) |
| WHEN → IF → THEN model | [platform-logic](platform-logic.md) |
| Historian recipes | [analytics-historian-cookbook](analytics-historian-cookbook.md) |
| Formula packs / catalog API | [analytics-formulas-and-packs](analytics-formulas-and-packs.md) |
| Live catalog | `GET /api/v1/platform/analytics/catalog` |
| Validate | `POST /api/v1/expressions/validate` |

**Source of truth in code:** `PlatformBindingRegistry` / `PlatformBindingCatalog` (`ispf-expression`), `AnalyticsEvaluatorRegistry` + `HistorianCelPreprocessor` (`ispf-analytics-*`), Google CEL via `ExpressionEngine`.

---

## 1. Two expression modes

| Mode | When | Constraint |
|------|------|------------|
| **Platform binding** | Entire expression matches one builtin (`movingAvg(...)`, `read(...)`, …) | **Whole expression only** — cannot mix with `+` / CEL in the same string |
| **CEL** | Anything else that compiles as Google CEL | Uses `self` / `context` / `input`; no ISPF functions registered inside CEL |

Historian rules (`kind: historian`) use **helpers** (`avg`, `oee`, …) and/or **CEL composites** where helpers expand to numbers first.

```text
Reactive rule expression
  ├─ matches PlatformBindingRegistry?  →  evaluate binding (stateful OK)
  └─ else                              →  compile & eval CEL

Historian rule expression
  ├─ single helper call (avg/min/…)    →  AnalyticsEvaluator
  └─ CEL with helpers inside           →  expand helpers → CEL
```

---

## 2. Literals and operators (CEL)

ISPF uses **Google CEL** (standard library). Telemetry numbers in `self` are coerced to **double**.

### Literals

| Kind | Examples | Notes |
|------|----------|--------|
| Boolean | `true`, `false` | |
| Null | `null` | |
| Integer | `42`, `-1` | Prefer **`42.0`** when mixing with doubles / historian expansions |
| Double | `3.14`, `2.0`, `0.0` | **Preferred** for arithmetic with live values |
| String | `"alarm"`, `'ok'` | Escape with `\"` / `\'` |
| List | `[1.0, 2.0]` | CEL lists |
| Map | `{"a": 1.0}` | CEL maps |

### Operators (common)

| Class | Operators |
|-------|-----------|
| Arithmetic | `+`, `-`, `*`, `/`, `%` |
| Comparison | `==`, `!=`, `<`, `<=`, `>`, `>=` |
| Logic | `&&`, `\|\|`, `!` |
| Ternary | `cond ? thenExpr : elseExpr` |

Standard CEL builtins also apply (`size()`, string methods, etc.) — see [CEL language definition](https://github.com/google/cel-spec).

### Field access

| Form | Example | Notes |
|------|---------|--------|
| Dot | `self.temperature.value` | Preferred |
| Bracket | `self.temperature["value"]` | Rewritten to dot before compile |

---

## 3. Identifiers in CEL (`ExpressionEngine`)

| Name | Bound when | Meaning |
|------|------------|---------|
| `self` | Expression contains `self` | Map of **local** variables → first-row field maps (`value`, …). Numbers are `double`. |
| `parent` | Referenced | **Always empty `{}`** — do not rely on it |
| `context` | Referenced | Call-site input map (dashboard / rule context) |
| `input` | Referenced | **Same map as `context`** |
| `payload` | `evaluateWithPayload` only | Alert / event payload — **not** available in ordinary binding CEL |

---

## 4. PlatformRef (addresses)

Slash grammar (same as REST path + name + field). See also [bindings § PlatformRef](bindings.md#platformref-addresses).

| Kind | Form | Example |
|------|------|---------|
| Variable | `<object>/<name>[/<field>]` | `@/temperature`, `root.platform.devices.a/temperature/value` |
| Function | `<object>/fn/<name>` | `@/fn/calculate` |
| Event | `<object>/evt/<name>` | `@/evt/alarmRaised` |
| Tag | `<object>/tag/<ruleId>` | `root.platform.devices.a/tag/avg-temp-5m` |

`@` = object where the rule runs.

---

## 5. Reactive platform bindings

Must be the **entire** expression. Optional trailing `field` on many helpers defaults to `value`. Stateful helpers keep state in `@bindingState`.

### Signal

| Function | Syntax | Description | Example |
|----------|--------|-------------|---------|
| `selectField` | `selectField(<source>[, field])` | Read a field from a DataRecord variable | `selectField(temperature)` |
| `scale` | `scale(<source>, inMin, inMax, outMin, outMax[, field])` | Linear range map | `scale(level, 0, 100, 0, 1)` |
| `clamp` | `clamp(<source>, min, max[, field])` | Clamp to [min, max] | `clamp(pressure, 0, 100)` |
| `format` | `format("<printf>", <source>[, field])` | Number → string | `format("%.1f", temperature)` |
| `unitConvert` | `unitConvert(<source>, from, to[, field])` | Temperature units `C` / `F` / `K` | `unitConvert(temperature, C, F)` |

### Stateful (live window / previous sample)

| Function | Syntax | Description | Example |
|----------|--------|-------------|---------|
| `delta` | `delta(<source>[, field])` | Diff vs previous sample | `delta(flowRate)` |
| `rate` | `rate(<source>[, field])` | Per-second rate | `rate(counter)` |
| `counterRate` | `counterRate(<source>[, wrapBits?][, field])` | SNMP-style counter rate with wrap | `counterRate(ifInOctets)` |
| `counterDelta` | `counterDelta(<source>[, wrapBits?][, field])` | Counter delta with wrap | `counterDelta(ifInOctets)` |
| `movingAvg` | `movingAvg(<source>, windowSec[, field])` | Rolling average — **seconds** | `movingAvg(temperature, 60)` |
| `movingMin` | `movingMin(<source>, windowSec[, field])` | Rolling minimum | `movingMin(pressure, 30)` |
| `movingMax` | `movingMax(<source>, windowSec[, field])` | Rolling maximum | `movingMax(pressure, 30)` |
| `deadband` | `deadband(<source>, band[, field])` | Suppress changes &lt; band | `deadband(level, 1.0)` |
| `hysteresis` | `hysteresis(<source>, on, off[, field])` | Schmitt-trigger boolean | `hysteresis(level, 80, 70)` |

### Cross-object / actions

| Function | Syntax | Description | Example |
|----------|--------|-------------|---------|
| `read` | `read(<ref>)` | Live variable read | `read(root.platform.devices.a/temperature)` |
| `write` | `write(<ref>, <value>)` | Write variable field | `write(@/setpoint, 42)` |
| `call` | `call(<fnRef>[, <inputRef>])` | Invoke tree function | `call(@/fn/ack, @/payload)` |
| `fire` | `fire(<evtRef>)` | Publish event | `fire(@/evt/alarmRaised)` |
| `queryScalar` | `queryScalar(<spec>, "<agg>"[, "<field>"])` | Object Query scalar | `queryScalar(@/downIfSpec, "count")` |
| `queryRows` | `queryRows(<spec>)` | Object Query rows (JSON) | `queryRows(@/downIfSpec)` |
| `executeQuery` | `executeQuery(<spec>)` | Alias of `queryRows` | `executeQuery(@/downIfSpec)` |
| `countScan` | `countScan("<pathPattern>"[, "<filter>"])` | Count matching objects | `countScan("root.platform.devices.*")` |
| `sumScan` | `sumScan("<pattern>", "<ref>"[, "<filter>"])` | Sum field across scan | `sumScan("root.platform.devices.*", "powerKw")` |
| `sumRecordField` | `sumRecordField(<tableVar>, "<field>")` | Sum column in table var | `sumRecordField(orders, "amount")` |

`<spec>` for Object Query is usually a variable holding the OQ JSON (`@/oqSpec`).

---

## 6. Historian helpers

Used in `kind: historian` rules. Window buckets use duration tokens (below), **not** seconds.

### Standalone evaluators (`AnalyticsEvaluatorRegistry`)

| Function | Syntax | Description | Example |
|----------|--------|-------------|---------|
| `avg` | `avg(<ref>, <bucket?>)` | Window average (catalog: “Rolling average”) | `avg(@/temperature, 5m)` |
| `min` | `min(<ref>, <bucket?>)` | Minimum in window | `min(@/pressure, 30m)` |
| `max` | `max(<ref>, <bucket?>)` | Maximum in window | `max(@/pressure, 30m)` |
| `last` | `last(<ref>)` | Latest sample; live fallback if empty | `last(@/temperature)` |
| `rateOfChange` | `rateOfChange(<ref>, <bucket?>)` | Last − first bucket average | `rateOfChange(@/level, 1h)` |
| `totalizer` | `totalizer(<ref>, <bucket?>)` | Accumulate bucket averages | `totalizer(@/energy, 1h)` |
| `oee` | `oee('<path>', '<avail>', '<perf>', '<qual>', '<bucket?>')` | A×P×Q composite % | `oee('root.platform.devices.line-01', 'availabilityPct', 'performancePct', 'qualityPct', '8h')` |

Legacy name **`rollingAvg`** → use **`avg`**. Docs sometimes say `hist.*`; **runtime uses bare** `avg(...)`, `live(...)`.

### CEL composites (helpers expand → numbers → CEL)

Inside a CEL expression on a historian rule:

| Helper | Syntax | Notes |
|--------|--------|--------|
| `avg` / `min` / `max` | `avg(<ref>, <bucket>)` | Expanded to double |
| `last` | `last(<ref>[, bucket])` | |
| `sum` | `sum(<ref>, <bucket>)` | **CEL preprocessor only** — not a standalone evaluator |
| `live` | `live(<ref>[, field])` | Current live value; **prefer inside CEL**, not as lone helper |

```cel
(avg(root.platform.devices.a/temperature, 5m) + avg(root.platform.devices.b/temperature, 5m)) / 2.0
```

### Duration buckets

Canonical allow-list (`parseBucket`): **`1m`**, **`5m`**, **`15m`**, **`30m`**, **`1h`**, **`6h`**, **`8h`**, **`1d`** (must be ≥1m and ≤7d).

Default rollup set often: `5m,1h,8h`.

| Context | Window unit |
|---------|-------------|
| Reactive `movingAvg(x, 60)` | **Seconds** |
| Historian `avg(x, 5m)` | **Bucket duration** |

---

## 7. Where expressions are used

| Surface | Typical expression | Mode |
|---------|-------------------|------|
| Binding `expression` | `movingAvg(@/temperature, 60)` or CEL | Platform or CEL |
| Binding `condition` | `self.temperature.value > 80.0` | CEL |
| Alert rule condition | `self.temperature.value > self.threshold.value` | CEL |
| Workflow gateway | CEL on process variables | CEL |
| Historian rule | `avg(@/temperature, 5m)` or CEL composite | Historian / CEL |
| Dashboard / form rules | `context.*` CEL (maturity varies) | CEL — see [platform-logic](platform-logic.md) |

---

## 8. Worked examples

### Alert when temperature exceeds threshold

```cel
self.temperature.value > self.threshold.value
```

### Reactive: scale 0–100 sensor to 0–1

```text
scale(level, 0, 100, 0, 1)
```

### Reactive: hysteresis alarm bit

```text
hysteresis(temperature, 80, 70)
```

### Mirror remote live value

```text
read(root.platform.devices.cluster.dev-03/sineWave)
```

### Historian rolling average → derived variable

```text
avg(root.platform.devices.sensor-a/temperature, 5m)
```

### Historian CEL: two-sensor mean

```cel
(avg(root.platform.devices.sensor-a/temperature, 5m) + avg(root.platform.devices.sensor-b/temperature, 5m)) / 2.0
```

### Call a local function on each tick

```text
call(@/fn/dispatch, @/lastIngress)
```

---

## 9. Gotchas (read this)

1. **Platform binding = whole expression.** `counterRate(@/ifInOctets) + 1.0` does **not** run as a binding — compose in CEL with `self.*` or use a separate rule.
2. **`read(...)` is not a CEL function.** Use it as a full platform expression, or use `self.var.field` for local CEL.
3. **Prefer doubles:** `2.0`, not `2`, with live / historian values.
4. **`parent` is empty** — never depend on it.
5. **`movingAvg(..., 60)` ≠ `avg(..., 5m)`** — seconds vs bucket.
6. **Standalone `live(...)` / `sum(...)`:** `sum` is CEL-composite only; lone `live` may not have an evaluator — put `live` inside CEL or use `last`.
7. **No `hist.` prefix** in current runtime — use bare `avg(...)`.
8. **Validate in UI** (Computations → Validate) or `POST /api/v1/expressions/validate`.

---

## 10. Discovery in product

| Place | What you get |
|-------|----------------|
| Web Console → Computations → expression editor | Insert snippets + function catalog |
| `GET /api/v1/platform/analytics/catalog` | Tier A/B/C metadata (`syntax`, `examples`, `parameters`) |
| `GET /api/v1/platform/analytics/catalog/{id}` | Single entry |
| System → Analytics formulas | User Tier B formulas |

When the catalog and this page disagree, **trust the running catalog API** for installed packs; update this doc in the same PR as registry changes.

---

## 11. Maintenance

Keep in sync with:

- `packages/ispf-expression/.../PlatformBindingRegistry.java`
- `packages/ispf-expression/.../PlatformBindingCatalog.java`
- `apps/web-console/src/utils/platformBindings.ts`
- `apps/web-console/src/utils/analyticsCelBindings.ts`
- `packages/ispf-analytics-engine/.../AnalyticsEvaluatorRegistry.java`
- `packages/ispf-server/.../AnalyticsCatalogRegistry.java`
