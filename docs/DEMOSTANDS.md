# Профили развёртывания ISPF

Руководство по выбору конфигурации в зависимости от цели: **промышленный prod**, **высокая пропускная способность**, **демо/HMI**, **edge с ограниченным CPU**.

Не привязано к конкретному хосту. Примеры скриптов и env — в [`deploy/`](../deploy/).

См. также: [DEPLOYMENT.md](DEPLOYMENT.md), [SECURITY.md](SECURITY.md), [CLUSTER.md](CLUSTER.md), [LOAD_TESTING.md](LOAD_TESTING.md), [OBSERVABILITY.md](OBSERVABILITY.md), [ADR-0026](decisions/0026-elastic-telemetry-ingress.md), [ADR-0033](decisions/0033-prod-idle-demostand-tuning.md).

## Как выбрать профиль

| Профиль | Типичная цель | Устройств / msg/s | CPU / RAM | Топология |
|---------|---------------|-------------------|-----------|-----------|
| **Production** | Эксплуатация на объекте, SLA, операторы 24/7 | десятки–сотни устройств, умеренный–высокий поток | 4+ vCPU на JVM; кластер при HA | 1–N реплик, elastic по нагрузке, PG + TS/CH |
| **Throughput** | Load-test, benchmark, capacity planning | сотни–тысячи msg/s | 4+ vCPU, 8+ GB на JVM | 1–N реплик, elastic **вкл**, CH/Scylla |
| **Demo / idle** | Публичный демостенд, учебный стенд | единицы–десятки, низкий poll | 2–4 vCPU, **одна** JVM | single node, elastic **выкл** |
| **Edge** | Шлюз на площадке, слабый CPU | 1–5 устройств | 1–2 ядра, 512 MB–2 GB | single node; драйверы локально или API к hub |

```text
  Эксплуатация, SLA ──► Production     cluster?, elastic ON, PG+TS/CH, RBAC
  Benchmark         ──► Throughput      elastic ON, peak tuning
  Мало устройств    ──► Demo / idle     elastic OFF, prod-idle.env
  Мало CPU          ──► Edge            minimal pools, coalesce↑
```

**Главное правило:** defaults ([ADR-0026](decisions/0026-elastic-telemetry-ingress.md)) — для **production throughput** и load-test. Профиль **demo-idle** (`prod-idle.env`) — **не** заменяет prod при реальной нагрузке. Для prod с малым фоном на одной JVM можно урезать пулы осознанно, но не копировать demostand overlay без анализа очередей.

---

## Профиль: Production (промышленная эксплуатация)

**Когда:** рабочий объект, MES/SCADA, постоянные операторы, интеграции, требования к доступности и аудиту.

Отличие от **demo-idle:** prod несёт **реальную** автоматизацию (`FULL` на нужных устройствах), retention, backup, мониторинг и (при необходимости) HA. Отличие от **throughput bench:** prod балансирует latency, стоимость и устойчивость, а не максимальный events/s.

### Подварианты по масштабу

| Подвариант | Устройства | Топология | Journal / historian |
|------------|------------|-----------|---------------------|
| **Prod S** — один объект, один узел | &lt; ~50 активных драйверов, умеренный poll | `ISPF_CLUSTER_ENABLED=false`, `replicaRole=all` | `jdbc` + Timescale (default PG image) |
| **Prod M** — HA, горизонталь API | 50–500+, операторы, failover REST/WS | 2–4 реплики, nginx, NATS + Redis ([CLUSTER.md](CLUSTER.md)) | Timescale или ClickHouse journal при росте events/s |
| **Prod L** — высокий поток telemetry | сотни–тысячи msg/s sustained | Кластер + выделенные `io` / `compute` реплики ([ADR-0032](decisions/0032-replica-profiles-and-capabilities.md)) | ClickHouse / Scylla ([0016](decisions/0016-clickhouse-event-journal.md), [0025](decisions/0025-cassandra-scylla-timeseries-store.md)) |

