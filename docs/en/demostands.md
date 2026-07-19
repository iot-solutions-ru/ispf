> **Language:** Canonical English. Russian edition: [ru/demostands.md](../ru/demostands.md).

# ISPF deployment profiles

> **Status:** Lab — Prod, lab, edge topologies. Hub: [doc-status.md](doc-status.md).

Guide to choosing a configuration based on your goal: **industrial production**, **high throughput**, **demo/HMI**, or **edge with limited CPU**.

Not tied to a specific host. Example scripts and env files are in [`deploy/`](../../deploy/).

See also: [deployment](deployment.md), [security](security.md), [cluster](cluster.md), [load-testing](load-testing.md), [observability](observability.md), [release-dogfood](release-dogfood.md), [operator-apps](operator-apps.md), [0026-elastic-telemetry-ingress](decisions/0026-elastic-telemetry-ingress.md), [0033-prod-idle-demostand-tuning](decisions/0033-prod-idle-demostand-tuning.md).

## How to choose a profile

| Profile | Typical goal | Devices / msg/s | CPU / RAM | Topology |
|---------|--------------|-------------------|-----------|----------|
| **Production** | Site operations, SLA, 24/7 operators | tens–hundreds of devices, moderate–high flow | 4+ vCPU per JVM; cluster for HA | 1–N replicas, elastic by load, PG + TS/CH |
| **Throughput** | Load testing, benchmark, capacity planning | hundreds–thousands msg/s | 4+ vCPU, 8+ GB per JVM | 1–N replicas, elastic **on**, CH/Scylla |
| **Demo / idle** | Public demostand, training lab | single digits–tens, low poll rate | 2–4 vCPU, **one** JVM | single node, elastic **off** |
| **Edge** | On-site gateway, weak CPU | 1–5 devices | 1–2 cores, 512 MB–2 GB | single node; drivers local or API to hub |

```text
  Operations, SLA  ──► Production     cluster?, elastic ON, PG+TS/CH, RBAC
  Benchmark        ──► Throughput      elastic ON, peak tuning
  Few devices      ──► Demo / idle     elastic OFF, prod-idle.env
  Low CPU          ──► Edge            minimal pools, coalesce↑
```

**Key rule:** defaults ([0026-elastic-telemetry-ingress](decisions/0026-elastic-telemetry-ingress.md)) target **production throughput** and load testing. The **demo-idle** profile (`prod-idle.env`) **does not** replace production under real load. For production with low background load on a single JVM, you may deliberately trim pools, but do not copy the demostand overlay without analyzing queue behavior.

---

## Profile: Production (industrial operations)

**When:** live site, MES/SCADA, permanent operators, availability and audit requirements.

Unlike **demo-idle:** runs **real** automation (`FULL` on devices that need it), persistence, backups, monitoring, and (when required) high availability. Unlike **throughput testing:** production balances latency, cost, and resilience rather than maximizing events/s.

### Scale sub-variants

| Sub-variant | Devices | Topology | Journal / historian |
|-------------|---------|----------|---------------------|
| **Prod S** — one site, one node | < ~50 active drivers, moderate polling | `ISPF_CLUSTER_ENABLED=false`, `replicaRole=all` | `jdbc` + Timescale (PG image by default) |
| **Prod M** — HA, horizontal API | 50–500+, operators, REST/WS failover | 2–4 replicas, nginx, NATS + Redis ([cluster](cluster.md)) | Timescale or ClickHouse journal as events grow |
| **Prod L** — high-flow telemetry | sustained hundreds–thousands msg/s | Cluster + dedicated `io` / `compute` replicas ([0032-replica-profiles-and-capabilities](decisions/0032-replica-profiles-and-capabilities.md)) | ClickHouse / Scylla ([0016-clickhouse-event-journal](decisions/0016-clickhouse-event-journal.md), [0025-cassandra-scylla-timeseries-store](decisions/0025-cassandra-scylla-timeseries-store.md)) |

Sizing rule: **no more than 1 JVM per 2 vCPU** under sustained driver+automation load. Four replicas on four vCPUs only if most of the time is idle.

### Topology and roles

**Single site (Prod S):**

```text
Operators → nginx (TLS) → ISPF unified (:8081)
              PostgreSQL (Timescale), Redis (optional)
```

