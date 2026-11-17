> **Language:** Canonical English. Russian edition: [ru/object-model.md](../ru/object-model.md).

# Object Model

> **Status:** Stable — hierarchical object tree, typed variables (`DataRecord`), events, functions, and persistence. Status tags: [doc-status.md](doc-status.md).

Every entity in ISPF — field devices, logic hubs, operator dashboards, SCADA mimics, alert rules, BPMN workflows, SQL reports, and user accounts — is a node in a **single hierarchical object tree**. Every node is addressed by a dot-separated path and accessed through the same REST and WebSocket APIs.

**Related guides:** [How ISPF works](learn.md) · [Architecture](architecture.md) · [Blueprints](blueprints.md) · [Binding rules](bindings.md).

**Demo:** built-in `demo-sensor-01` (ships with the server); smallest bundle tree: [`examples/platform-primitive`](../../examples/platform-primitive/).

---

## 1. Tree Hierarchy and Paths

![Object tree in Admin Explorer](../assets/ispf-object-tree.png)

Path segments are separated by a **dot** (`.`): a child path is formed as `parentPath + "." + name`.

```text
root
└── root.platform
    ├── root.platform.devices
    │   ├── root.platform.devices.demo-sensor-01
    │   └── root.platform.devices.snmp-localhost
    ├── root.platform.singleton-blueprints
    ├── root.platform.instance-types
    ├── root.platform.mixin-blueprints
    ├── root.platform.instances
    ├── root.platform.dashboards
    │   └── root.platform.dashboards.demo-sensor
    ├── root.platform.mimics
    ├── root.platform.workflows
    │   └── root.platform.workflows.demo-alarm-handler
    ├── root.platform.alert-rules
    │   └── root.platform.alert-rules.temperature-threshold-exceeded
    ├── root.platform.correlators
    ├── root.platform.reports
    └── root.platform.security
        ├── root.platform.security.users
        └── root.platform.security.roles
```

---

## 2. Node Types (`ObjectType`)

| `ObjectType` | Purpose |
|--------------|---------|
| `ROOT`, `PLATFORM`, `TENANT` | Root structural containers (`root`, `root.platform`, `root.tenant.<id>`) |
| `DEVICES`, `DEVICE` | Device catalog and individual field device nodes (drivers and telemetry only) |
| `SINGLETON` / `INSTANCE` (on `CUSTOM`) | Application logic hubs and digital-twin instances created from blueprints |
| `DASHBOARDS`, `DASHBOARD` | Dashboard catalog and operator screen definitions (`dashboard-v1`) |
| `MIMICS`, `MIMIC` | SCADA mimic diagram catalog and vector P&ID / single-line diagrams (`mimic-v1`) |
| `WORKFLOWS`, `WORKFLOW` | Workflow catalog and BPMN 2.0 processes (`workflow-v1`) |
| `ALERT_RULES`, `ALERT` | Alert catalog and CEL threshold rules (`alert-rule-v1`) |
| `CORRELATORS`, `CORRELATOR` | Event correlator catalog and multi-event pattern rules (`correlator-v1`) |
| `REPORTS`, `REPORT` | Report catalog and read-only SQL / tree-variable reports (`report-v1`) |
| `APPLICATIONS`, `APPLICATION` | Installed application bundles (`root.platform.applications.<appId>`) |
| `OPERATOR_APPS` | Operator workspace menu definitions (`root.platform.operator-apps`) |
| `SECURITY`, `USERS`, `USER`, `ROLES`, `ROLE` | Users and roles mirrored into the object tree |
| `VISUAL_GROUP` | Logical grouping of existing objects (`@groupMembers`) without changing their canonical paths |
| `CUSTOM` | General-purpose folder or blueprint-backed logic object |

---

## 3. Anatomy of a Platform Object (`PlatformObject`)

Each node in the tree has:
1. **Metadata:** `id`, `path`, `type`, `displayName`, `description`, `templateId`, `appliedBlueprintIds`, `sortOrder`, `revision`, `createdAt`.
2. **Variables (`Variable`):** typed values stored as a `DataRecord` with a `DataSchema`.
3. **Events (`EventDescriptor`):** named event definitions with a payload schema and severity (`DEBUG`, `INFO`, `WARNING`, `ERROR`, `CRITICAL`).
4. **Functions (`FunctionDescriptor`):** callable operations with input and output schemas (`script`, `java`, `object-query`, or built-in handlers).