Правило sizing: **не больше 1 JVM на 2 vCPU** под sustained driver+automation load. Четыре реплики на четырёх vCPU — только если большая часть времени idle.

### Топология и роли

**Single-site (Prod S):**

```text
Operators → nginx (TLS) → ISPF unified (:8081)
              PostgreSQL (Timescale), Redis (опционально)
```

Compose-пример: [`deploy/docker-compose.prod-stack.yml`](../deploy/docker-compose.prod-stack.yml). Remote install: [`deploy/remote-setup-ispf.sh`](../deploy/remote-setup-ispf.sh).

**HA cluster (Prod M/L):**

```text
Operators → nginx (ip_hash / health) → replica-1..N
              PostgreSQL, Redis, NATS (JetStream)
              опционально: ClickHouse, Scylla
```

См. [CLUSTER.md](CLUSTER.md), [`deploy/docker-compose.vps-cluster.yml`](../deploy/docker-compose.vps-cluster.yml), rollout [`vps-cluster-rollout.sh`](../deploy/vps-cluster-rollout.sh).

| `ISPF_REPLICA_PROFILE` | Когда |
|------------------------|-------|
| `unified` / `all` | Универсальный узел (малый prod) |
| `io` | Выделенные driver I/O при кластере |
| `compute` | Async reports / platform jobs |
| `edge-api` | Удалённая площадка без локальных драйверов ([FEDERATION.md](FEDERATION.md)) |

### Elastic pools и pipeline

На prod **оставляйте elastic включённым** (defaults), если есть:

- MQTT / push ingress с burst;
- много binding/alert rules на `FULL` устройствах;
- рост `objectChangeQueueSize` / `eventJournalQueueSize` в пике.

Начните с defaults `application.yml`; при устойчивом backlog — увеличивайте max workers и queue capacity ([`deploy/vps-event-journal-peak-tuning.sh`](../deploy/vps-event-journal-peak-tuning.sh) как образец для journal).

**Не применяйте** [`ispf-server.prod-idle.env`](../deploy/ispf-server.prod-idle.env) на prod с реальной автоматизацией — это профиль **demo-idle**, не operational prod.

Точечное урезание (если один узел и стабильно низкие очереди): зафиксируйте workers через env, но проверьте metrics после каждого изменения.

### Драйверы и publish mode

| Сценарий | Режим | Заметки |
|----------|-------|---------|
| PLC/Modbus/SNMP, много точек, historian | `TELEMETRY_ONLY` | Coalesce по SLA (1–5s) |
| Алерты, bindings, workflows на устройстве | `FULL` | Только где нужна automation |
| Высокий поток событий без CEL | `EVENT_JOURNAL_ONLY` | [ADR-0027](decisions/0027-event-journal-ingress-fast-path.md) |
| Устройство в дереве, но не опрашивается | `STOPPED` | Не держать RUNNING «на всякий случай» |

Драйверы: maturity **PRODUCTION** ([DRIVERS.md](DRIVERS.md), [ADR-0022](decisions/0022-driver-production-matrix.md)).

### Безопасность и compliance

| Параметр | Production |
|----------|------------|
| `ISPF_BOOTSTRAP_FIXTURES_ENABLED` | **false** |
| Аутентификация | OIDC / RBAC ([SECURITY.md](SECURITY.md)), не дефолтный `admin/admin` в сети |
| TLS | nginx / ingress terminates HTTPS |
| Actuator | `/actuator/*` не публично; Prometheus — admin role |
| AI mutate tools | `ispf.ai.agent-require-approval-for-mutate=true` на prod |
| Секреты | `/opt/ispf/ispf-server.env` chmod 600, не в git |
| Driver packs | `permissive` profile deploy ([LICENSE_COMPLIANCE.md](LICENSE_COMPLIANCE.md)) |

### Persistence и retention

