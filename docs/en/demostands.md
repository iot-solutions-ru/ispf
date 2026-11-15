> **Language:** Canonical English. Russian edition: [ru/demostands.md](../ru/demostands.md).

# Deployment Profiles and Server Sizing

> **Status:** Lab — selecting and tuning `ispf-server` profiles for production plants, high-throughput benchmarks, low-CPU demo stands, and resource-constrained Edge gateways. Status tags: [doc-status.md](doc-status.md).

This guide explains how to choose the right `ISPF_DEPLOY_PROFILE` and thread-pool configuration for your hardware and workload. Ready-to-use environment files and scripts live under [`deploy/`](../../deploy/).

**Related guides:** [Customer demo playbook](customer-demo-playbook.md) · [Deployment and configuration](deployment.md) · [Security](security.md) · [ISPF Cluster](cluster.md) · [Load testing](load-testing.md) · [Observability](observability.md) · [ADR-0026](decisions/0026-elastic-telemetry-ingress.md) · [ADR-0033](decisions/0033-prod-idle-demostand-tuning.md).

---

## 1. How to Choose a Deployment Profile

| Profile (`ISPF_DEPLOY_PROFILE`) | Typical Goal | Workload Scale | Recommended CPU / RAM | Topology |
|---------------------------------|--------------|----------------|-----------------------|----------|
| **`production`** | 24/7 plant operations, SCADA/MES, SLA | Tens to hundreds of devices, moderate-to-high telemetry rate | 4+ vCPU per JVM; multi-replica cluster for HA | 1..N replicas, elastic worker pools **ON**, PostgreSQL + TimescaleDB / ClickHouse |
| **`throughput`** | Load testing, stress benchmarks, capacity planning | Hundreds to thousands of messages/s | 4+ vCPU, 8+ GB RAM per JVM | 1..N replicas, elastic worker pools **ON**, ClickHouse or ScyllaDB |
| **`demo-idle`** | Public demostand or training server with minimal idle CPU usage | Single digits to tens of devices, low poll rate | 2–4 vCPU, **one** JVM | Single node, elastic worker pools **OFF** (`deploy/ispf-server.prod-idle.env`) |
| **`edge`** | On-site industrial gateway or single-board computer | 1–5 local devices | 1–2 CPU cores, 512 MB – 2 GB RAM | Single node, fixed 1-thread pools, 2–5 s telemetry coalescing |

```text
  Plant operations, SLA ──► production   (elastic pools ON, PG + Timescale/ClickHouse, RBAC)
  Stress benchmark      ──► throughput   (elastic pools ON, peak queue tuning)
  Low-CPU demostand     ──► demo-idle    (elastic pools OFF, prod-idle.env)
  Small ARM/Edge gateway──► edge         (1 worker per pool, coalesce 2–5 s)
```

> **Key rule:** default settings in `application.yml` ([ADR-0026](decisions/0026-elastic-telemetry-ingress.md)) target **production throughput**. The **`demo-idle`** overlay (`deploy/ispf-server.prod-idle.env`) is designed specifically to keep background CPU near zero on small demo VMs — **never apply `prod-idle.env` to a production plant with hundreds of active devices**.

After starting or recreating a container, inspect the active profile and resolved worker pool sizes via `GET /api/v1/platform/effective-config` (`profile` and `resolvedPools`).

---

## 2. Profile: `production` (Industrial Operations)

### Scale Sub-Variants

| Sub-Variant | Device Count | Topology | Event Journal & Historian |
|-------------|--------------|----------|---------------------------|
| **Prod S** — single site, single node | Up to ~50 active drivers | `ISPF_CLUSTER_ENABLED=false`, `replicaRole=all` | PostgreSQL + TimescaleDB (`jdbc`) |
| **Prod M** — high availability | 50–500+ devices, operator failover | 2–4 replicas behind nginx, NATS + Redis ([cluster.md](cluster.md)) | PostgreSQL / TimescaleDB or ClickHouse event journal |
| **Prod L** — high-rate telemetry | Sustained hundreds–thousands msg/s | Cluster with dedicated `edge-api`, `io`, `compute`, `analytics` replicas ([ADR-0032](decisions/0032-replica-profiles-and-capabilities.md)) | ClickHouse ([historian-tiers.md](historian-tiers.md)) |

**CPU sizing rule:** allocate **at least 2 vCPU per JVM replica** under sustained driver and automation load.

### Choosing Driver Publish Modes (`telemetryPublishMode`)

