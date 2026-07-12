> **Язык:** русская версия (вычитка). Канонический английский: [en/load-testing.md](../en/load-testing.md).

# Нагрузочное тестирование автоматизации ISPF

Нагрузочные сценарии для измерения пропускной способности **API событий HTTP** и **внутреннего конвейера автоматизации** (драйвер → правило оповещения → журнал событий).

Базовый показатель зафиксирован на прод-стенде, версия **0.9.18**, июнь 2026 г. **Профиль развёртывания** для нагрузочного теста — пропускная способность ([demostands](demostands.md)); не в восторге от простоя/крайнего окружения.

**Абсолютная пропускная способность** (события/семплы) зависит от процессора, диска, хранилища журналов/историков и настроек — таблицы ниже **не являются SLA**; Их для сравнения режимов на одном стенде поддерживают, а не переносимые цифры.

См. также [observability](observability.md) — очистка Prometheus и экспорт OTLP.

## Три контура

| Контур | Скрипт | Что измеряет |
|--------|--------|--------------|
| **HTTP** | `deploy/events-load-test.py` | `POST /api/v1/events/fire` + `GET /api/v1/events` с клиента |
| **Internal (poll)** | `deploy/events-internal-load-test.py` | Virtual driver → `sineWave` → alert → journal |
| **Вход MQTT (подписка)** | `deploy/mqtt-ingress-load-test.py` | Реальный брокер → драйвер mqtt **подписка** → оповещение → журнал |
| **Вход MQTT (push, лабораторная работа)** | `--mode push` | Синтетический издатель → локальный Mosquitto |
| **Журнал событий MQTT (внутренний)** | `deploy/mqtt-event-journal-test-remote.sh` | драйвер mqtt → `EVENT_JOURNAL_ONLY` → `fireIngress` → журнал |
| **Журнал событий MQTT (нажатие HTTP)** | `deploy/mqtt-event-ingest-test-remote.sh` | Внешний абонент → `POST /events/fire` (базовый уровень служебных данных API) |

**Перед каждым прогоном** скрипты останавливают фоновую нагрузку и loadtest-фикстуры:

- **Драйверы:** все DEVICE (рекурсивно: mini-TEC, demo-sensor, mqtt-lab, …), кроме `platform-metrics-probe`
- **Правила оповещений:** отключаются все не-loadtest; правила loadtest удаляются (mqtt-test)
- **Корреляторы/расписания:** отключаются все не-loadtest

```powershell
python deploy/loadtest-cleanup.py
python deploy/loadtest-cleanup.py --purge-mqtt --purge-virtual
python deploy/loadtest-cleanup.py --keep-background   # только loadtest, без отключения demo
```

После проверки демо-объектов остаются в дереве; Правила оповещений и расписания необходимо включать вручную или перезапускать сервер (начальная загрузка mini-TEC не пересоздает уже временные правила, но драйверы mini-TEC снова запускаются при перезапуске).

## Вход MQTT — журнал событий (внутренний быстрый путь)

Сценарий **одно входное-обновление → одна запись в `event_history`**: драйвер mqtt, режим `EVENT_JOURNAL_ONLY`, без правил оповещений и без HTTP.

См. [0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md), [0026-elastic-telemetry-ingress](decisions/0026-elastic-telemetry-ingress.md).

```bash
# VPS
bash /opt/ispf/loadtest/mqtt-event-journal-test-remote.sh

# Параметры: RATE, PHASE, WARMUP, DEVICE, TOPIC, EVENT
RATE=10000 PHASE=60 WARMUP=15 bash /opt/ispf/loadtest/mqtt-event-journal-test-remote.sh
```

Setup (один датчик + событие `messageReceived`):

```powershell
python deploy/setup-mqtt-event-journal.py --base-url http://127.0.0.1:8080
```

| Параметр | По умолчанию | Описание |
|----------|---------|----------|
| `telemetryPublishMode` | `EVENT_JOURNAL_ONLY` | Пропуск historian и object-change bus |
| `ingressEventName` | `messageReceived` | Descriptor на объекте |
| `telemetryCoalesceMs` | `1` | Lab: почти 1:1 message→event |

**HTTP Tap** (`deploy/mqtt-event-ingest-tap.py`, `mqtt-event-ingest-test-remote.sh`) — тот же издатель, огонь через REST; базовый API накладных расходов на той же сцене.