| Данные | Prod S/M | Prod L |
|--------|----------|--------|
| Config, ACL, objects | PostgreSQL | PostgreSQL |
| Variable history | Timescale `variable_samples` (jdbc) | ClickHouse / Scylla опционально |
| Event journal | Timescale `event_history` (jdbc) | ClickHouse ([DEPLOYMENT.md](DEPLOYMENT.md) playbook) |
| Retention | `ISPF_*_RETENTION_DAYS`, Timescale policies ([ADR-0009](decisions/0009-timescaledb-retention.md)) | + архив CH |

Flyway: миграции при старте; перед upgrade — backup БД. Repair: [`deploy/vps-flyway-repair.sh`](../deploy/vps-flyway-repair.sh).

### Наблюдаемость и эксплуатация

- **Метрики:** `/actuator/prometheus` или OTLP ([OBSERVABILITY.md](OBSERVABILITY.md)).
- **Диагностика:** Admin → System → Metrics; при инциденте — `GET /api/v1/platform/metrics`, cluster diagnostics.
- **Metrics probe:** только на время расследования (runtime toggle), не at boot на prod.
- **Backup:** регулярный `pg_dump`; проверка restore.
- **Deploy:** staging jar + UI, rolling restart реплик ([`vps-deploy-direct.ps1`](../deploy/vps-deploy-direct.ps1)); на Docker — **recreate** при смене env.
- **Не делать** factory reset при desync конфига — [ADR-0030](decisions/0030-cluster-config-structure-replica-sync.md), `vps-cluster-verify.sh --config-sync`.

### Env-ориентиры (prod, не idle)

```properties
# Идентичность
ISPF_ENVIRONMENT=prod
ISPF_BOOTSTRAP_FIXTURES_ENABLED=false
ISPF_PLATFORM_METRICS_PROBE_ENABLED=false

# Кластер (Prod M/L)
ISPF_CLUSTER_ENABLED=true
ISPF_NATS_ENABLED=true
ISPF_REDIS_ENABLED=true

# Elastic — defaults (не задавать false без обоснования)
# ISPF_OBJECT_CHANGE_ELASTIC_WORKERS=true  # default

# JVM (подбор по heap % и GC)
JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC

# Historian/journal — по масштабу (Prod S)
ISPF_VARIABLE_HISTORY_STORE=jdbc
ISPF_EVENT_JOURNAL_STORE=jdbc
```