Compose example: [`deploy/docker-compose.prod-stack.yml`](../../deploy/docker-compose.prod-stack.yml). Remote install: [`deploy/remote-setup-ispf.sh`](../../deploy/remote-setup-ispf.sh).

**High-availability cluster (Prod M/L):**

```text
Operators → nginx (ip_hash / health) → replica-1..N
              PostgreSQL, Redis, NATS (JetStream)
              optional: ClickHouse, Scylla
```

See [cluster](cluster.md), [`deploy/docker-compose.vps-cluster.yml`](../../deploy/docker-compose.vps-cluster.yml), rollout [`vps-cluster-rollout.sh`](../../deploy/vps-cluster-rollout.sh).

**Analytics scale-out (Prod L + ClickHouse):** add internal `analytics` replicas so driver `io` nodes stay free of rollup materializer CPU. Lab stack: [`deploy/docker-compose.analytics.yml`](../../deploy/docker-compose.analytics.yml). Helm: `analytics.enabled=true`, `analytics.replicaCount`, optional `analytics.affinity` to pin pods near ClickHouse.

| `ISPF_REPLICA_PROFILE` | When |
|------------------------|------|
| `unified` / `all` | General-purpose node (small prod) |
| `io` | Dedicated driver I/O in a cluster |
| `compute` | Async reports / platform jobs |
| `analytics` | Rollup materializer, heavy historian backfill ([analytics-platform-roadmap](analytics-platform-roadmap.md) BL-207) |
| `edge-api` | Remote site without local drivers ([federation](federation.md)) |

### Elastic pools and pipeline

In production, **keep elastic enabled** (default) when you have:

- MQTT/push ingress with bursts;
- many binding/alert rules on `FULL` devices;
- rising `objectChangeQueueSize` / `eventJournalQueueSize` at peak.

Start with defaults from `application.yml`; if lag persists, increase max workers and queue capacity ([`deploy/vps-event-journal-peak-tuning.sh`](../../deploy/vps-event-journal-peak-tuning.sh) as a journal tuning example).

**Do not apply** [`ispf-server.prod-idle.env`](../../deploy/ispf-server.prod-idle.env) on production with live automation visualization — that is a **demo-idle** profile, not working production.

Targeted trimming (single node, consistently low queues): pin workers via env, but verify metrics after every change.

### Drivers and publish mode

| Scenario | Mode | Notes |
|----------|------|-------|
| PLC/Modbus/SNMP, many points, historian | `TELEMETRY_ONLY` | Coalesce per SLA (1–5s) |
| Alerts, bindings, workflows on device | `FULL` | Only where automation is needed |
| High event flow without CEL | `EVENT_JOURNAL_ONLY` | [0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md) |
| Device in tree but not required | `STOPPED` | Do not keep RUNNING "just in case" |

Drivers: maturity varies by `driverId` — see honesty note in [drivers](drivers.md) (registry **PRODUCTION** ≠ ready-for-field for all IDs) and [0022-driver-production-matrix](decisions/0022-driver-production-matrix.md).

### Security and compliance

| Parameter | Production |
|-----------|------------|
| `ISPF_BOOTSTRAP_FIXTURES_ENABLED` | **false** |
| Authentication | OIDC/RBAC ([security](security.md)), not default `admin/admin` on the network |
| TLS | nginx/ingress terminates HTTPS |
| Actuator | `/actuator/*` not public; Prometheus — admin role |
| AI mutate tools | `ispf.ai.agent-require-approval-for-mutate=true` on prod |
| Secrets | `/opt/ispf/ispf-server.env` chmod 600, not in git |
| Driver packs | `permissive` deploy profile ([license-compliance](license-compliance.md)) |

### Durability and retention

| Data | Prod S/M | Prod L |
|------|----------|--------|
| Configuration, ACL, objects | PostgreSQL | PostgreSQL |
| Variable history | Timescale `variable_samples` (jdbc) | ClickHouse / Scylla optional |
| Event journal | Timescale `event_history` (jdbc) | ClickHouse (rule set in [deployment](deployment.md)) |
| Retention | `ISPF_*_RETENTION_DAYS`, Timescale policy ([0009-timescaledb-retention](decisions/0009-timescaledb-retention.md)) | + CH archive |

