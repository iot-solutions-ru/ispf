> **Язык:** русская версия (вычитка). Канонический английский: [en/demostands.md](../en/demostands.md).

# Профили развёртывания ISPF

Руководство по выбору конфигурации в зависимости от цели: **промышленный продукт**, **высокая пропускная способность**, **демо/HMI**, **edge с ограниченным процессором**.

Не привязано к конкретному хосту. Примеры скриптов и env — в [`deploy/`](../deploy/).

См. также: [deployment](deployment.md), [security](security.md), [cluster](cluster.md), [load-testing](load-testing.md), [observability](observability.md), [0026-elastic-telemetry-ingress](decisions/0026-elastic-telemetry-ingress.md), [0033-prod-idle-demostand-tuning](decisions/0033-prod-idle-demostand-tuning.md).

## Как выбрать профиль

| Профиль | Типичная цель | Устройств / сообщения/с | ЦП/ОЗУ | Топология |
|---------|---------------|-------------------|-----------|-----------|
| **Производство** | Эксплуатация на объекте, SLA, операторы 24/7 | десять–сотни устройств, умеренный–высокий поток | 4+ виртуальных ЦП на JVM; кластер при ГА | 1–N реплика, эластичная по свойствам, PG + TS/CH |
| **Пропускная способность** | Нагрузочное тестирование, бенчмарк, планирование мощности | стоимости–тысячи мсг/с | 4+ виртуальных ЦП, 8+ ГБ на JVM | 1–N реплика, эластичная **вкл**, CH/Scylla |
| **Демо/холостой** | Публичный демостенд, учебный стенд | качество–десятки, низкий опрос | 2–4 виртуальных ЦП, **одна** JVM | одиночный узел, эластичный **выкл** |
| **Край** | Шлюз на устройствах, слабый процессор | 1–5 устройств | 1–2 ядра, 512 МБ–2 ГБ | одиночный узел; драйверы локально или API к хабу |

```text
  Эксплуатация, SLA ──► Production     cluster?, elastic ON, PG+TS/CH, RBAC
  Benchmark         ──► Throughput      elastic ON, peak tuning
  Мало устройств    ──► Demo / idle     elastic OFF, prod-idle.env
  Мало CPU          ──► Edge            minimal pools, coalesce↑
```

**Главное правило:** значения по умолчанию ([0026-elastic-telemetry-ingress](decisions/0026-elastic-telemetry-ingress.md)) — для **производительности** и нагрузочного теста. Профиль **demo-idle** (`prod-idle.env`) — **не** заменяет prod при реальной нагрузке. Для продукта с малым фоном на одной JVM можно сознательно вырезать пулы, но не копировать оверлей без анализа очередей.

---

## Профиль: Производство (промышленная эксплуатация)

**Когда:** рабочий объект, MES/SCADA, постоянные операторы, требования к доступности и аудиту.

Отличие от **demo-idle:** производится **реальная** автоматизация (`FULL` на нужных устройствах), сохранение, резервное копирование, мониторинг и (при необходимости) высокая доступность. Отличие от **теста пропускной способности**: продукт балансирует задержку, стоимость и устойчивость, а не максимальное количество событий/с.

### Подварианты по масштабу

| Подвариант | Устройства | Топология | Журнал / историк |
|------------|------------|-----------|---------------------|
| **Prod S** — один объект, один узел | &lt; ~50 активных драйверов, умеренный опрос | `ISPF_CLUSTER_ENABLED=false`, `replicaRole=all` | `jdbc` + Временная шкала (изображение PG по умолчанию) |
| **Prod M** — HA, горизонтальный API | 50–500+, операторы, аварийное переключение REST/WS | 2–4 реплики, nginx, NATS + Redis ([cluster](cluster.md)) | Timescale или журнал ClickHouse при росте событий |
| **Prod L** — телеметрия с высоким потоком | устойчивые значения–тысячи мсг/с | Кластер + выделенные `io` / `compute` реплики ([0032-replica-profiles-and-capabilities](decisions/0032-replica-profiles-and-capabilities.md)) | ClickHouse / Scylla ([0016-clickhouse-event-journal](decisions/0016-clickhouse-event-journal.md), [0025-cassandra-scylla-timeseries-store](decisions/0025-cassandra-scylla-timeseries-store.md)) |

