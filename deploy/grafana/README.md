# Grafana: ISPF Automation Pipeline

Dashboard **`ISPF Automation Pipeline`** (`uid: ispf-automation-pipeline`) — все Micrometer-метрики automation pipeline из [OBSERVABILITY.md](../../docs/OBSERVABILITY.md).

## Метрики на дашборде

| Секция | Метрики |
|--------|---------|
| Throughput | `ispf_events_fired_total{source}`, `ispf_alert_fires_total`, `ispf_alert_evaluations_total`, `ispf_correlator_triggers_total`, `ispf_workflow_starts_total{trigger}` |
| Object-change bus | `ispf_object_change_queue_size{lane}`, `ispf_object_change_workers_active{lane}`, `ispf_object_change_processed_total`, `ispf_object_change_queue_dropped_total` |
| Event journal | `ispf_event_journal_queue_size`, `ispf_event_journal_flushed_total`, `ispf_event_journal_queue_full_sync_fallback_total`, `ispf_event_history_records` |
| State | `ispf_workflow_instances_running`, `ispf_variable_history_samples` |
| Infra | `ispf_drivers_active`, `ispf_drivers_connected`, `ispf_database_connections_*` |

Имена в Prometheus — с подчёркиваниями (`ispf_alert_fires_total`), Micrometer registry использует точки.

## Два контура данных

### Pull — Prometheus scrape

```yaml
# deploy/prometheus-ispf-scrape.yml
scrape_configs:
  - job_name: ispf-server
    metrics_path: /actuator/prometheus
    scheme: https
    bearer_token: <admin JWT or file>
    static_configs:
      - targets: [ispf.iot-solutions.ru]
```

### Push — OTLP → OTel Collector → Prometheus

```yaml
# ISPF env
ISPF_OTLP_METRICS_ENABLED=true
ISPF_OTLP_METRICS_URL=http://otel-collector:4318/v1/metrics
```

Collector re-export: [`deploy/otel-collector-minimal.yaml`](../otel-collector-minimal.yaml) → Prometheus `:8889/metrics`.

Prometheus scrape job `ispf-otel-collector` в том же файле.

## Быстрый старт (локально)

```powershell
cd deploy
python grafana/generate-dashboard.py   # при изменении generate-dashboard.py
docker compose -f docker-compose.observability.yml up -d
```

- Grafana: http://localhost:3000 (admin / admin)
- Prometheus: http://localhost:9090
- OTel Collector OTLP HTTP: http://localhost:4318

Дашборд подхватывается из provisioning (папка **ISPF**).

## Импорт в существующий Grafana

1. **Dashboards → Import → Upload JSON**
2. Файл: `deploy/grafana/ispf-automation-pipeline.json`
3. Выберите Prometheus datasource (pull или collector)

## Регенерация JSON

Панели описаны в `generate-dashboard.py`:

```powershell
python deploy/grafana/generate-dashboard.py
```