### Стенд для одного устройства на ярмарке VPS (журнал событий)

1× mqtt, `EVENT_JOURNAL_ONLY`, `ingressCoalesceEnabled=false`, журнал Сцилла. Сравнительные фазы emqtt для регрессии на производственном оборудовании:

```bash
# На VPS (после scp deploy/vps-ispf-fair-*.sh)
bash /opt/ispf/loadtest/vps-ispf-fair-run.sh
```

| Фаза | emqtt | Типичные событияВыстрел/с (VPS 0.9.87, Scylla 1 SMP) |
|-------|-------|--------------------------------------------------|
| Устойчивый | 65 с, 20 клиентов, 10 мс | **~1,9k** (эластичный L0 + L5′) |
| Пик | 65с, 32 клиента, 1мс | высокая дельта; нужна очередь журнала ≥500 тыс. |

Опциональная настройка перед пиком: `bash deploy/vps-event-journal-peak-tuning.sh` (очередь 500к, пакет 1к, сброс 20мс). Метрики: `eventsFiredTotal`, `eventJournalSyncFallbackTotal`, `eventJournalQueueSize` в `/api/v1/platform/metrics` (раздел `automation`). Сцилла `COUNT(*)` после пика может таймаут — см. [0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md).

### Лабораторный стресс (Scylla, emqtt с несколькими устройствами)

Выделенный лабораторный хост, **EVENT_JOURNAL_ONLY**, 16 драйверов mqtt, хранилище журналов **Scylla**. Полная документация: **[lab-event-journal-stress](lab-event-journal-stress.md)** (SSH/HTTP — в `lab-loadgen.env`, не в документации).

| Метрика (пиковая, 8 шардов emqtt) | Значение |
|---------------------------------|-------|
| Журнал (eventsFired/мета Scylla) | **~110 тыс. событий/с** |
| За устройство | **~6,8 тыс. событий/с** |
| eventFired → сброшено → мета | **100%** (без потерь на пути ISPF) |
| Ограничивающий фактор | Процессор Scylla (~108%) |

Сценарий тестирования сообщает о **трех уровнях эффективности**: по сравнению с настроенным целевым объектом MQTT (только для справки), по сравнению с формулой emqtt и **захватом ISPF (доставка eventsFired/Mosquitto)** — используйте последний для проверки сквозной доставки. Требуется `sys_interval 1` в `deploy/mosquitto/mosquitto.conf`.

```bash
# Lab host
bash lab-mqtt-event-journal-multi-test.sh
DEVICES=16 RATE_PER_DEVICE=32000 EMQTT_SHARD_MAX=8 bash lab-mqtt-event-journal-multi-test.sh
AUTO_CALIBRATE=true bash lab-mqtt-event-journal-multi-test.sh
```

### VPS prod (журнал Scylla, меньший размер Scylla)

Та же методология на `ispf.example.invalid` — несколько устройств: `deploy/vps-mqtt-event-journal-multi-test.sh`, оркестровка `deploy/run_vps_max_load.py`. Выставочный стенд для одного устройства: `deploy/vps-ispf-fair-bench.sh` ([0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md)).

| Метрическая | VPS прод (контекст) | Лаборатория (ссылка) |
|--------|-------------------|-----------------|
| 16 × пик (предварительно, 04 июля 2026 г.) | **~349/с** eventFired | ~110 тыс./с |
| 1 × устойчивый (эластичный 0.9.87+, 05.07.2026) | **~1,9 тыс./с** eventFired | — |
| Сцилла | 1 СМП/750М | 20 СМП/48Г |

Абсолютные показатели **несопоставимы** на разных хостах; используйте справедливый стенд для регрессии одного устройства, сценарий для нескольких устройств для загрузки в форме парка.

## Вход MQTT — архиватор (по умолчанию)

Сценарий **датчик → дашборд**: драйвер mqtt записан в топике, `TELEMETRY_ONLY` пишется в `variable_samples` (Timescale), **без** оповещения/коррелятора/рабочего процесса.

```powershell
# Lab: Mosquitto на VPS + synthetic publisher
python deploy/mqtt-ingress-load-test.py --mode push --broker-url tcp://127.0.0.1:1883 `
  --devices 4 --messages-per-second 2000 --telemetry-coalesce-ms 50 `
  --publish-via-ssh deploy-user@production-host --skip-monitor-setup
```