Правило определения размера: **не более 1 JVM на 2 виртуальных ЦП** при постоянной нагрузке драйвера+автоматизации. Четыре реплики на четырех виртуальных ЦП — только если большая часть времени простаивает.

### Топология и ролики

**Один сайт (Prod S):**

```text
Operators → nginx (TLS) → ISPF unified (:8081)
              PostgreSQL (Timescale), Redis (опционально)
```

Compose-пример: [`deploy/docker-compose.prod-stack.yml`](../deploy/docker-compose.prod-stack.yml). Удаленная установка: [`deploy/remote-setup-ispf.sh`](../deploy/remote-setup-ispf.sh).

**Кластер высокой доступности (Prod M/L):**

```text
Operators → nginx (ip_hash / health) → replica-1..N
              PostgreSQL, Redis, NATS (JetStream)
              опционально: ClickHouse, Scylla
```

См. [cluster](cluster.md), [`deploy/docker-compose.vps-cluster.yml`](../deploy/docker-compose.vps-cluster.yml), развертывание [`vps-cluster-rollout.sh`](../deploy/vps-cluster-rollout.sh).

| `ISPF_REPLICA_PROFILE` | Когда |
|--------|-------|
| `unified` / `all` | Универсальный узел (малый prod) |
| `io` | Выделенные driver I/O при кластере |
| `compute` | Async reports / platform jobs |
| `edge-api` | Удалённая площадка без локальных драйверов ([federation](federation.md)) |

### Эластичные пулы и конвейер

На проде **предоставлен эластичный включенный** (по умолчанию), если есть:

- MQTT/push ingress с Burst;
- много binding/alert rules на `FULL` устройствах;
- рост `objectChangeQueueSize` / `eventJournalQueueSize` в пике.

ввести настройки по умолчанию `application.yml`; при устойчивом отставании — увеличьте максимальное количество рабочих и емкость очереди ([`deploy/vps-event-journal-peak-tuning.sh`](../deploy/vps-event-journal-peak-tuning.sh) как образец для журнала).

**Не применяйте** [`ispf-server.prod-idle.env`](../deploy/ispf-server.prod-idle.env) на prod с визуализацией автоматизации — это профиль **demo-idle**, нерабочий prod.

Точечное урезание (если один узел и стабильно низкая очередь): зафиксируйте рабочие через env, но проверьте метрики после каждого изменения.

### Драйверы и режим публикации

| Сценарий | Режим | Заметки |
|----------|-------|---------|
| PLC/Modbus/SNMP, много точек, historian | `TELEMETRY_ONLY` | Coalesce по SLA (1–5s) |
| Алерты, привязки, рабочие процессы на устройстве | `FULL` | Только там, где нужна автоматизация |
| Высокий поток событий без CEL | `EVENT_JOURNAL_ONLY` | [0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md) |
| Устройство в дереве, но не требуется | `STOPPED` | Не держать БЕГ «на всякий случай» |

Драйверы: зрелость **PRODUCTION** ([drivers](drivers.md), [0022-driver-production-matrix](decisions/0022-driver-production-matrix.md)).

### Безопасность и соответствие

| Параметр | Производство |
|----------|------------|
| `ISPF_BOOTSTRAP_FIXTURES_ENABLED` | **false** |
| Аутентификация | OIDC/RBAC ([security](security.md)), не дефолтный `admin/admin` в сети |
| ТЛС | nginx/ingress завершает HTTPS |
| Actuator | `/actuator/*` не публично; Prometheus — admin role |
| AI mutate tools | `ispf.ai.agent-require-approval-for-mutate=true` на prod |
| Секреты | `/opt/ispf/ispf-server.env` chmod 600, не в git |
| Пакеты драйверов | `permissive` развертывание профиля ([license-compliance](license-compliance.md)) |

