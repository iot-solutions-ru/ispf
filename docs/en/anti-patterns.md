> **Language:** Canonical English. Russian edition: [ru/anti-patterns.md](../ru/anti-patterns.md).

# Common design mistakes and how to avoid them

> **Status:** Stable — engineering rules backed by platform validators, engine limits, and safe defaults. Status tags: [doc-status.md](doc-status.md).

Every rule on this page addresses a concrete failure mode seen in real SCADA and IoT deployments — slow operator screens, runaway recalculation loops, bloated event tables, and unrepeatable manual configurations — and documents how ISPF prevents or flags it automatically.

**Canonical list:** agent/rules summaries (`AGENTS.md`, `.cursor/rules`) mirror this page — do not invent a second catalogue.

Related guides: [How ISPF works](learn.md) · [Application architecture rules](application-principles.md) · [Calculation rules (bindings)](bindings.md#execution) · [Variable history](variable-history.md) · [Solution developer guide](solution-developer-guide.md).

---

## 1. Putting multi-step calculations inside dashboard widgets

**What goes wrong:** Engineers write long conditional formulas or scripts directly inside individual dashboard widgets. Soon the same formula is copied across five screens, cannot be tested without opening a browser, and slows down UI rendering.

**How to do it in ISPF:** Keep widgets thin — bind them directly to object variables (`temperature`, `pumpStatusText`, `shiftEfficiency`). Put calculations, unit conversions, and branching in **calculation rules** (*binding rules*) or **functions** on a `SINGLETON` or `INSTANCE` logic object. If you build a custom React web UI, call those same object functions via `POST /api/v1/bff/invoke`.

**Demo:** [`examples/platform-primitive`](../../examples/platform-primitive/) — thin dashboard bound to a `SINGLETON` hub (logic stays off the widget).

**Platform check:** `BundleManifestValidator` raises warning `HEAVY_WIDGET_EXPRESSION` whenever a widget `expression`, `condition`, or `script` reaches **400 characters or more**. Overly long binding formulas are also flagged on save by `BindingCascadeAnalyzer`.

---

## 2. Circular or conflicting variable calculation rules

**What goes wrong:** Multiple rules write to the same variable, or Rule A updates Variable X which triggers Rule B to update Variable Y which triggers Rule A again. Under load, values oscillate or queue up behind intermediate updates.

**How to do it in ISPF:**
- Give **each calculated variable a single owner rule** that writes to it.
- Set explicit `order` numbers on rules within an object so upstream values are calculated before downstream ones.
- Use `activators.async = true` only for independent side effects (for example, logging or notifications), not for sequential math.

**Platform check:** On rule save, `BindingCascadeAnalyzer` detects write-to-activator cycles and warns immediately. At runtime (`BindingRuleEngine`), per-object rules run in `order` across at most **`MAX_PASSES = 8`** stabilization passes, and cross-object propagation stops at **`MAX_DEPTH = 16`**. Crossing either limit throws `IllegalStateException`, logs an error, and increments the in-JVM truncation counter (`BindingRuleEngine.truncationTotal()`). Details: [bindings.md § Execution](bindings.md#execution).

---

## 3. Recording an unbounded event journal on high-rate telemetry

**What goes wrong:** A gateway or MQTT topic receives thousands of messages per second, and every incoming packet is written as a row in the object event journal (`event_history`) without retention. Database storage fills up and event-log queries slow down.

**How to do it in ISPF:**
- Treat high-frequency telemetry as **variable updates** (latest value + optional time-series historian with retention), not audit events.
- Enable the object event journal (`eventJournalEnabled = true`) only on objects where discrete state changes or operator actions require an audit trail, and always configure retention ([ADR-0061](decisions/0061-high-rate-telemetry-history-defaults.md)).

**Platform defaults:** By default, the MQTT driver keeps **`eventToVariable = false`** (coalescing latest variable values instead of emitting an event per message), and newly created objects start with **`eventJournalEnabled = false`**.

---

## 4. Attaching previous values to every telemetry update globally

**What goes wrong:** Copying the previous record onto every variable update across thousands of tags doubles message payload size over WebSocket and internal event buses, even though 99% of tags only display the current reading.

**How to do it in ISPF:** Turn on **`includePreviousValueInEvent = true`** only on the specific variables where an alert rule, audit log, or script explicitly compares the previous and new value. Leave it off everywhere else.

---

## 5. Building hourly or shift aggregates out of custom events

**What goes wrong:** To get 5-minute, hourly, or shift averages, engineers create timer scripts that emit custom "bucket" events and parse event payloads in reports. Backfilling or querying across days becomes brittle and slow.

**How to do it in ISPF:** Use **historian calculation rules** (`kind: "historian"`) with `windowBucket` (for example, `5m`, `1h`, `8h`) and optional materialized `rollupBuckets`, or query aggregated buckets directly via `GET /api/v1/objects/by-path/variables/history/aggregate`. Recipes and examples: [Historian cookbook](analytics-historian-cookbook.md).

---

## 6. Placing application or plant logic on `DEVICE` nodes

**What goes wrong:** Plant-wide KPIs, SQL queries, or multi-device control scripts are attached directly to a `DEVICE` node (such as a PLC or sensor). When an engineer replaces, disables, or re-provisions that device driver, application functions and screens break.

**How to do it in ISPF:**
- Keep **`DEVICE`** nodes strictly for protocol drivers and raw/mapped device tags.
- Put application, orchestration, and digital-twin logic on a **`SINGLETON`** hub (one central controller per application) or an **`INSTANCE`** object (one per twin/unit, created from an Instance Type template).

**Platform check:** Validation reports **`LOGIC_HOST_DEVICE`** (warning during interactive authoring; hard error in `--strict` deploys, `examples/` CI, and the marketplace catalog — see [ADR-0060](decisions/0060-solution-authoring-constraints.md)).

---

## 7. Copying live object trees by hand instead of packaging a bundle

**What goes wrong:** Changes are clicked together directly on a production or test server and transferred between environments via manual exports. Nobody knows which version is running, and updates overwrite site-specific settings.

**How to do it in ISPF:** Keep your solution in version control as an **application bundle** (`bundle.json`, plus an optional `ui-pack` zip for custom React screens). Use `ispf pack`, `ispf validate`, `ispf diff`, and `ispf deploy` — or sync changes into a pull request through **Application Git** ([ADR-0063](decisions/0063-application-git-lifecycle.md)). Full workflow: [Solution developer guide](solution-developer-guide.md).

---

## Naming and authoring conventions

Consistent naming makes expressions, tables, and API calls predictable across a team:

| Element | Convention | Reason |
|---------|------------|--------|
| Variables, functions, rule IDs | `lowerCamelCase` (`supplyTemp`, `totalFlow`) | Works cleanly in CEL expressions (`self.supplyTemp.value`) without quoting |
| Function names | Start with a verb (`getTelemetry`, `acknowledgeAlarm`, `createOrder`) | Makes button bindings and API calls self-explanatory |
| Object names in paths | Omit redundant type prefixes (`pump-01`, not `device-pump-01`) | Parent path (`devices`) and `ObjectType` already carry the type |
| Descriptions | Fill in `description` on functions and non-obvious variables | Shown as tooltips in the console, operator forms, and API schemas |

---

## Pre-deployment engineering checklist

Before deploying a project to a test or production server, verify:

1. **Logic separation:** No `LOGIC_HOST_DEVICE` findings — calculations and functions live on `SINGLETON` or `INSTANCE` objects, not on `DEVICE` nodes.
2. **Thin screens:** No `HEAVY_WIDGET_EXPRESSION` warnings — widgets display variables and call hub functions rather than running embedded scripts.
3. **High-rate channels:** `eventJournalEnabled = false` on high-frequency devices; MQTT `eventToVariable = false` unless every packet must be audited with retention.
4. **Clean rule graph:** Zero cycle warnings from `BindingCascadeAnalyzer`; every calculated variable has one clear owner rule and sensible `order`.
5. **Repeatable package:** `ispf validate` (or `validate_bundle`) passes cleanly, and all tree changes are saved in `bundle.json` / Git rather than left only on a live stand.
