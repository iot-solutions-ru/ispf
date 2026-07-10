# ADR-0033: Demostand profiles (idle / edge) and read-only hot paths

## Status

Accepted (2026-07-06)

## Context

Platform defaults ([0026-elastic-telemetry-ingress](0026-elastic-telemetry-ingress.md)) target **throughput**: elastic L0–L3 pools (ingress 4–32 workers, binding-async, object-change bus). On stands with a **small** number of devices (demo, HMI, edge gateway) the same defaults cause:

- extra threads and elastic scale churn with no real queue;
- high CPU on poll drivers in `FULL` mode (full automation pipeline on every OID);
- need for **one** JVM instead of several replicas on limited CPU.

**Exception storms** were found from read-only transactions with hidden INSERT/UPDATE on hot paths (`AlarmShelfService`, `ScheduleObjectService`).

Stopping drivers reduces load but **does not replace** profile selection: demos need live devices with reduced pools and correct publish modes.

General guide: [DEMOSTANDS](../DEMOSTANDS.md).

## Decision

### 1. Three deployment profiles (+ throughput bench)

| Profile | Elastic | Topology | Documentation |
|---------|---------|-----------|---------------|
| **Production** | ON (defaults) | 1–N replicas, PG + TS/CH | [DEMOSTANDS.md § Production](../DEMOSTANDS.md) |
| **Demo / idle** | OFF, fixed pools | single unified node | [DEMOSTANDS.md § Demo](../DEMOSTANDS.md) |
| **Edge** | OFF, minimal 1 | single node, coalesce↑ | [DEMOSTANDS.md § Edge](../DEMOSTANDS.md) |
| **Throughput** | ON (peak tuning) | benchmark | [load-testing](../load-testing.md) |

### 2. `prod-idle` profile (env overlay for demo / idle)

File [`deploy/ispf-server.prod-idle.env`](../../deploy/ispf-server.prod-idle.env) — reference **demo-idle**; for **edge** reduce workers to 1 (see DEMOSTANDS).

- `ISPF_*_ELASTIC=false` for object-change, L3 ingress, binding-async, driver I/O;
- fixed `*_WORKERS` / `*_THREADS` (not just min/max);
- historian/journal: `jdbc`, small writer pools;
- `ISPF_PLATFORM_METRICS_PROBE_ENABLED=false` at boot;
- `ISPF_CLUSTER_JOB_CONSUMER_ENABLED=false` on single-node without async job queue.

**Convention:** when `elastic=false`, Spring `*Properties.resolved*()` uses the **fixed** `*WORKERS`/`*THREADS` field.

Apply: merge into `ispf-server.env` + **recreate** container ([`deploy/vps-apply-prod-idle-env.sh`](../../deploy/vps-apply-prod-idle-env.sh) — template for any host).

### 3. Driver publish modes

- Poll drivers with many points → `TELEMETRY_ONLY` + coalesce ≥ poll interval.
- `FULL` — only on devices where alerts/workflows are needed in demo.
- Example API script: [`deploy/vps-demostand-tune-drivers.sh`](../../deploy/vps-demostand-tune-drivers.sh).

### 4. Single unified node for idle demo

`ISPF_CLUSTER_ENABLED=false`, `replicaRole=all`. Multi-replica only when CPU headroom and HA/throughput goals exist ([cluster](../cluster.md)).

### 5. Read-only hot paths without writes

| Service | Hot path | Before | After |
|---------|----------|--------|-------|
| `AlarmShelfService` | `isShelved()`, `listActive()` | `expireStale()` UPDATE in read-only tx | `@Scheduled expireStaleScheduled()` 60s |
| `ScheduleObjectService` | `listEnabled()` | `ensureCatalog` INSERT in read-only tx | Empty list; catalog in bootstrap `ensureCatalog()` |

Rule: `@Transactional(readOnly = true)` on frequent ticks **must not** call ensure/create/update.

### 6. Container recreate for env (Docker)

After changing `env_file` — `compose rm` + `up`, not `docker restart`.

## Consequences

- Predictable low CPU on demo/edge with live drivers.
- Clear separation of «load-test defaults» vs «idle overlay».
- Fewer hidden ERRORs in logs.

Risks:

- Prod-idle **not** suitable for flood MQTT without switching env to throughput profile.
- One `FULL` demo device is an intentional CPU cost.
- Docker single-node requires a separate deploy path (not systemd `apply-platform-update.sh`).

**Documentation**

- [DEMOSTANDS](../DEMOSTANDS.md) — primary guide
- [vps-demostand](../vps-demostand.md) — example ops on one host
- [deployment](../deployment.md), [cluster](../cluster.md)

## Related

- [0026-elastic-telemetry-ingress](0026-elastic-telemetry-ingress.md) — elastic ingress defaults (throughput)
- [0027-event-journal-ingress-fast-path](0027-event-journal-ingress-fast-path.md) — journal fast path
- [0028-horizontal-active-active-cluster](0028-horizontal-active-active-cluster.md) — cluster vs single
- [load-testing](../load-testing.md) — throughput
- [observability](../observability.md) — load diagnostics
