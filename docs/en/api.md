> **Language:** Canonical English. Russian edition: [ru/api.md](../ru/api.md).

# REST API Reference (v1)

> **Status:** Stable — primary HTTP REST endpoints and WebSocket channels of `ispf-server`. Status tags: [doc-status.md](doc-status.md).

Default base URL: `http://localhost:8080`

**Authentication:** pass `Authorization: Bearer <token>` obtained from `POST /api/v1/auth/login` (in the `local` profile, demo accounts `admin`/`admin`, `developer`/`developer`, and `operator`/`operator` are seeded on an empty database; in production profiles, OIDC JWT tokens from Keycloak are also supported). For the complete role authorization matrix, see **[Security and RBAC (`security.md`)](security.md)**.

---

## 1. Platform Info, Runtime Settings, and Semantic Search

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `GET` | `/api/v1/info` | public | Server name, version, `javaVersion`, `springBootVersion`, and enabled `capabilities[]` |
| `GET` | `/api/v1/auth/me` | public | Current authenticated principal and roles |
| `POST` | `/api/v1/auth/login` | public | Authenticate with username/password (and optional `totpCode`) |
| `GET` | `/api/v1/platform/effective-config` | `operator+` | Active deploy profile and resolved worker pool sizes ([ADR-0033](decisions/0033-prod-idle-demostand-tuning.md)) |
| `GET` | `/api/v1/platform/metrics` | `admin` | Summary metrics (JVM, DB pool, object tree, drivers, queues, historian, load diagnostics) |
| `GET` | `/api/v1/platform/runtime-settings` | `admin` | Read effective runtime settings |
| `PATCH` | `/api/v1/platform/runtime-settings` | `admin` | Persist runtime setting overrides |
| `POST` | `/api/v1/platform/runtime-settings/restart` | `admin` | Schedule a server process restart |
| `GET / POST` | `/api/v1/platform/update/status` (`/check`, `/apply`) | `admin` | Check and apply GitHub release updates |
| `POST` | `/api/v1/expressions/validate` | `admin` | Compile and formally verify a CEL expression ([ADR-0055](decisions/0055-cel-formal-verification-ai-gate.md)) |
| `GET` | `/api/v1/platform/haystack/export` | `operator+` | Export subtree as a Project Haystack grid (`rootPath`, `includePoints`) |
| `GET` | `/api/v1/platform/haystack/query` | `operator+` | Query objects and points by Haystack filter (`filter`, `rootPath`, `entityKind`, `offset`, `limit`) |

Example Project Haystack filter query:

```http
GET /api/v1/platform/haystack/query?filter=point+and+temp&rootPath=root.platform.devices.lab-userA-01&entityKind=point
```

---

## 2. Object Tree and Variables (`/api/v1/objects/**`)

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `GET` | `/api/v1/objects` | `operator+` | List child objects (`?parent=<path>`) |
| `GET` | `/api/v1/objects/by-path` | `operator+` | Read object metadata by path (`?path=...`) |
| `GET` | `/api/v1/objects/by-path/editor` | `operator+` | Read object together with its variables, events, and functions |
| `POST` | `/api/v1/objects` | `developer+` | Create a new tree node |
| `PATCH` | `/api/v1/objects/by-path` | `developer+` | Update `displayName` and `description` |
| `DELETE` | `/api/v1/objects/by-path` | `developer+` | Delete an object and its subtree |
| `PUT` | `/api/v1/objects/reorder` | `developer+` | Reorder sibling nodes (`parentPath`, `orderedPaths`) |
| `GET` | `/api/v1/objects/by-path/variables` | `operator+` | List all variables and current values on an object |
| `GET` | `/api/v1/objects/by-path/variables/detail` | `operator+` | Read one variable (`?path=...&name=...`) |
| `PUT` | `/api/v1/objects/by-path/variables` | `operator+` / `admin` | Write variable value (`writable` variables for operators) |
| `GET` | `/api/v1/objects/by-path/variables/history` | `operator+` | Query raw historical samples (`path`, `name`, `field`, `from`, `to`, `limit`) |
| `GET` | `/api/v1/objects/by-path/variables/history/aggregate` | `operator+` | Query bucketed aggregates (`bucket`, `from`, `to`, `limit`: `avg`, `min`, `max`, `count`) |
| `GET` | `/api/v1/objects/by-path/variables/history/export` | `operator+` | Export variable history (`format=csv\|json`, up to 10,000 points) |
| `PATCH` | `/api/v1/objects/by-path/variables/history` | `admin` | Configure `historyEnabled`, `historyRetentionDays`, `historySampleMode` |

---

## 3. Functions, Dashboards, Mimics, and BPMN Workflows

### Object Functions and BFF Gateway
| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `POST` | `/api/v1/objects/by-path/functions/invoke` | `operator+` | Invoke an object function (`path`, `name`, input `DataRecord`) |
| `POST` | `/api/v1/objects/by-path/functions/invoke-batch` | `operator+` | Batch invoke up to 100 function calls with per-item ACL checks |
| `POST` | `/api/v1/bff/invoke` | `operator+` | Universal UI Backend-for-Frontend gateway (`wireProfile: "ispf-operator-v1"`) |

### Dashboards (`DASHBOARD`) and SCADA Mimics (`MIMIC`)
| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `GET` | `/api/v1/dashboards/by-path` | `operator+` | Read dashboard `title`, `layoutJson`, and `refreshIntervalMs` |
| `PUT` | `/api/v1/dashboards/by-path/layout` (`/title`) | `admin` | Save dashboard layout JSON or title |
| `GET` | `/api/v1/mimics/by-path` | `operator+` | Read SCADA mimic diagram (`diagramJson`) |
| `PUT` | `/api/v1/mimics/by-path/diagram` (`/title`) | `admin` | Save SCADA mimic `diagramJson` or title |