Flyway path: migrations on startup; backup DB before upgrade. Repair: [`deploy/vps-flyway-repair.sh`](../../deploy/vps-flyway-repair.sh).

### Observability and operations

- **Metrics:** `/actuator/prometheus` or OTLP ([observability](observability.md)).
- **Diagnostics:** Admin → System → Metrics; during incidents — `GET /api/v1/platform/metrics`, cluster diagnostics.
- **Metrics probe:** only during events (runtime toggle), not on product startup.
- **Backup:** regular `pg_dump`; verify restore.
- **Deploy:** staged jar + UI, rolling replica restart ([`vps-deploy-direct.ps1`](../../deploy/vps-deploy-direct.ps1)); in Docker — **recreate** when env changes.
- **Do not** factory-reset on config desync — [0030-cluster-config-structure-replica-sync](decisions/0030-cluster-config-structure-replica-sync.md), `vps-cluster-verify.sh --config-sync`.

### Env reference (prod, not idle)

```properties
# Identity
ISPF_ENVIRONMENT=prod
ISPF_BOOTSTRAP_FIXTURES_ENABLED=false
ISPF_PLATFORM_METRICS_PROBE_ENABLED=false

# Cluster (Prod M/L)
ISPF_CLUSTER_ENABLED=true
ISPF_NATS_ENABLED=true
ISPF_REDIS_ENABLED=true

# Elastic — defaults (do not set false without justification)
# ISPF_OBJECT_CHANGE_ELASTIC_WORKERS=true  # default

# JVM (tune by heap % and GC)
JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC

# Historian/journal — by scale (Prod S)
ISPF_VARIABLE_HISTORY_STORE=jdbc
ISPF_EVENT_JOURNAL_STORE=jdbc
```

Prod L: add `ISPF_EVENT_JOURNAL_STORE=clickhouse`, `ISPF_VARIABLE_HISTORY_STORE=clickhouse` and URLs/credentials — see [deployment.md § ClickHouse](deployment.md).

### Production health checks

1. `GET /api/v1/info` — expected version, `environment=prod`.
2. Cluster state (if enabled): all replicas UP, driver locks consistent.
3. Pipeline queues ≈ 0 in steady state; brief spikes are acceptable.
4. No repeated ERROR/read-only transaction messages in logs.
5. Journal/historian writes (verify in UI or SQL).
6. TLS, login, RBAC work from operator workstations.

### Common production mistakes

| Mistake | Consequence |
|---------|-------------|
| `prod-idle.env` on a site with hundreds of devices | Lag, latency, event loss at peak |
| 4 replicas on 4 vCPUs with no headroom | Sustained high load, GC pressure |
| All drivers `FULL` | Extra CPU, object-change storm |
| `docker restart` after env change | Old config still in container |
| Factory reset on desync | Data loss instead of deploying a fix |

---

## Shared principles (all profiles)

### Driver publish mode (`telemetryPublishMode`)

| Mode | Pipeline | CPU |
|------|----------|-----|
| `TELEMETRY_ONLY` | RAM + historian (when history enabled) | Low |
| `EVENT_JOURNAL_ONLY` | Async event journal ([0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md)) | Low–medium |
| `FULL` | Object-change → bindings → alerts → workflows | High |

Polled drivers with many points — **`TELEMETRY_ONLY`** + coalesce. **`FULL`** — only where automation is required.

### Coalescing

- **Per-device:** `telemetryCoalesceMs` in configure driver API.
- **Platform:** `ISPF_RUNTIME_TELEMETRY_COALESCE_MS`, ingress queue coalesce (L3).

### Read-only hot paths

Methods with `@Transactional(readOnly = true)` on hot ticks **must not** include INSERT/UPDATE. See [0033-prod-idle-demostand-tuning](decisions/0033-prod-idle-demostand-tuning.md).

### Restart and env (Docker)

`docker restart` **does not** re-read `env_file`. After env changes — **recreate** the container.

### Stopping drivers

In demos, do not fix CPU by mass STOP. In production — RUNNING only for devices that are actually polled.

---

## Profile: Throughput (high load / test)

