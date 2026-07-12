> **Язык:** русская версия (вычитка). Канонический английский: [en/observability.md](../en/observability.md).

# Наблюдаемость (ISPF)

Конвейер автоматизации экспорта метрик: **Prometheus Scraping** (pull) и **OTLP** (push, серверные части, совместимые с OpenTelemetry).

## Прометей (по умолчанию в проде)

| Конечная точка | Авторизация | Формат |
|----------|------|--------|
| `/actuator/prometheus` | admin role | Prometheus text |

Ключевые метрики ISPF (микрометр):

| Метрическая | Тип | Описание |
|--------|------|----------|
| `ispf.events.fired.total{source=...}` | counter | Fires по источнику (alert, api, correlator, function) |
| `ispf.alert.fires.total` | counter | Срабатывания alert rules |
| `ispf.alert.evaluations.total` | counter | Оценки CEL |
| `ispf.correlator.triggers.total` | counter | Срабатывания correlators |
| `ispf.object_change.queue.size{lane=telemetry\|automation\|total}` | gauge | Глубина async-очередей |
| `ispf.object_change.workers.active{lane=telemetry\|automation}` | gauge | Активные worker-потоки (elastic mode) |
| `ispf.object_change.processed.total` | gauge | Обработанные object-change events |
| `ispf.object_change.queue.dropped.total` | counter | Dropped events (queue full) |
| `ispf.event_journal.queue.size` | gauge | Очередь async writer журнала |
| `ispf.event_journal.flushed.total` | gauge | Записано в journal (cumulative) |
| `ispf.event_journal.queue_full.sync_fallback.total` | counter | Sync fallback при переполнении очереди |
| `ispf.workflow.starts.total{trigger=...}` | counter | Старты workflow по триггеру |
| `ispf.event_history.records` | gauge | Записей в журнале событий |
| `ispf.workflow_instances.running` | gauge | Активные workflow instances |
| `ispf.drivers.active` / `connected` | gauge | Драйверы |
| `ispf.database.connections.*` | gauge | HikariCP pool |

### Проверка метрик (синхронизация дерева объектов)

Текущая синхронизация `/api/v1/platform/metrics` → `root.platform.devices.platform-metrics-probe` (переменные `eventsPerSecond`, `objectChangeQueueSize`, `heapUsedMb`, …). Альтернатива внешнему Прометею во время нагрузочного теста.

| Конечная точка | Авторизация | Описание |
|----------|------|----------|
| `GET /api/v1/platform/diagnostics/metrics-probe` | admin | `{ enabled, devicePath, devicePresent }` |
| `PUT /api/v1/platform/diagnostics/metrics-probe` | admin | `{ "enabled": true\|false }` — вкл/выкл sync |

**Интерфейс (рекомендуется):** Администратор → Система → Метрики → **Загрузить диагностику** → чекбокс «Синхронизировать метрики с зондирующим устройством». При закрытии страницы датчик **автоматически включается** (не создает фоновую нагрузку).

Env `ISPF_PLATFORM_METRICS_PROBE_ENABLED` и время загрузки `ispf.platform-metrics-probe.enabled` **не запускают** планировщик — только переключение времени выполнения через API/UI. Интервал: `ISPF_PLATFORM_METRICS_PROBE_INTERVAL_MS` (по умолчанию 5000) или Настройки времени выполнения → `metrics-probe.interval-ms`.

Перед включением создайте probe device: `python deploy/setup-platform-metrics-monitor.py`.

## Диагностика пользовательского интерфейса метрик (0.9.97+)

Админ → **Система → Метрика** — карточка **«Диагностика нагрузки»** (кластерный разветвление).

| Конечная точка | Авторизация | Описание |
|----------|------|----------|
| `GET /api/v1/platform/metrics` | admin | Метрики **текущей** JVM + `diagnostics` (CPU, suspects, `detail`) |
| `GET /api/v1/platform/cluster/diagnostics` | admin | Все реплики: CPU rank, suspects, drill-down по ноде |

