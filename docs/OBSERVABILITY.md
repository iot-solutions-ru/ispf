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

## Связанные документы

- [LOAD_TESTING.md](LOAD_TESTING.md) — baselines и load test scripts
- [MESSAGING.md](MESSAGING.md) — JetStream / Redis optional transports (ADR-0021)
- [decisions/0021-automation-pipeline-evolution.md](decisions/0021-automation-pipeline-evolution.md)