**When:** load tests, MQTT flood, capacity planning. Close to **Prod L**, but the goal is the limit, not SLA.

### Topology

- One JVM with sufficient CPU **or** [cluster](cluster.md) — with enough CPU per JVM.
- Journal: ClickHouse or Scylla. Historian: CH/Cassandra at high sample rates.

### Elastic pools — **enabled** (default)

| Level | Component | Typical range |
|-------|-----------|---------------|
| L0–L1 | Driver ingress buffer, MQTT callback | 4→32 |
| L3 | `TelemetryIngressDispatcher` | 4→32 |
| L5 | Event journal / variable history writers | 4→32 |

Peak tuning: [`deploy/vps-event-journal-peak-tuning.sh`](../../deploy/vps-event-journal-peak-tuning.sh). See [load-testing](load-testing.md).

---

## Profile: Demo / idle (low load)

**When:** public demostand, training lab, always-on HMI with a few simulated devices, low background CPU.

### Topology

- **One unified JVM:** `ISPF_CLUSTER_ENABLED=false`, `ISPF_REPLICA_ROLE=all`.
- PostgreSQL (+ Redis for ACL/correlator) is enough; Scylla/ClickHouse **not required**.
- `ISPF_BOOTSTRAP_FIXTURES_ENABLED=false` on external prod (fixtures — for lab).

### Elastic pools — **disabled**, fixed size

With `ISPF_*_ELASTIC=false`, Spring uses **fixed** `*_WORKERS` / `*_THREADS`, not min/max:

| Subsystem | Example env |
|-----------|-------------|
| Object-change telemetry / automation | `ISPF_OBJECT_CHANGE_TELEMETRY_WORKERS=2`, `ISPF_OBJECT_CHANGE_AUTOMATION_WORKERS=2` |
| Binding async | `ISPF_BINDING_ASYNC_THREADS=2` |
| L3 ingress | `ISPF_RUNTIME_TELEMETRY_INGRESS_ELASTIC_WORKERS=false`, min/max 1–2 |
| Driver I/O / ingress buffer | `ISPF_DRIVER_IO_THREADS=2`, `ISPF_DRIVER_INGRESS_BUFFER_THREADS=1` |

Ready overlay: [`deploy/ispf-server.prod-idle.env`](../../deploy/ispf-server.prod-idle.env). Apply (merge + **recreate** container): [`deploy/vps-apply-prod-idle-env.sh`](../../deploy/vps-apply-prod-idle-env.sh).

Additional idle settings:

```properties
ISPF_VARIABLE_HISTORY_STORE=jdbc
ISPF_EVENT_JOURNAL_STORE=jdbc
ISPF_EVENT_JOURNAL_ELASTIC_WRITER=false
ISPF_VARIABLE_HISTORY_ELASTIC_WRITER_ENABLED=false
ISPF_PLATFORM_METRICS_PROBE_ENABLED=false
ISPF_CLUSTER_JOB_CONSUMER_ENABLED=false
ISPF_RUNTIME_TELEMETRY_COALESCE_MS=1000
SERVER_TOMCAT_THREADS_MAX=50
```

### Drivers

| Type | Recommendation |
|------|----------------|
| SNMP / Modbus with many points | `TELEMETRY_ONLY`, poll ≥ 5s, `telemetryCoalesceMs` = poll |
| 1–2 devices for alerts/workflow | `FULL`, moderate poll (2–5s) |
| Other objects in tree | `STOPPED`, not RUNNING |

Example API tuning script: [`deploy/vps-demostand-tune-drivers.sh`](../../deploy/vps-demostand-tune-drivers.sh) (template — substitute your `devicePath` values).

### Diagnostics

- UI: Admin → System → Metrics → **Load diagnostics**.
- CLI: [`deploy/vps-idle-thread-sample.py`](../../deploy/vps-idle-thread-sample.py).

**Healthy idle:** object-change/journal/historian queue = 0; `pressureScore` < 25; `binding-async` and `telemetry-ingress` threads — single digits, not tens.

### Common mistakes

| Symptom | Cause |
|---------|-------|
| High CPU with 2 devices | Throughput defaults + SNMP `FULL` |
| Env not applied | `docker restart` instead of recreate |
| 4 JVMs on a small VPS | Cluster without CPU headroom |

