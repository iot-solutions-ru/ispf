> **Language:** Canonical English. Russian edition: [ru/observability.md](../ru/observability.md).

# Observability (ISPF)

Automation pipeline metrics export: **Prometheus scrape** (pull) and **OTLP** (push, OpenTelemetry-compatible backends).

## Prometheus (default on prod)

| Endpoint | Auth | Format |
|----------|------|--------|
| `/actuator/prometheus` | admin role | Prometheus text |

Key ISPF metrics (Micrometer):

| Metric | Type | Description |
|--------|------|-------------|
| `ispf.events.fired.total{source=...}` | counter | Fires by source (alert, api, correlator, function) |
| `ispf.alert.fires.total` | counter | Alert rule firings |
| `ispf.alert.evaluations.total` | counter | CEL evaluations |
| `ispf.correlator.triggers.total` | counter | Correlator triggers |
| `ispf.telemetry.coalesce_drops.total` | counter | Last-value coalesce skipped/superseded samples |
| `ispf.telemetry.binding_bypass.total` | counter | Binding consumer present — coalesce skipped |
| `ispf.telemetry.historian_only.total` | counter | Historian fast path without object-change bus |
| `ispf.object_change.queue.size{lane=telemetry\|automation\|total}` | gauge | Async queue depth |
| `ispf.object_change.workers.active{lane=telemetry\|automation}` | gauge | Active worker threads (elastic mode) |
| `ispf.object_change.processed.total` | gauge | Processed object-change events |
| `ispf.object_change.queue.dropped.total` | counter | Dropped events (queue full) |
| `ispf.event_journal.queue.size` | gauge | Journal async writer queue |
| `ispf.event_journal.flushed.total` | gauge | Written to journal (cumulative) |
| `ispf.event_journal.queue_full.sync_fallback.total` | counter | Sync fallback on queue overflow |
| `ispf.workflow.starts.total{trigger=...}` | counter | Workflow starts by trigger |
| `ispf.event_history.records` | gauge | Event journal record count |
| `ispf.workflow_instances.running` | gauge | Active workflow instances |
| `ispf.drivers.active` / `connected` | gauge | Drivers |
| `ispf.database.connections.*` | gauge | HikariCP pool |

### Metrics probe (object tree sync)

In-process sync `/api/v1/platform/metrics` → `root.platform.devices.platform-metrics-probe` (variables `eventsPerSecond`, `objectChangeQueueSize`, `heapUsedMb`, …). Alternative to external Prometheus during load test.

| Endpoint | Auth | Description |
|----------|------|-------------|
| `GET /api/v1/platform/diagnostics/metrics-probe` | admin | `{ enabled, devicePath, devicePresent }` |
| `PUT /api/v1/platform/diagnostics/metrics-probe` | admin | `{ "enabled": true\|false }` — enable/disable sync |

**UI (recommended):** Admin → System → Metrics → **Load diagnostics** → checkbox "Sync metrics to probe device". When leaving the page probe **automatically disables** (do not leave background load).

Env `ISPF_PLATFORM_METRICS_PROBE_ENABLED` and boot-time `ispf.platform-metrics-probe.enabled` **do not start** scheduler — only runtime toggle via API/UI. Interval: `ISPF_PLATFORM_METRICS_PROBE_INTERVAL_MS` (default 5000) or Runtime settings → `metrics-probe.interval-ms`.

Before enabling create probe device: `python deploy/setup-platform-metrics-monitor.py`.

## Metrics UI diagnostics (0.9.97+)

Admin → **System → Metrics** — **Load diagnostics** card (cluster fan-out).

| Endpoint | Auth | Description |
|----------|------|-------------|
| `GET /api/v1/platform/metrics` | admin | Metrics of **current** JVM + `diagnostics` (CPU, suspects, `detail`) |
| `GET /api/v1/platform/cluster/diagnostics` | admin | All replicas: CPU rank, suspects, drill-down per node |

