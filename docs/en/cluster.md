> **Language:** Canonical English. Russian edition: [ru/cluster.md](../ru/cluster.md).

# ISPF cluster (multi-replica)

Guide to horizontal API scaling: multiple JVM replicas, one object tree in PostgreSQL, live value synchronization via NATS ([ADR-0029](decisions/0029-cluster-live-variable-replica-sync.md)).

See also: [ADR-0028](decisions/0028-horizontal-active-active-cluster.md), [deployment.md](deployment.md), [messaging.md](messaging.md), [bindings.md](bindings.md).

## Cluster ≠ federation

| | **Cluster** | **Federation** |
|---|-------------|------------------|
| Object tree | Single `root.platform.*` in one DB | Multiple sites / edge agents |
| Replicas | N stateless JVMs behind LB | Hub ↔ spoke communication |
| Driver | Exactly one poll per device | See [federation.md](federation.md) |

## Topology (VPS / lab example)

```text
                    nginx :8080
           REST ip_hash  │  WS ip_hash (one replica per client)
        ┌──────────┬───────┴───────┬──────────┐
        ▼          ▼               ▼          │
   replica-1   replica-2      replica-3      │
   :8081       :8082           :8083          │
        └──────────┴───────┬───┴──────────────┘
                           │
              PostgreSQL (one tree)
              NATS (fan-out between replicas)
              Redis (path interest, ACL, correlator)
```

Compose: [`deploy/docker-compose.cluster.yml`](../deploy/docker-compose.cluster.yml); VPS: [`deploy/docker-compose.vps-cluster.yml`](../deploy/docker-compose.vps-cluster.yml).

Each replica on startup:

1. Flyway migrations (once per DB).
2. `loadFromDatabase()` — identical tree on all nodes.
3. Registration in `platform_cluster_replicas` + heartbeat.
4. Acquire `platform_driver_locks` for devices assigned to this node.

## Where data lives

| Data | Storage | Cluster behavior |
|------|---------|------------------|
| Object structure, configs, bindings | PostgreSQL | Write on any replica → NATS fan-out → reload on peers ([ADR-0030](decisions/0030-cluster-config-structure-replica-sync.md): `reloadPathFromDatabase`, config variables: `syncVariableFromDatabase`) |
| **Real-time telemetry** (`ifInOctets`, `temperature`, …) | **RAM on owner replica** | Not written to PG on every tick |
| **Live mirror on subscriber** | RAM (copy snapshot) | ADR-0029: NATS payload includes `value` |
| Historian / event journal | PG / ClickHouse / Cassandra | Written **only by owner** |
| Bindings / alerts / cascading functions | RAM + automation pipeline | Executed **only on owner** |

### Driver ownership

Exactly one replica polls each DEVICE (`platform_driver_locks`, TTL + refresh). When a node fails, its lock expires and another replica takes the device.

Check: `GET /api/v1/platform/cluster/health` (admin) — `heldDevicePaths` per node.

## Bindings, variables, functions, dashboards

### Variables

- **Raw telemetry** arrives from the driver on the owner → `setDriverTelemetryValue()` → RAM.
- **REST** `GET /api/v1/objects/{path}/variables` reads **local RAM** of the replica that served the request.
- **WebSocket** `/ws/objects` — pushes `VARIABLE_UPDATED` to clients subscribed to the path.

Before ADR-0029, follower RAM for telemetry was empty → round-robin REST could return `null` / stale values.

### Bindings

**Local** (on the same DEVICE):

```cel
counterRate(ifInOctets)   → variable ifInOctetsRate
```

**Cross-object** (on a hub object):

```cel
refAt("root.platform.devices.snmp-router-01", ifInOctetsRate)   → routerNetDown
```

Chain on the **owner** replica where the source device lives:

```text
SNMP poll → ifInOctets (RAM)
         → binding counterRate → ifInOctetsRate (RAM)
         → (if hub on same owner or ref reads owner RAM) → derived vars
```

Followers **do not recompute** bindings — they receive computed values via the NATS mirror (NATS events without automation).

### Functions

`INVOKE_FUNCTION`, script handlers, platform functions — run on the replica that accepted the HTTP request. For side effects, design for idempotency. Real-time device reads work from any replica after ADR-0029.

