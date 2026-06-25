# Observability (ISPF)

Экспорт метрик автomation pipeline: **Prometheus scrape** (pull) и **OTLP** (push, OpenTelemetry-compatible backends).

## Prometheus (default on prod)

| Endpoint | Auth | Формат |
|----------|------|--------|
| `/actuator/prometheus` | admin role | Prometheus text |

Ключевые метрики ISPF (Micrometer):

| Metric | Type | Описание |
|--------|------|----------|
| `ispf.events.fired.total{source=...}` | counter | Fires по источнику (alert, api, correlator, function) |
| `ispf.alert.fires.total` | counter | Срабатывания alert rules |
| `ispf.alert.evaluations.total` | counter | Оценки CEL |
| `ispf.correlator.triggers.total` | counter | Срабатывания correlators |
| `ispf.object_change.queue.size{lane=telemetry\|automation\|total}` | gauge | Глубина async-очередей |
| `ispf.object_change.processed.total` | gauge | Обработанные object-change events |
| `ispf.event_history.records` | gauge | Записей в журнале событий |
| `ispf.workflow_instances.running` | gauge | Активные workflow instances |
| `ispf.drivers.active` / `connected` | gauge | Драйверы |
| `ispf.database.connections.*` | gauge | HikariCP pool |

Probe dashboard (`ISPF_PLATFORM_METRICS_PROBE_ENABLED=true`) синхронизирует subset в object tree для HMI.

## OTLP metrics export (optional, 0.9.9+)

Push метрик в OpenTelemetry Collector / Grafana Cloud / Uptrace через Micrometer OTLP registry.

**Выключено по умолчанию** — без `ISPF_OTLP_METRICS_ENABLED=true` экспорт не выполняется.

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

Resource attributes: `service.name=ispf-server`, `deployment.environment` из `ISPF_ENVIRONMENT`.

Все ISPF counters/gauges выше экспортируются в OTLP вместе со стандартными JVM/Spring Boot metrics (`jvm.*`, `http.server.requests`, `hikaricp.*`).

### Пример: локальный OTel Collector

```yaml
# docker-compose snippet
otel-collector:
  image: otel/opentelemetry-collector-contrib:latest
  ports:
    - "4318:4318"
  volumes:
    - ./otel-collector.yaml:/etc/otelcol/config.yaml
```

Prometheus и OTLP могут работать одновременно: Prometheus scrape для VPS без collector, OTLP — для centralized observability stack.

## OTLP tracing (optional, 0.9.10+)

Distributed traces для automation pipeline через Micrometer Observation → OpenTelemetry bridge.

**Выключено по умолчанию.**

```yaml
ISPF_OTLP_TRACING_ENABLED=true
ISPF_OTLP_TRACING_URL=http://otel-collector:4318/v1/traces
ISPF_OTLP_TRACING_SAMPLING=0.1   # 1.0 для dev/debug
```

| Property | Env | Default |
|----------|-----|---------|
| `management.tracing.enabled` | `ISPF_OTLP_TRACING_ENABLED` | `false` |
| `management.opentelemetry.tracing.export.otlp.endpoint` | `ISPF_OTLP_TRACING_URL` | `http://localhost:4318/v1/traces` |
| `management.tracing.sampling.probability` | `ISPF_OTLP_TRACING_SAMPLING` | `0.1` |

### Automation spans

Каждый async handler object-change bus создаёт span `ispf.object-change.handler` с тегами:

| Tag | Пример |
|-----|--------|
| `handler` | `AlertRuleListener`, `EventCorrelatorListener`, `WorkflowTriggerListener` |
| `lane` | `telemetry`, `automation` |
| `change_type` | `VARIABLE_UPDATED`, `EVENT_FIRED` |
| `path` | object path |
| `variable` / `event_name` | при наличии |

HTTP requests получают стандартные Spring Boot spans (`http.server.requests`) автоматически.

### Локальный collector

Готовый конфиг: [`deploy/otel-collector-minimal.yaml`](../deploy/otel-collector-minimal.yaml) — OTLP HTTP `:4318`, debug + Prometheus `:8889`.

```bash
docker run --rm -p 4318:4318 -p 8889:8889 \
  -v $(pwd)/deploy/otel-collector-minimal.yaml:/etc/otelcol/config.yaml \
  otel/opentelemetry-collector-contrib:latest
```

## Связанные документы

- [LOAD_TESTING.md](LOAD_TESTING.md) — baselines и load test scripts
- [MESSAGING.md](MESSAGING.md) — JetStream / Redis optional transports (ADR-0021)
- [decisions/0021-automation-pipeline-evolution.md](decisions/0021-automation-pipeline-evolution.md)
