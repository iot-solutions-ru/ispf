> **Language:** Canonical English. Russian edition: [ru/applications.md](../ru/applications.md).

# Platform applications (REQ-PF)

Platform layer for deploying application solutions **without industry Java code in `ispf-server`**. Aligns with [0001-app-platform-boundary](decisions/0001-app-platform-boundary.md) and REQ-PF requirements.

**Roadmap and REQ-PF status:** [roadmap](roadmap.md) (Part A).

## Overview

| REQ-PF | Capability | API / component |
|--------|------------|-----------------|
| 01 | Application Function Runtime | `POST /applications/{appId}/functions/deploy`, JSON script engine |
| 02 | Application Data Layer | `POST /applications/{appId}/data/migrate` |
| 03 | Application Package Deploy | `POST /applications/{appId}/deploy` |
| 04 | BPMN `invoke_function` | `WorkflowActionType.INVOKE_FUNCTION` |
| 05 | Platform Scheduler | `GET/POST /schedules` |
| 06 | BFF Wire Gateway | `POST /bff/invoke` |
| 07 | Model Registry Persistence | `model_definitions` + auto-save on model CRUD |
| 10 | Workflow Cancel | `POST /workflows/instances/{id}/cancel` |

## Application registration

```http
POST /api/v1/applications
Content-Type: application/json

{
  "appId": "myapp",
  "displayName": "My Application",
  "tablePrefix": "myapp_",
  "schemaName": "myapp"
}
```

## Data migrations (REQ-PF-02)

Application SQL scripts **do not** go into platform Flyway. Deploy via API into an **isolated schema** (`schemaName`, default `app_{appId}`):

```http
POST /api/v1/applications/myapp/data/migrate
Content-Type: application/json

{
  "version": "1.0.0",
  "scripts": [
    { "id": "items", "sql": "CREATE TABLE IF NOT EXISTS demo_item (...);" }
  ]
}
```

Repeat call with the same `version` + `id` is **idempotent** (script is skipped).

**Guard:** DDL cannot create platform tables (`applications`, `workflow_*`, …). With non-empty `tablePrefix`, table names should start with the prefix.

```http
GET /api/v1/applications/myapp/data/status
```

### Seed (smoke)

```http
POST /api/v1/applications/myapp/data/seed
Content-Type: application/json

{ "profile": "smoke-demo" }
```

Built-in profile `smoke-demo` — idempotent INSERTs into generic tables (`demo_category`, `demo_item`, `demo_metric`). Repeat call skips already applied seed blocks.

```http
GET /api/v1/applications/myapp/data/status
```

## Function deploy (REQ-PF-01)

Functions are JSON **scripts** with steps:

| Step | Purpose |
|------|---------|
| `selectOne` | One SQL row → var (`Map`) |
| `selectMany` | List of SQL rows → var (`List<Map>`) |
| `exec` | DML/DDL with `params` |
| `setVar` | Assign literal or `${path}` |
| `buildRecord` | Build `Map` in var from `fields` (field mapping) |
| `map` | Transform list: `source` + `fields` with `${item.*}` context |
| `invoke_function` | Call another deploy function; propagate `error_code` |
| `when`, `if` | Branching (`then` / `else`) |
| `readVariable` | Read object variable field (`objectPath: self`) |
| `jsonParse` | Parse JSON string into fields |
| `setDriverTelemetry` | Write driver telemetry |
| `instantiateModelIfMissing` | Create object from model |
| `cancel_workflows` | Cancel workflow instances |
| `failIfNull` | Exit with `error_code` / `error_message` |
| `failIfNotEquals` | Check var value |
| `return` | Build output (`fields`); arrays via `${var}` |

Full examples of script/java/built-in functions on tree objects: [object-functions](object-functions.md). The same steps run in `sourceType=script` on an object (Inspector) and in application deploy.

One invoke = one JDBC transaction (rollback on unhandled exception). Nested `invoke_function` — up to 8 levels. Invalid script → **400** on deploy.