### Dashboards

A dashboard is a `DASHBOARD` object with layout JSON. Widgets reference `objectPath` / bindings. HMI flow:

1. WS subscribes to table/chart paths.
2. Receive `VARIABLE_UPDATED` (local or after NATS mirror).
3. Occasional REST refetch — should hit a replica with current RAM (after ADR-0029 — any replica).

## ADR-0029: live variable replica sync

### Problem (before 0029)

| Mechanism | Gap |
|-----------|-----|
| NATS `ispf.events.*` | Only `path` + `variableName`, **no value** |
| `NatsEventBridge` | Skipped `telemetry=true` |
| REST vs WS LB | `ip_hash` on REST and WS — one client (IP) → one JVM; failover via `max_fails` + `proxy_next_upstream` |
| WS path interest | Local to one JVM — owner did not know about subscribers on other replicas |

### Solution

```text
Owner (driver)
  → RAM update
  → automation (bindings, alerts, historian) — only here
  → ClusterLiveVariableReplicaPublisher (coalesced NATS + full DataRecord)

Follower
  → ClusterVariableReplicaApplier → RAM mirror
  → ObjectChangeEvent(replicaIngress=true) → WS push
  → REST GET /variables — fresh value
```

```mermaid
flowchart TB
    subgraph owner [Driver owner replica]
        DRV[Driver poll / MQTT]
        RAM1[RAM update]
        AUTO[Bindings / alerts / historian]
        INT[Redis path interest]
        PUB[ClusterLiveVariableReplicaPublisher]
        DRV --> RAM1 --> AUTO
        RAM1 --> PUB
        INT --> PUB
    end
    subgraph bus [NATS]
        NATS["ispf.events.variable_updated + value"]
    end
    subgraph follower [Any other replica]
        SUB[NatsObjectChangeSubscriber]
        RAM2[RAM mirror apply]
        WS[WebSocket push]
        REST[GET /variables]
        SUB --> RAM2 --> WS
        RAM2 --> REST
    end
    PUB --> NATS --> SUB
```

### replicaIngress

Events with `replicaIngress=true` on followers:

| Consumer | Behavior |
|----------|----------|
| NATS / `ClusterLiveVariableReplicaPublisher` | Skip (no loop) |
| Bindings / historian / alerts | Skip |
| WebSocket | Push to clients |

### Cluster-wide path interest (Redis)

When `ispf.cluster.cluster-path-interest-enabled=true` and Redis:

- WS `subscribe` / `unsubscribe` updates ref-count in Redis (`ispf:cluster:ws:interest:{path}`).
- Owner publishes NATS sync even if all browsers are on other replicas.

Without Redis — local interest only; pin REST+WS to the same replica or enable Redis.

## Example: SNMP fleet monitoring (3 replicas)

### Objects

| Path | Type | Role |
|------|------|------|
| `root.platform.devices.snmp-router-01` | DEVICE | SNMP router, model `snmp-agent-v1` |
| `root.platform.devices.snmp-switch-02` | DEVICE | SNMP switch |
| `root.platform.devices.snmp-fleet.hub` | CUSTOM | Cross-object aggregator |
| `root.platform.dashboards.snmp-host-monitoring` | DASHBOARD | btop table + charts |

### Bindings

On each DEVICE (local):

```json
{
  "targetVariable": "ifInOctetsRate",
  "expression": "counterRate(ifInOctets)"
}
```

On the hub (cross-object):

```json
{
  "targetVariable": "routerNetDown",
  "expression": "refAt(\"root.platform.devices.snmp-router-01\", ifInOctetsRate)"
}
```

```json
{
  "targetVariable": "totalNetDown",
  "expression": "routerNetDown + switchNetDown"
}
```

### Step-by-step scenario

**T0 — startup:** R1, R2, R3 load the same tree from PG. R1 acquires lock on `snmp-router-01`, R2 on `snmp-switch-02`.

**T1 — operator opens HMI:** browser → nginx → WS on R3 (`ip_hash`), subscribe to dashboard paths. Redis records global interest → owners R1/R2 start publishing updates.