| Флаг | По умолчанию | Описание |
|------|---------|----------|
| `--mode` | `subscribe` | `subscribe` = реальный брокер; `push` = lab publisher |
| `--telemetry-coalesce-ms` | `50` | Частота сэмплов в historian (per-device) |
| `--automation` | false | Старый режим: FULL + alert rules → event journal |
| `--publish-via-ssh` | — | push mode: publisher на VPS |

Модель `mqtt-sensor-v1`: `temperature` с `historyEnabled=true`. Binding `double(raw)` → `temperature.value` для chart widget.

**Ограничение prod:** `ispf.variable-history.min-interval-ms` (по умолчанию **5000**) — устранение записей в БД. Для высокоскоростного нагрузочного теста: `ISPF_VARIABLE_HISTORY_MIN_INTERVAL_MS=100` в `/opt/ispf/ispf-server.env` + перезапуск (см. `application.yml`).

### Лабораторный стресс historian (Scylla vs ClickHouse, split topology)

Выделенная lab, **TELEMETRY_ONLY**, 16 драйверов mqtt, historian на хосте loadgen/DB, ISPF на хосте приложения. Runbook: **[lab-mqtt-historian-stress](lab-mqtt-historian-stress.md)**; шаблоны: **[examples/lab-mqtt-historian-stress](../../examples/lab-mqtt-historian-stress/)**.

| Store | Цель (16 dev) | Historian flushed (lab 0.9.137) | Примечание |
|-------|---------------|----------------------------------|------------|
| Scylla | 250k msg/s | **~258k** samples/s | Scylla `COUNT(*)` может таймаутить — смотрите flushed + Mosquitto |
| Scylla | 500k msg/s | **~520–559k** samples/s | Очередь может вырасти (~8M); сливается в 0 |
| ClickHouse | 250k msg/s | **~288k** samples/s | CH `count()` совпадает с flushed |
| ClickHouse | 500k msg/s | **~579k** samples/s | |

```bash
# На хосте приложения lab (см. ISPF_LAB_CLUSTER_HOST в examples/lab-mqtt-historian-stress/env/lab-loadgen.env)
DEVICES=16 RATE_PER_DEVICE=15625 PHASE=90 WARMUP=20 INTERVAL_MS=1 EMQTT_SHARD_MAX=8 \
  bash ~/ispf/lab-single-mqtt-historian-test.sh

docker compose --env-file lab-stress-ch.env -f lab-test-host-compose.yml up -d --force-recreate ispf-server nginx
DEVICES=16 RATE_PER_DEVICE=31250 PHASE=90 WARMUP=20 INTERVAL_MS=1 EMQTT_SHARD_MAX=8 \
  bash ~/ispf/lab-single-mqtt-historian-ch-test.sh
```

В lab: `minIntervalMs=0`, ingress coalesce выкл., очередь historian 8M, числовой payload — **не** настройки прода ([demostands](demostands.md)).

### Lab gateway ingress (сценарий I-02)

**Один mqtt-драйвер → `dispatchTelemetry` → child sensor'ы → historian** на split topology (брокер на хосте loadgen, не `tcp://mqtt:1883` в compose приложения). Runbook: **[lab-mqtt-gateway-ingress](lab-mqtt-gateway-ingress.md)**.

Id в ordered suite: `I-02-mqtt-gateway`. Главная метрика PASS: дельта **`variableHistoryFlushedTotal`** (не `sampleCount` после тяжёлых прогонов I-01). Loadtest выставляет `historySampleMode=ALL_VALUES` на child при `NUMERIC_PAYLOAD`.

Реальный брокер `mqtt-broker.example.invalid` — см. subscribe mode ниже; нужны MQTT credentials.

### Объединение развертки (историк)

**MQTT historian** — per-device `telemetryCoalesceMs`, `TELEMETRY_ONLY`:

```powershell
python deploy/mqtt-coalesce-sweep.py --messages-per-second 2000 --phase-seconds 40
```

**Automation benchmark** (legacy): `--automation` или sweep ниже.

**Virtual driver automation** — глобальный `ISPF_RUNTIME_TELEMETRY_COALESCE_MS` + рестарт `ispf-server`:

```powershell
python deploy/events-coalesce-sweep.py --coalesce-ms 250,100,50,25,10,5,1
```

Per-device override в loadtest: `--telemetry-coalesce-ms 1` в `mqtt-ingress-load-test.py`.