### Стойкость и удержание

| Данные | Прод С/М | Прод Л |
|--------|----------|--------|
| Конфигурация, ACL, объекты | PostgreSQL | PostgreSQL |
| Variable history | Timescale `variable_samples` (jdbc) | ClickHouse / Scylla опционально |
| Журнал событий | Временная шкала `event_history` (jdbc) | ClickHouse (сборник правил [deployment](deployment.md)) |
| Удержание | `ISPF_*_RETENTION_DAYS`, Политика временных рамок ([0009-timescaledb-retention](decisions/0009-timescaledb-retention.md)) | + архив CH |

Пролетный путь: прибытие при старте; перед обновлением — резервное копирование БД. Ремонт: [`deploy/vps-flyway-repair.sh`](../deploy/vps-flyway-repair.sh).

### Наблюдаемость и эксплуатация

- **Метрики:** `/actuator/prometheus` или OTLP ([observability](observability.md)).
- **Диагностика:** Администратор → Система → Метрики; при инциденте — `GET /api/v1/platform/metrics`, кластерная диагностика.
- **Проверка метрик:** только во время событий (переключение времени выполнения), а не при загрузке продукта.
- **Backup:** регулярный `pg_dump`; проверка restore.
- **Развертывание:** промежуточный jar + пользовательский интерфейс, последовательный перезапуск реплики ([`vps-deploy-direct.ps1`](../deploy/vps-deploy-direct.ps1)); в Docker — **пересоздать** при смене env.
- **Не делать** сброс настроек при рассинхронизации конфига — [0030-cluster-config-structure-replica-sync](decisions/0030-cluster-config-structure-replica-sync.md), `vps-cluster-verify.sh --config-sync`.

### Env-ориентиры (прод, не простаивает)

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

Prod L: добавьте `ISPF_EVENT_JOURNAL_STORE=clickhouse`, `ISPF_VARIABLE_HISTORY_STORE=clickhouse` и URL/учетные данные — см. [DEPLOYMENT.md § ClickHouse](deployment.md).

### Проверка здоровья prod

1. `GET /api/v1/info` — ожидаемая версия, `environment=prod`.
2. Состояние кластера (если включен): все реплики UP, блокировки драйверов консистентны.
3. Очереди конвейера ≈ 0 в установившемся режиме; допустимы краткие пики.
4. Нет повторяющихся ОШИБОК/транзакция только для чтения в логах.
5. Журнал/историк пишется (проверка UI или SQL).
6. TLS, логин, RBAC работают с операторскими местами.

### Типичные ошибки на продукте

| Ошибка | Последствие |
|--------|-------------|
| `prod-idle.env` на объекте с сотнями устройств | Отставание, задержка, потеря событий при пике |
| 4 реплики на 4 виртуальных ЦП без запаса | Постоянный высокая нагрузка, давление ГХ |
| Все драйверы `FULL` | Лишний CPU, лавина object-change |
| `docker restart` после смены env | Старая конфигурация в контейнере |
| Сброс к заводским настройкам при рассинхронизации | Потеря данных вместо развертывания fix |

---

## Общие принципы (все профили)

### Режим публикации драйвера (`telemetryPublishMode`)

| Режим | Трубопровод | процессор |
|-------|----------|-----|
| `TELEMETRY_ONLY` | RAM + historian (при включённом history) | Низкий |
| `EVENT_JOURNAL_ONLY` | Журнал асинхронных событий ([0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md)) | Низкий–средний |
| `FULL` | Object-change → bindings → alerts → workflows | Высокий |

Опросные драйверы с многими точками — **`TELEMETRY_ONLY`** + слияние. **`FULL`** — только там, где нужна автоматизация.

### Объединение