**T2 — SNMP poll on R1:** `ifInOctets` updates → `counterRate` binding → `ifInOctetsRate`. Demand-driven: interest present → `ObjectChangeEvent` → coalesced NATS with full `value`.

**T3 — REST refresh on R2:** `GET .../snmp-router-01/variables/ifInOctetsRate` — follower already applied NATS snapshot → current value (no sticky REST required).

**T4 — hub `totalNetDown`:** computed on the hub-object owner (or source-device owner). Derived value also goes to NATS → all replicas show the same value on the dashboard.

**T5 — R1 failure:** lock expires → R2/R3 redistribute device; brief telemetry gap until recovery; structure and config unchanged (PG).

## Configuration

### Required (each replica)

```bash
# /opt/ispf/ispf-server.env
ISPF_CLUSTER_ENABLED=true
ISPF_REPLICA_ID=replica-1          # unique per node
ISPF_DB_URL=jdbc:postgresql://postgres:5432/ispf
ISPF_NATS_ENABLED=true
ISPF_NATS_REPLICA_EVENTS=true
ISPF_REDIS_ENABLED=true
ISPF_CLUSTER_LIVE_VARIABLE_SYNC=true
ISPF_CLUSTER_PATH_INTEREST=true
```

### Coalescing: two separate knobs

| Property | Environment | Default | Where it applies |
|----------|-------------|---------|------------------|
| `ispf.runtime-telemetry.coalesce-ms` | `ISPF_RUNTIME_TELEMETRY_COALESCE_MS` | **250** | Owner ingress: merge driver ticks |
| `ispf.cluster.live-variable-sync-coalesce-ms` | `ISPF_CLUSTER_LIVE_VARIABLE_SYNC_COALESCE_MS` | **500** | NATS fan-out owner → followers |

**Why separate:** Telemetry coalescing optimizes owner CPU/automation; cluster coalescing optimizes **inter-replica NATS traffic**. For HMI, 500–1000 ms on the cluster is often enough while keeping 250 ms on ingress.

Example: aggressive ingress + economical NATS:

```bash
ISPF_RUNTIME_TELEMETRY_COALESCE_MS=250
ISPF_CLUSTER_LIVE_VARIABLE_SYNC_COALESCE_MS=1000
```

Per-device override (ingress only, **not** NATS):

```json
{
  "host": "192.168.1.1",
  "community": "public",
  "telemetryCoalesceMs": 1000
}
```

### Runtime settings UI

Admin → Platform → Runtime settings → **Cluster** section:

- `cluster.live-variable-sync-coalesce-ms` — hot-reloadable
- `cluster.live-variable-sync`, `cluster.path-interest`, driver lock TTL

### Disabling live sync (debug)

```bash
ISPF_CLUSTER_LIVE_VARIABLE_SYNC=false
```

Followers lose RAM mirror again; sticky REST+WS session or read only from owner required.

## Load and tuning

### Demand-driven (ADR-0024)

NATS sync only when subscribers exist: historian, bindings, alerts, UI (local or Redis global interest). “Dead” telemetry with no history and no open dashboard — **0 NATS**.

### Message rate estimate

```text
NATS_msg_per_sec ≈ (N_variables_with_interest × replicas_followers) / cluster_coalesce_ms × 1000
```

Example: 200 paths on screen, 2 followers, coalesce 500 ms:

```text
200 × 2 / 0.5 ≈ 800 msg/s  (worst case, all vars change every coalesce window)
```

In practice counterRate/SNMP changes less often; coalescing and last-value wins strongly reduce peaks.

### When to worry

- More than 10k historian variables with interest and coalesce &lt; 250 ms.
- Very large `DataRecord` (wide tables) in every NATS message.

Mitigation: increase `ISPF_CLUSTER_LIVE_VARIABLE_SYNC_COALESCE_MS`, narrow history flags, check JetStream and NATS core.

## Replica profiles and platform jobs (ADR-0031 / ADR-0032)

Default **unified** (`ISPF_REPLICA_PROFILE=unified` or `ISPF_REPLICA_ROLE=all`): full stack on one JVM.

### Profiles (ADR-0032)

