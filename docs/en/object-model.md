> **Language:** Canonical English. Russian edition: [ru/object-model.md](../ru/object-model.md).

# Object model

ISPF business logic is expressed through **tree node composition**: models (blueprints), variables, events, functions, workflow, and automation nodes. See [architecture.md § Core principle](architecture.md).

## Hierarchy

All platform entities are **object tree nodes** with dot-notation paths:

```
root
└── root.platform
    ├── root.platform.devices
    │   ├── root.platform.devices.demo-sensor-01
    │   └── root.platform.devices.snmp-localhost
    ├── root.platform.relative-blueprints
    ├── root.platform.instance-types
    ├── root.platform.absolute-blueprints
    ├── root.platform.instances
    ├── root.platform.dashboards
    │   └── root.platform.dashboards.demo-sensor
    ├── root.platform.reports
    │   └── root.platform.reports.ready-items
    ├── root.platform.security
    │   ├── root.platform.security.users
    │   │   ├── root.platform.security.users.admin
    │   │   └── root.platform.security.users.operator
    │   └── root.platform.security.roles
    │       ├── root.platform.security.roles.admin
    │       └── root.platform.security.roles.operator
    ├── root.platform.workflows
    │   └── root.platform.workflows.demo-alarm-handler
    ├── root.platform.alert-rules
    │   └── root.platform.alert-rules.temperature-threshold-exceeded
    └── root.platform.correlators
        └── root.platform.correlators.alarm-handler-on-threshold-event
```

Paths are separated by a **dot** (`.`), not a slash. Child path resolution: `parentPath + "." + name`.

## Object types (`ObjectType`)

| Type | Purpose |
|------|---------|
| `ROOT` | Tree root |
| `TENANT` | Tenant (multi-tenancy, planned) |
| `PLATFORM` | `root.platform` node |
| `DEVICES` | Device catalog |
| `DEVICE` | Device with driver |
| `DRIVER` | Driver instance |
| `MODEL` | Model definition |
| `DASHBOARDS` | Dashboard catalog |
| `DASHBOARD` | HMI screen |
| `WORKFLOWS` | Workflow catalog |
| `WORKFLOW` | BPMN process |
| `ALERT_RULES` | Alert rules catalog |
| `ALERT` | Alert rule |
| `CORRELATORS` | Correlators catalog |
| `CORRELATOR` | Event correlator |
| `APPLICATIONS` | Applications catalog |
| `APPLICATION` | Application (bundle) |
| `OPERATOR_APPS` | Operator UI catalog |
| `SECURITY` | RBAC root |
| `USERS` / `USER` | Users |
| `ROLES` / `ROLE` | Roles |
| `FUNCTIONS` / `FUNCTION` | Application functions |
| `REPORTS` / `REPORT` | `root.platform.reports` catalog and SQL reports (`report-v1`) |
| `AGENT` | Edge agent |
| `VISUAL_GROUP` | Visual group — references objects in `@groupMembers` without changing their paths |
| `CUSTOM` | Arbitrary container (fallback for unknown nodes) |

System folders (`PLATFORM`, `DEVICES`, `ALERT_RULES`, …) get semantic types at bootstrap and via migration `V22__system_object_types.sql`. User nodes — from model template or `CUSTOM`.

## Security in the tree

Nodes under `root.platform.security` are created automatically at startup and when users change:

| Path | Type | Variables |
|------|------|-----------|
| `...security.users.{username}` | `USER` | `username`, `displayName`, `roles`, `enabled` |
| `...security.roles.{role}` | `CUSTOM` | `roleName`, `description` |

User CRUD — via `POST/PUT/DELETE /api/v1/security/users` (`admin` role). Deleting a `USER` tree node also deletes the account.

## Object composition (`PlatformObject`)

Each node contains:

- **Metadata:** `id`, `path`, `displayName`, `description`, `templateId`, `sortOrder`, `createdAt`
- **Variables** (`Variable`) — typed values
- **Functions** (`FunctionDescriptor`) — callable operations
- **Events** (`EventDescriptor`) — publishable event types

## Typed data

### DataSchema

Field description for a record:

```json
{
  "name": "temperature",
  "fields": [
    { "name": "value", "type": "DOUBLE" },
    { "name": "unit", "type": "STRING" }
  ]
}
```

Field types (`FieldType`): `BOOLEAN`, `INTEGER`, `LONG`, `DOUBLE`, `STRING`, `DATETIME`, `BINARY`, `RECORD`, `RECORD_LIST`.

### Telemetry quality (BL-82)

Optional `quality` field on telemetry rows: `GOOD`, `UNCERTAIN`, `BAD` ([0025-telemetry-quality-flags](decisions/0025-telemetry-quality-flags.md)). Drivers map protocol status (e.g. OPC UA StatusCode) to these levels. Chart widgets skip `BAD` samples (line gap); historian quality column is follow-up work.

### DataRecord

Tabular structure with data rows. Typical single-row REST format:

```json
{
  "schema": { "name": "temperature", "fields": [...] },
  "rows": [{ "value": 23.5, "unit": "C" }]
}
```

Widgets and API read fields via `valueField` (default `"value"`).

## Variables

| Property | Description |
|----------|-------------|
| `name` | Name on the object |
| `schema` | `DataSchema` |
| `readable` / `writable` | Access rights |
| `value` | Current `DataRecord` |

### Computed bindings (binding rules)

Rules on the object in `@bindingRules` (see [bindings](bindings.md)). Runtime — `BindingRuleEngine`; cross-object via activators and `refAt`.