**Level 1 — cluster:** which replica loads CPU (`processCpuPercent`), pipeline queues, `clusterTopSuspect`.

**Level 2 — inside node** (expand in UI):

| Block | Contents |
|-------|----------|
| Thread groups | `ispf-driver-io`, `driver-ingress`, `object-change`, top-5 threads by CPU Δ |
| Driver bindings | `devicePath`, `driverId`, ingress pending/coalesced, `pressureScore` |
| Jobs | `platform_jobs` with status `RUNNING` on `holder_id` |
| Workflows | top `workflow_instances` in `RUNNING` status |

Suspect heuristics: object-change / event journal / historian backlog, JDBC pool, heap pressure, hot driver binding.

Peer fan-out: heartbeat writes `http_port` to `platform_cluster_replicas` (V65); aggregator polls `http://127.0.0.1:{port}/api/v1/platform/metrics` on VPS host network.

If all JVMs show low CPU — culprit outside ISPF (Scylla, ClickHouse, Postgres): `docker stats` on host.

### CLI (single-node, SSH)

Script [`deploy/vps-idle-thread-sample.py`](../deploy/vps-idle-thread-sample.py) — two samples of `GET /api/v1/platform/metrics` ~6s apart, prints thread groups and suspects to stdout. Handy over SSH without UI.

```bash
scp deploy/vps-idle-thread-sample.py deploy-user@production-host:/tmp/
ssh deploy-user@production-host python3 /tmp/vps-idle-thread-sample.py
```

Details: [demostands](demostands.md) (verification section), [vps-demostand](vps-demostand.md) (ops example).

**Grafana dashboard** (automation + telemetry hot path): [`deploy/grafana/ispf-automation-pipeline.json`](../deploy/grafana/ispf-automation-pipeline.json) — see [`deploy/grafana/README.md`](../deploy/grafana/README.md). Local stack: `docker compose -f deploy/docker-compose.observability.yml up -d`.

**WebSocket sessions** are not exported on `/actuator/prometheus` yet — use `GET /api/v1/platform/metrics` → `connections.websocketClients` until a Micrometer gauge exists.

**Golden path smoke (alarm → journal → ack):** [`deploy/tools/golden-path-alarm-smoke.py`](../deploy/tools/golden-path-alarm-smoke.py) against a fixtures-enabled server (`demo-sensor-01`).

## OTLP metrics export (optional, 0.9.9+)

Push metrics to OpenTelemetry Collector / Grafana Cloud / Uptrace via Micrometer OTLP registry.

**Disabled by default** — without `ISPF_OTLP_METRICS_ENABLED=true` export does not run.

```yaml
# /opt/ispf/ispf-server.env
ISPF_OTLP_METRICS_ENABLED=true
ISPF_OTLP_METRICS_URL=http://otel-collector:4318/v1/metrics
ISPF_OTLP_METRICS_STEP=30s
ISPF_ENVIRONMENT=prod
```

Spring properties (`application.yml`):

| Property | Env | Default |
|----------|-----|---------|
| `management.otlp.metrics.export.enabled` | `ISPF_OTLP_METRICS_ENABLED` | `false` |
| `management.otlp.metrics.export.url` | `ISPF_OTLP_METRICS_URL` | `http://localhost:4318/v1/metrics` |
| `management.otlp.metrics.export.step` | `ISPF_OTLP_METRICS_STEP` | `30s` |

Resource attributes: `service.name=ispf-server`, `deployment.environment` from `ISPF_ENVIRONMENT`.

All ISPF counters/gauges above export to OTLP together with standard JVM/Spring Boot metrics (`jvm.*`, `http.server.requests`, `hikaricp.*`).

### Example: local OTel Collector

```yaml
# docker-compose snippet
otel-collector:
  image: otel/opentelemetry-collector-contrib:latest
  ports:
    - "4318:4318"
  volumes:
    - ./otel-collector.yaml:/etc/otelcol/config.yaml
```