| Profile | Environment | API/WS | Config write | Drivers | Jobs | Schedulers | Analytics |
|---------|-------------|--------|--------------|---------|------|------------|-----------|
| unified | `ISPF_REPLICA_PROFILE=unified` | yes | yes | yes | yes | yes | yes |
| edge-api | `edge-api` (alias: `api`) | yes | yes | no | no | yes | no |
| hmi-read | `hmi-read` | yes | no | no | no | no | no |
| io | `io` | no | no | yes | no | yes | no |
| compute | `compute` (alias: `worker`) | internal | no | no | yes | no | no |
| analytics | `analytics` | internal | no | no | no | yes | yes |

**analytics** — rollup materializer and heavy historian backfill ([ADR-0038](decisions/0038-analytics-platform-architecture.md), BL-207). When UP analytics replicas exist, `io` and `edge-api` replicas do **not** run the materializer. Single-node `unified` still runs analytics workloads.

**edge-api** without local drivers — see [demostands.md](demostands.md) (Edge B). Local drivers on a weak CPU — **unified** + [demostands.md](demostands.md) (Edge A).

Explicit override: `ISPF_REPLICA_CAPABILITIES=http-public,ws,replica-sync`.

```bash
# edge tier (behind nginx)
ISPF_REPLICA_PROFILE=edge-api

# driver I/O (internal, not in LB)
ISPF_REPLICA_PROFILE=io

# async reports worker
ISPF_REPLICA_PROFILE=compute
ISPF_CLUSTER_JOB_MAX_CONCURRENT=4

# analytics engine (rollup materializer, heavy backfill)
ISPF_REPLICA_PROFILE=analytics
ISPF_ANALYTICS_MATERIALIZER_ENABLED=true
```

### Async reports

```http
POST /api/v1/reports/by-path/run-async?path=root.platform.reports.daily
→ 202 { "jobId": "…", "status": "QUEUED" }

GET /api/v1/platform/jobs/{jobId}
→ { "status": "COMPLETED", "result": { … same as sync run … } }
```

Web console calls `run-async` and polls until `COMPLETED`. Sync `POST …/run` retained for tests.

Job storage in `platform_jobs` (PostgreSQL). Worker claim: `FOR UPDATE SKIP LOCKED`. Stale `RUNNING` returns to `QUEUED`.

Details: [ADR-0031](decisions/0031-cluster-replica-roles-platform-jobs.md), [ADR-0032](decisions/0032-replica-profiles-and-capabilities.md).

### VPS prod (single unified node)

> **Profiles:** production / throughput / demo-simple / edge — [demostands.md](demostands.md). Below — **demo-idle** example for one node.

```text
Internet → nginx :8080 → replica-1 (unified / role all, :8081)
```

`ISPF_CLUSTER_ENABLED=false` — one JVM with all capabilities (drivers + jobs + HTTP/WS). ADR-0032 disallows `unified` when `cluster.enabled=true`.

| Artifact | Path |
|----------|------|
| Compose | [`deploy/docker-compose.vps-single.yml`](../deploy/docker-compose.vps-single.yml) |
| Nginx | [`deploy/nginx-vps-single.conf`](../deploy/nginx-vps-single.conf) |
| Rollout | [`deploy/vps-single-rollout.sh`](../deploy/vps-single-rollout.sh) |
| Prod-idle env | [`deploy/ispf-server.prod-idle.env`](../deploy/ispf-server.prod-idle.env) + [`vps-apply-prod-idle-env.sh`](../deploy/vps-apply-prod-idle-env.sh) |
| Driver tuning | [`deploy/vps-demostand-tune-drivers.sh`](../deploy/vps-demostand-tune-drivers.sh) |

Verify: `curl -sf https://ispf.iot-solutions.ru/api/v1/info` → `clusterEnabled=false`, `replicaRole=all`.

**Multi-replica** (lab / HA): [`deploy/docker-compose.vps-cluster.yml`](../deploy/docker-compose.vps-cluster.yml), `vps-cluster-rollout.sh`, `vps-cluster-verify.sh`.

## Operations

### Health API

```http
GET /api/v1/platform/cluster/health
GET /api/v1/platform/cluster/diagnostics
Authorization: Bearer …
```