---

## Profile: Edge (limited CPU)

**When:** industrial gateway (ARM, 1–2 cores, 512 MB–1 GB RAM), local collection from a few devices **or** thin API node to a central hub.

Two sub-variants:

### A. Edge gateway — local drivers

ISPF on the device polls PLCs/sensors and serves HMI or syncs to a hub ([federation](federation.md)).

| Parameter | Recommendation |
|-----------|----------------|
| Role | `ISPF_REPLICA_ROLE=all` (drivers required) |
| Cluster | `ISPF_CLUSTER_ENABLED=false` |
| JVM heap | `-Xms128m -Xmx256m` … `-Xmx512m` (tune via `docker stats` / heap %) |
| Elastic | **all** `ISPF_*_ELASTIC=false` |
| Thread pools | **1** (critical minimum) or **2** (if 2 cores) — see idle env, trim further if needed |
| Tomcat | `SERVER_TOMCAT_THREADS_MAX=20` … `30` |
| Historian | `jdbc`; `ISPF_VARIABLE_HISTORY_MIN_INTERVAL_MS=10000`+; writers=1 |
| Journal | `jdbc`; writers=1; `ISPF_EVENT_JOURNAL_ELASTIC_WRITER=false` |
| Coalesce | `ISPF_RUNTIME_TELEMETRY_COALESCE_MS=2000`–`5000`; per-device coalesce = poll |
| Poll interval | ≥ 5 s (10–60 s for slow signals) |
| Publish mode | **`TELEMETRY_ONLY`** or **`EVENT_JOURNAL_ONLY`**; avoid `FULL` |
| Active drivers | Only what is actually needed (1–5); everything else STOPPED |
| Metrics probe | `false` |
| Job consumer | `ISPF_CLUSTER_JOB_CONSUMER_ENABLED=false` |
| AI/OTLP | disabled |
| Redis | `ISPF_REDIS_ENABLED=false` unless correlator windows / cluster ACL needed |
| NATS | `ISPF_NATS_ENABLED=false` |
| Fixtures bootstrap | `ISPF_BOOTSTRAP_FIXTURES_ENABLED=false` |
| Process | **systemd** preferred over Docker on weak ARM (lower overhead) |

Minimal env fragment (idle baseline, even more aggressive):

```properties
ISPF_OBJECT_CHANGE_TELEMETRY_WORKERS=1
ISPF_OBJECT_CHANGE_AUTOMATION_WORKERS=1
ISPF_BINDING_ASYNC_THREADS=1
ISPF_RUNTIME_TELEMETRY_INGRESS_WORKERS_MIN=1
ISPF_RUNTIME_TELEMETRY_INGRESS_WORKERS_MAX=1
ISPF_DRIVER_IO_THREADS=1
ISPF_DRIVER_SCHEDULER_THREADS=1
ISPF_DRIVER_INGRESS_BUFFER_THREADS=1
ISPF_VARIABLE_HISTORY_WRITER_THREADS=1
ISPF_EVENT_JOURNAL_WRITER_THREADS=1
ISPF_RUNTIME_TELEMETRY_COALESCE_MS=5000
JAVA_OPTS=-Xms128m -Xmx256m -XX:+UseG1GC
```

**Do not enable** on edge: cluster, elastic scaling, ten parallel alert rules per poll, WebSocket fan-out to many clients at once.

### B. Edge API — no local drivers

Node serves REST/WS to operators only; site telemetry goes to the hub ([cluster](cluster.md) — `edge-api` profile).

| Parameter | Recommendation |
|-----------|----------------|
| `ISPF_REPLICA_PROFILE` | `edge-api` |
| Drivers / schedulers | none (capabilities without `drivers`) |
| Federation tunnel | to central node |
| JVM | can be even smaller — no driver I/O |

Use when there is **no** point running a polling JVM on the gateway: all SCADA on central, edge is a thin API.

### Edge health checks

1. `GET /api/v1/info` — one replica, `clusterEnabled=false`.
2. Startup logs: `elastic=false`, workers 1–1 or 1–2.
3. `GET /api/v1/platform/metrics` — `processCpuPercent` steady < 15% at configured poll rate.
4. No growth in `objectChangeQueueSize`/`eventJournalQueueSize` in steady state.

