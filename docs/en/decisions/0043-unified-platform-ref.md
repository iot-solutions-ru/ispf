# ADR-0043: Unified PlatformRef addressing

## Status

**Accepted** (2026-07-12)

## Context

ISPF had **parallel reference formats** for the same tree entities: bare variable names in expressions, split `{ objectPath, variableName }` in activators, separate analytics tag keys, and REST `path` + `name` + `field`. That made cross-object bindings, historian tags, and UI configs harder to reason about and to teach to agents.

ADR [0019-platform-rule-unification](0019-platform-rule-unification.md) unified rule *effects*; this ADR unifies **addresses** (variables, functions, events, historian tags).

## Decision

### 1. Canonical model: `PlatformRef`

```java
PlatformRef(object, kind, name, field)
```

| Field | Values |
|-------|--------|
| `object` | Absolute dot-path or `@` (current rule / expression object) |
| `kind` | `variable`, `function`, `event`, `tag` |
| `name` | Variable, function, event, or rule id |
| `field` | Record field for variables (default `value`); null for fn/evt/tag |

### 2. Slash grammar (canonical text form)

| Kind | Form | Example |
|------|------|---------|
| variable | `<object>/<name>[/<field>]` | `@/temperature`, `root.platform.devices.a/temperature` |
| function | `<object>/fn/<name>` | `@/fn/calculate` |
| event | `<object>/evt/<name>` | `@/evt/alarmRaised` |
| tag | `<object>/tag/<ruleId>` | `root.../tag/avg-temp-5m` |

Reserved path segments: `fn`, `evt`, `tag`.

### 3. Operations

| Op | Purpose | REST equivalent |
|----|---------|-----------------|
| `read(ref)` | Live variable field | `GET .../variables` |
| `call(ref, input?)` | Invoke function | `POST .../functions/invoke` |
| `fire(ref, payload?)` | Publish event | `POST /events/fire` |
| Historian helpers | `avg(ref, 5m)`, `live(ref)` | history aggregate API |

### 4. Structured JSON

Configs use canonical field **`ref`** (slash string). Split fields (`objectPath` + `variableName`, etc.) are accepted on **read** for existing saved configs; **write** paths prefer `ref`:

```json
{ "ref": "root.platform.devices.demo/temperature" }
```

Activators example:

```json
{ "onVariableChange": [{ "ref": "root.platform.devices.remote/temperature" }] }
```

### 5. Documentation and examples

All rules, widgets, mimic bindings, scripts, agent prompts, and examples use PlatformRef slash form only. See [bindings](../bindings.md).

## Consequences

- One grammar for humans, AI, REST, and JSON configs.
- `PlatformRefParser` in `ispf-core`; `PlatformRefExecutor` in `ispf-server`.
- Platform bindings accept slash refs in source arguments (`scale(@/t, ...)`, `read(root.../other/t)`).
- Cross-object **event** activators via `onEventRef`.
- Historian tag identity: `objectPath/tag/ruleId` (catalog, DAG, schedules).

Risks:

- TS/Java parser drift — shared golden vectors in `platform-ref-vectors.json`.

## Related

- [0010-binding-rules-only](0010-binding-rules-only.md)
- [0019-platform-rule-unification](0019-platform-rule-unification.md)
- [0040-unified-computations-ui](0040-unified-computations-ui.md)
- [0041-multi-tag-historian-computations](0041-multi-tag-historian-computations.md)
- [bindings](../bindings.md)
