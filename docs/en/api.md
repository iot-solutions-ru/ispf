> **Language:** Canonical English. Russian edition: [ru/api.md](../ru/api.md).

# REST API (v1)

Base URL: `http://localhost:8080`

Authentication: JWT (Keycloak) or header `X-ISPF-Role: admin|developer|operator` (`local` profile).  
Role matrix: [security.md](security.md).

## Platform

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/api/v1/info` | public | Name, version, `javaVersion`, `springBootVersion`, `capabilities[]` |
| GET | `/api/v1/platform/metrics` | admin | Platform summary metrics (runtime, DB, tree, drivers, connections, security, historian, automation) |
| GET | `/api/v1/platform/haystack/export` | operator+ | Haystack grid export for a subtree (`rootPath`, `includePoints`) |
| GET | `/api/v1/platform/haystack/search` | operator+ | AND tag search (`tags`, `rootPath`, `entityKind`, `limit`) |
| GET | `/api/v1/platform/haystack/query` | operator+ | Haystack filter query (`filter`, `rootPath`, `entityKind`, `offset`, `limit`) — see [ADR-0023](decisions/0023-haystack-query-runtime.md) |
| GET | `/api/v1/platform/update/status` | admin | Check for updates from GitHub Releases |
| POST | `/api/v1/platform/update/check` | admin | Force release check |
| POST | `/api/v1/platform/update/apply` | admin | Download release and restart server (VPS, `apply-enabled=true`) |
| GET | `/api/v1/auth/me` | public | Principal and roles |
| POST | `/api/v1/expressions/validate` | admin | CEL validation |

**Haystack filter query (BL-102):**

```http
GET /api/v1/platform/haystack/query?filter=point+and+temp&rootPath=root.platform.devices.lab-userA-01&entityKind=point
```

```json
{
  "formatVersion": 1,
  "filter": "point and temp",
  "count": 1,
  "matches": [
    {
      "entityKind": "point",
      "path": "root.platform.devices.lab-userA-01",
      "variableName": "sineWave",
      "tags": { "point": true, "temp": true, "sensor": true },
      "curVal": 0.42,
      "unit": "°C"
    }
  ]
}
```

Filter v1: marker conjunction with `and` (e.g. `equip and ahu`). Legacy tag search: `GET /platform/haystack/search?tags=equip&tags=temp`.

## Objects

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/api/v1/objects` | operator+ | List (`?parent=` — children) |
| GET | `/api/v1/objects/by-path` | operator+ | Object by `path` |
| GET | `/api/v1/objects/by-path/editor` | operator+ | Object + events + functions |
| POST | `/api/v1/objects` | admin | Create object |
| PATCH | `/api/v1/objects/by-path` | admin | displayName, description |
| DELETE | `/api/v1/objects/by-path` | admin | Delete subtree |
| PUT | `/api/v1/objects/reorder` | admin | Child order (`parentPath`, `orderedPaths`) |
| GET | `/api/v1/objects/by-path/variables` | operator+ | List variables |
| GET | `/api/v1/objects/by-path/variables/detail` | operator+ | One variable (`name`) |
| GET | `/api/v1/objects/by-path/variables/history` | operator+ | Variable history (`path`, `name`, `field`, `from`, `to`, `limit`; only when `historyEnabled`) |
| GET | `/api/v1/objects/by-path/variables/history/aggregate` | operator+ | Aggregates (`bucket`, `from`, `to`, `limit` — up to 2000 buckets; `avg`/`min`/`max`/`count`; `dataSource`: `rollup`/`raw`/`none`) |
| GET | `/api/v1/objects/by-path/variables/history/export` | operator+ | Download history (`format=csv\|json`, same filters, `limit` up to 10000) |
| PATCH | `/api/v1/objects/by-path/variables/history` | admin | `historyEnabled`, `historyRetentionDays` |
| PUT | `/api/v1/objects/by-path/variables` | admin | Write value |

### Create object

```http
POST /api/v1/objects
Content-Type: application/json

{
  "parentPath": "root.platform.devices",
  "name": "pump-01",
  "type": "DEVICE",
  "displayName": "Pump 01",
  "description": "",
  "templateId": "mqtt-sensor-v1",
  "driverId": "mqtt",
  "autoApplyRelativeBlueprints": true
}
```