- **Per-device:** `telemetryCoalesceMs` в configure driver API.
- **Platform:** `ISPF_RUNTIME_TELEMETRY_COALESCE_MS`, ingress queue coalesce (L3).

### Горячие пути только для чтения

Методы с `@Transactional(readOnly = true)` на частных тиках **не следует** включать INSERT/UPDATE. См. [0033-prod-idle-demostand-tuning](decisions/0033-prod-idle-demostand-tuning.md).

### Перезапуск и env (Docker)

`docker restart` **не** перечитывает `env_file`. После смены env — **recreate** контейнера.

### Остановка драйверов

В демо-версии не лечите CPU массовым STOP. На проде — РАБОТАЮТ только у реально опрашиваемых устройств.

---

## Профиль: Пропускная способность (высокая нагрузка/тест)

**Когда:** нагрузочные тесты, MQTT-флуд, планирование мощности. Близок к **Prod L**, но цель — предел, а не SLA.

### Топология

-Одна мощная JVM **или** [кластер](cluster.md) — при достаточном процессоре на каждую JVM.
- Журнал: ClickHouse или Scylla. Историк: CH/Cassandra при высокой частоте дискретизации.

### Эластичные пулы — **включены** (по умолчанию)

| Уровень | Компонент | Типичный диапазон |
|---------|-----------|-------------------|
| Л0–Л1 | Входной буфер драйвера, обратный вызов MQTT | 4→32 |
| L3 | `TelemetryIngressDispatcher` | 4→32 |
| Л5 | Журнал событий / авторы переменной истории | 4→32 |

Пиковая настройка: [`deploy/vps-event-journal-peak-tuning.sh`](../deploy/vps-event-journal-peak-tuning.sh). См. [load-testing](load-testing.md).

---

## Профиль: Демо/холостой ход (малая нагрузка)

**Когда:** общедоступный демостенд, учебная лаборатория, постоянно включенный HMI с несколькими подъемными устройствами, низкий фоновый процессор.

### Топология

- **Одна unified JVM:** `ISPF_CLUSTER_ENABLED=false`, `ISPF_REPLICA_ROLE=all`.
- PostgreSQL (+ Redis при ACL/корреляторе) — достаточно; Scylla/ClickHouse **не обязателен**.
- `ISPF_BOOTSTRAP_FIXTURES_ENABLED=false` на внешнем prod (fixtures — для lab).

### Эластичные пулы — **выключены**, фиксированный размер

При `ISPF_*_ELASTIC=false` Spring берёт **фиксированные** `*_WORKERS` / `*_THREADS`, а не min/max:

| Подсистема | Пример окружения |
|------------|------------|
| Object-change telemetry / automation | `ISPF_OBJECT_CHANGE_TELEMETRY_WORKERS=2`, `ISPF_OBJECT_CHANGE_AUTOMATION_WORKERS=2` |
| Binding async | `ISPF_BINDING_ASYNC_THREADS=2` |
| L3 ingress | `ISPF_RUNTIME_TELEMETRY_INGRESS_ELASTIC_WORKERS=false`, min/max 1–2 |
| Driver I/O / ingress buffer | `ISPF_DRIVER_IO_THREADS=2`, `ISPF_DRIVER_INGRESS_BUFFER_THREADS=1` |

Готовый оверлей: [`deploy/ispf-server.prod-idle.env`](../deploy/ispf-server.prod-idle.env). Применение (объединить + **пересоздать** контейнер): [`deploy/vps-apply-prod-idle-env.sh`](../deploy/vps-apply-prod-idle-env.sh).

Дополнительно для простоя:

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

Пример настройки API-скрипта: [`deploy/vps-demostand-tune-drivers.sh`](../deploy/vps-demostand-tune-drivers.sh) (шаблон — подставьте свои `devicePath`).

### Диагностика

- Пользовательский интерфейс: Администратор → Система → Метрики → **Диагностика нагрузки**.
- CLI: [`deploy/vps-idle-thread-sample.py`](../deploy/vps-idle-thread-sample.py).