| Device Scenario | Recommended Mode | Notes |
|-----------------|------------------|-------|
| Multi-point PLC / Modbus / SNMP polling for trends and HMI | `TELEMETRY_ONLY` | Updates in-memory RAM and historian without triggering CEL alert/correlator chains; set `telemetryCoalesceMs` to 1–5 s |
| Devices driving CEL bindings, `ALERT` rules, or BPMN workflows | `FULL` | Runs the full automation pipeline |
| High-rate raw event archiving without CEL evaluation | `EVENT_JOURNAL_ONLY` | Direct batched write to `event_history` ([ADR-0027](decisions/0027-event-journal-ingress-fast-path.md)) |
| Unused or standby device nodes in the tree | `STOPPED` | Do not leave unused drivers `RUNNING` |

### Production Environment Template

```properties
ISPF_ENVIRONMENT=prod
ISPF_DEPLOY_PROFILE=production
ISPF_BOOTSTRAP_FIXTURES_ENABLED=false
ISPF_PLATFORM_METRICS_PROBE_ENABLED=false

# Multi-replica cluster (Prod M / Prod L):
ISPF_CLUSTER_ENABLED=true
ISPF_NATS_ENABLED=true
ISPF_REDIS_ENABLED=true

# JVM heap (size according to host RAM):
JAVA_OPTS=-Xms512m -Xmx2048m -XX:+UseG1GC

# Historian & journal (jdbc for Prod S/M, clickhouse for Prod L):
ISPF_VARIABLE_HISTORY_STORE=jdbc
ISPF_EVENT_JOURNAL_STORE=jdbc
```

> **Docker note:** `docker restart` **does not** re-read `.env` files. Always recreate the container (`docker compose up -d --force-recreate`) after changing environment variables.

---

## 3. Profile: `throughput` (High-Load Benchmarks)

- Elastic worker pools (`ISPF_*_ELASTIC=true`) scale from `4` to `32` threads as queues fill.
- Uses ClickHouse or ScyllaDB for high-rate time-series and event journal writes.
- Peak journal tuning script: [`deploy/vps-event-journal-peak-tuning.sh`](../../deploy/vps-event-journal-peak-tuning.sh). Full methodology: **[Load testing (`load-testing.md`)](load-testing.md)**.

---

## 4. Profile: `demo-idle` (Low-Load Demostand)

- Single unified JVM (`ISPF_CLUSTER_ENABLED=false`, `ISPF_REPLICA_ROLE=all`).
- Elastic worker pools disabled (`ISPF_*_ELASTIC=false`) with fixed 1–2 thread pools.
- Tracked overlay: [`deploy/ispf-server.prod-idle.env`](../../deploy/ispf-server.prod-idle.env), applied via [`deploy/vps-apply-prod-idle-env.sh`](../../deploy/vps-apply-prod-idle-env.sh).

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

---

## 5. Profile: `edge` (Resource-Constrained Gateway)

For ARM gateways or compact industrial PCs (1–2 cores, 512 MB – 1 GB RAM) polling 1–5 local PLCs and forwarding to a central Hub via [Federation](federation.md):

```properties
ISPF_DEPLOY_PROFILE=edge
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

Ready-to-use ARM64 Docker Compose stack: [`deploy/edge/arm64/docker-compose.yml`](../../deploy/edge/arm64/docker-compose.yml) ([README](../../deploy/edge/arm64/README.md)).

---

## 6. Summary Comparison

| Setting | `production` | `throughput` | `demo-idle` | `edge` |
|---------|--------------|--------------|-------------|--------|
| `ISPF_*_ELASTIC` | `true` (default) | `true` | `false` | `false` |
| Ingress workers | Elastic `4..32` | Elastic `4..32` | Fixed `1–2` | Fixed `1` |
| Object-change workers | Elastic | Elastic | Fixed `2` | Fixed `1` |
| Journal / Historian store | `jdbc` (S/M) or `clickhouse` (L) | `clickhouse` / `scylla` | `jdbc` | `jdbc` |
| `telemetryPublishMode` | `TELEMETRY_ONLY` + `FULL` where needed | `EVENT_JOURNAL_ONLY` | `TELEMETRY_ONLY` + 1× `FULL` | `TELEMETRY_ONLY` |
| Cluster (NATS + Redis) | Yes (Prod M/L) | Optional | No | No |
| Demo fixtures (`FIXTURES`) | `false` | `false` | Optional | `false` |
| Typical JVM heap (`-Xmx`) | `1–4+ GB` | `2–8+ GB` | `256–512 MB` | `128–256 MB` |
