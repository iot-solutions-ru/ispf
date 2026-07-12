> **Язык:** русская версия (вычитка). Канонический английский: [en/lab-mqtt-gateway-ingress.md](../en/lab-mqtt-gateway-ingress.md).

# Лаборатория: MQTT gateway ingress (сценарий I-02)

Нагрузочный сценарий **I-02-mqtt-gateway** проверяет путь **один MQTT-драйвер → dispatch на дочерние сенсоры → historian**, в отличие от **I-01**, где каждое устройство имеет свой mqtt-драйвер.

**Цель:** убедиться, что `mqtt-gateway-v1` принимает ingress, вызывает `dispatchTelemetry`, создаёт child sensor'ы (lazy `instantiateModelIfMissing`) и пишет сэмплы в historian при `TELEMETRY_ONLY` (без event journal).

**Где запускать:** lab с **разделённой топологией** (ISPF на хосте приложения, Mosquitto + emqtt на хосте loadgen). Реальные адреса — только в локальных `deploy/lab-*.env` (не коммитить).

## Конвейер

```text
emqtt-bench (хост loadgen, NUMERIC_PAYLOAD)
    → Mosquitto (tcp://<loadgen-host>:1883)
    → 1× mqtt-драйвер на loadtest-mqtt-gateway (TELEMETRY_ONLY)
    → lastIngress → dispatchTelemetry
    → до N child sensor'ов (loadtest-mqtt-sensor-00001…)
    → temperature → TelemetryHistorianFastPath → variable_samples (Scylla)
```

Параметры по умолчанию в ordered suite: **4** топика, **~2000 msg/s** суммарно, фаза **60 с**, прогрев **20 с**, `telemetryCoalesceMs=50` на gateway.

## Запуск (single-node, split topology)

На хосте приложения брокер — **не** `tcp://mqtt:1883` (в compose приложения нет Mosquitto). Используйте адрес из `lab-loadgen.env`:

```bash
cd ~/ispf/loadtest
# shellcheck source=../lab-loadgen.env
source ../lab-loadgen.env

ISPF_EMQTT_DOCKER_NETWORK=ispf-lab_default ISPF_EMQTT_SHARD_MAX=2 \
python3 mqtt-ingress-load-test.py \
  --base-url http://127.0.0.1:${ISPF_LAB_HTTP_PORT:-8000} \
  --mode push \
  --broker-url "tcp://${ISPF_MQTT_BROKER_HOST}:${ISPF_MQTT_BROKER_PORT}" \
  --publisher emqtt-bench \
  --publish-via-ssh "${ISPF_LAB_LOADGEN_SSH}" \
  --remote-deploy-dir "${ISPF_LAB_LOADGEN_ROOT}/loadtest" \
  --emqtt-interval-ms 10 \
  --devices 4 --messages-per-second 2000 \
  --phase-seconds 60 --warmup-seconds 20 \
  --skip-monitor-setup \
  --gateway --telemetry-coalesce-ms 50
```

Через обёртку сценария (soft reset между прогонами):

```bash
cd ~/ispf
bash lab-single-scenario-run.sh --soft-reset I-02-mqtt-gateway -- \
  'cd ~/ispf/loadtest && source ../lab-loadgen.env && \
   ISPF_EMQTT_DOCKER_NETWORK=ispf-lab_default ISPF_EMQTT_SHARD_MAX=2 \
   python3 mqtt-ingress-load-test.py ...'   # как выше
```

С рабочей станции (upload скриптов + SSH):

```bash
python deploy/run_lab_ordered_suite.py --topology single --only I-02 --reset soft
```

## Критерии PASS (скрипт)

| Проверка | Условие |
|----------|---------|
| Gateway driver | `RUNNING` + `connected=true` |
| Ingress | live MQTT до фазы измерения (gateway или child `temperature`) |
| Historian | дельта flushed ≥ 50 за окно 60 с, rate ≥ 0,5 samples/s |
| Event journal | `event_history` не растёт (`TELEMETRY_ONLY`) |

Успешный прогон печатает `=== I-02 PASS ===`.

## Что делает `mqtt-ingress-load-test.py --gateway`

| Шаг | Действие |
|-----|----------|
| 1 | Cleanup loadtest mqtt-устройств |
| 2 | Seed: 1× `loadtest-mqtt-gateway`, child'ы **не** создаются заранее — только lazy dispatch |
| 3 | Стабилизация gateway driver (~30 с) |
| 4 | emqtt на loadgen по SSH; `NUMERIC_PAYLOAD=true` (тело `25.0`) |
| 5 | Прогрев, ожидание ingress на `loadtest-mqtt-sensor-00001` |
| 6 | PATCH `historySampleMode=ALL_VALUES` на всех появившихся child sensor'ах |
| 7 | Измерение 60 с: дельта **`variableHistoryFlushedTotal`** (не `sampleCount`) |