Brief list of platform bindings:

| Function | Purpose |
|----------|---------|
| `selectField` | Source variable field |
| `scale` | Linear range mapping |
| `clamp` | Limit min…max |
| `format` | String via `String.format` template |
| `delta` | Δ to previous sample (stateful) |
| `counterRate` | B/s from Counter32 with wrap (stateful) |

Validation: `POST /api/v1/expressions/validate`.

## Events

`EventDescriptor` defines name, description, payload schema, and level (`DEBUG` … `CRITICAL`).

Publish:

```http
POST /api/v1/events/fire
```

The event is validated against the object descriptor, written to `event_history`, and broadcast via WebSocket.

## Functions

`FunctionDescriptor` — name, input/output `DataSchema`, optionally `sourceType` + `sourceBody`.

| `sourceType` | Behavior |
|--------------|----------|
| *(empty)* | Built-in platform `FunctionHandler` by function name on the object |
| `script` | JSON DSL (`steps`) — `ScriptFunctionHandler` |
| `java` | Public class source → `ObjectJavaFunction`; **compile on save** (`PUT .../functions`), invoke via `JavaFunctionHandler` |

**Detailed examples for all types:** [object-functions](object-functions.md) (built-in handlers, script steps, Java, bindings, workflow, REST).

Invoke:

```http
POST /api/v1/objects/by-path/functions/invoke?path=...&name=...
Content-Type: application/json

{ "schema": {...}, "rows": [{...}] }
```

**Java function:** class `implements com.ispf.core.function.ObjectJavaFunction`; compile on save; see [object-functions.md § Java](object-functions.md).

Built-in handlers (function name on object + server handler): `acknowledgeAlarm`, Virtual Lab (`calculate`, `fireEvent1`, …), Mini-TEC (`gpu_start`, …), `dispatchTelemetry` (MQTT gateway). Full list and payloads — in [object-functions](object-functions.md).

Platform extension: implement `FunctionHandler` in `ispf-server` and register as Spring `@Component`.

## Models (templates)

Objects are created manually or from a **model** (`templateId`). A model defines variables, events, functions, and bindings.

**RELATIVE mixins** on create auto-apply only with a **non-empty** CEL (*Applicability condition*). Empty CEL → explicit apply only (`templateId`, API). Fixture models (`mqtt-sensor-v1`, …) are not in the core registry.

**DEVICE + driver:** `driver*` variables are embedded at `provisionDriver()`, not via relative auto-apply.

See [blueprints](blueprints.md), [0018-fixture-models-and-cel-applicability](decisions/0018-fixture-models-and-cel-applicability.md).

## Persistence

Flyway migrations (`packages/ispf-server/src/main/resources/db/migration/`):

| Table | Contents |
|-------|----------|
| `object_nodes` | Tree nodes (including alert rules and correlators) |
| `object_variables` | Variable values (`@bindingRules` — reserved var on object) |
| `variable_samples` | Telemetry history (time-series samples; Timescale hypertable — [0009-timescaledb-retention](decisions/0009-timescaledb-retention.md)) |
| `event_history` | Event journal (Timescale hypertable — [0015-event-history-timescale](decisions/0015-event-history-timescale.md)) |
| `workflow_instances` | BPMN instances |
| `workflow_user_tasks` | Operator tasks |
| `correlator_hits` | Correlator firings (runtime) |
| `alert_rules` / `event_correlators` | Legacy; migrated into tree at startup |

At startup `ObjectManager` loads the tree from the DB or runs `PlatformBootstrap` (empty DB).

## Live updates

`ObjectChangeEvent` is published on create/update/delete of objects and variables.

WebSocket `WS /ws/objects` broadcasts JSON:

```json
{
  "type": "VARIABLE_UPDATED",
  "path": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "timestamp": "2026-06-19T12:00:00Z"
}
```

Web Console subscribes via `useObjectWebSocket` and invalidates the TanStack Query cache.

## CRUD via API

| Operation | Endpoint |
|-----------|----------|
| List | `GET /api/v1/objects?parent=` |
| Get | `GET /api/v1/objects/by-path?path=` |
| Editor | `GET /api/v1/objects/by-path/editor?path=` |
| Create | `POST /api/v1/objects` |
| Update | `PATCH /api/v1/objects/by-path?path=` |
| Delete | `DELETE /api/v1/objects/by-path?path=` |
| Child order | `PUT /api/v1/objects/reorder` (`parentPath`, `orderedPaths`) |
| Variables | `GET/PUT .../variables` |

Creating `DASHBOARD` / `WORKFLOW` automatically applies the corresponding built-in model.

## Variable history

Current value — in `object_variables`. **History** is written to `variable_samples` only for variables with `historyEnabled = true` on each `VARIABLE_UPDATED` (debounced by `min-interval-ms`).

Per variable (in model and on object):

| Field | Description |
|-------|-------------|
| `historyEnabled` | Whether to record time series |
| `historyRetentionDays` | Retention in days; `null` — platform default from `ispf.variable-history.retention-days` |

```http
GET /api/v1/objects/by-path/variables/history?path=...&name=temperature&field=value&limit=500
PATCH /api/v1/objects/by-path/variables/history?path=...&name=temperature
Content-Type: application/json

{"historyEnabled": true, "historyRetentionDays": 30}
```

Platform config: `ispf.variable-history` in `application.yml` (`enabled`, `min-interval-ms`, `retention-days`).

Charts (`useTrendSeries`) load history from the server and append live points via WebSocket/polling.

Detailed roadmap: [variable-history](variable-history.md).