#### Архиватор MQTT базовой развертки (0.9.24, TELEMETRY_ONLY, мин-интервал = 100 мс, 4 dev, ~ 2 тыс. сообщений/с)

| телеметрияCoalesceMs | Образцы/ы | Оповещение/я | ТелQ |
|-----|-----------|---------|------|
| 250 | ~139 | 0 | 0 |
| 100 | ~160 | 0 | 0 |
| 50 | ~165 | 0 | 0 |
| 5 | **~166** | 0 | 0 |
| 1 | ~157 | 0 | 0 |

Report: `deploy/mqtt-coalesce-sweep-report-1782385852.json`. Prod env: `ISPF_VARIABLE_HISTORY_MIN_INTERVAL_MS=100`.

*(С `min-interval-ms=5000` (до 0.9.24) сэмплов/с было ~1,2 при том же издателе — историке debounce, а не MQTT.)*

#### Базовая автоматизация MQTT (0.9.23, FULL+alerts, 4 устройства, издатель ~2 тыс. сообщений/с, ClickHouse)

| телеметрияCoalesceMs | События | Оповещение/я | Очереди (авто/журнал) |
|-----|----------|---------|-------------------------|
| 250 | ~30 | ~30 | 0 / 0 |
| 100 | ~54 | ~54 | 0 / 0 |
| 50 | ~87 | ~85 | 0 / 0 |
| 25 | ~76 | ~73 | 0 / 0 |
| 10 | ~78 | ~78 | 0 / 0 |
| 5 | ~102 | ~101 | 0 / 0 |
| 1 | **~108** | **~107** | 0 / 0 |

Report: `deploy/mqtt-coalesce-sweep-report-1782383597.json`.

**Вывод:** при `coalesce-ms=250` потолке ~30 событий/с (4 dev) — упирается в коалесцер. При `coalesce-ms≤5` потолок ~**100–108 событий/с** (~27/dev) — объединение уже не ограничено; Дальше упирается конвейер автоматизации (переплет + оповещение + журнал), очередь при этом не раскрыта. Издатель **10 тыс. сообщений/с** при `coalesce=1` даёт **хуже** (~28 событий/с) — входная перегрузка (обратный вызов MQTT + коалесцер) снижает пропускную способность.

### Базовый уровень (0.9.23, подписка MQTT + лабораторная отправка, 4 устройства, журнал ClickHouse)

| Издатель | Брокер | События | Оповещение о пожарах |
|-----------|--------|----------|---------------|
| ~20 msg/s (synthetic) | `tcp://127.0.0.1:1883` (VPS Mosquitto) | ~20.0 | ~20.1 |

*(4× `loadtest-mqtt-dev-*`, драйвер mqtt РАБОТАЕТ, состояние `self.temperature["value"] > -1000.0`. Отчет `deploy/mqtt-ingress-load-test-report-1782382991.json`. Реальный `mqtt-broker.example.invalid` на продукте: TCP OK, MQTT-соединение `not authorised` — драйверы 0/4, событий/с 0.)*

Lab push (локальный Mosquitto, `--mode push`) — синтетический publisher, см. `mqtt-loadtest-publisher.py`.

### Ingress historian (`--ingress-history-only`)

Benchmark **gateway `lastIngress.raw` only** — no child sensors, no `dispatchTelemetry` binding:

```powershell
python deploy/mqtt-ingress-load-test.py --mode push --broker-url tcp://127.0.0.1:1883 `
  --ingress-history-only --devices 4 --messages-per-second 2000 --telemetry-coalesce-ms 1 `
  --publish-via-ssh deploy-user@production-host --skip-monitor-setup
```

Число выборок/с измерено через `GET .../variables/history` для `lastIngress.raw` на шлюзе (не глобально `sampleCount`). Драйвер использует `ingressTopicLanes=false`, поэтому параллельная отправка не подавляет публикацию архива.

### Высокоскоростной издатель (emqtt-bench)