---

## Profile comparison (summary)

| Aspect | Production | Throughput | Demo / idle | Edge |
|--------|------------|------------|-------------|------|
| `ISPF_*_ELASTIC` | true (default) | true | false | false |
| Ingress workers | elastic 4–32 | elastic 4–32 | fixed 1–2 | fixed 1 |
| Object-change workers | elastic | elastic | fixed 2 | fixed 1 |
| Journal / historian | jdbc (S/M) or CH (L) | CH / Scylla | jdbc | jdbc |
| `telemetryPublishMode` | per project: `TELEMETRY_ONLY` + `FULL` where needed | `EVENT_JOURNAL_ONLY` | `TELEMETRY_ONLY` + 1× `FULL` | `TELEMETRY_ONLY` |
| Cluster / NATS | for HA (M/L) | optional | no | no |
| Bootstrap fixtures | false | clear before test | false | false |
| Metrics probe on startup | false | as needed | false | false |
| JVM heap | 512m–2g+ | 512m–2g+ | 256–512m | 128–256m |
| `prod-idle.env` | **do not use** | do not use | **yes** | baseline, trim to 1 worker |

---

## Repository artifacts

| File | Profile |
|------|---------|
| [`deploy/docker-compose.prod-stack.yml`](../../deploy/docker-compose.prod-stack.yml) | Production S (PG + ISPF + nginx) |
| [`deploy/docker-compose.vps-cluster.yml`](../../deploy/docker-compose.vps-cluster.yml) | Production M/L (multi-replica) |
| [`deploy/vps-deploy-direct.ps1`](../../deploy/vps-deploy-direct.ps1) | Deploy jar + UI (staging) |
| [`deploy/vps-cluster-rollout.sh`](../../deploy/vps-cluster-rollout.sh) | Rolling replica restart |
| [`deploy/docker-compose.edge-arm.yml`](../../deploy/docker-compose.edge-arm.yml) | Edge ARM64 (legacy path) |
| [`deploy/edge/arm64/docker-compose.yml`](../../deploy/edge/arm64/docker-compose.yml) | Edge ARM64 gateway (**BL-187 Done**, Pi profile) — see [`deploy/edge/arm64/README.md`](../../deploy/edge/arm64/README.md) |
| [`deploy/helm/ispf/`](../../deploy/helm/ispf/) | K8s Helm chart (**BL-186 Done**) — `bash deploy/helm/ispf/validate.sh` |
| [`deploy/ispf-server.prod-idle.env`](../../deploy/ispf-server.prod-idle.env) | **Only** demo-idle / edge baseline |
| [`deploy/vps-apply-prod-idle-env.sh`](../../deploy/vps-apply-prod-idle-env.sh) | Merge idle env + recreate |
| [`deploy/vps-event-journal-peak-tuning.sh`](../../deploy/vps-event-journal-peak-tuning.sh) | Throughput / Prod L journal |
| [`deploy/vps-idle-thread-sample.py`](../../deploy/vps-idle-thread-sample.py) | Thread diagnostics |
| [`deploy/loadtest-cleanup.py`](../../deploy/loadtest-cleanup.py) | Load-test prep |
| [`deploy/tools/golden-path-alarm-smoke.py`](../../deploy/tools/golden-path-alarm-smoke.py) | Golden path: fire → journal → ack (fixtures) |
| [`docs/en/release-dogfood.md`](release-dogfood.md) | Pre-tag dogfood checklist |
| [`docs/en/operator-apps.md`](operator-apps.md) | Operator starters (alarm / work-queue / HMI wall) |
| [`deploy/grafana/ispf-automation-pipeline.json`](../../deploy/grafana/ispf-automation-pipeline.json) | Optional Grafana export |

---

## Related decisions

- [0026-elastic-telemetry-ingress](decisions/0026-elastic-telemetry-ingress.md) — multi-level ingress, elastic defaults.
- [0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md) — journal fast path
- [0033-prod-idle-demostand-tuning](decisions/0033-prod-idle-demostand-tuning.md) — IDLE profile rationale
- [load-testing](load-testing.md) — throughput scripts
- [cluster](cluster.md) — when you need multiple replicas instead of a single node