Prod L: добавить `ISPF_EVENT_JOURNAL_STORE=clickhouse`, `ISPF_VARIABLE_HISTORY_STORE=clickhouse` и URL/credentials — см. [DEPLOYMENT.md § ClickHouse](DEPLOYMENT.md#clickhouse-variable-history-prod-playbook-bl-114).

### Проверка здорового prod

1. `GET /api/v1/info` — ожидаемая версия, `environment=prod`.
2. Cluster health (если включён): все реплики UP, driver locks консистентны.
3. Очереди pipeline ≈ 0 в steady state; краткие пики допустимы.
4. Нет повторяющихся ERROR / read-only transaction в логах.
5. Historian/journal пишутся (проверка UI или SQL).
6. TLS, login, RBAC работают с операторских мест.

### Типичные ошибки на prod

| Ошибка | Последствие |
|--------|-------------|
| `prod-idle.env` на объекте с сотнями устройств | Backlog, latency, потеря событий при пике |
| 4 реплики на 4 vCPU без запаса | Постоянный high load, GC pressure |
| Все драйверы `FULL` | Лишний CPU, лавина object-change |
| `docker restart` после смены env | Старая конфигурация в контейнере |
| Factory reset при desync | Потеря данных вместо deploy fix |

---

## Общие принципы (все профили)

### Режим публикации драйвера (`telemetryPublishMode`)

| Режим | Pipeline | CPU |
|-------|----------|-----|
| `TELEMETRY_ONLY` | RAM + historian (при включённом history) | Низкий |
| `EVENT_JOURNAL_ONLY` | Async event journal ([ADR-0027](decisions/0027-event-journal-ingress-fast-path.md)) | Низкий–средний |
| `FULL` | Object-change → bindings → alerts → workflows | Высокий |

Опросные драйверы с многими точками — **`TELEMETRY_ONLY`** + coalesce. **`FULL`** — только где нужна automation.

### Coalesce

- **Per-device:** `telemetryCoalesceMs` в configure driver API.
- **Platform:** `ISPF_RUNTIME_TELEMETRY_COALESCE_MS`, ingress queue coalesce (L3).

### Read-only hot paths

Методы с `@Transactional(readOnly = true)` на частых тиках **не должны** вызывать INSERT/UPDATE. См. [ADR-0033](decisions/0033-prod-idle-demostand-tuning.md).

### Перезапуск и env (Docker)

`docker restart` **не** перечитывает `env_file`. После смены env — **recreate** контейнера.

### Остановка драйверов

На demo не лечить CPU массовым STOP. На prod — RUNNING только у реально опрашиваемых устройств.

---

## Профиль: Throughput (высокая нагрузка / benchmark)

**Когда:** нагрузочные тесты, MQTT flood, capacity planning. Близок к **Prod L**, но цель — предел, а не SLA.

### Топология

- Одна мощная JVM **или** [кластер](CLUSTER.md) — при достаточном CPU на каждую JVM.
- Journal: ClickHouse или Scylla. Historian: CH/Cassandra при высоком sample rate.

### Elastic pools — **включены** (defaults)

| Уровень | Компонент | Типичный диапазон |
|---------|-----------|-------------------|
| L0–L1 | Driver ingress buffer, MQTT callback | 4→32 |
| L3 | `TelemetryIngressDispatcher` | 4→32 |
| L5 | Event journal / variable history writers | 4→32 |

Peak tuning: [`deploy/vps-event-journal-peak-tuning.sh`](../deploy/vps-event-journal-peak-tuning.sh). См. [LOAD_TESTING.md](LOAD_TESTING.md).

---

## Профиль: Demo / idle (малая нагрузка)

**Когда:** публичный демостенд, учебная лаборатория, постоянно включённый HMI с несколькими живыми устройствами, низкий фоновый CPU.

### Топология

- **Одна unified JVM:** `ISPF_CLUSTER_ENABLED=false`, `ISPF_REPLICA_ROLE=all`.
- PostgreSQL (+ Redis при ACL/correlator) — достаточно; Scylla/ClickHouse **не обязательны**.
- `ISPF_BOOTSTRAP_FIXTURES_ENABLED=false` на внешнем prod (fixtures — для lab).

### Elastic pools — **выключены**, фиксированный размер

При `ISPF_*_ELASTIC=false` Spring берёт **фиксированные** `*_WORKERS` / `*_THREADS`, а не min/max:

| Подсистема | Пример env |
|------------|------------|
| Object-change telemetry / automation | `ISPF_OBJECT_CHANGE_TELEMETRY_WORKERS=2`, `ISPF_OBJECT_CHANGE_AUTOMATION_WORKERS=2` |
| Binding async | `ISPF_BINDING_ASYNC_THREADS=2` |
| L3 ingress | `ISPF_RUNTIME_TELEMETRY_INGRESS_ELASTIC_WORKERS=false`, min/max 1–2 |
| Driver I/O / ingress buffer | `ISPF_DRIVER_IO_THREADS=2`, `ISPF_DRIVER_INGRESS_BUFFER_THREADS=1` |

Готовый overlay: [`deploy/ispf-server.prod-idle.env`](../deploy/ispf-server.prod-idle.env). Применение (merge + **recreate** контейнера): [`deploy/vps-apply-prod-idle-env.sh`](../deploy/vps-apply-prod-idle-env.sh).

Дополнительно для idle:

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

### Драйверы

| Тип | Рекомендация |
|-----|--------------|
| SNMP / Modbus с многими точками | `TELEMETRY_ONLY`, poll ≥ 5s, `telemetryCoalesceMs` = poll |
| 1–2 устройства для алертов/workflow | `FULL`, умеренный poll (2–5s) |
| Остальные объекты в дереве | `STOPPED`, не RUNNING |

Пример API-скрипта настройки: [`deploy/vps-demostand-tune-drivers.sh`](../deploy/vps-demostand-tune-drivers.sh) (шаблон — подставьте свои `devicePath`).

### Диагностика

- UI: Admin → System → Metrics → **Диагностика нагрузки**.
- CLI: [`deploy/vps-idle-thread-sample.py`](../deploy/vps-idle-thread-sample.py).

**Здоровый idle:** очереди object-change / journal / historian = 0; `pressureScore` &lt; 25; потоки `binding-async` и `telemetry-ingress` — единицы, не десятки.

### Типичные ошибки

| Симптом | Причина |
|---------|---------|
| Высокий CPU при 2 устройствах | Defaults throughput + SNMP `FULL` |
| Env не применился | `docker restart` вместо recreate |
| 4 JVM на малом VPS | Кластер без запаса CPU |

---

## Профиль: Edge (ограниченный CPU)

**Когда:** промышленный шлюз (ARM, 1–2 ядра, 512 MB–1 GB RAM), локальный сбор с нескольких устройств **или** тонкий API-узел к центральному hub.

Два подварианта:

### A. Edge gateway — локальные драйверы

ISPF на устройстве опрашивает PLC/датчики и отдаёт HMI или синхронизируется с hub ([FEDERATION.md](FEDERATION.md)).

| Параметр | Рекомендация |
|----------|--------------|
| Роль | `ISPF_REPLICA_ROLE=all` (нужны drivers) |
| Кластер | `ISPF_CLUSTER_ENABLED=false` |
| JVM heap | `-Xms128m -Xmx256m` … `-Xmx512m` (подбор по `docker stats` / heap %) |
| Elastic | **все** `ISPF_*_ELASTIC=false` |
| Пулы потоков | **1** (критичный минимум) или **2** (если 2 ядра) — см. prod-idle, уменьшите ещё |
| Tomcat | `SERVER_TOMCAT_THREADS_MAX=20` … `30` |
| Historian | `jdbc`; `ISPF_VARIABLE_HISTORY_MIN_INTERVAL_MS=10000`+; writers=1 |
| Journal | `jdbc`; writers=1; `ISPF_EVENT_JOURNAL_ELASTIC_WRITER=false` |
| Coalesce | `ISPF_RUNTIME_TELEMETRY_COALESCE_MS=2000`–`5000`; per-device coalesce = poll |
| Poll interval | ≥ 5s (10–60s для медленных сигналов) |
| Publish mode | **`TELEMETRY_ONLY`** или **`EVENT_JOURNAL_ONLY`**; избегать `FULL` |
| Активные драйверы | Только реально нужные (1–5); остальное STOPPED |
| Metrics probe | `false` |
| Job consumer | `ISPF_CLUSTER_JOB_CONSUMER_ENABLED=false` |
| AI / OTLP | выключены |
| Redis | `ISPF_REDIS_ENABLED=false` если не нужны correlator windows / cluster ACL |
| NATS | `ISPF_NATS_ENABLED=false` |
| Fixtures bootstrap | `ISPF_BOOTSTRAP_FIXTURES_ENABLED=false` |
| Процесс | **systemd** предпочтительнее Docker на слабом ARM (меньше overhead) |

Минимальный env-фрагмент (дополняет prod-idle, ещё агрессивнее):

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

**Не включайте** на edge: кластер, elastic scale, десятки параллельных alert rules на каждый poll, WebSocket fan-out на множество клиентов одновременно.

### B. Edge API — без локальных драйверов

Узел только REST/WS к оператору; телеметрия с площадки идёт на hub ([CLUSTER.md](CLUSTER.md) — профиль `edge-api`).

| Параметр | Рекомендация |
|----------|--------------|
| `ISPF_REPLICA_PROFILE` | `edge-api` |
| Drivers / schedulers | нет (capabilities без `drivers`) |
| Federation tunnel | к центральному hub |
| JVM | можно ещё меньше — нет driver I/O |

Подходит, когда на шлюзе **нет** смысла крутить JVM с poll-потоками: весь SCADA на central, edge — тонкий API.

### Проверка edge-стенда

1. `GET /api/v1/info` — одна реплика, `clusterEnabled=false`.
2. Логи старта: `elastic=false`, workers 1–1 или 1–2.
3. `GET /api/v1/platform/metrics` — `processCpuPercent` в покое &lt; 15% при заданном poll.
4. Нет роста `objectChangeQueueSize` / `eventJournalQueueSize` в steady state.

---

## Сравнение профилей (кратко)

| Аспект | Production | Throughput | Demo / idle | Edge |
|--------|------------|------------|-------------|------|
| `ISPF_*_ELASTIC` | true (default) | true | false | false |
| Ingress workers | elastic 4–32 | elastic 4–32 | fixed 1–2 | fixed 1 |
| Object-change workers | elastic | elastic | fixed 2 | fixed 1 |
| Journal / historian | jdbc (S/M) или CH (L) | CH / Scylla | jdbc | jdbc |
| `telemetryPublishMode` | по проекту: `TELEMETRY_ONLY` + `FULL` где нужно | `EVENT_JOURNAL_ONLY` | `TELEMETRY_ONLY` + 1× `FULL` | `TELEMETRY_ONLY` |
| Cluster / NATS | при HA (M/L) | опционально | нет | нет |
| Fixtures bootstrap | false | cleanup перед тестом | false | false |
| Metrics probe at boot | false | по необходимости | false | false |
| JVM heap | 512m–2g+ | 512m–2g+ | 256m–512m | 128m–256m |
| `prod-idle.env` | **не использовать** | не использовать | **да** | база, урезать до 1 worker |

---

## Артефакты в репозитории

| Файл | Профиль |
|------|---------|
| [`deploy/docker-compose.prod-stack.yml`](../deploy/docker-compose.prod-stack.yml) | Production S (PG + ISPF + nginx) |
| [`deploy/docker-compose.vps-cluster.yml`](../deploy/docker-compose.vps-cluster.yml) | Production M/L (multi-replica) |
| [`deploy/vps-deploy-direct.ps1`](../deploy/vps-deploy-direct.ps1) | Deploy jar + UI (staging) |
| [`deploy/vps-cluster-rollout.sh`](../deploy/vps-cluster-rollout.sh) | Rolling restart реплик |
| [`deploy/docker-compose.edge-arm.yml`](../deploy/docker-compose.edge-arm.yml) | Edge ARM64 (legacy path) |
| [`deploy/edge/arm64/docker-compose.yml`](../deploy/edge/arm64/docker-compose.yml) | Edge ARM64 gateway (BL-187, Pi profile) |
| [`deploy/ispf-server.prod-idle.env`](../deploy/ispf-server.prod-idle.env) | **Только** demo-idle / edge baseline |
| [`deploy/vps-apply-prod-idle-env.sh`](../deploy/vps-apply-prod-idle-env.sh) | Merge idle env + recreate |
| [`deploy/vps-event-journal-peak-tuning.sh`](../deploy/vps-event-journal-peak-tuning.sh) | Throughput / Prod L journal |
| [`deploy/vps-idle-thread-sample.py`](../deploy/vps-idle-thread-sample.py) | Диагностика потоков |
| [`deploy/loadtest-cleanup.py`](../deploy/loadtest-cleanup.py) | Подготовка к load-test |

---

## Связанные решения

- [ADR-0026](decisions/0026-elastic-telemetry-ingress.md) — multi-level ingress, elastic defaults
- [ADR-0027](decisions/0027-event-journal-ingress-fast-path.md) — fast journal path
- [ADR-0033](decisions/0033-prod-idle-demostand-tuning.md) — обоснование idle-профиля
- [LOAD_TESTING.md](LOAD_TESTING.md) — throughput сценарии
- [CLUSTER.md](CLUSTER.md) — когда нужен multi-replica vs single node