| Field | Description |
|-------|-------------|
| `templateId` | Explicit blueprint apply (INSTANCE name or RELATIVE mixin) |
| `driverId` | For `DEVICE`: driver provisioning after create |
| `autoApplyRelativeBlueprints` | Default `true`. RELATIVE mixins with **empty** CEL are not applied; non-empty `suitabilityExpression` required |

`mqtt-sensor-v1` is a fixture blueprint (`ispf.bootstrap.fixtures-enabled`). See [blueprints.md](blueprints.md), [ADR-0018](decisions/0018-fixture-models-and-cel-applicability.md).

## Functions

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| POST | `/api/v1/objects/by-path/functions/invoke` | operator+ | Invoke (`path`, `name`, body) |

## Dashboards

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/api/v1/dashboards/by-path` | operator+ | title, layoutJson, refreshIntervalMs |
| PUT | `/api/v1/dashboards/by-path/layout` | admin | Save layout JSON |
| PUT | `/api/v1/dashboards/by-path/title` | admin | Screen title |

## Workflows

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/api/v1/workflows/by-path` | operator+ | BPMN, status, instance state |
| PUT | `/api/v1/workflows/by-path/bpmn` | admin | Save BPMN XML |
| PUT | `/api/v1/workflows/by-path/status` | admin | `DRAFT` / `ACTIVE` / `STOPPED` |
| POST | `/api/v1/workflows/by-path/run` | admin | Start instance |
| POST | `/api/v1/workflows/instances/{instanceId}/cancel` | operator+ | Cancel instance |

## Applications (REQ-PF)

Platform layer for deploying application solutions without Java in `ispf-server`.  
Full description: [applications.md](applications.md).

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| POST | `/api/v1/applications` | admin | Register application |
| POST | `/api/v1/applications/{appId}/deploy` | admin | Bundle deploy (migrations + functions + schedules + reports) |
| GET | `/api/v1/applications/{appId}/reports` | admin | List SQL reports |
| POST | `/api/v1/applications/{appId}/reports/deploy` | admin | Deploy one report |
| POST | `/api/v1/applications/{appId}/reports/{reportId}/run` | operator+ | Run report |
| GET | `/api/v1/applications/{appId}/reports/{reportId}/export` | operator+ | CSV export |
| POST | `/api/v1/applications/{appId}/data/migrate` | admin | Application SQL migrations |
| GET | `/api/v1/applications/{appId}/data/status` | admin | Migration status |
| POST | `/api/v1/applications/{appId}/functions/deploy` | admin | Deploy script function |
| POST | `/api/v1/bff/invoke` | operator+ | Universal BFF gateway to functions |
| GET | `/api/v1/schedules` | admin | List schedules |
| POST | `/api/v1/schedules` | admin | Create/update schedule |

See also: [applications.md](applications.md), [reports.md](reports.md), [plugins.md](plugins.md).

## Work Queue

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/api/v1/work-queue` | operator+ | Open user tasks (`limit`) |
| POST | `/api/v1/work-queue/claim` | operator+ | `taskId`, `operatorId` |
| POST | `/api/v1/work-queue/complete` | operator+ | Complete task |

## Events

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/api/v1/events` | operator+ | Journal (`objectPath`, `limit` ≤ 200) |
| POST | `/api/v1/events/fire` | operator+ | Publish event |

### Fire event

```http
POST /api/v1/events/fire
Content-Type: application/json

{
  "objectPath": "root.platform.devices.demo-sensor-01",
  "eventName": "thresholdExceeded",
  "payload": {
    "schema": { "name": "payload", "fields": [...] },
    "rows": [{ "temperature": 95.0, "threshold": 80.0 }]
  }
}
```

## Alert Rules

| Method | Path | Roles |
|--------|------|-------|
| GET | `/api/v1/alert-rules` | admin |
| GET | `/api/v1/alert-rules/by-path?path=` | admin |
| POST | `/api/v1/alert-rules` | admin |
| PUT | `/api/v1/alert-rules/by-path?path=` | admin |
| DELETE | `/api/v1/alert-rules/by-path?path=` | admin |

## Event Correlators

| Method | Path | Roles |
|--------|------|-------|
| GET | `/api/v1/correlators` | admin |
| GET | `/api/v1/correlators/by-path?path=` | admin |
| POST | `/api/v1/correlators` | admin |
| PUT | `/api/v1/correlators/by-path?path=` | admin |
| DELETE | `/api/v1/correlators/by-path?path=` | admin |