**Здоровый простой:** очередь изменения объекта/журнала/историка = 0; `pressureScore` &lt; 25; потоки `binding-async` и `telemetry-ingress` — результат, не десятки.

### Типичные ошибки

| Симптом | Причина |
|---------|---------|
| Высокий CPU при 2 устройствах | Defaults throughput + SNMP `FULL` |
| Env не применился | `docker restart` вместо recreate |
| 4 JVM на небольшом VPS | Кластер без запаса процессора |

---

## Профиль: Edge (ограниченный процессор)

**Когда:** промышленный шлюз (ARM, 1–2 ядра, 512 МБ–1 ГБ ОЗУ), локальный сбор с несколькими устройствами **или** тонкий API-узел к центральному хабу.

Два подварианта:

### A. Edge шлюз — локальные драйверы

ISPF на устройстве запрашивает ПЛК/датчики и предоставляет HMI или синхронизируется с концентратором ([federation](federation.md)).

| Параметр | Рекомендация |
|----------|--------------|
| Роль | `ISPF_REPLICA_ROLE=all` (нужны drivers) |
| Кластер | `ISPF_CLUSTER_ENABLED=false` |
| JVM heap | `-Xms128m -Xmx256m` … `-Xmx512m` (подбор по `docker stats` / heap %) |
| Elastic | **все** `ISPF_*_ELASTIC=false` |
| Пулы потоки | **1** (критичный минимум) или **2** (если 2 ядра) — см. бездельничать, уменьшите ещё |
| Tomcat | `SERVER_TOMCAT_THREADS_MAX=20` … `30` |
| Historian | `jdbc`; `ISPF_VARIABLE_HISTORY_MIN_INTERVAL_MS=10000`+; writers=1 |
| Journal | `jdbc`; writers=1; `ISPF_EVENT_JOURNAL_ELASTIC_WRITER=false` |
| Coalesce | `ISPF_RUNTIME_TELEMETRY_COALESCE_MS=2000`–`5000`; per-device coalesce = poll |
| Интервал опроса | ≥ 5 с (10–60 с для медленных сигналов) |
| Publish mode | **`TELEMETRY_ONLY`** или **`EVENT_JOURNAL_ONLY`**; избегать `FULL` |
| Активные драйверы | Только реально нужные (1–5); остальное ОСТАНОВЛЕНО |
| Metrics probe | `false` |
| Job consumer | `ISPF_CLUSTER_JOB_CONSUMER_ENABLED=false` |
| ИИ/ОТЛП | выключены |
| Redis | `ISPF_REDIS_ENABLED=false` если не нужны correlator windows / cluster ACL |
| NATS | `ISPF_NATS_ENABLED=false` |
| Fixtures bootstrap | `ISPF_BOOTSTRAP_FIXTURES_ENABLED=false` |
| Процесс | **systemd** предпочтительный Docker на слабом ARM (меньше накладных расходов) |

Минимальный env-фрагмент (выполняется на холостом ходу, ещё агрессивнее):

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

**Не включаете** на край: кластер, эластичное масштабирование, десять параллельных правил оповещения на каждый опрос, разветвление WebSocket на несколько клиентов одновременно.

### B. Edge API — без локальных драйверов

Узел только REST/WS к оператору; телеметрия с площадки идет на хаб ([cluster](cluster.md) — профиль `edge-api`).

| Параметр | Рекомендация |
|----------|--------------|
| `ISPF_REPLICA_PROFILE` | `edge-api` |
| Drivers / schedulers | нет (capabilities без `drivers`) |
| Туннель Федерации | к центральному узлу |
| JVM | можно ещё меньше — нет драйвера ввода/вывода |

Настройка, когда на шлюзе **нет** смысла крутить JVM с опросами: вся SCADA на центральном, периферийном уровне — тонкий API.

### Проверка края стенда