**Уровень 1 — кластер:** какая реплика грузит ЦП (`processCpuPercent`), очередь конвейера, `clusterTopSuspect`.

**Уровень 2 — внутри ноды** (развернуть в пользовательском интерфейсе):

| Блок | Содержимое |
|------|------------|
| Thread groups | `ispf-driver-io`, `driver-ingress`, `object-change`, top-5 потоков по CPU Δ |
| Driver bindings | `devicePath`, `driverId`, ingress pending/coalesced, `pressureScore` |
| Jobs | `platform_jobs` со статусом `RUNNING` на `holder_id` |
| Workflows | top `workflow_instances` в статусе `RUNNING` |

Подозреваемые Эвристики: невыполненные изменения объектов/журнал событий/историк, пул JDBC, давление кучи, горячая привязка драйвера.

Равноправное разветвление: сердцебиение пишет `http_port` в `platform_cluster_replicas` (V65); агрегатор опрашивает `http://127.0.0.1:{port}/api/v1/platform/metrics` в хост-сети VPS.

Если все JVM с низким процессором — виновник вне ISPF (Scylla, ClickHouse, Postgres): `docker stats` на хосте.

### CLI (один узел, SSH)

Скрипт [`deploy/vps-idle-thread-sample.py`](../deploy/vps-idle-thread-sample.py) — два образца `GET /api/v1/platform/metrics` с интервалом ~6 с, выводят группы потоков и подозреваемых в стандартный вывод. Удобен по SSH без пользовательского интерфейса.

```bash
scp deploy/vps-idle-thread-sample.py deploy-user@production-host:/tmp/
ssh deploy-user@production-host python3 /tmp/vps-idle-thread-sample.py
```

Подробнее: [demostands](demostands.md) (раздел проверки), [vps-demostand](vps-demostand.md) (пример ops).

**Панель управления Grafana** (все метрики конвейера): [`deploy/grafana/ispf-automation-pipeline.json`](../deploy/grafana/ispf-automation-pipeline.json) — см. [`deploy/grafana/README.md`](readme.md). Локальный стек: `docker compose -f deploy/docker-compose.observability.yml up -d`.

## Экспорт метрик OTLP (необязательно, 0.9.9+)

Отправьте метрику в OpenTelemetry Collector / Grafana Cloud / Uptrace через реестр Micrometer OTLP.

**Выключено по умолчанию** — без `ISPF_OTLP_METRICS_ENABLED=true` экспорт не выполняется.

```yaml
# /opt/ispf/ispf-server.env
ISPF_OTLP_METRICS_ENABLED=true
ISPF_OTLP_METRICS_URL=http://otel-collector:4318/v1/metrics
ISPF_OTLP_METRICS_STEP=30s
ISPF_ENVIRONMENT=prod
```

Spring properties (`application.yml`):

| Недвижимость | Окружение | По умолчанию |
|----------|-----|---------|
| `management.otlp.metrics.export.enabled` | `ISPF_OTLP_METRICS_ENABLED` | `false` |
| `management.otlp.metrics.export.url` | `ISPF_OTLP_METRICS_URL` | `http://localhost:4318/v1/metrics` |
| `management.otlp.metrics.export.step` | `ISPF_OTLP_METRICS_STEP` | `30s` |

Resource attributes: `service.name=ispf-server`, `deployment.environment` из `ISPF_ENVIRONMENT`.

Все счетчики/датчики ISPF выше экспортируются в OTLP вместе со стандартными метриками JVM/Spring Boot (`jvm.*`, `http.server.requests`, `hikaricp.*`).

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

Prometheus и OTLP могут работать одновременно: Prometheus Scrape для VPS без коллектора, OTLP — для централизованного стека наблюдаемости.

## OTLP-трассировка (необязательно, 0.9.10+)

Распределенные трассировки для конвейера автоматизации через Micrometer Observation → мост OpenTelemetry.

**Выключено по умолчанию.**