```http
POST /api/v1/applications/myapp/functions/deploy
Content-Type: application/json

{
  "objectPath": "root.platform.devices.demo-sensor-01",
  "functionName": "myapp_ping",
  "version": "1",
  "descriptor": {
    "inputSchema": { "name": "in", "fields": [{"name": "orderId", "type": "STRING"}] },
    "outputSchema": {
      "name": "out",
      "fields": [
        {"name": "error_code", "type": "STRING"},
        {"name": "error_message", "type": "STRING"}
      ]
    }
  },
  "source": {
    "type": "script",
    "body": "{ \"steps\": [ ... ] }"
  }
}
```

On first invoke the function descriptor is automatically added to the object.

SQL parameters: `"${input.orderId}"` or `"$input.orderId"`.

## Bundle deploy (REQ-PF-03)

### Semver contract (BL-97)

`manifest.version` is **required** and must be strict semver `MAJOR.MINOR.PATCH` (e.g. `1.0.0`). Invalid values are rejected at parse, validate, and deploy.

Optional fields:

- `changelog` — top-level string shown in the solution catalog
- `metadata.changelog` — alternative location for the same text

When upgrading an installed app, `POST …/bundle/validate` warns on **major** version bumps (`1.x.x` → `2.0.0`) so integrators review migrations and operator UI breaks.

API: `GET /api/v1/solutions/catalog`. Reference demos (MES, Warehouse, Building HVAC) install from the marketplace (`POST /api/v1/solutions/marketplaces/{marketplaceId}/listings/{slug}/install`).

One request — registration, metadata, migrations, functions, schedules:

```http
POST /api/v1/applications/myapp/deploy
Content-Type: application/json

{
  "version": "1.0.0",
  "displayName": "My Application",
  "tablePrefix": "",
  "schemaName": "myapp",
  "objects": [
    {
      "parentPath": "root.platform",
      "name": "myapp",
      "type": "CUSTOM",
      "displayName": "My Application"
    }
  ],
  "dashboards": [
    {
      "path": "root.platform.myapp.ops",
      "title": "Operator Board",
      "layoutJson": "{ \"columns\": 12, \"rowHeight\": 72, \"widgets\": [] }"
    }
  ],
  "workflows": [
    {
      "path": "root.platform.myapp.workflows.main",
      "bpmnXml": "<definitions ...>...</definitions>",
      "status": "ACTIVE"
    }
  ],
  "blueprints": [
    {
      "name": "my-device-v1",
      "description": "Custom device template",
      "type": "DEVICE",
      "targetObjectType": "DEVICE",
      "variables": []
    }
  ],
  "migrations": [ { "id": "...", "sql": "..." } ],
  "functions": [ ... ],
  "schedules": [
    {
      "scheduleId": "import-job",
      "enabled": true,
      "intervalMs": 60000,
      "actionType": "invoke_function",
      "action": {
        "objectPath": "root.platform.myapp.integration",
        "functionName": "myapp_importRecords"
      }
    }
  ]
}
```

Response: `{ "status": "OK", "applied": [...], "skipped": [...], "errors": [] }`.

## BFF (REQ-PF-06)

Universal gateway for Operator UI:

```http
POST /api/v1/bff/invoke
Content-Type: application/json

{
  "objectPath": "root.platform.devices.demo-sensor-01",
  "functionName": "myapp_processRecord",
  "input": {
    "schema": { "name": "in", "fields": [{"name": "orderId", "type": "STRING"}] },
    "rows": [{ "orderId": "..." }]
  },
  "wireProfile": "ispf-operator-v1"
}
```

### Wire profile `ispf-operator-v1`

| Rule | Behavior |
|------|----------|
| Success | `error_code === "OK"`, `error_message === ""` |
| Error | `result` absent |
| Table | `result` = array of rows (unwrap `rows` field) |
| Labels | `result_field_labels`: output schema field `description` → profile map `ispf-operator-v1` → field name |

Response (scalar): `{ "error_code": "OK", "error_message": "", "result": { ... }, "result_field_labels": {...}, "wireProfile": "ispf-operator-v1" }`.

Response (table): `{ "error_code": "OK", "result": [ {...}, ... ], "result_field_labels": {...} }`.

## Schedules (REQ-PF-05)

```http
GET /api/v1/schedules
POST /api/v1/schedules
```

