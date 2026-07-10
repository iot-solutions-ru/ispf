> **Language:** Canonical English. Russian edition: [ru/messaging.md](../ru/messaging.md).

# Messaging contract (ISPF)

Contract for buses and synchronous platform ↔ solution calls. REQ-FW-32.

## Principle

| Channel | Semantics | When to use |
|---------|-----------|-------------|
| **REST / BFF invoke** | Sync request/response | CRUD, application functions, deploy, reports |
| **WebSocket `/ws/objects`** | Push object tree changes | Live HMI, explorer, variable updates |
| **Event journal (DB)** | Durable async log | Operator event feed, audit, correlators |
| **NATS** | Async pub/sub between replicas | Replica fan-out, workflow side-effects |

Do not mix sync RPC and async bus in one "universal" channel.

## WebSocket object tree

| Endpoint | Purpose |
|----------|---------|
| `GET /ws/objects` + `Sec-WebSocket-Protocol: ispf-bearer, <token>` | Subscribe paths, presence, **event catalog** |

### Client messages

```json
{ "type": "subscribe", "paths": ["root.platform.devices.demo-sensor-01"] }
```

```json
{ "type": "subscribe_events", "appId": "mes-reference", "events": ["mesRackOverTemp"] }
```

Response `subscribe_events_result`: `accepted[]`, `rejected[]` (roles from bundle `events[]` — FW-31).

### Object change push

```json
{
  "type": "VARIABLE_UPDATED",
  "path": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "timestamp": "2026-06-22T12:00:00Z"
}
```

## NATS subjects (production, `ispf.nats.enabled=true`)

| Subject pattern | Publisher | Consumer | Payload |
|-----------------|-----------|----------|---------|
| `ispf.object.{path}.{changeType}` | `NatsEventBridge` | external integrators | path, type, variableName, timestamp, source |
| `ispf.events.{changeType}` | `NatsEventBridge` (replica fan-out) | `NatsObjectChangeSubscriber` | same |
| `ispf.events.{changeType}` (JetStream) | `NatsEventBridge` when `jet-stream-enabled` | `NatsObjectChangeSubscriber` durable consumer | same payload, persisted in stream `ispf-automation` |
| `ispf.workflow.{workflowPath}.{event}` | `WorkflowService` | external / analytics | workflowPath, event, payload |
| BPMN `publish_nats` task | workflow engine | custom | task-defined subject + message |

Configuration: `ispf.nats.url`, `ispf.nats.replica-id`, `ispf.nats.replica-events-enabled`.

### JetStream (optional, 0014)

When `ispf.nats.jet-stream-enabled=true` (requires `ispf.nats.enabled=true`):

| Setting | Env | Default |
|---------|-----|---------|
| Stream name | `ISPF_NATS_JETSTREAM_STREAM` | `ispf-automation` |
| Retention | `ISPF_NATS_JETSTREAM_MAX_AGE_HOURS` | `24` |
| Consumer prefix | `ISPF_NATS_JETSTREAM_CONSUMER_PREFIX` | `ispf-replica-` |

Replica fan-out uses JetStream publish/subscribe instead of core NATS for `ispf.events.>` subjects. Per-replica durable consumers (`ispf-replica-{replicaId}`) allow catch-up after restarts. Object-level subjects (`ispf.object.*`) remain core NATS for external integrators.

**Ops:** live status (connection, stream messages/bytes, consumer pending, `PUBLISH_NATS` availability) — **System → Metrics** → NATS & JetStream card; API `GET /api/v1/platform/nats/health`.

### Redis correlator windows (optional, 0014)

When `ispf.redis.enabled=true` and `ispf.redis.correlator-windows-enabled=true`:

| Setting | Env | Default |
|---------|-----|---------|
| Correlator windows | `ISPF_REDIS_CORRELATOR_WINDOWS` | `false` |

Sliding-window correlator state (COUNT / SEQUENCE / EVENT_CHAIN hits) is stored in Redis sorted sets (`ispf:corr:hits:{correlatorId}:{objectPath}`) instead of PostgreSQL `correlator_hits`. Enables shared windows across replicas; default remains JDBC for single-node deployments.

