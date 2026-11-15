> **Language:** Canonical English. Russian edition: [ru/messaging.md](../ru/messaging.md).

# Messaging Channels: REST/BFF, WebSocket, Event Journal, and NATS

> **Status:** Stable — communication channels between the platform, operator browsers, cluster replicas, and external integrations. Status tags: [doc-status.md](doc-status.md).

In ISPF, each communication channel serves a distinct purpose: synchronous commands and queries use HTTP REST/BFF, live screen updates use WebSocket, durable audit trails use the database event journal, and inter-replica or external pub/sub uses NATS.

---

## 1. When to Use Each Channel

| Channel | Semantics | Typical Use |
|---------|-----------|-------------|
| **REST API & BFF Gateway** (`/api/v1/**`) | Synchronous request / response | Object tree CRUD, invoking application functions (`POST /api/v1/bff/invoke`), deploying `bundle.json`, running reports |
| **WebSocket** (`/ws/objects`) | Real-time server push to browsers | Live updates for dashboard widgets, SCADA mimics, and the Admin Explorer tree |
| **Event Journal (Database)** | Durable asynchronous log | Operator event feed, alarm history, audit trail, and input to event correlators |
| **NATS Bus** (`ispf.events.*`, `ispf.object.*`) | Asynchronous server-to-server pub/sub | Cluster replica synchronization, external enterprise integrations, and BPMN `publish_nats` tasks |

---

## 2. Browser WebSocket Subscriptions (`/ws/objects`)

Clients connect to `GET /ws/objects` passing the Bearer token in the header `Sec-WebSocket-Protocol: ispf-bearer, <token>`.

### Subscribing to All Variables on an Object

```json
{ "type": "subscribe", "paths": ["root.platform.devices.demo-sensor-01"] }
```

### Narrow Variable Subscription (Recommended for HMI Widgets)

To receive notifications only for specific variables displayed on screen, pass `variablesByPath`:

```json
{
  "type": "subscribe",
  "paths": ["root.platform.devices.demo-sensor-01"],
  "variablesByPath": {
    "root.platform.devices.demo-sensor-01": ["temperature", "online"]
  }
}
```

- Omitting `variablesByPath` for a path subscribes to **all** variables on that object.
- Sending an empty array `paths: []` clears the subscription (the server never broadcasts the entire object tree without explicit paths).

### Subscribing to Application Event Catalogs (`subscribe_events`)

```json
{ "type": "subscribe_events", "appId": "mes-reference", "events": ["mesRackOverTemp"] }
```

The server checks the user's roles against the bundle's `events[]` catalog and replies with `subscribe_events_result` (`accepted[]`, `rejected[]`).

### Incoming Variable Change Notification

```json
{
  "type": "VARIABLE_UPDATED",
  "path": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "timestamp": "2026-06-22T12:00:00Z"
}
```

---

## 3. NATS Subjects (`ispf.nats.enabled=true`)

| NATS Subject Pattern | Publisher | Consumer | Payload |
|----------------------|-----------|----------|---------|
| `ispf.object.<path>.<changeType>` | `NatsEventBridge` | External integrators | `path`, `type`, `variableName`, `timestamp`, `source` |
| `ispf.events.<changeType>` | `NatsEventBridge` | Cluster replicas (`NatsObjectChangeSubscriber`) | Same fields + full `DataRecord` `value` snapshot when live variable sync ([ADR-0029](decisions/0029-cluster-live-variable-replica-sync.md)) is enabled |
| `ispf.workflow.<workflowPath>.<event>` | `WorkflowService` | External subscribers & analytics | `workflowPath`, `event`, `payload` |
| Custom subject from BPMN `publish_nats` | Workflow engine | External services | Subject and message configured on the BPMN task |

### NATS JetStream for Durable Replica Synchronization

When `ispf.nats.jet-stream-enabled=true` (required in cluster mode per [ADR-0069](decisions/0069-cluster-consistency.md)):

| Setting | Environment Variable | Default |
|---------|----------------------|---------|
| Stream name | `ISPF_NATS_JETSTREAM_STREAM` | `ispf-automation` |
| Retention window | `ISPF_NATS_JETSTREAM_MAX_AGE_HOURS` | `24` (hours) |
| Durable consumer prefix | `ISPF_NATS_JETSTREAM_CONSUMER_PREFIX` | `ispf-replica-` |

Health check: **System → Metrics** (NATS & JetStream card) or `GET /api/v1/platform/nats/health`.

### Redis Sliding Windows for Event Correlators

When `ispf.redis.enabled=true` and `ispf.redis.correlator-windows-enabled=true` (`ISPF_REDIS_CORRELATOR_WINDOWS=true`), sliding-window correlator state (`COUNT`, `SEQUENCE`, `EVENT_CHAIN`, `WINDOW`) is stored in Redis sorted sets (`ispf:corr:hits:{correlatorId}:{objectPath}`) instead of PostgreSQL `correlator_hits`. Health check: `GET /api/v1/platform/redis/health`.

---

## 4. Application Event Catalog (`events[]` in `bundle.json`)

Applications can declare a typed event catalog with role restrictions in `bundle.json`:

```json
"events": [
  { "id": "mesRackOverTemp", "roles": ["admin"] },
  { "id": "mesOrderUpdated", "roles": ["operator", "admin"] }
]
```

- Deployed into `application_event_catalog` and listed via `GET /api/v1/applications/{appId}/events`.
- Validated at publish time when `POST /api/v1/events/fire?appId={appId}` is called.

**Related guides:** [Solution developer public API](solution-developer-public-api.md) · [Automation](automation.md) · [Federation](federation.md) · [Applications](applications.md).
