> **Language:** Canonical English. Russian edition: [ru/bindings.md](../ru/bindings.md).

# Binding rules (mandatory rules)

A **binding rule** is a declarative rule for computing variable values on an object: **when** (activators) → **if** (condition, CEL) → **how** (expression) → **where** (target).

Rules are stored in system variable `@bindingRules` (JSON array, reserved). Runtime — **`BindingRuleEngine`** (unified binding engine since v0.8.0).

See also: [object-model.md](object-model.md), [blueprints.md](blueprints.md), ADR [0010](decisions/0010-binding-rules-only.md).

---

## `BindingRule` model

```json
{
  "id": "member3-sine",
  "name": "Member3 sine",
  "enabled": true,
  "order": 20,
  "activators": {
    "onStartup": false,
    "onVariableChange": [
      { "objectPath": "root.platform.devices.virt-cluster.dev-03", "variableName": "sineWave" }
    ],
    "onEvent": null,
    "periodicMs": 0
  },
  "condition": "",
  "expression": "refAt(\"root.platform.devices.virt-cluster.dev-03\", sineWave)",
  "target": { "variableName": "member3Sine", "field": "value" }
}
```

| Field | Purpose |
|------|------------|
| `activators.onStartup` | Recalculate on server start / model attach |
| `activators.onVariableChange` | List of `{ objectPath, variableName }`; `"self"` + `"*"` = any local variable |
| `activators.periodicMs` | Periodic recalculation (0 = off); indexed in `platform_binding_periodic_rules`, wake by `next_run_at` |
| `condition` | CEL; empty = always |
| `expression` | CEL or single platform function |
| `target` | Where to write result (see **Target kinds** below) |
| `kind` | Optional: `reactive` (default) or `historian` — historian rules are evaluated by the analytics engine, not `BindingRuleEngine` ([ADR-0041](decisions/0041-multi-tag-historian-computations.md)) |
| `windowBucket` | Historian only: aggregate window (`5m`, `1h`, `8h`, …) |
| `rollupBuckets` | Historian only: optional materialized rollup windows |

### Historian rules (`kind: historian`)

Same `@bindingRules` array; multiple rules per device; arbitrary output variable names. Tag catalog path = `objectPath#ruleId`.

**Recipes (rolling avg, OEE, tag chains, CEL):** [analytics-historian-cookbook.md](analytics-historian-cookbook.md)

```json
{
  "id": "shift-oee",
  "kind": "historian",
  "enabled": true,
  "order": 10,
  "activators": { "periodicMs": 300000, "onVariableChange": [] },
  "expression": "oee('root.platform.devices.line-01', 'availabilityPct', 'performancePct', 'qualityPct', '8h')",
  "windowBucket": "8h",
  "target": { "kind": "variable", "variableName": "oeePct", "field": "value" }
}
```

### Periodic execution

Rules with `periodicMs > 0` are parsed into JDBC index `platform_binding_periodic_rules` when `@bindingRules` is saved. **`BindingPeriodicScheduler`** wakes the JVM once at the nearest `next_run_at` and runs only due hits — no per-second full-tree scan. If no periodic rules exist, background wake is a no-op.

### Target kinds (platform rule)

Model extension — ADR [0019](decisions/0019-platform-rule-unification.md). If `target.kind` is absent → **`variable`** (backward compatible).

| `kind` | Fields | Purpose |
|--------|------|------------|
| `variable` | `variableName`, `field` | As today — write to object variable |
| `context` | `path` (dot-notation) | Write to `@dashboardContext` on `DASHBOARD` object |
| `event` | `eventName` | Publish platform event; payload from `expression` |

Example dashboard rule (planned):

```json
{
  "id": "alarm-mode",
  "activators": { "onContextChange": true },
  "condition": "context.selection.device != \"\"",
  "expression": "\"alarm\"",
  "target": { "kind": "context", "path": "params.mode" }
}
```

Activator **`onContextChange`** — recalculate when `@dashboardContext` changes. Full spec: [platform-logic.md](platform-logic.md).

**Cross-object:** activator on remote path + `refAt("path", var)` in expression. On remote variable change (including driver telemetry) `BindingPropagationListener` recalculates rules on consumer objects.

**Default activators** (if not set): `refAt` in expression → automatic remote activators; otherwise local `self:*`.

---

## Expressions (`expression`)

Two kinds (as before, but inside `rule.expression`):

| Kind | Example |
|-----|--------|
| **CEL** | `self.temperature.value + 1.0` |
| **Platform binding** | `counterRate(ifInOctets)`, `hysteresis(temperature, 80, 70)`, `refAt("root...dev-01", sineWave)` |

Validation: `POST /api/v1/expressions/validate` or Web Console **Validate**.

Stateful bindings (`counterRate`, `hysteresis`, …) — state in `@bindingState` (see prior behavior).

---

## REST API

```http
GET  /api/v1/objects/by-path/binding-rules?path={objectPath}
PUT  /api/v1/objects/by-path/binding-rules?path={objectPath}
DELETE /api/v1/objects/by-path/binding-rules/{ruleId}?path={objectPath}
```

Agent tool: `create_binding_rule` (path, id, targetVariable, expression, RemoteObjectPath?, RemoteVariableName?, condition?, onStartup?, order?).

---

## Models

In models — `ModelBindingRule` (full schema or `ModelBindingRule.of(id, target, expression)`). On apply/create, rules merge via `ModelBindingRulesMerger` **after** functions.

`defaultBinding` on model variable **removed** (v0.8.0).

---

## Upgrade from v0.7.x (legacy `bindingExpression`)

`bindingExpression` on variable and `binding_expr` column **removed** (0010, v0.8.0). Bindings — only `@bindingRules`.

**Prod** (`ispf.iot-solutions.ru`): PostgreSQL in Docker (`ispf-postgres`), not H2. **Local dev:** H2 file or Docker Compose PostgreSQL.

```bash
# Prod VPS (Docker postgres)
systemctl stop ispf-server
docker exec ispf-postgres psql -U ispf -d postgres -c 'DROP DATABASE IF EXISTS ispf;' -c 'CREATE DATABASE ispf OWNER ispf;'
systemctl start ispf-server

# Local H2: delete ./data/ispf-local.mv.db
# Local/dev compose: docker compose exec postgres psql ...
```

Existing DB without recreate: Flyway `V41__drop_binding_expr.sql` drops the column; on **V1 checksum mismatch** recreate is required (see [deployment.md](deployment.md)).

---

## UI

Web Console → Object inspector → **Computations** tab (reactive + historian rules). See [ADR-0040](decisions/0040-unified-computations-ui.md).

---

## Not to be confused with

- **SQL bindings** (`ApplicationSqlBindingService`) — separate scheduler, not object binding rules
- **Alert rules**, correlators, workflows — separate subsystems