Tick every 5 s; action `invoke_function` with JSON `{ objectPath, functionName, input? }`.

## BPMN invoke_function (REQ-PF-04)

In a BPMN service task:

```xml
<ispf:serviceTask action="invoke_function"
  objectPath="root.platform.devices.demo-sensor-01"
  functionName="myapp_acknowledge"
  inputMap="deviceId=${workflow.deviceId}"
  outputMap="ackResult=result" />
```

## Workflow cancel (REQ-PF-10)

```http
POST /api/v1/workflows/instances/{instanceId}/cancel
Content-Type: application/json

{
  "reason": "incident",
  "detailJson": "{\"incidentId\":\"...\"}",
  "cancelledBy": "operator-1"
}
```

## Models (REQ-PF-07)

Custom models are persisted in `model_definitions` and restored at startup.

## Access control

| Endpoint | Role |
|----------|------|
| `/applications/**`, `/schedules/**` (POST/PUT) | `admin` |
| `/applications/*/operator-ui`, `/applications/*/hmi-ui`, `/applications/*/operator-manifest` (GET) | `operator`, `admin` |
| `/bff/invoke`, `/workflows/instances/*/cancel`, `/applications/*/reports/*/run` | `operator`, `admin` |

## SQL bindings (REQ-PF-08)

Declarative sync of object variable ← SQL in app schema:

```http
POST /api/v1/applications/{appId}/bindings/deploy
{
  "objectPath": "root.platform.devices.demo-sensor-01",
  "variable": "readyCount",
  "query": "SELECT COUNT(*) AS value FROM demo_item WHERE status = 'ready'",
  "refresh": "on_schedule",
  "refreshIntervalMs": 30000,
  "valueField": "value"
}
```

| `refresh` | Behavior |
|-----------|----------|
| `on_schedule` | Periodic poll (`ApplicationSqlBindingScheduler`) |
| `on_function_success` | After successful function invoke (optional `triggerObjectPath` / `triggerFunctionName`) |
| `on_event` | After `EVENT_FIRED` on object (optional filter by `triggerObjectPath` / event) |

**Variable-level binding:** on an object variable you can set `bindingExpression`:

```text
sqlBinding('warehouse-app', 'readyCount')
```

The platform runs the SQL binding from `application_sql_bindings` for that app and variable (see `ServerBindingEvaluator`).

Also deploy via bundle: `bindings[]` in manifest.

```http
GET  /api/v1/applications/{appId}/bindings
POST /api/v1/applications/{appId}/bindings/refresh
```

## Function versions (REQ-PF-11)

```http
GET  /api/v1/applications/{appId}/functions?objectPath=&functionName=
POST /api/v1/applications/{appId}/functions/rollback
{ "objectPath", "functionName", "version": "2" }
```

Rollback raises `deployed_at` of the selected version — `findLatest` uses it again.

## Bundle deploy history and rollback (PF-03b)

Each `POST .../deploy` stores a manifest snapshot in `application_bundle_deployments`.

```http
GET  /api/v1/applications/{appId}/deploy/history
POST /api/v1/applications/{appId}/deploy/rollback
{ "version": "1.0.0" }
```

Rollback re-applies the stored manifest (migrations skipped, functions re-applied).

## Bundle export and validate (round-trip)

```http
GET  /api/v1/applications/{appId}/export
GET  /api/v1/applications/{appId}/export?version=1.0.0
POST /api/v1/applications/{appId}/bundle/validate?dryRun=true|false
```

`export` returns the active (or specified) snapshot manifest from `application_bundle_deployments`. Web Console: **Deploy** tab on `APPLICATION` node — JSON editor, validate, deploy, resource table (add/remove in manifest).

```http
POST /api/v1/applications/{appId}/bundle/pull-from-tree
{
  "sections": ["dashboards", "workflows"],
  "paths": ["root.platform.dashboards.demo"],
  "mergeActive": true
}
```

Builds manifest from **live object tree** (visual groups + app stores). `paths` — optional, only listed nodes; without `paths` — all members of bundle visual groups. `models[]` is not reverse-engineered (stays from base manifest).