1. `GET /api/v1/info` — одна реплика, `clusterEnabled=false`.
2. Логи старта: `elastic=false`, рабочие 1–1 или 1–2.
3. `GET /api/v1/platform/metrics` — `processCpuPercent` в какое-то &lt; 15% при заданном опросе.
4. Нет роста `objectChangeQueueSize`/`eventJournalQueueSize` в устойчивом состоянии.

---

## Сравнение профилей (кратко)

| Аспект | Производство | Пропускная способность | Демо / простой | Край |
|--------|------------|------------|-------------|------|
| `ISPF_*_ELASTIC` | true (default) | true | false | false |
| Входные рабочие | резинка 4–32 | резинка 4–32 | исправлено 1–2 | исправлено 1 |
| Рабочие по смене объектов | эластичный | эластичный | исправлено 2 | исправлено 1 |
| Журнал / историк | jdbc (S/M) или CH (L) | CH / Сцилла | jdbc | jdbc |
| `telemetryPublishMode` | по проекту: `TELEMETRY_ONLY` + `FULL` где нужно | `EVENT_JOURNAL_ONLY` | `TELEMETRY_ONLY` + 1× `FULL` | `TELEMETRY_ONLY` |
| Кластер / НАТС | при ГА (M/L) | опционально | нет | нет |
| Светильники начальной загрузки | ложный | очистка перед тестом | ложный | ложный |
| Проверка показателей при загрузке | ложный | по необходимости | ложный | ложный |
| JVM-куча | 512м–2г+ | 512м–2г+ | 256–512 м | 128–256 м |
| `prod-idle.env` | **не использовать** | не использовать | **да** | база, урезать до 1 рабочего |

---

## Артефакты в репозиториях

| Файл | Профиль |
|------|---------|
| [`deploy/docker-compose.prod-stack.yml`](../deploy/docker-compose.prod-stack.yml) | Продукция S (PG + ISPF + nginx) |
| [`deploy/docker-compose.vps-cluster.yml`](../deploy/docker-compose.vps-cluster.yml) | Производство M/L (мультиреплика) |
| [`deploy/vps-deploy-direct.ps1`](../deploy/vps-deploy-direct.ps1) | Deploy jar + UI (staging) |
| [`deploy/vps-cluster-rollout.sh`](../deploy/vps-cluster-rollout.sh) | Rolling restart реплик |
| [`deploy/docker-compose.edge-arm.yml`](../deploy/docker-compose.edge-arm.yml) | Edge ARM64 (legacy path) |
| [`deploy/edge/arm64/docker-compose.yml`](../deploy/edge/arm64/docker-compose.yml) | Шлюз Edge ARM64 (BL-187, профиль Pi) |
| [`deploy/ispf-server.prod-idle.env`](../deploy/ispf-server.prod-idle.env) | **Только** демо-холостой ход/пограничная базовая линия |
| [`deploy/vps-apply-prod-idle-env.sh`](../deploy/vps-apply-prod-idle-env.sh) | Merge idle env + recreate |
| [`deploy/vps-event-journal-peak-tuning.sh`](../deploy/vps-event-journal-peak-tuning.sh) | Пропускная способность / Журнал Prod L |
| [`deploy/vps-idle-thread-sample.py`](../deploy/vps-idle-thread-sample.py) | Диагностика потоков |
| [`deploy/loadtest-cleanup.py`](../deploy/loadtest-cleanup.py) | Подготовка к load-test |

---

## Связанные решения

- [0026-elastic-telemetry-ingress](decisions/0026-elastic-telemetry-ingress.md) — многоуровневый вход, эластичные значения по умолчанию.
- [0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md) — быстрый путь к журналу
- [0033-prod-idle-demostand-tuning](decisions/0033-prod-idle-demostand-tuning.md) — обоснование IDLE-профиля
- [load-testing](load-testing.md) — скрипты пропускной способности
- [cluster](cluster.md) — когда нужно несколько реплик вместо одного узла