---

## 4. Typed Data: `DataSchema`, `DataRecord`, and Quality Flags

### `DataSchema` and `FieldType`

Every variable and function payload is described by a `DataSchema`:

```json
{
  "name": "temperature",
  "fields": [
    { "name": "value", "type": "DOUBLE" },
    { "name": "unit", "type": "STRING" }
  ]
}
```

Supported field types (`FieldType`): `BOOLEAN`, `INTEGER`, `LONG`, `DOUBLE`, `STRING`, `DATETIME`, `BINARY`, `RECORD`, `RECORD_LIST`.

### `DataRecord`

A `DataRecord` holds the schema and one or more rows of data:

```json
{
  "schema": {
    "name": "temperature",
    "fields": [
      { "name": "value", "type": "DOUBLE" },
      { "name": "unit", "type": "STRING" }
    ]
  },
  "rows": [
    { "value": 23.5, "unit": "°C" }
  ]
}
```

### Telemetry Quality (`GOOD`, `UNCERTAIN`, `BAD`)

Telemetry rows may carry an optional `quality` flag ([ADR-0025](decisions/0025-telemetry-quality-flags.md), [ADR-0074](decisions/0074-historian-quality-persistence.md)): `GOOD`, `UNCERTAIN`, or `BAD`. Protocol drivers map device status codes (such as OPC UA `StatusCode`) to these levels, and SQL bindings set `quality=BAD` when a query fails or returns no usable value. Trend charts render a gap across `BAD` samples instead of plotting false zeros.

---

## 5. Variables, Calculation Rules, Events, and Functions

- **Variables and history:** each variable defines `readable`, `writable`, `readRoles`, `writeRoles`, `historyEnabled`, and `historyRetentionDays`. See [Variable history](variable-history.md).
- **Calculation rules (`@bindingRules`):** reactive and historical formulas evaluated by `BindingRuleEngine` and `AnalyticsEngineService`. See [Binding rules](bindings.md) and [Expression language](expression-language.md).
- **Publishing events:** `POST /api/v1/events/fire` validates the payload against the object's `EventDescriptor`, records the event in `event_history`, triggers matching correlators, and broadcasts over WebSocket.
- **Invoking functions:** `POST /api/v1/objects/by-path/functions/invoke?path=...&name=...` or `POST /api/v1/bff/invoke`. See [Object functions](object-functions.md).

---

## 6. Database Persistence and Live WebSocket Updates

| Database Table | Contents |
|----------------|----------|
| `object_nodes` | Tree hierarchy, metadata, and optimistic concurrency `revision` |
| `object_variables` | Current variable values and `@bindingRules` JSON |
| `variable_samples` | Time-series history (TimescaleDB hypertable or ClickHouse) |
| `event_history` | Platform event journal (TimescaleDB hypertable or ClickHouse) |
| `workflow_instances`, `workflow_user_tasks` | Active BPMN process state and operator work-queue tasks |

Whenever an object or variable changes, `ObjectManager` publishes an `ObjectChangeEvent` that is pushed to subscribed browsers over `WS /ws/objects`:

```json
{
  "type": "VARIABLE_UPDATED",
  "path": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "timestamp": "2026-06-19T12:00:00Z"
}
```

---

## 7. Core Object Tree REST Endpoints

| Operation | HTTP Method & Path |
|-----------|--------------------|
| List child nodes | `GET /api/v1/objects?parent=root.platform.devices` |
| Read node by path | `GET /api/v1/objects/by-path?path=root.platform.devices.pump-01` |
| Read node with variables, events, and functions | `GET /api/v1/objects/by-path/editor?path=...` |
| Create node | `POST /api/v1/objects` |
| Update display name / description | `PATCH /api/v1/objects/by-path?path=...` |
| Reorder sibling nodes | `PUT /api/v1/objects/reorder` |
| Delete node and subtree | `DELETE /api/v1/objects/by-path?path=...` |
| Read or write variables | `GET / PUT /api/v1/objects/by-path/variables?path=...` |