## Подводные камни (I-02)

| Проблема | Симптом | Решение |
|----------|---------|---------|
| Брокер `tcp://mqtt:1883` на split lab | ingress ~0, gateway без трафика | `ISPF_MQTT_BROKER_HOST` + `--publish-via-ssh` из `lab-loadgen.env` |
| Метрика `sampleCount` в historian | FAIL при живом historian после I-01 stress | Дельта по **`variableHistoryFlushedTotal`** / `flushedTotal`; `COUNT(*)` на Scylla даёт 0/timeout |
| `CHANGES_ONLY` (по умолчанию с 0.9.139) + `NUMERIC_PAYLOAD` | мало или 0 сэмплов на child | `ALL_VALUES` на `temperature` child'ов; в instance type `mqtt-gateway-sensor-v1` — `historySampleMode: ALL_VALUES` |
| `NUMERIC_PAYLOAD` не на remote emqtt | timestamp в payload, нестабильный parse | Префикс `NUMERIC_PAYLOAD=true` в SSH-команде `mqtt-emqtt-bench.sh` |
| Короткая длительность publisher | measure без трафика | duration ≥ warmup + wait + phase (~200 с) |
| Stale `lastIngress` + constant payload | WARN ingress при работающем dispatch | Gate: live child `temperature` или waiver при historian delta ≥ 50 |
| Soft reset, Scylla на remote DB | preflight exit 1, `scylla_count=-1` | `lab-single-soft-reset.py` truncate через `ISPF_LAB_DB_SSH`; `--skip-scylla-verify` если COUNT медленный |
| Долгая нагрузка / 403 на API | login/metrics 403 | Перезапуск `ispf-server` + nginx на хосте приложения |

## Базовый уровень (lab, split topology)

Стенд: single-node ISPF на хосте приложения, Scylla + Mosquitto на хосте loadgen, historian store **Scylla**, stress env (очередь historian 8M). После прогонов I-01 на том же стенде таблица `variable_samples` велика — ориентируйтесь на **flushed**, не на `sampleCount`.

| Параметр | Значение |
|----------|----------|
| Устройства (топики) | 4 |
| Publish rate | ~2000 msg/s |
| Historian rate (measure) | **~2,3k samples/s** (flushed delta) |
| Child sensors | 4 (lazy) |
| Gateway coalesce | 50 ms |
| Результат | **I-02 PASS** |

Абсолютные samples/s зависят от железа и профиля stress; таблица — ориентир для регрессии на **одном** lab, не SLA прода.

## Связь с I-01 (historian stress)

| | I-01 | I-02 |
|---|------|------|
| MQTT подключений | N драйверов (по устройству) | 1 gateway |
| Historian target | `temperature` на каждом device | `temperature` на child после dispatch |
| Типичный rate | 250k–500k msg/s (stress) | ~2k msg/s (ingress suite) |
| Главная метрика | `variableHistoryFlushedTotal` | то же (**не** `sampleCount`) |

См. [lab-mqtt-historian-stress](lab-mqtt-historian-stress.md) — стресс historian и `ALL_VALUES` для loadtest seed.

## Ключевые файлы

| Файл | Назначение |
|------|------------|
| `deploy/mqtt-ingress-load-test.py` | Тело I-02/I-04/I-08; флаги `--gateway`, PASS/FAIL |
| `deploy/mqtt_loadtest_lib.py` | Seed gateway, `enable_gateway_children_history`, flushed total |
| `deploy/mqtt-emqtt-bench.sh` | emqtt; `NUMERIC_PAYLOAD`, shard |
| `deploy/lab-single-scenario-run.sh` | Обёртка single-node + reset |
| `deploy/lab-single-soft-reset.py` | Soft reset + remote Scylla truncate |
| `deploy/run_lab_ordered_suite.py` | Suite I-01…I-08; `SINGLE_LOADTEST_COMMON` с loadgen broker |
| `deploy/lab-loadgen.env` | `ISPF_MQTT_BROKER_HOST`, `ISPF_LAB_LOADGEN_SSH` (локально, gitignored) |

Метрики: `GET /api/v1/platform/metrics` → `automation.variableHistoryFlushedTotal`, `variableHistory.flushedTotal`.

## Связанные документы

- [lab-mqtt-historian-stress](lab-mqtt-historian-stress.md) — I-01 historian flood
- [load-testing](load-testing.md) — ordered suite, clean slate
- [decisions/0017-telemetry-ingest-pipeline](decisions/0017-telemetry-ingest-pipeline.md) — gateway dispatch
- [variable-history](variable-history.md) — `historySampleMode`, CHANGES_ONLY vs ALL_VALUES