Health response: `liveVariableSyncEnabled`, `liveVariableSyncCoalesceMs`, `clusterPathInterestEnabled`, node list, driver locks.

Diagnostics response: CPU per replica, `clusterTopSuspect`, drill-down (threads, driver bindings, jobs, workflows).

**Drill-down (expand nodes):**

| Block | Fields |
|-------|--------|
| Suspects | `kind` (subsystem/driver/thread/job/workflow), `severity`, `score` |
| Thread groups | `ispf-driver-io`, `driver-ingress`, `object-change`, …; CPU Δ over ~20s window |
| Drivers | `ingressPending`, `pressureScore` (≥100 — hot driver) |
| Jobs / workflows | `RUNNING` on this replica, `runningSeconds` |

UI: Admin → System → Metrics → **Load diagnostics** (CPU) and Cluster card (health). Optional: **Sync metrics with audit device** checkbox — audit runtime in the tree (see [observability.md](observability.md)); disabled when leaving the page.

At 100% CPU: expand the hot replica in diagnostics; first thread sample CPU is warmup (refresh ~20s); if all JVMs are low — `docker stats` on host (Scylla/CH/Postgres).

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8083/api/v1/platform/metrics | jq '.diagnostics'
```

### Smoke / CI

```bash
bash deploy/cluster-smoke-test.sh
bash deploy/cluster-smoke-test.sh --config-sync
python deploy/cluster-scale-load-test.py --scale-factor-floor 1.8
```

### Failover checklist

1. `curl https://ispf.example/api/v1/info` — 200 from any live replica.
2. Stop one replica — REST through LB without 502.
3. Driver locks migrate within TTL + failover scan.
4. HMI on other replicas keeps receiving current values (ADR-0029).

### nginx

REST and WS **may** use different LB policies when ADR-0029 is enabled — values are mirrored on all JVMs.

REST and WS share one upstream with `ip_hash` — lower cross-replica NATS percentage, stable operator session.

When a replica fails, nginx marks upstream down (`max_fails`) and routes the client elsewhere; after deploy all replicas restart with the new build — Ctrl+F5 is enough.

## Troubleshooting (desync)

| Symptom | Likely cause | Action |
|---------|--------------|--------|
| Object deleted but visible again in tree | Follower RAM missed `DELETED` (before v0.9.93 — demand-gated valve) | Upgrade to 0.9.93+; Ctrl+F5; **do not** factory reset |
| Mimic/diagram empty after save | Config not synced to follower | 0.9.92+ fix; run `bash deploy/vps-cluster-verify.sh --config-sync` |
| Different REST values on refresh | Round-robin before ADR-0029 for telemetry | Ensure `liveVariableSyncEnabled=true` in cluster health |
| “Everything broken” after experiments | Accumulated RAM drift | `bash /opt/ispf/bin/vps-cluster-factory-reset.sh --no-fixtures` (prod) |

### VPS deployment

**Single-node demostand (current prod):** see [vps-demostand.md](vps-demostand.md). Hotfix path: SCP + `docker-compose` recreate, not `apply-platform-update.sh`.

**Multi-replica cluster:**

```powershell
.\deploy\vps-deploy-direct.ps1 -Version 0.9.93 -SkipTests -Cluster
```

Deploy uses [`vps-cluster-rollout.sh`](../deploy/vps-cluster-rollout.sh) (rolling replica restart, no `docker-compose --force-recreate`).

Initial cluster install: [`vps-cluster-bootstrap.sh`](../deploy/vps-cluster-bootstrap.sh).

Reset DB: [`vps-cluster-factory-reset.sh`](../deploy/vps-cluster-factory-reset.sh).

## Related ADRs and backlog

- [ADR-0028](decisions/0028-horizontal-active-active-cluster.md) — topology, driver locks
- [ADR-0029](decisions/0029-cluster-live-variable-replica-sync.md) — RAM mirror
- [ADR-0030](decisions/0030-cluster-config-structure-replica-sync.md) — config/structure CRUD sync
- [ADR-0024](decisions/0024-demand-driven-variable-change-pubsub.md) — demand-driven publish
- ROADMAP BL-134…143