## Drivers

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/api/v1/drivers` | operator+ | SPI driver catalog |
| GET | `/api/v1/drivers/runtime/status` | operator+ | Poll loop status |
| POST | `/api/v1/drivers/runtime/start` | admin | Start (`devicePath`) |
| POST | `/api/v1/drivers/runtime/stop` | admin | Stop |
| PUT | `/api/v1/drivers/runtime/configure` | admin | Config + point mappings |

## Blueprints Plugin

| Method | Path | Roles |
|--------|------|-------|
| GET | `/api/v1/blueprints` | admin |
| GET | `/api/v1/blueprints/{id}` | admin |
| GET | `/api/v1/blueprints/by-name/{name}` | admin |
| POST | `/api/v1/blueprints` | admin |
| PUT | `/api/v1/blueprints/{id}` | admin |
| DELETE | `/api/v1/blueprints/{id}` | admin |
| POST | `/api/v1/blueprints/{id}/apply?objectPath=` | admin |
| POST | `/api/v1/blueprints/{id}/instantiate` | admin |
| POST | `/api/v1/blueprints/from-object` | admin |
| GET | `/api/v1/blueprints/attachments` | admin |

## Platform analytics (BL-160, BL-206)

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/api/v1/platform/analytics/templates` | operator+ | List analytics templates |
| GET | `/api/v1/platform/analytics/tags?path=` | operator+ | List deployed analytics tags (catalog, lineage) |
| GET | `/api/v1/platform/analytics/tags/by-path?path=` | operator+ | Analytics tag catalog entry |
| POST | `/api/v1/platform/analytics/templates/apply` | admin | Apply template to device |
| POST | `/api/v1/platform/analytics/query` | operator+ | Multi-tag aligned aggregate query |
| POST | `/api/v1/platform/analytics/query/export?format=csv\|parquet` | operator+ | Export query result |
| POST | `/api/v1/platform/analytics/tags/backfill` | admin | Recompute derived tag window |
| POST | `/api/v1/platform/analytics/rollups/rebuild` | admin | Rebuild materialized rollups |
| GET | `/api/v1/platform/analytics/frames/active` | operator+ | List active event frames |
| POST | `/api/v1/platform/analytics/frames/open-shift` | admin | Open shift frame from MES `mes_oee_shift` |
| POST | `/api/v1/platform/analytics/frames/open` | admin | Open custom event frame |
| POST | `/api/v1/platform/analytics/frames/close` | admin | Close event frame |
| GET | `/api/v1/platform/analytics/frames/downtime-report` | operator+ | Downtime minutes per frame |
| GET | `/api/v1/platform/analytics/historian-sla` | operator+ | Historian query SLA snapshot |
| GET | `/api/v1/platform/analytics/analytics-slo` | operator+ | Analytics platform SLO targets (BL-210) |

### Multi-tag query

```http
POST /api/v1/platform/analytics/query
Content-Type: application/json

{
  "tags": [
    { "path": "root.platform.devices.demo-sensor-01", "variable": "temperature", "field": "value", "label": "temp" },
    { "path": "root.platform.devices.other", "variable": "pressure", "field": "value" }
  ],
  "from": "2026-07-01T00:00:00Z",
  "to": "2026-07-08T00:00:00Z",
  "bucket": "1h",
  "agg": "avg",
  "maxBuckets": 500
}
```

Response includes aligned `timestamps[]`, per-series `values[]` (null when bucket missing), `dataSource` per series (`rollup` or `raw`), and `latencyMs`. Single-tag aggregate remains on `GET .../history/aggregate` with `dataSource` field.

Limits (configurable): max 20 tags per query, 3s timeout, soft rate limit 120/min.

## Actuator

| Path | Access |
|------|--------|
| `/actuator/health` | public |
| `/actuator/prometheus` | public |
| `/actuator/metrics` | authorized |

## WebSocket

**Endpoint:** `WS /ws/objects`

No authentication (dev). Messages on object changes:

```json
{
  "type": "VARIABLE_UPDATED | OBJECT_CREATED | OBJECT_DELETED | EVENT_FIRED | ...",
  "path": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "timestamp": "2026-06-19T12:00:00Z"
}
```

Client: `apps/web-console/src/hooks/useObjectWebSocket.ts`.

## Error codes

Standard HTTP: `400` (validation), `404` (ObjectNotFoundException), `403` (RBAC), `500`.