**Ops:** live status (connection, correlator store backend, ACL cache backend, TTLs, key count) — **System → Metrics** → Redis card; API `GET /api/v1/platform/redis/health`.

## Sync RPC (primary API)

| API | Examples |
|-----|---------|
| `POST /api/v1/applications/{appId}/deploy` | Bundle deploy |
| `POST /api/v1/bff/invoke` | Application functions |
| `POST /api/v1/objects/by-path/functions/invoke` | Tree functions |
| Federation proxy | `GET /api/v1/federation/proxy/...` |

Federation proxy — sync read over HTTP; not a replacement for event bus.

## Event catalog in bundle (FW-31)

`events[]` section in manifest:

```json
"events": [
  { "id": "mesRackOverTemp", "roles": ["admin"] },
  { "id": "mesOrderUpdated", "roles": ["operator", "admin"] }
]
```

- Deploy loads catalog into `application_event_catalog`
- `GET /api/v1/applications/{appId}/events` — list for solution developers
- WS `subscribe_events` checks roles (admin bypass)
- `POST /api/v1/events/fire?appId={appId}` — optional **fire-time** check of `payloadSchema` from catalog (FW-31); object-level `EventDescriptor.payloadSchema` always applies

## External NATS consumers

For integrating external systems with platform event bus (when `ispf.nats.enabled=true`):

### Connection

```text
NATS_URL=nats://ispf-nats:4222   # same cluster as ispf.nats.url
```

Subscription — **core NATS** (not JetStream by default). NATS authentication — network/VPN level; platform does not add JWT on subject.

### Recommended subject patterns

| Pattern | When to subscribe |
|---------|-------------------|
| `ispf.object.>` | All object changes (VARIABLE_UPDATED, …) |
| `ispf.object.root.platform.devices.*.VARIABLE_UPDATED` | Narrow filter by devices |
| `ispf.workflow.>` | BPMN lifecycle side-effects |
| Custom from `publish_nats` task | Subject from workflow definition |

### Payload (object change)

```json
{
  "path": "root.platform.devices.demo-sensor-01",
  "type": "VARIABLE_UPDATED",
  "variableName": "temperature",
  "timestamp": "2026-06-22T12:00:00Z",
  "source": "platform"
}
```

Fields are stable for `NatsEventBridge`; do not rely on undocumented keys.

### Live variable replica sync (ADR-0029, `ispf.cluster.live-variable-sync-enabled=true`)

Replica fan-out `ispf.events.variable_updated` may include a full value snapshot (coalesced on owner with `ispf.cluster.live-variable-sync-coalesce-ms`, default 500 ms):

```json
{
  "type": "VARIABLE_UPDATED",
  "path": "root.platform.devices.snmp-router-01",
  "variableName": "ifInOctets",
  "timestamp": "2026-07-05T12:00:00Z",
  "source": "replica-2",
  "observedAt": "2026-07-05T12:00:00.123Z",
  "value": {
    "schema": { "name": "ifInOctets", "fields": [{ "name": "value", "type": "DOUBLE" }] },
    "rows": [{ "value": 1234567890 }]
  }
}
```

Follower replicas apply `value` to local RAM (`ClusterVariableReplicaApplier`); messages without `value` keep legacy notify-only behaviour.

Cluster-wide WebSocket path interest (Redis, `ispf.cluster.cluster-path-interest-enabled`) ensures driver owners publish when UI clients subscribe on any replica.

### Consumer practice

1. One durable consumer process per integration; idempotent handling by `(path, variableName, timestamp)`.
2. Do not block platform — heavy processing in your own queue/worker.
3. Read-after-write for consistency — `GET /api/v1/objects/by-path` (sync), do not expect full snapshot from NATS.
4. Federation events are not duplicated on hub NATS automatically — only local instance changes.

Example (nats-cli): `nats sub 'ispf.object.>'`

## Bundle dependencies (FW-12)

```json
"requires": [
  { "appId": "warehouse", "minVersion": "1.0.0" }
]
```

Deploy checks active version of dependent bundle before apply.

## Related documents

- [solution-developer-public-api](solution-developer-public-api.md)
- [automation](automation.md)
- [federation](federation.md)
- [applications](applications.md)