```yaml
ISPF_OTLP_TRACING_ENABLED=true
ISPF_OTLP_TRACING_URL=http://otel-collector:4318/v1/traces
ISPF_OTLP_TRACING_SAMPLING=0.1   # 1.0 для dev/debug
```

| Недвижимость | Окружение | По умолчанию |
|----------|-----|---------|
| `management.tracing.enabled` | `ISPF_OTLP_TRACING_ENABLED` | `false` |
| `management.opentelemetry.tracing.export.otlp.endpoint` | `ISPF_OTLP_TRACING_URL` | `http://localhost:4318/v1/traces` |
| `management.tracing.sampling.probability` | `ISPF_OTLP_TRACING_SAMPLING` | `0.1` |

### Промежутки автоматизации

Каждый async handler object-change bus создаёт span `ispf.object-change.handler` с тегами:

| Тег | Пример |
|-----|--------|
| `handler` | `AlertRuleListener`, `EventCorrelatorListener`, `WorkflowTriggerListener` |
| `lane` | `telemetry`, `automation` |
| `change_type` | `VARIABLE_UPDATED`, `EVENT_FIRED` |
| `path` | object path |
| `variable` / `event_name` | при наличии |

HTTP requests получают стандартные Spring Boot spans (`http.server.requests`) автоматически.

### Локальный коллектор

Готовый конфиг: [`deploy/otel-collector-minimal.yaml`](../deploy/otel-collector-minimal.yaml) — OTLP HTTP `:4318`, отладка + Прометей `:8889`.

```bash
docker run --rm -p 4318:4318 -p 8889:8889 \
  -v $(pwd)/deploy/otel-collector-minimal.yaml:/etc/otelcol/config.yaml \
  otel/opentelemetry-collector-contrib:latest
```

## Эластичные работники изменения объектов (0.9.11+)

По умолчанию шина изменения объекта использует **эластичные** рабочие пулы на полосе (телеметрия/автоматизация): потоки чисел масштабируются между минимальными и максимальными значениями в последовательном порядке:

- **scale up** — когда `queue.size >= elastic-scale-up-queue-threshold`, target workers растёт (до max);
- **масштаб вниз** — после `elastic-scale-down-steps` подряда измерения с пустой очередью целевой выброс на 1 (до мин);
- периодическая проверка — `elastic-scale-check-interval-ms`; Дополнительное масштабирование при постановке в очередь, если очередь уже выше порога.

**Включено по умолчанию** (`ISPF_OBJECT_CHANGE_ELASTIC_WORKERS=true`). Включить: `ISPF_OBJECT_CHANGE_ELASTIC_WORKERS=false` или **Система → Настройки выполнения** (`GET/PATCH /api/v1/platform/runtime-settings`).

```yaml
# /opt/ispf/ispf-server.env — тонкая настройка prod load (elastic уже true по умолчанию)
ISPF_OBJECT_CHANGE_AUTOMATION_WORKERS_MIN=2
ISPF_OBJECT_CHANGE_AUTOMATION_WORKERS_MAX=16
ISPF_OBJECT_CHANGE_ELASTIC_SCALE_UP_THRESHOLD=50
ISPF_OBJECT_CHANGE_ELASTIC_SCALE_DOWN_STEPS=6
ISPF_OBJECT_CHANGE_ELASTIC_SCALE_CHECK_MS=500
ISPF_OBJECT_CHANGE_AUTOMATION_QUEUE_CAPACITY=10000
```

Кратковременный рост `ispf.object_change.queue.size` при всплеске — нормален; устойчивый при росте на низком `workers.active` или на максимальном уровне работники сигнализируют о узком месте в нисходящем направлении (CEL, журнал, DB).

## Связанные документы

- [load-testing](load-testing.md) — базовые показатели и скрипты нагрузочного тестирования
- [messaging](messaging.md) — дополнительные транспорты JetStream / Redis (0014)
- [decisions/0014-automation-pipeline-evolution.md](decisions/0014-automation-pipeline-evolution.md)