Prometheus and OTLP can run simultaneously: Prometheus scrape for VPS without collector, OTLP — for centralized observability stack.

## OTLP tracing (optional, 0.9.10+)

Distributed traces for automation pipeline via Micrometer Observation → OpenTelemetry bridge.

**Disabled by default.**

```yaml
ISPF_OTLP_TRACING_ENABLED=true
ISPF_OTLP_TRACING_URL=http://otel-collector:4318/v1/traces
ISPF_OTLP_TRACING_SAMPLING=0.1   # 1.0 for dev/debug
```

| Property | Env | Default |
|----------|-----|---------|
| `management.tracing.enabled` | `ISPF_OTLP_TRACING_ENABLED` | `false` |
| `management.opentelemetry.tracing.export.otlp.endpoint` | `ISPF_OTLP_TRACING_URL` | `http://localhost:4318/v1/traces` |
| `management.tracing.sampling.probability` | `ISPF_OTLP_TRACING_SAMPLING` | `0.1` |

### Automation spans

Each async handler on object-change bus creates span `ispf.object-change.handler` with tags:

| Tag | Example |
|-----|---------|
| `handler` | `AlertRuleListener`, `EventCorrelatorListener`, `WorkflowTriggerListener` |
| `lane` | `telemetry`, `automation` |
| `change_type` | `VARIABLE_UPDATED`, `EVENT_FIRED` |
| `path` | object path |
| `variable` / `event_name` | when present |

HTTP requests get standard Spring Boot spans (`http.server.requests`) automatically.

### Local collector

Ready config: [`deploy/otel-collector-minimal.yaml`](../deploy/otel-collector-minimal.yaml) — OTLP HTTP `:4318`, debug + Prometheus `:8889`.

```bash
docker run --rm -p 4318:4318 -p 8889:8889 \
  -v $(pwd)/deploy/otel-collector-minimal.yaml:/etc/otelcol/config.yaml \
  otel/opentelemetry-collector-contrib:latest
```

## Elastic object-change workers (0.9.11+)

By default object-change bus uses **elastic** worker pools per lane (telemetry / automation): thread count scales between min and max by queue depth:

- **scale up** — when `queue.size >= elastic-scale-up-queue-threshold`, target workers grow (to max);
- **scale down** — after `elastic-scale-down-steps` consecutive checks with empty queue target decreases by 1 (to min);
- periodic check — `elastic-scale-check-interval-ms`; additional scale-up on enqueue if queue already above threshold.

**Enabled by default** (`ISPF_OBJECT_CHANGE_ELASTIC_WORKERS=true`). Disable: `ISPF_OBJECT_CHANGE_ELASTIC_WORKERS=false` or **System → Runtime settings** (`GET/PATCH /api/v1/platform/runtime-settings`).

```yaml
# /opt/ispf/ispf-server.env — fine-tune prod load (elastic already true by default)
ISPF_OBJECT_CHANGE_AUTOMATION_WORKERS_MIN=2
ISPF_OBJECT_CHANGE_AUTOMATION_WORKERS_MAX=16
ISPF_OBJECT_CHANGE_ELASTIC_SCALE_UP_THRESHOLD=50
ISPF_OBJECT_CHANGE_ELASTIC_SCALE_DOWN_STEPS=6
ISPF_OBJECT_CHANGE_ELASTIC_SCALE_CHECK_MS=500
ISPF_OBJECT_CHANGE_AUTOMATION_QUEUE_CAPACITY=10000
```

Short spike in `ispf.object_change.queue.size` is normal; sustained growth with low `workers.active` or at max workers signals downstream bottleneck (CEL, journal, DB).

## Related documents

- [load-testing](load-testing.md) — baselines and load test scripts
- [messaging](messaging.md) — JetStream / Redis optional transports (0014)
- [decisions/0014-automation-pipeline-evolution.md](decisions/0014-automation-pipeline-evolution.md)