Object tree subtree (JSON, `formatVersion: 1`): `GET /api/v1/platform/backup/export?rootPath=root.platform.devices.demo-sensor-01` — **JSON export** tab in Inspector.

## Reports (REQ-PF-12)

SQL reports in app schema: deploy via bundle `reports[]` or `POST .../reports/deploy`, run `POST .../reports/{id}/run`, CSV export `GET .../reports/{id}/export`.

Details: [reports](reports.md).

## Operator UI (dashboards from tree)

Unified operator shell: navigation over `DASHBOARD` objects from the tree, same widgets as Dashboard Builder (read-only).

`operatorUi` contract:

```json
{
  "appId": "platform",
  "title": "Platform HMI",
  "defaultDashboard": "root.platform.dashboards.snmp-host-monitoring",
  "dashboards": [
    { "path": "root.platform.dashboards.snmp-host-monitoring", "title": "SNMP Host Monitoring" }
  ]
}
```

Sources (by priority):

1. `GET /api/v1/operator-apps/{appId}/ui` — built-in and admin-configured apps (`operator_app_ui` table, tree node `root.platform.operator-apps`)
2. `operatorUi` field in deploy bundle → `GET /api/v1/applications/{appId}/operator-ui`
3. Auto-generation from `dashboards[]` in bundle (path + title)
4. Legacy fallback: `public/operator-apps/{appId}.ui.json` (dev only)

URL: `?mode=operator&app=platform&dashboard=<path>`.

### Legacy: operator manifest

Field `operatorManifest` in deploy bundle → `GET /api/v1/applications/{appId}/operator-manifest` — **deprecated** (tables/reports via BFF). For new apps use `operatorUi` + dashboards.

Web Console: API first, fallback `public/operator-apps/<appId>.manifest.json` (legacy shell only).

### Tree objects

After register/deploy, application entities sync to `root.platform.applications.{appId}`:

| Folder | Contents |
|--------|----------|
| `reports` | SQL reports (`ObjectType.REPORT`) |
| `functions` | Deployed script functions |
| `schedules` | `platform_schedules` |
| `bindings` | SQL bindings |
| `migrations` | Applied data migrations |
| `screens` | Operator manifest screens (legacy) |

Sync: on deploy/register/migrate and at server startup.

**Example:** `appId=platform` configured in admin: tree → `root.platform.operator-apps`. Deploy apps — `operatorUi` in bundle.

## Related documentation

- [api](api.md) — endpoint table
- [workflows](workflows.md) — BPMN `invoke_function`, instance cancel
- [web-console](web-console.md) — BPMN editor and auto-layout
- [roadmap](roadmap.md) — REQ-PF, sprint roadmap
- [plugins](plugins.md) — core vs commercial plugin boundaries
- [security](security.md) — RBAC matrix

## Deprecation path (PF-03, Phase 5.5)

Table `applications` and REST `/api/v1/applications/*` — **metadata and schema isolation only** (app registry, SQL migrations, deploy history). Not a parallel runtime.

After `POST /api/v1/applications/{appId}/deploy`:

- Functions are addressed via object tree: `{appId}.functions.{name}` on the application node path.
- Objects, dashboards, workflows, models from bundle — tree nodes (`objects[]` reconcile, not create-only).
- Invoke: `POST /api/v1/bff/invoke` or `POST /api/v1/objects/by-path/functions/invoke` by tree path.
- SQL bindings: `bindingExpression: sqlBinding('appId','var')` on the node variable.

**Legacy (deprecated, warn-only):** operator manifest `screens[]` in bundle — Phase 3.5; prefer `operatorUi` + dashboards in tree. Legacy manifest screen types: `table`/`report` (BFF), `dashboard` (embedded DASHBOARD path), `chart` (single-variable trend), `map` (child devices). See [solution-developer-guide](solution-developer-guide.md).

## Next steps (backlog)

Phase 17 and Phase 19 are closed. Current wave — **[roadmap.md § Phase 18](roadmap.md)**:

- **18.1** Playwright admin e2e (Explorer, operator deep link) — Phase 3.4 tail
- **18.2** Driver stub promotion (demand-driven)

Sprint planning: [roadmap](roadmap.md).