### BPMN Workflows (`WORKFLOW`) and Shift Work Queue
| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `GET` | `/api/v1/workflows/by-path` | `operator+` | Read BPMN XML, status, and latest instance state |
| `PUT` | `/api/v1/workflows/by-path/bpmn` (`/status`) | `admin` | Save BPMN XML or set status (`DRAFT`, `ACTIVE`, `STOPPED`) |
| `POST` | `/api/v1/workflows/by-path/run` | `admin` | Start a new workflow instance |
| `POST` | `/api/v1/workflows/instances/{instanceId}/cancel` | `operator+` | Cancel a running workflow instance |
| `GET` | `/api/v1/work-queue` | `operator+` | List open operator tasks (`limit`, optional `operatorAppId`) |
| `POST` | `/api/v1/work-queue/claim` (`/complete`) | `operator+` | Claim or complete a BPMN operator task (`taskId`, `operatorId`) |

---

## 4. Application Bundles (`bundle.json`), Reports, and Schedules

Full specification: **[Application Solutions (`applications.md`)](applications.md)**.

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `POST` | `/api/v1/applications` | `developer+` | Register an application (`appId`, `schemaName`, `tablePrefix`) |
| `POST` | `/api/v1/applications/{appId}/deploy` | `developer+` | Deploy a full `bundle.json` manifest (`?strict=true\|false&runTests=true\|false`) |
| `POST` | `/api/v1/applications/{appId}/bundle/validate` | `developer+` | Validate a `bundle.json` manifest (`?dryRun=true\|false&strict=true\|false`) |
| `GET` | `/api/v1/applications/{appId}/export` | `developer+` | Export the active deployed bundle manifest |
| `POST` | `/api/v1/applications/{appId}/data/migrate` | `developer+` | Run SQL migrations in the application's isolated schema |
| `GET` | `/api/v1/applications/{appId}/data/status` | `developer+` | Check applied SQL migration versions |
| `POST` | `/api/v1/applications/{appId}/functions/deploy` | `developer+` | Deploy a JSON step-script function |
| `GET / POST` | `/api/v1/reports/by-path` (`/run`, `/export`) | `operator+` | Read, execute, and export reports (`csv`, `xlsx`, `pdf`, `html`) |
| `GET / POST` | `/api/v1/schedules` | `developer+` | List and create periodic schedules |

---

## 5. Events, Alert Rules, Correlators, Drivers, and Blueprints

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `GET` | `/api/v1/events` | `operator+` | Query the event journal (`objectPath`, `limit` ≤ 200, optional CEL `expr`) |
| `POST` | `/api/v1/events/fire` | `operator+` | Publish an event on an object |
| `POST` | `/api/v1/events/journal/purge` | `admin` | Purge persisted event rows older than `olderThan` (ISO-8601) |
| `GET / POST / PUT / DELETE` | `/api/v1/alert-rules` (`/by-path?path=`) | `developer+` | Manage `ALERT` rules |
| `GET / POST / PUT / DELETE` | `/api/v1/correlators` (`/by-path?path=`) | `developer+` | Manage `CORRELATOR` rules |
| `GET` | `/api/v1/drivers` | `operator+` | List registered protocol drivers and maturity levels |
| `GET` | `/api/v1/drivers/runtime/status` | `operator+` | Inspect driver polling status (`?devicePath=...`) |
| `PUT` | `/api/v1/drivers/runtime/configure` | `admin` | Configure device connection properties and `pointMappings` |
| `POST` | `/api/v1/drivers/runtime/start` (`/stop`) | `admin` | Start or stop a device driver |
| `GET / POST / PUT / DELETE` | `/api/v1/blueprints/**` | `developer+` | Manage `SINGLETON`, `INSTANCE`, and `MIXIN` blueprints |

---

## 6. Platform Analytics (`/api/v1/platform/analytics/**`)

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `GET` | `/api/v1/platform/analytics/tags?path=` | `operator+` | List deployed `kind: "historian"` analytics tags and lineage |
| `GET` | `/api/v1/platform/analytics/tags/by-path?path=` | `operator+` | Read a single analytics tag (`objectPath/tag/ruleId`) |
| `POST` | `/api/v1/platform/analytics/query` | `operator+` | Aligned multi-tag bucketed query (up to 20 tags) |
| `POST` | `/api/v1/platform/analytics/query/export?format=csv\|parquet` | `operator+` | Export multi-tag query results |
| `POST` | `/api/v1/platform/analytics/tags/backfill` | `admin` | Recompute a derived historian tag over a past window |
| `GET` | `/api/v1/platform/analytics/catalog` | `operator+` | Unified catalog of Tier A/B/C analytics functions |
| `POST` | `/api/v1/platform/analytics/expression/validate` (`/evaluate`) | `operator+` | Validate or evaluate a historian CEL expression |

---

## 7. WebSocket and HTTP Status Codes

- **Live object tree WebSocket:** `WS /ws/objects` with header `Sec-WebSocket-Protocol: ispf-bearer, <token>`. Full message protocol: **[Messaging (`messaging.md`)](messaging.md)**.
- **Standard HTTP status codes:** `200 OK`, `400 Bad Request` (validation error), `401 Unauthorized` / `403 Forbidden` (RBAC/ACL denied), `404 Not Found`, `409 Conflict` (`revision` mismatch), `423 Locked` (active subtree edit lease).