Для **>2k msg/s** используйте [emqtt-bench](https://github.com/emqx/emqtt-bench) через Docker на VPS (`deploy/mqtt-emqtt-bench.sh`):

```powershell
# Deploy Mosquitto + emqtt-bench image + scripts
.\deploy\vps-mqtt-broker-deploy.ps1

# Ingress load test с emqtt-bench (рекомендуется --gateway)
python deploy/mqtt-ingress-load-test.py --mode push --broker-url tcp://127.0.0.1:1883 `
  --devices 4 --messages-per-second 10000 --telemetry-coalesce-ms 1 `
  --gateway --publisher emqtt-bench --emqtt-interval-ms 10 `
  --publish-via-ssh deploy-user@production-host --skip-monitor-setup

# Standalone на VPS (50k msg/s, 4 топика):
# ssh deploy-user@production-host 'bash /opt/ispf/loadtest/mqtt-emqtt-bench.sh --devices 4 --messages-per-second 50000 --duration-seconds 30'
```

| Флаг | По умолчанию | Описание |
|------|---------|----------|
| `--publisher` | `python` | `emqtt-bench` — Docker на VPS; `python` — paho (~1.5k msg/s) |
| `--emqtt-interval-ms` | `10` | `-I` emqtt-bench (100 msg/s на клиента при 10 ms) |

Формула: `msg/s ≈ devices × clients_per_topic × (1000 / interval_ms)`.

**Важно:** строки `Done (formula estimate)` и `ISPF_EMQTT_FORMULA_RATE=` в стандартном выводе — **рачёт**, не замер брокера. Для лабораторного теста на нескольких устройствах с Mosquitto `$SYS` и метриками ISPF см. [lab-event-journal-stress](lab-event-journal-stress.md).

## Внутренний нагрузочный тест (виртуальный драйвер)

## Подготовка окружения

### 1. Loadtest-устройство

```powershell
python deploy/vps-load-test.py --seed-only --devices 60
```

Создаёт `root.platform.devices.loadtest-dev-*` (шаблон `virtual-lab-v1`).

### 2. Мониторинг (зонд + дашборд)

```powershell
python deploy/setup-platform-metrics-monitor.py --base-url ${ISPF_BASE_URL:-https://ispf.example.invalid}
```

- Probe: `root.platform.devices.platform-metrics-probe`
- Dashboard: `root.platform.dashboards.platform-metrics`
- Синхронизирует `GET /api/v1/platform/metrics` → переменные зонды (события/с, срабатывания оповещений, куча, пул БД, глубина очереди)

**Включение синхронизации:** Администратор → Система → Метрики → Загрузить диагностику → «Синхронизировать метрики с зондирующим устройством», или `PUT /api/v1/platform/diagnostics/metrics-probe` `{ "enabled": true }`. Скрипты нагрузочного тестирования автоматически включают зонд через API; при ручном мониторинге **выключите** зонд после теста (UI снимает галочку при закрытии страницы).

### 3. API метрик платформы

Admin-only: `GET /api/v1/platform/metrics` — секция `automation`:

| Поле | Смысл |
|------|--------|
| `eventHistoryRecords` | Размер журнала событий (PostgreSQL) |
| `alertFiresTotal` | Счётчик срабатываний alert rules (in-memory, platform-wide) |
| `objectChangeQueueSize` | Глубина async-очереди object-change bus |
| `eventJournalQueueSize` | Очередь async writer журнала |

Прометей: `/actuator/prometheus` (роль администратора) — счетчики `ispf.events.fired.total`, `ispf.alert.fires.total`, датчики `ispf.object_change.queue.size{lane=telemetry|automation|total}`, `ispf.event_history.records`, `ispf.workflow_instances.running`, `ispf.variable_history.samples`, `ispf.drivers.active`, `ispf.database.connections.*`.

## HTTP-нагрузочный тест

```powershell
python deploy/events-load-test.py `
  --base-url ${ISPF_BASE_URL:-https://ispf.example.invalid} `
  --concurrency 40 `
  --duration-seconds 60
```

**Baseline (0.9.5, 60 devices, concurrency 40):** ~147–164 RPS на `POST /events/fire`.

JUnit-аналог: `EventFireLoadTest` (150 concurrent HTTP).

**CI-шлюз (BL-113):** рабочий процесс `.github/workflows/load-test.yml` — ночной + `workflow_dispatch`, прогон `EventFireLoadTest` + `ListDevicesLoadTest`, порог `ISPF_LOAD_P99_CEILING_MS` (по умолчанию 3000 мс), артефакты логов Gradle. Шаги Gradle с `set -o pipefail`, чтобы тест на падение не маскировалось `tee`.

## Внутренний нагрузочный тест

```powershell
# Авто-cleanup: останавливает mqtt loadtest, пересоздаёт virtual alert rules
python deploy/events-internal-load-test.py --skip-monitor-setup --poll-ms 1000 --phase-seconds 60
```

Файл `deploy/loadtest-sinewave-condition.txt`:

```cel
self.sineWave["value"] > -1000.0
```

### Параметры

| Флаг | По умолчанию | Описание |
|------|---------|----------|
| `--warmup-seconds` | 15 | Ожидание после configure driver (coalesce + async journal) |
| `--skip-cleanup` | false | Не останавливать mqtt loadtest перед прогоном |
| `--condition-expr` | `true` | CEL для alert rules |
| `--condition-expr-file` | — | Условие из файла |
| `--poll-ms` | `3000,1000,500` | Интервалы опроса virtual driver |
| `--telemetry-mix-ratio` | `0` | Доля устройств в `TELEMETRY_ONLY` (0.5 = половина без automation lane) |
| `--max-devices` | 0 (all) | Лимит loadtest-устройств |

### Базовый уровень (0.9.23, 60 устройств, опрос=1000 мс, прогрев=45 с, журнал ClickHouse)

| условиеВыражение | События | Оповещение о пожарах |
|---------------|----------|---------------|
| `self.sineWave["value"] > -1000.0` | ~29.4 | ~17.5 |

*(0.9.23 P3b: `ISPF_EVENT_JOURNAL_STORE=clickhouse`, пакетная вставка HTTP JSONEachRow. Отчет `deploy/events-internal-load-test-report-1782382458.json`. На 60 dev пропускная способность ниже, чем в Timescale JDBC (0.9.18) — успешно: выигрыш ClickHouse на больших объемах журнала, удержания и аналитики, а не на микро-бенчмарке с локальным PG.)*

### Базовый уровень (0.9.18, 60 устройств, опрос = 1000 мс, прогрев = 45 с, объединение = 250 мс)

| условиеВыражение | События | Оповещение о пожарах |
|---------------|----------|---------------|
| `self.sineWave["value"] > -1000.0` | ~37.9 | ~25.6 |

*(0.9.18 P3a: `event_history` Временная шкала гипертаблицы + сегмент сжатия по `object_path`, ​​удержание 90d. Отчет `deploy/events-internal-load-test-report-1782378017.json`. Прирост против 0.9.17 скромный на 60 разработчиков — основной выигрыш Временная шкала для больших объемов журнала и удержания, а не для микро-бенчмарка.)*

### Базовый уровень (0.9.17, 60 устройств, опрос = 1000 мс, прогрев = 45 с, объединение = 250 мс)

Для каждого устройства **`telemetryPublishMode`**: `FULL` (по умолчанию) или `TELEMETRY_ONLY` (ОЗУ + архиватор, без оповещений/рабочего процесса в объединенном тике).

| Режим | условиеВыражение | События | Оповещение о пожарах |
|-------|---------------|----------|---------------|
| all FULL | `self.sineWave["value"] > -1000.0` | ~36.8 | ~24.5 |
| 50% ТОЛЬКО ТЕЛЕМЕТРИЯ | то же самое | ~30,1 | ~24,7 |

*(0.9.17: выбор режима привязки драйвера; `PUT /api/v1/drivers/runtime/configure` поля `telemetryPublishMode`, `telemetryCoalesceMs`. Срабатывает/с оповещение — глобальный счётчик (фон mini-TEC); журнал падает при TELEMETRY_ONLY, полоса автоматизации разгружается.)*

Пример настройки:

```json
{
  "driverId": "virtual",
  "pollIntervalMs": 1000,
  "telemetryPublishMode": "TELEMETRY_ONLY",
  "autoStart": true
}
```

### Базовый уровень (0.9.16, 60 устройств, опрос = 1000 мс, прогрев = 45 с, объединение = 250 мс)

| условиеВыражение | События | Оповещение о пожарах |
|---------------|----------|---------------|
| `self.sineWave["value"] > -1000.0` | ~41.8 | ~29.0 |

*(0.9.16: пакетный журнал JDBC, счетчик событий O(1), fireAutomation без TX, эластичные рабочие процессы, 6 авторов журнала.)*

### Базовый уровень (0.9.15, 60 устройств, опрос = 1000 мс, прогрев = 30 с, объединение = 250 мс)

| условиеВыражение | События | Оповещение о пожарах |
|---------------|----------|---------------|
| `self.sineWave["value"] > -1000.0` | ~39.9 | ~27.6 |

*(Ссылка на 0.9.14: ~27,4 событий/с — время выполнения предупреждений в памяти. 0.9.15 добавляет кэш CEL, журнал с несколькими записывающими устройствами, объединение 250 мс.)*

### Базовый уровень (0.9.14, 60 устройств, опрос=1000 мс, прогрев=30 с)

| условиеВыражение | События | Оповещение о пожарах |
|---------------|----------|---------------|
| `self.sineWave["value"] > -1000.0` | ~27.4 | ~15.7 |

*(Ссылка на версию 0.9.13: ~26,0 событий/с — состояние выполнения оповещений в памяти.)*

### Базовый уровень (0.9.9, 60 устройств, опрос = 1000 мс, прогрев = 15 с)

| условиеВыражение | События | Оповещение о пожарах |
|---------------|----------|---------------|
| `true` | ~20.7 | ~20.7 |
| `self.sineWave["value"] > -1000.0` | ~21.9 | ~22.1 |

*(ссылка на 0.9.5: `true` ~20,7, реалистичный CEL ~17,4 событий/с — см. история в git.)*

### Важно для баланса

1. **Водители** должны находиться в режиме RUNNING с `autoStart: true` (`PUT /api/v1/drivers/runtime/configure`).
2. **`alertFiresTotal`** — глобальный счётчик; Перед прогоном очистки отключает оповещения mini-TEC/демо и останавливает их драйверы.
3. **`eventHistoryRecords`** — асинхронная запись; воспользуйтесь прогревом перед измерениями.
4. **Не вызывайте** виртуальный опрос (`loadtest-dev-*`) и mqtt subscribe (`loadtest-mqtt-dev-*`) без очистки — скрипты делают это по умолчанию.
5. Точечная запись `self.sineWave.value` в CEL для правил оповещения ненадёжна; предпочитайте `self.sineWave["value"]` или привязку → производную переменную → alert (как `demo-sensor-01` / `alarmActive`).

## Архитектура конвейера (кратко)

См. [0014 эволюция конвейера автоматизации](decisions/0014-automation-pipeline-evolution.md):

- **Синхронизация:** привязки, WebSocket.
– **Асинхронная шина (двухполосная):** телеметрия (архиватор) и автоматизация (оповещения, рабочие процессы, корреляторы).
- **Объединение:** `RuntimeTelemetryCoalescer` (глобальное значение по умолчанию + переопределение для каждого устройства) перед публикацией `ObjectChangeEvent`
- **Многоуровневый вход:** Буфер драйвера L0 → Буфер сервера L1 → Входная очередь L3 → Объединение L4 → Архиватор L5 **или** Быстрый путь журнала событий L5' ([0026-elastic-telemetry-ingress](decisions/0026-elastic-telemetry-ingress.md), [0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md))
- **Telemetry publish mode:** `FULL` | `TELEMETRY_ONLY` | `EVENT_JOURNAL_ONLY` на driver binding
- **Alert path:** `AlertRuleListener` → CEL → `EventService.fire` → `EventJournalAsyncWriter`
- **Ingress journal path:** `TelemetryEventJournalFastPath` → `EventService.fireIngress` → `EventJournalAsyncWriter` (без HTTP и без alert CEL)
- **Состояние выполнения оповещения:** в памяти (`AlertRuleRuntimeStore`); периодическая очистка дерева объектов (по умолчанию 30 с), а не при каждой оценке
- **Хранилище журнала событий:** prod VPS — `ISPF_EVENT_JOURNAL_STORE=clickhouse` ([0016-clickhouse-event-journal](decisions/0016-clickhouse-event-journal.md)); реляционные данные остаются в PostgreSQL. Резервный вариант: `jdbc` + Timescale ([0015-event-history-timescale](decisions/0015-event-history-timescale.md)). Удержание `ISPF_EVENT_JOURNAL_RETENTION_DAYS` (по умолчанию 90). Скрипты: `deploy/vps-clickhouse-setup.sh`, `deploy/vps-clickhouse-verify.sh`, откат — `deploy/vps-event-journal-jdbc.sh`.

## Связанные документы

- [automation](automation.md) — события, правила оповещений, корреляторы
- [deployment](deployment.md) — развертывание VPS, переменные окружения
- [api](api.md) — `/api/v1/platform/metrics`
- [testing](testing.md) — JUnit/CI
