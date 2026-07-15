> **Язык:** русская версия (вычитка). Канонический английский: [en/lab-mqtt-event-journal-ingress.md](../en/lab-mqtt-event-journal-ingress.md).

# Лаборатория: MQTT event journal ingress (сценарий I-03)

Сценарий **I-03-mqtt-event-journal** проверяет путь **N× mqtt-драйвер → `EVENT_JOURNAL_ONLY` → `fireIngress` → Scylla `event_history`** без historian, шины object-change и правил оповещений.

**Цель:** подтвердить быстрый путь журнала из драйвера ([0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md)) под нагрузкой на **split topology** (ISPF на хосте приложения, Mosquitto + emqtt + Scylla на loadgen/DB).

**Где запускать:** тот же split lab, что I-01 / I-02. В git — адреса [RFC 5737](https://datatracker.ietf.org/doc/html/rfc5737) (`198.51.100.x`); реальные SSH-хосты — только в локальном `deploy/lab-*.env`.

## Конвейер

```text
emqtt-bench (хост loadgen)
    → Mosquitto (tcp://<loadgen-host>:1883)
    → N× mqtt driver (EVENT_JOURNAL_ONLY, ingressCoalesceEnabled=false)
    → TelemetryEventJournalFastPath → EventService.fireIngress
    → EventJournalAsyncWriter → Scylla event_history
```

**Не участвуют:** `variable_samples`, alert CEL, HTTP `/events/fire`, object-change bus.

Smoke в ordered suite: **4** устройства, **~500 msg/s** на устройство, фаза **60 с**, прогрев **20 с**.

## Запуск (split topology)

Шаблоны: [`examples/lab-mqtt-historian-stress/`](../../examples/lab-mqtt-historian-stress/) — скопировать `scripts/`, `env/`, `compose/` в `~/ispf`. Seed-скрипт `setup-mqtt-event-journal-devices.py` — в **gitignored** `deploy/` (копировать в `~/ispf/loadtest/`).

```bash
cd ~/ispf
# shellcheck source=lab-loadgen.env
source lab-loadgen.env

# Smoke / регрессия (PROFILE=smoke по умолчанию)
bash lab-single-mqtt-event-journal-test.sh

# Пик per-topic (16 устройств, свой топик у каждого)
bash lab-run-event-journal-peak.sh

# Цель ~400k events/s (fan-out — см. ниже)
bash lab-run-event-journal-400k.sh
```

Повтор без re-seed:

```bash
SKIP_DEVICE_SETUP=true bash lab-run-event-journal-peak.sh
```

Из ordered suite (single topology):

```bash
python deploy/run_lab_ordered_suite.py --topology single --only I-03 --reset soft
```

## Критерии PASS (скрипт)

| Проверка | Условие |
|----------|---------|
| Пропускная способность журнала | дельта `eventsFiredTotal` ≥ `MIN_RATE_EVENTS × PHASE` |
| Утечка в historian | `variable_samples` остаётся **0** |
| Очередь | `eventJournalQueueSize` → ≤1000 после drain |
| Sync fallback | дельта `eventJournalSyncFallbackTotal` ≈ 0 |

Главные метрики: дельты **`eventsFiredTotal`** и **`eventJournalFlushedTotal`** за окно измерения. `COUNT(*)` по Scylla `event_history` после тяжёлых прогонов может дать **0** или timeout — ориентируйтесь на метрики платформы.

Успешный прогон печатает `=== I-03 PASS ===`.

## Профили

| Профиль | Скрипт | Устройства | Форма нагрузки | Порог PASS (по умолчанию) |
|---------|--------|------------|----------------|---------------------------|
| **smoke** | `lab-single-mqtt-event-journal-test.sh` | 4 | 4×500 msg/s, 2 шарда emqtt | ≥50 events/s |
| **peak** | `lab-run-event-journal-peak.sh` | 16 | 16×32k msg/s (топик на устройство), 8 шардов | ≥80k events/s |
| **400k** | `lab-run-event-journal-400k.sh` | 16 | fan-out: ~26k publish/s × 16 подписчиков | ≥400k events/s |

Переопределение: `DEVICES`, `RATE_PER_DEVICE`, `PHASE`, `WARMUP`, `EMQTT_SHARD_MAX`, `INTERVAL_MS`, `MIN_RATE_EVENTS`, `SHARED_TOPIC`, `MQTT_PUBLISH_RATE`, `EMQTT_CPU_LIMIT`.

## Базовый уровень (split lab, ISPF 0.9.144, журнал Scylla, stress env)

Стенд: приложение `198.51.100.11`, loadgen/DB `198.51.100.10`, `ISPF_EVENT_JOURNAL_STORE=scylla`, очередь журнала 5M, 24 writer threads, MQTT callback threads 256.

| Прогон | Режим | eventsFired (фаза) | Примечания |
|--------|-------|-------------------|------------|
| Smoke | 4×500, per-topic | **~250 events/s** | Регрессия цепочки |
| Peak v3 | 16×32k, per-topic | **~319k events/s** | Mosquitto ~270k msg/s; flushed ≈100% |
| 400k fan-out | 16 подписчиков, 1 топик, ~26k publish/s | **~403k events/s** | flushed 100.2%; MQTT publish ~26k msg/s |

Абсолютные events/s зависят от железа — **не SLA прода**. Таблица — регрессия на одном стенде.

### Потолок per-topic vs fan-out

При **отдельном MQTT-топике на устройство** (16 независимых ingress) стенд упирается в **~330–345k events/s** — Mosquitto может давать больше, но **L0 MQTT / ingress ISPF** насыщается раньше.

Для **≥400k events/s** используйте **fan-out брокера**: все 16 драйверов на **одном** топике (`--shared-topic` / `SHARED_TOPIC=ispf/loadtest/journal-fanout/temperature`). Один publish emqtt доставляется 16 подписчикам → **одно MQTT-сообщение → 16 событий журнала**. Скорость publish ≈ `цель_events / devices` (~26k msg/s для 400k).

## Шаги бенчмарка

| Шаг | Действие |
|-----|----------|
| 1 | `setup-mqtt-event-journal-devices.py` — N× `loadtest-mqtt-dev-*`, `EVENT_JOURNAL_ONLY`, `messageReceived`, historian на `temperature` выключен |
| 2 | Опционально `--shared-topic` для fan-out |
| 3 | Стабилизация драйверов; cleanup emqtt на loadgen |
| 4 | emqtt через `lab-emqtt-remote.sh` |
| 5 | Прогрев + фаза `PHASE` с: дельты `eventsFiredTotal`, `eventJournalFlushedTotal`, Mosquitto `$SYS` |
| 6 | Drain `eventJournalQueueSize` |

## Подводные камни (I-03)

| Проблема | Симптом | Решение |
|----------|---------|---------|
| Брокер `tcp://mqtt:1883` на split lab | ~0 events | `ISPF_MQTT_BROKER_HOST` из `lab-loadgen.env` |
| Битые / CRLF скрипты на edge | emqtt не стартует, ~500 msg/s | Заливать через `scp`, Unix LF |
| Ошибки `\r` в `lab-loadgen-common.sh` | emqtt не запускается | `tr -d '\r'` на хосте |
| Scylla `COUNT(*)` = 0 | ложный FAIL | Метрики `eventsFiredTotal` / `eventJournalFlushedTotal` |
| Цель 400k per-topic | FAIL на ~330–345k | Fan-out: `lab-run-event-journal-400k.sh` |
| Нет seed-скрипта | file not found | Копировать `setup-mqtt-event-journal-devices.py` из `deploy/` |
| Async counter Scylla | meta отстаёт от metrics | Норма при `ASYNC_COUNTER_UPDATE=true` |

## Анонимизация (git)

В git — `198.51.100.x` и placeholder-пароли в [`examples/lab-mqtt-historian-stress/`](../../examples/lab-mqtt-historian-stress/). Только у оператора: `deploy/lab_ssh.py`, реальный `lab-loadgen.env`, `deploy/setup-mqtt-event-journal-devices.py`, `deploy/mqtt_loadtest_lib.py`. Перед push: `python deploy/tools/anonymize-repo.py`.

## Связанные документы

- [lab-event-journal-stress](lab-event-journal-stress.md) — legacy single-host пик (~110k)
- [lab-mqtt-historian-stress](lab-mqtt-historian-stress.md) — I-01 historian
- [lab-mqtt-gateway-ingress](lab-mqtt-gateway-ingress.md) — I-02 gateway
- [load-testing](load-testing.md) — suite I-01…I-08; обезличенные результаты: [`examples/.../reports/ordered-suite-i01-i08.md`](../../examples/lab-mqtt-historian-stress/reports/ordered-suite-i01-i08.md)
- [0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md)
